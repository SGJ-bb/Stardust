package com.aicompanion.skill

import android.content.Context
import android.util.Log
import com.aicompanion.screen.AppSkillManager
import org.json.JSONArray
import java.io.File

/**
 * 技能仓库：`filesDir/skills.json` 单文件持久化（含正文），并提供：
 * · 清单渲染（渐进式披露，给 LLM 看的是 name + description + 适用 App）
 * · 指令/包名匹配（决定这一步要加载哪些技能）
 * · 与旧 AppSkillManager（app_ui_skills.json）的一次性迁移
 * · Markdown 导入导出
 */
class SkillStore(private val context: Context) {

    companion object {
        private const val TAG = "SkillStore"
        private const val FILE_NAME = "skills.json"
        private const val MIGRATION_FLAG = "skill_migrated_from_appui_v1"
        /** 内置技能所在 assets 目录 */
        private const val BUNDLED_DIR = "skills"
        /** 记录每个内置技能已导入到第几版（frontmatter 的 rev） */
        private const val BUNDLED_PREFS = "skill_bundled"
        private val regexRev = Regex("(?m)^rev:\\s*(\\d+)")
    }

    private val file: File get() = File(context.filesDir, FILE_NAME)

    // ==================== 读写 ====================

    @Synchronized
    fun getAll(): List<Skill> {
        return try {
            if (!file.exists()) return emptyList()
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                try { Skill.fromJson(arr.getJSONObject(i)) } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAll error: ${e.message}")
            emptyList()
        }
    }

    fun getEnabled(): List<Skill> = getAll().filter { it.enabled }

    fun getById(id: String): Skill? = getAll().firstOrNull { it.id == id }

    fun findByName(name: String): Skill? = getAll().firstOrNull {
        it.name == name || it.displayName == name || it.id == name
    }

    @Synchronized
    fun save(skill: Skill): Boolean {
        return try {
            val list = getAll().toMutableList()
            val idx = list.indexOfFirst { it.id == skill.id }
            if (idx >= 0) {
                val old = list[idx]
                list[idx] = skill.copy(
                    createdAt = old.createdAt,
                    updatedAt = System.currentTimeMillis(),
                    version = old.version + 1,
                    useCount = old.useCount,
                    successCount = old.successCount
                )
            } else {
                list.add(skill)
            }
            write(list)
            true
        } catch (e: Exception) {
            Log.e(TAG, "save error: ${e.message}")
            false
        }
    }

    @Synchronized
    fun delete(id: String): Boolean = try {
        val list = getAll().toMutableList()
        val removed = list.removeAll { it.id == id }
        if (removed) write(list)
        removed
    } catch (_: Exception) {
        false
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean): Boolean = try {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) false else {
            list[idx] = list[idx].copy(enabled = enabled, updatedAt = System.currentTimeMillis())
            write(list)
            true
        }
    } catch (_: Exception) {
        false
    }

    fun record(id: String, success: Boolean) {
        try {
            val list = getAll().toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) return
            val s = list[idx]
            list[idx] = s.copy(
                useCount = s.useCount + 1,
                successCount = if (success) s.successCount + 1 else s.successCount
            )
            write(list)
        } catch (_: Exception) {}
    }

    private fun write(list: List<Skill>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString())
    }

    // ==================== 匹配与渲染 ====================

    /**
     * 按「用户指令 + 当前 App」给技能打分并取 top-N。
     * 打分：包名精确 6 分 / App 名精确 5 分 / 触发词每命中 4 分 / 名称命中 3 分 / 通用水 1 分。
     */
    fun match(instruction: String, packageName: String = "", appName: String = "", limit: Int = 3): List<Skill> {
        val scored = getEnabled().mapNotNull { s ->
            var score = 0
            if (s.scope == "global") score += 1
            if (packageName.isNotBlank() && s.appPackage.isNotBlank() && s.appPackage == packageName) score += 6
            if (appName.isNotBlank() && s.appName.isNotBlank()) {
                if (s.appName == appName) score += 5
                else if (appName.contains(s.appName) || s.appName.contains(appName)) score += 2
            }
            score += s.triggers.count { it.isNotBlank() && instruction.contains(it, ignoreCase = true) } * 4
            if (s.name.isNotBlank() && instruction.contains(s.name, ignoreCase = true)) score += 3
            if (score <= 0) null else s to score
        }.sortedByDescending { it.second }
        return scored.take(limit).map { it.first }
    }

    /** 注入系统提示词的技能清单（只给 name + description，保持 token 精简） */
    fun buildSkillIndexPrompt(): String {
        val skills = getEnabled()
        if (skills.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("## 可用技能库（跨 App 自动化流程，需要时用 skill 工具 action=get 读取正文后再执行）")
        skills.take(40).forEach { s ->
            val scope = when {
                s.appPackage.isNotBlank() -> "app:${s.appName.ifBlank { s.appPackage }}"
                s.appName.isNotBlank() -> "app:${s.appName}"
                else -> "通用"
            }
            sb.append("- ").append(s.displayName).append(" (").append(scope).append(")")
            if (s.description.isNotBlank()) sb.append("：").append(s.description)
            if (s.triggers.isNotEmpty()) sb.append("  [触发: ").append(s.triggers.take(5).joinToString("/")).append("]")
            sb.appendLine()
        }
        sb.appendLine("提示：如果某个技能与当前任务相关，先读取它的正文再动手，能显著提高成功率。")
        return sb.toString()
    }

    /** 供 operate_phone / 单步规划使用的技能正文块 */
    fun buildSkillContextBlock(instruction: String, packageName: String = "", appName: String = ""): String {
        val matched = match(instruction, packageName, appName, limit = 2)
        if (matched.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("【命中技能（先按技能里的步骤规划，并随时用当前屏幕校验）】")
        matched.forEach { s ->
            sb.appendLine("### ${s.displayName}")
            if (s.description.isNotBlank()) sb.appendLine(s.description)
            if (s.content.isNotBlank()) sb.appendLine(s.content)
            sb.appendLine()
        }
        return sb.toString()
    }

    // ==================== 导入导出 ====================

    fun allAsMarkdown(): String {
        val skills = getAll()
        if (skills.isEmpty()) return ""
        return skills.joinToString("\n\n<!-- ===== SKILL SPLIT ===== -->\n\n") { it.toMarkdown() }
    }

    /** 导入 Markdown（支持多段用 `<!-- ===== SKILL SPLIT ===== -->` 分隔） */
    fun importFromMarkdown(text: String): List<Skill> {
        val chunks = text.split("<!-- ===== SKILL SPLIT ===== -->")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val imported = mutableListOf<Skill>()
        chunks.forEachIndexed { i, chunk ->
            val base = Skill.makeId("import_${System.currentTimeMillis()}_$i", "sk")
            var skill = Skill.fromMarkdown(chunk, base)
            if (skill.id.isBlank() || getById(skill.id) != null) {
                skill = skill.copy(id = Skill.makeId(skill.displayName, "sk") + "_${System.currentTimeMillis() % 10000}")
            }
            if (save(skill)) imported.add(skill)
        }
        return imported
    }

    // ==================== 内置技能（随 APK 打包） ====================

    /**
     * 导入 assets/skills 目录下全部 .md 内置技能。
     *
     * 为什么要在应用内维护一份：这些是「怎么把界面自动化做对」的经验
     * （结构读不到怎么降级、点哪里才算点中、中文怎么输入、点完怎么确认），
     * 写进 assets 才能在**离线**状态下被 AI 读到，而且和用户数据无关。
     *
     * 三种情况分开处理：
     * 1. 库里没有这个 id → 导入
     * 2. 库里已有，但 md 的 rev 比上次导入的高 → 用内置正文覆盖（保留使用统计）
     * 3. 库里已有且 rev 不变 → 跳过（用户或 AI 改过的内容不会被打回）
     *
     * 这样发新版本时，只要把改过的 md 的 rev 加一，老用户升级后就自动拿到新内容。
     */
    @Synchronized
    fun importBundled(): Int {
        val files = runCatching {
            context.assets.list(BUNDLED_DIR)?.toList().orEmpty()
        }.getOrDefault(emptyList())
            .filter { it.endsWith(".md", ignoreCase = true) }
            // README 之类只给人看，不当技能导入
            .filterNot { it.equals("README.md", ignoreCase = true) || it.startsWith("_") }
        if (files.isEmpty()) return 0

        val prefs = context.getSharedPreferences(BUNDLED_PREFS, Context.MODE_PRIVATE)
        val list = getAll().toMutableList()
        var changed = 0

        for (fileName in files) {
            val text = runCatching {
                context.assets.open("$BUNDLED_DIR/$fileName").bufferedReader().use { it.readText() }
            }.getOrNull() ?: continue

            val skill = Skill.fromMarkdown(text, fileName.removeSuffix(".md"))
            if (skill.id.isBlank() || skill.content.isBlank()) continue

            val rev = regexRev.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val importedRev = prefs.getInt("rev_${skill.id}", 0)
            val idx = list.indexOfFirst { it.id == skill.id }

            when {
                idx < 0 -> {
                    list.add(skill.copy(source = "bundled"))
                    prefs.edit().putInt("rev_${skill.id}", rev).apply()
                    changed++
                }
                rev > importedRev -> {
                    val old = list[idx]
                    list[idx] = old.copy(
                        name = skill.name,
                        description = skill.description,
                        scope = skill.scope,
                        appName = skill.appName,
                        appPackage = skill.appPackage,
                        triggers = skill.triggers,
                        content = skill.content,
                        source = "bundled",
                        updatedAt = System.currentTimeMillis(),
                        version = old.version + 1
                    )
                    prefs.edit().putInt("rev_${skill.id}", rev).apply()
                    changed++
                }
            }
        }

        if (changed > 0) {
            runCatching { write(list) }
            Log.i(TAG, "内置技能已更新：$changed 条（共 ${list.size} 条）")
        }
        return changed
    }

    // ==================== 旧数据迁移 ====================

    /**
     * 把旧的 AppSkillManager（app_ui_skills.json：每个 App 一份 uiMap + 多个已知任务）
     * 迁移为「每个 App 一条技能」，source=auto。只执行一次。
     */
    @Synchronized
    fun migrateFromAppSkillsIfNeeded() {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean(MIGRATION_FLAG, false)) return
        try {
            val old = AppSkillManager(context).getAllSkills()
            if (old.isNotEmpty()) {
                var added = 0
                old.forEach { legacy ->
                    val id = "appui_" + Skill.makeId(legacy.appName, "app").removePrefix("sk_")
                    if (getById(id) != null) return@forEach
                    val sb = StringBuilder()
                    if (legacy.uiMap.isNotBlank()) {
                        sb.appendLine("## UI 结构知识（自动归纳，可能与最新版 App 有出入，以当前屏幕为准）")
                        sb.appendLine(legacy.uiMap)
                        sb.appendLine()
                    }
                    if (legacy.knownTasks.isNotEmpty()) {
                        sb.appendLine("## 已验证的任务流程")
                        legacy.knownTasks.forEach { t ->
                            sb.appendLine("### ${t.taskName}")
                            sb.appendLine(t.workflow)
                            if (t.triggerKeywords.isNotEmpty()) {
                                sb.appendLine("触发词：${t.triggerKeywords.joinToString("、")}")
                            }
                            sb.appendLine()
                        }
                    }
                    val triggers = legacy.knownTasks.flatMap { it.triggerKeywords }
                        .map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    val skill = Skill(
                        id = id,
                        name = "${legacy.appName} 操作知识",
                        description = legacy.knownTasks.firstOrNull()?.taskName?.let { "自动归纳：$it 等流程" } ?: "自动归纳的 UI 结构知识",
                        scope = "app",
                        appName = legacy.appName,
                        appPackage = legacy.appPackage,
                        triggers = triggers,
                        content = sb.toString().trim(),
                        source = "auto"
                    )
                    if (save(skill)) added++
                }
                Log.i(TAG, "迁移旧 AppSkillManager：${old.size} 个 App，新增 $added 条技能")
            }
        } catch (e: Exception) {
            Log.e(TAG, "migrateFromAppSkills error: ${e.message}")
        } finally {
            prefs.edit().putBoolean(MIGRATION_FLAG, true).apply()
        }
    }
}
