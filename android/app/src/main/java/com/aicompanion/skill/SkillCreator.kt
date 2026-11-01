package com.aicompanion.skill

import android.content.Context
import com.aicompanion.network.ApiClient
import com.aicompanion.util.AppLogger
import org.json.JSONObject

/**
 * 技能创建器（Skill Creator）：
 * 1) 一句话生成技能（AI 写正文）
 * 2) 从一次成功操作轨迹归纳技能（与 operate_phone 的学习闭环打通）
 * 3) 手动创建空技能模板 / 在已有技能基础上按反馈改进
 */
object SkillCreator {

    private const val TAG = "SkillCreator"

    /** 自动归纳技能最多保留几个「任务」小节（超出丢最老的） */
    private const val MAX_TASKS = 6

    /** 自动归纳技能正文上限：技能会整段塞进系统提示词，太长挤占上下文 */
    private const val MAX_CHARS = 4000

    /** 一句话（可附当前屏幕）生成技能 */
    suspend fun createFromDescription(
        context: Context,
        apiClient: ApiClient,
        requirement: String,
        screenContext: String = "",
        packageName: String = "",
        existing: Skill? = null
    ): Result<Skill> {
        return try {
            val raw = apiClient.generateSkill(
                requirement = requirement,
                screenContext = screenContext,
                packageName = packageName.ifBlank { existing?.appPackage ?: "" },
                existingContent = existing?.let { "${it.toMarkdown()}" } ?: ""
            )
            if (raw.isNullOrBlank()) {
                return Result.failure(IllegalStateException("AI 没有返回内容，请检查 API 配置"))
            }
            val skill = parseSkillJson(raw, requirement, existing)
            if (SkillStore(context).save(skill)) {
                AppLogger.i(TAG, "AI 生成技能成功: ${skill.displayName}")
                Result.success(skill)
            } else {
                Result.failure(IllegalStateException("技能保存失败"))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "createFromDescription 失败: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 从一次成功操作轨迹归纳技能（operate_phone 执行成功后调用）。
     * 复用 ApiClient.summarizeSkill 的产物，转成统一 Skill 结构。
     */
    suspend fun createFromOperationTrace(
        context: Context,
        apiClient: ApiClient,
        userRequest: String,
        actionsJson: String
    ): Skill? {
        return try {
            val raw = apiClient.summarizeSkill(userRequest, actionsJson) ?: return null
            val obj = JSONObject(raw)
            val appName = obj.optString("appName", "")
            val uiMap = obj.optString("uiMap", "")
            val taskName = obj.optString("taskName", "")
            val workflow = obj.optString("workflow", "")
            if (appName.isBlank() && workflow.isBlank()) return null

            val triggers = obj.optJSONArray("triggerKeywords")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
            } ?: emptyList()

            val store = SkillStore(context)
            val id = "appui_" + Skill.makeId(appName.ifBlank { taskName }, "app").removePrefix("sk_")
            val existing = store.getById(id)

            val sb = StringBuilder()
            val merged = mergeContent(
                existing = existing?.content.orEmpty(),
                uiMap = uiMap,
                taskName = taskName,
                workflow = workflow,
                triggers = triggers
            )
            if (merged.isBlank()) return null
            sb.append(merged)

            val skill = Skill(
                id = id,
                name = existing?.name ?: "${appName.ifBlank { "通用" }} 操作技能",
                description = existing?.description ?: (if (taskName.isNotBlank()) "自动归纳：$taskName" else "自动归纳的操作知识"),
                scope = if (appName.isBlank()) "global" else "app",
                appName = appName,
                appPackage = obj.optString("appPackage", "").ifBlank { existing?.appPackage ?: "" },
                triggers = (existing?.triggers ?: emptyList()).plus(triggers).distinct(),
                content = sb.toString().trim(),
                source = "auto"
            )
            if (store.save(skill)) skill else null
        } catch (e: Exception) {
            AppLogger.e(TAG, "createFromOperationTrace 失败: ${e.message}")
            null
        }
    }

    /**
     * 把新的一次操作归纳**合并**进已有技能，而不是无脑往后追加。
     *
     * 老实现是 `existing + "---" + 新内容`，同一个 App 每跑一次任务就多贴一段，
     * 实测「哔哩哔哩 操作技能」十几次之后就变成 rev 13、正文里**同样的 UI 描述重复七八遍**，
     * 既占满上下文（模型读到一大坨重复内容，反而抓不住重点），也没法维护。
     *
     * 现在的规则：
     * - 按 `## 标题` 切块，同标题**只保留最新一份**（UI 结构知识、同名任务自动覆盖）
     * - 不同任务各自一节，互不覆盖
     * - 体积超了就丢最老的几节（`MAX_SECTIONS`），永远保留 UI 结构 + 最近的任务
     */
    private fun mergeContent(
        existing: String,
        uiMap: String,
        taskName: String,
        workflow: String,
        triggers: List<String>
    ): String {
        val uiBlocks = LinkedHashMap<String, String>()      // 只留一份
        val taskBlocks = LinkedHashMap<String, String>()     // 按任务名去重，后来的覆盖先前的
        val otherBlocks = LinkedHashMap<String, String>()

        fun put(title: String, body: String) {
            val t = title.trim()
            if (t.isEmpty()) return
            when {
                t.startsWith("UI 结构知识") -> uiBlocks["UI 结构知识"] = body
                t.startsWith("任务：") -> taskBlocks[t] = body
                else -> if (!otherBlocks.containsKey(t)) otherBlocks[t] = body
            }
        }

        if (existing.isNotBlank()) {
            existing.split(Regex("(?m)^## ")).forEach { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) return@forEach
                val nl = trimmed.indexOf('\n')
                val title = if (nl < 0) trimmed else trimmed.substring(0, nl)
                var body = if (nl < 0) "" else trimmed.substring(nl + 1).trim()
                // 清掉上一版遗留的分隔线和空节
                body = body.lines()
                    .filterNot { it.trim() == "---" }
                    .joinToString("\n").trim()
                put(title, body)
            }
        }

        if (uiMap.isNotBlank()) uiBlocks["UI 结构知识"] = uiMap.trim()
        if (taskName.isNotBlank() && workflow.isNotBlank()) {
            val body = buildString {
                appendLine(workflow.trim())
                if (triggers.isNotEmpty()) append("触发词：${triggers.joinToString("、")}")
            }.trim()
            taskBlocks["任务：$taskName"] = body
        }

        // 体积控制：只保留最近 MAX_TASKS 个任务
        val keptTasks = if (taskBlocks.size > MAX_TASKS) {
            taskBlocks.entries.toList().takeLast(MAX_TASKS)
        } else {
            taskBlocks.entries.toList()
        }

        val out = StringBuilder()
        uiBlocks["UI 结构知识"]?.takeIf { it.isNotBlank() }?.let {
            out.appendLine("## UI 结构知识（自动归纳，以当前屏幕为准）")
            out.appendLine(it)
            out.appendLine()
        }
        otherBlocks.forEach { (t, b) ->
            if (b.isNotBlank()) {
                out.appendLine("## $t")
                out.appendLine(b)
                out.appendLine()
            }
        }
        keptTasks.forEach { (t, b) ->
            out.appendLine("## $t")
            out.appendLine(b)
            out.appendLine()
        }
        return out.toString().trim().take(MAX_CHARS)
    }

    /** 手动创建空模板，用户可在 App 内编辑 */
    fun createTemplate(name: String, appName: String = "", appPackage: String = ""): Skill {
        val id = Skill.makeId(name, "sk")
        val content = buildString {
            appendLine("## 适用")
            appendLine(if (appName.isBlank()) "- 通用流程" else "- App：$appName")
            appendLine()
            appendLine("## 步骤")
            appendLine("1. ")
            appendLine("2. ")
            appendLine("3. ")
            appendLine()
            appendLine("## 注意事项")
            appendLine("- 找不到元素时：先 scroll 翻页，再 read 重新读取屏幕")
            appendLine("- 遇到支付/验证码/密码页面：立即停止并向用户确认")
            appendLine()
            appendLine("## 危险环节（需要用户确认）")
            appendLine("- ")
        }
        return Skill(
            id = id,
            name = name,
            description = "",
            scope = if (appName.isBlank()) "global" else "app",
            appName = appName,
            appPackage = appPackage,
            triggers = listOf(name),
            content = content,
            source = "manual"
        )
    }

    /** 把 AI 返回的 JSON 解析为 Skill；解析失败则退化为「把原文当正文」 */
    private fun parseSkillJson(raw: String, requirement: String, existing: Skill?): Skill {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            val obj = JSONObject(cleaned)
            val name = obj.optString("name", "").ifBlank { requirement.take(20) }
            val triggers = obj.optJSONArray("triggers")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
            } ?: emptyList()
            Skill(
                id = existing?.id ?: Skill.makeId(name, "sk"),
                name = name,
                description = obj.optString("description", ""),
                scope = obj.optString("scope", if (existing?.scope != null) existing.scope else "app"),
                appName = obj.optString("appName", "").ifBlank { existing?.appName ?: "" },
                appPackage = obj.optString("appPackage", "").ifBlank { existing?.appPackage ?: "" },
                triggers = triggers.ifEmpty { listOf(name) },
                content = obj.optString("content", "").ifBlank { cleaned },
                source = "ai"
            )
        } catch (_: Exception) {
            Skill(
                id = existing?.id ?: Skill.makeId(requirement, "sk"),
                name = existing?.name ?: requirement.take(20),
                description = existing?.description ?: "由 AI 根据需求生成",
                scope = existing?.scope ?: "app",
                appName = existing?.appName ?: "",
                appPackage = existing?.appPackage ?: "",
                triggers = existing?.triggers ?: listOf(requirement.take(12)),
                content = cleaned,
                source = "ai"
            )
        }
    }
}
