package com.aicompanion.screen

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * APP UI 知识库管理器：学习型框架的核心
 *
 * 设计哲学：
 * 旧方案（已废弃）：把 raw actions 存下来下次盲目回放 —— 脆弱，屏幕稍变就崩。
 * 新方案：让 LLM 归纳"UI 结构知识"（页面布局、关键元素、导航路径、任务 workflow），
 *       后续使用时把这份知识作为"先验"喂给 LLM，由 LLM 基于知识重新规划动作。
 *       这样 LLM 真正"懂"UI，而非闭眼按上次的路径点。
 *
 * Skill 存储：filesDir/app_ui_skills.json，JSON 数组格式，每个元素是一个 APP 的 UI 知识
 */
class AppSkillManager(private val context: Context) {

    companion object {
        private const val FILE_NAME = "app_ui_skills.json"
        private const val TAG = "AppSkillManager"
    }

    /**
     * 单个 APP 的 UI 知识。
     * @param uiMap LLM 归纳的 UI 结构描述（自然语言），包含主要页面的布局、关键元素、导航关系
     * @param knownTasks 已学习过的任务，每个任务记录高层 workflow（自然语言步骤，非 raw click）
     */
    data class Skill(
        val id: String,
        val appName: String,
        val appPackage: String = "",   // 包名（用于精准识别 APP，可为空）
        val uiMap: String,             // UI 结构知识（自然语言）
        val knownTasks: List<KnownTask>,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
        val useCount: Int = 0,
        val successCount: Int = 0
    )

    /** 已学习的任务：记录 workflow（自然语言高层步骤），而非 raw actions */
    data class KnownTask(
        val taskName: String,
        val triggerKeywords: List<String>,
        val workflow: String,          // 自然语言描述的高层步骤（如："1.点击底部'微信'tab 2.点击目标聊天 3.输入框输入文本 4.点击发送"）
        val lastSuccessAt: Long = 0L
    )

    private val skillFile: File by lazy { File(context.filesDir, FILE_NAME) }

    /** 读取所有 skill */
    fun getAllSkills(): List<Skill> {
        return try {
            if (!skillFile.exists()) return emptyList()
            val content = skillFile.readText()
            if (content.isBlank()) return emptyList()
            val arr = JSONArray(content)
            (0 until arr.length()).map { idx ->
                jsonToSkill(arr.getJSONObject(idx))
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "getAllSkills error: ${e.message}")
            emptyList()
        }
    }

    /**
     * 获取指定 APP 的完整 UI 知识（含 uiMap 和 knownTasks），供 LLM 规划时作为先验。
     * @param appName APP 名（如"微信"），精确匹配优先，模糊匹配兜底
     * @return 完整 Skill 或 null
     *
     * 模糊匹配的歧义处理：互相包含时（如"QQ" vs "QQ音乐"），返回长度差最小的
     * （即最相近的那个），避免短名误命中长名 APP。
     */
    fun getSkillForApp(appName: String): Skill? {
        val skills = getAllSkills()
        // 1. 精确匹配优先
        skills.find { it.appName == appName }?.let { return it }
        // 2. 模糊匹配：互相包含时，返回长度差最小的（最相近）
        return skills
            .filter { appName in it.appName || it.appName in appName }
            .minByOrNull { Math.abs(it.appName.length - appName.length) }
    }

    /**
     * 保存或更新 skill（同 appName 视为更新）。
     * 更新时合并已知任务（同 taskName 的更新 workflow，新增的追加），uiMap 取较新版本。
     */
    fun saveSkill(skill: Skill): Boolean {
        return try {
            val skills = getAllSkills().toMutableList()
            val existingIdx = skills.indexOfFirst { it.appName == skill.appName }
            if (existingIdx >= 0) {
                val existing = skills[existingIdx]
                // 合并 knownTasks：同 taskName 用新 workflow 覆盖，新 task 追加
                val mergedTasks = existing.knownTasks.toMutableList()
                skill.knownTasks.forEach { newTask ->
                    val idx = mergedTasks.indexOfFirst { it.taskName == newTask.taskName }
                    if (idx >= 0) mergedTasks[idx] = newTask
                    else mergedTasks.add(newTask)
                }
                // uiMap：若新版本非空则用新版本（更完整），否则保留旧的
                val mergedUiMap = if (skill.uiMap.isNotBlank()) skill.uiMap else existing.uiMap
                skills[existingIdx] = skill.copy(
                    id = existing.id,
                    appPackage = skill.appPackage.ifBlank { existing.appPackage },
                    uiMap = mergedUiMap,
                    knownTasks = mergedTasks,
                    createdAt = existing.createdAt,
                    updatedAt = System.currentTimeMillis(),
                    useCount = existing.useCount,
                    successCount = existing.successCount
                )
            } else {
                skills.add(skill)
            }
            writeSkills(skills)
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "saveSkill error: ${e.message}")
            false
        }
    }

    /** 增加使用次数（执行任务时调用） */
    fun incrementUseCount(appName: String, success: Boolean) {
        try {
            val skills = getAllSkills().toMutableList()
            val idx = skills.indexOfFirst { it.appName == appName }
            if (idx >= 0) {
                val skill = skills[idx]
                skills[idx] = skill.copy(
                    useCount = skill.useCount + 1,
                    successCount = if (success) skill.successCount + 1 else skill.successCount,
                    updatedAt = System.currentTimeMillis()
                )
                writeSkills(skills)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "incrementUseCount error: ${e.message}")
        }
    }

    /** 删除 skill */
    fun deleteSkill(id: String): Boolean {
        return try {
            val skills = getAllSkills().toMutableList()
            val removed = skills.removeAll { it.id == id }
            if (removed) writeSkills(skills)
            removed
        } catch (e: Exception) {
            false
        }
    }

    private fun writeSkills(skills: List<Skill>) {
        val arr = JSONArray()
        skills.forEach { arr.put(skillToJson(it)) }
        skillFile.writeText(arr.toString())
    }

    private fun skillToJson(skill: Skill): JSONObject {
        val tasksArr = JSONArray()
        skill.knownTasks.forEach { task ->
            tasksArr.put(JSONObject().apply {
                put("taskName", task.taskName)
                put("triggerKeywords", JSONArray(task.triggerKeywords))
                put("workflow", task.workflow)
                put("lastSuccessAt", task.lastSuccessAt)
            })
        }
        return JSONObject().apply {
            put("id", skill.id)
            put("appName", skill.appName)
            put("appPackage", skill.appPackage)
            put("uiMap", skill.uiMap)
            put("knownTasks", tasksArr)
            put("createdAt", skill.createdAt)
            put("updatedAt", skill.updatedAt)
            put("useCount", skill.useCount)
            put("successCount", skill.successCount)
        }
    }

    private fun jsonToSkill(obj: JSONObject): Skill {
        val tasks = obj.optJSONArray("knownTasks")?.let { arr ->
            (0 until arr.length()).map { i ->
                val t = arr.getJSONObject(i)
                val keywords = t.optJSONArray("triggerKeywords")?.let { kArr ->
                    (0 until kArr.length()).map { kArr.getString(it) }
                } ?: emptyList()
                KnownTask(
                    taskName = t.optString("taskName"),
                    triggerKeywords = keywords,
                    workflow = t.optString("workflow"),
                    lastSuccessAt = t.optLong("lastSuccessAt", 0L)
                )
            }
        } ?: emptyList()
        return Skill(
            id = obj.optString("id"),
            appName = obj.optString("appName"),
            appPackage = obj.optString("appPackage", ""),
            uiMap = obj.optString("uiMap"),
            knownTasks = tasks,
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            useCount = obj.optInt("useCount", 0),
            successCount = obj.optInt("successCount", 0)
        )
    }
}
