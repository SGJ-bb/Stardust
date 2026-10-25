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
            if (existing != null && existing.content.isNotBlank()) {
                sb.appendLine(existing.content)
                sb.appendLine()
                sb.appendLine("---")
                sb.appendLine()
            }
            if (uiMap.isNotBlank()) {
                sb.appendLine("## UI 结构知识（自动归纳，以当前屏幕为准）")
                sb.appendLine(uiMap)
                sb.appendLine()
            }
            if (taskName.isNotBlank() && workflow.isNotBlank()) {
                sb.appendLine("## 任务：$taskName")
                sb.appendLine(workflow)
                if (triggers.isNotEmpty()) sb.appendLine("触发词：${triggers.joinToString("、")}")
            }

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
