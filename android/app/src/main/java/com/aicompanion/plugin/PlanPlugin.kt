package com.aicompanion.plugin

import android.content.Context
import com.aicompanion.models.ToolDefinition
import org.json.JSONArray
import org.json.JSONObject

/**
 * 任务计划工具：把「多步任务」显式写出来，并在每步完成后更新状态。
 *
 * 为什么需要它（这是 agent 与"一次性问答"的分水岭）：
 * - 长任务里模型容易忘记自己做到第几步、还剩什么，表现为反复做同一件事或提前宣布完成；
 * - 把计划落到一个可读写的状态里，模型每轮都能 re-read，用户也能看到进度；
 * - 完成判定变成"计划里所有步骤都是 done"，而不是模型自己觉得差不多了。
 *
 * 存储用 SharedPreferences（进程被杀也能恢复），只保留最近一个任务。
 */
class PlanPlugin(private val context: Context) : ToolPlugin {

    override val name = "plan"

    override val description = "维护多步任务的计划清单（列步骤 / 更新进度 / 读取当前计划）"

    private val prefs = context.getSharedPreferences("agent_plan", Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = true

    override fun getDefinition() = ToolDefinition(
        name = "plan",
        description = """维护「任务计划清单」。做需要 3 步以上的任务时，必须先建计划，再逐步执行。

【什么时候用】
- 用户给的任务需要多步（写文件+验证、查资料+整理、改配置+重启+复查）
- 中途被打断，需要知道还剩什么
- 任务失败要回退/换方案，需要重写计划

【action】
- set：新建/重写计划。steps = 步骤标题数组（每步只做一件事，动词开头，5-20 字）
- update：更新某步状态。index 从 1 开始，status = doing / done / failed
- get：读取当前计划和进度

【纪律】
1. 先 set，再动手；不要边做边改计划
2. 每次只把一步标 doing，做完立刻标 done（或 failed）
3. 全部 done 才能宣布任务完成；有 failed 必须说明原因和替代方案
4. 步骤要可验证：写"读回文件确认内容"，不要写"处理文件"
""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("set", "update", "get"),
                    "description" to "set=新建计划；update=更新某步状态；get=读取当前计划"
                ),
                "steps" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string"),
                    "description" to "action=set 时的步骤标题列表（动词开头，每步只做一件事）"
                ),
                "index" to mapOf(
                    "type" to "integer",
                    "description" to "action=update 时的步骤序号，从 1 开始"
                ),
                "status" to mapOf(
                    "type" to "string",
                    "enum" to listOf("doing", "done", "failed"),
                    "description" to "action=update 时的新状态"
                ),
                "note" to mapOf(
                    "type" to "string",
                    "description" to "可选备注：失败原因、产出文件路径等"
                ),
                "goal" to mapOf(
                    "type" to "string",
                    "description" to "action=set 时的任务目标一句话"
                )
            ),
            "required" to listOf("action")
        )
    )

    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        return when (args.optString("action", "get").lowercase()) {
            "set" -> setPlan(args)
            "update" -> updateStep(args)
            else -> render()
        }
    }

    private fun setPlan(args: JSONObject): String {
        val stepsJson = args.optJSONArray("steps") ?: JSONArray()
        if (stepsJson.length() == 0) return "steps 不能为空：请给出 2 个以上的步骤标题"
        val arr = JSONArray()
        for (i in 0 until stepsJson.length()) {
            arr.put(
                JSONObject().apply {
                    put("title", stepsJson.optString(i))
                    put("status", "todo")
                    put("note", "")
                }
            )
        }
        prefs.edit()
            .putString("goal", args.optString("goal", ""))
            .putString("steps", arr.toString())
            .putLong("createdAt", System.currentTimeMillis())
            .apply()
        return "计划已建立（共 ${arr.length()} 步）：\n" + render()
    }

    private fun updateStep(args: JSONObject): String {
        val steps = loadSteps()
        if (steps.length() == 0) return "当前没有计划，请先 action=set"
        val index = args.optInt("index", 0)
        if (index < 1 || index > steps.length()) {
            return "index 越界：当前共 ${steps.length()} 步（从 1 开始）"
        }
        val item = steps.getJSONObject(index - 1)
        val status = args.optString("status", "done").lowercase()
        if (status !in listOf("doing", "done", "failed")) return "status 只能是 doing/done/failed"
        item.put("status", status)
        val note = args.optString("note", "")
        if (note.isNotBlank()) item.put("note", note)
        prefs.edit().putString("steps", steps.toString()).apply()
        return "已更新第 $index 步 → $status\n" + render()
    }

    private fun loadSteps(): JSONArray = runCatching {
        JSONArray(prefs.getString("steps", "[]"))
    }.getOrElse { JSONArray() }

    /** 进度用 ✅/🔄/❌/⬜ 表示，模型下一轮读到就能判断该继续还是收尾 */
    private fun render(): String {
        val steps = loadSteps()
        if (steps.length() == 0) return "当前没有计划。多步任务请先 action=set 建计划。"
        val sb = StringBuilder()
        val goal = prefs.getString("goal", "").orEmpty()
        if (goal.isNotBlank()) sb.append("目标：").append(goal).append('\n')
        var done = 0
        for (i in 0 until steps.length()) {
            val it = steps.getJSONObject(i)
            val st = it.optString("status", "todo")
            val mark = when (st) {
                "done" -> "✅"
                "doing" -> "🔄"
                "failed" -> "❌"
                else -> "⬜"
            }
            if (st == "done") done++
            sb.append(mark).append(' ').append(i + 1).append(". ").append(it.optString("title"))
            val note = it.optString("note", "")
            if (note.isNotBlank()) sb.append("（").append(note).append("）")
            sb.append('\n')
        }
        sb.append("进度：").append(done).append('/').append(steps.length())
        if (done == steps.length()) sb.append(" —— 全部完成，可以收尾")
        return sb.toString().trimEnd()
    }
}
