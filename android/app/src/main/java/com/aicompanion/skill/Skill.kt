package com.aicompanion.skill

import org.json.JSONArray
import org.json.JSONObject

/**
 * 技能（Skill）：可复用的「操作知识 / 流程说明」，供 AI 在自动化不同 App 时加载。
 *
 * 设计参考 Claude Skills 的渐进式披露：
 * · AI 平时只看到「技能清单」（name + description + 适用 App），token 开销极小；
 * · 需要时再用 skill(action=get) 读取正文（详细步骤、注意事项、坐标/资源ID 约定）；
 * · 正文里也可以写 shell 命令、HTTP 调用等，配合 run_shell 工具执行。
 *
 * 与旧 AppSkillManager（app_ui_skills.json）的关系：
 * 旧结构偏「自动归纳的 UI 地图」，本结构是「可读可写可分享的技能条目」。
 * SkillStore 启动时会把旧数据迁移成 scope=app 的技能，二者并存不冲突。
 */
data class Skill(
    val id: String,
    val name: String,
    val description: String = "",
    /** "app" = 针对某个 App；"global" = 通用流程（不限 App） */
    val scope: String = "app",
    val appName: String = "",
    val appPackage: String = "",
    /** 触发词：用户指令里出现这些词时优先加载本技能 */
    val triggers: List<String> = emptyList(),
    /** Markdown 正文 */
    val content: String = "",
    val enabled: Boolean = true,
    /** manual=用户手写 | ai=AI 生成 | auto=自动归纳 */
    val source: String = "manual",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val useCount: Int = 0,
    val successCount: Int = 0
) {
    val displayName: String get() = name.ifBlank { id }

    val successRate: Int
        get() = if (useCount <= 0) 0 else (successCount * 100 / useCount)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("scope", scope)
        put("appName", appName)
        put("appPackage", appPackage)
        put("triggers", JSONArray(triggers))
        put("content", content)
        put("enabled", enabled)
        put("source", source)
        put("version", version)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("useCount", useCount)
        put("successCount", successCount)
    }

    /** 导出为带 YAML frontmatter 的 Markdown，便于复制/分享 */
    fun toMarkdown(): String = buildString {
        appendLine("---")
        appendLine("id: $id")
        appendLine("name: $name")
        appendLine("description: $description")
        appendLine("scope: $scope")
        if (appPackage.isNotBlank()) appendLine("appPackage: $appPackage")
        if (appName.isNotBlank()) appendLine("appName: $appName")
        if (triggers.isNotEmpty()) appendLine("triggers: ${triggers.joinToString(", ")}")
        appendLine("source: $source")
        appendLine("---")
        appendLine()
        append(content)
    }

    companion object {

        fun fromJson(obj: JSONObject): Skill = Skill(
            id = obj.optString("id"),
            name = obj.optString("name"),
            description = obj.optString("description", ""),
            scope = obj.optString("scope", "app"),
            appName = obj.optString("appName", ""),
            appPackage = obj.optString("appPackage", ""),
            triggers = obj.optJSONArray("triggers")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
            } ?: emptyList(),
            content = obj.optString("content", ""),
            enabled = obj.optBoolean("enabled", true),
            source = obj.optString("source", "manual"),
            version = obj.optInt("version", 1),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            useCount = obj.optInt("useCount", 0),
            successCount = obj.optInt("successCount", 0)
        )

        /** 解析带 frontmatter 的 Markdown（导入用）；无 frontmatter 时整体作为正文 */
        fun fromMarkdown(text: String, fallbackId: String): Skill {
            val normalized = text.replace("\r\n", "\n").trim()
            if (!normalized.startsWith("---")) {
                return Skill(id = fallbackId, name = fallbackId, content = normalized, source = "manual")
            }
            val end = normalized.indexOf("\n---", startIndex = 3)
            if (end < 0) {
                return Skill(id = fallbackId, name = fallbackId, content = normalized, source = "manual")
            }
            val header = normalized.substring(3, end)
            val body = normalized.substring(end + 4).trim()
            val map = HashMap<String, String>()
            header.lines().forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
                }
            }
            val triggers = map["triggers"]?.split(',', '，')
                ?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            return Skill(
                id = map["id"]?.takeIf { it.isNotBlank() } ?: fallbackId,
                name = map["name"]?.takeIf { it.isNotBlank() } ?: fallbackId,
                description = map["description"].orEmpty(),
                scope = map["scope"]?.takeIf { it.isNotBlank() } ?: "app",
                appName = map["appName"].orEmpty(),
                appPackage = map["appPackage"].orEmpty(),
                triggers = triggers,
                content = body,
                source = map["source"]?.takeIf { it.isNotBlank() } ?: "manual"
            )
        }

        /** 由中文/英文名生成稳定的技能 ID */
        fun makeId(name: String, prefix: String = "sk"): String {
            val slug = name.trim().replace(" ", "_")
                .replace(Regex("[^a-zA-Z0-9_\\u4e00-\\u9fa5]"), "")
                .take(24)
            return if (slug.isBlank()) "${prefix}_${System.currentTimeMillis()}" else "${prefix}_$slug"
        }
    }
}
