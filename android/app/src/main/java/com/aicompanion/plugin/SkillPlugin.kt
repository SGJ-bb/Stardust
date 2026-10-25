package com.aicompanion.plugin

import android.content.Context
import com.aicompanion.models.ToolDefinition
import com.aicompanion.settings.SettingsManager
import com.aicompanion.skill.Skill
import com.aicompanion.skill.SkillCreator
import com.aicompanion.skill.SkillStore
import com.aicompanion.util.AppLogger
import org.json.JSONObject

/**
 * 技能库工具：让 AI 自己管理「跨 App 自动化技能」。
 *
 * 用法（渐进式披露）：
 * · 系统提示词里只列出技能清单（名称+描述），token 成本极低；
 * · 真正要用时 action=get 读取正文，再照着正文操作；
 * · 一次任务成功后可以 action=create 把流程沉淀下来，下次直接复用。
 */
class SkillPlugin(private val context: Context) : ToolPlugin {

    companion object {
        private const val TAG = "SkillPlugin"
    }

    override val name = "skill"
    override val description = "技能库：查询/读取/创建/修改跨 App 自动化技能"

    override fun isEnabled(): Boolean = SettingsManager(context).skillEnabled

    override fun getDefinition() = ToolDefinition(
        name = "skill",
        description = """管理「自动化技能库」。技能是可复用的操作流程说明（针对某个 App 或其他 App 的通用流程），
读取后按它的步骤操作能显著提高成功率。

action 取值：
- list：列出所有技能（名称+描述+适用App）。不确定有没有相关技能时先 list
- get：读取指定技能的完整正文（用 name 或 id 指定）。准备执行某个 App 的操作前，如果技能清单里有相关技能，必须先 get 读取
- create：创建新技能。给出 name 与 requirement（这个技能要做什么），AI 会自动写好正文；也可直接给 content
- update：用 requirement 描述改进要求，或直接给 content 覆盖，改已有技能
- delete：删除技能（需 id）
- search：按关键词搜索技能
- learn_ui：把**当前屏幕上这个界面**的 UI 结构记录成技能页面（先让用户打开目标 App 的那一页）

建议：当你成功完成一个原本很复杂的 App 操作流程后，用 create 把它沉淀成技能，下次直接复用。""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("list", "get", "create", "update", "delete", "search", "learn_ui", "dedupe"),
                    "description" to "要执行的操作"
                ),
                "name" to mapOf("type" to "string", "description" to "技能名（get/search 时用于匹配）"),
                "id" to mapOf("type" to "string", "description" to "技能 ID（精确操作时使用）"),
                "requirement" to mapOf("type" to "string", "description" to "create/update 时：这个技能要做什么，或要怎么改"),
                "content" to mapOf("type" to "string", "description" to "create/update 时：直接指定正文（Markdown），不给则由 AI 自动撰写"),
                "app_name" to mapOf("type" to "string", "description" to "该技能针对的 App 中文名，通用流程留空"),
                "app_package" to mapOf("type" to "string", "description" to "该技能针对的 App 包名，如 com.tencent.mm"),
                "description" to mapOf("type" to "string", "description" to "一句话描述（供 AI 判断是否加载）"),
                "triggers" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string"),
                    "description" to "触发词，用户指令里出现这些词时优先加载本技能"
                )
            ),
            "required" to listOf("action")
        )
    )

    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val action = args.optString("action", "list").lowercase()
        val store = SkillStore(context)
        return try {
            when (action) {
                "list" -> listSkills(store)
                "get" -> getSkill(store, args)
                "search" -> searchSkills(store, args)
                "create" -> createSkill(store, args)
                "update" -> updateSkill(store, args)
                "delete" -> deleteSkill(store, args)
                "learn_ui" -> learnUi(store, args)
                "dedupe" -> dedupe(store)
                else -> "未知 action：$action（可用：list/get/create/update/delete/search/learn_ui）"
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "skill $action 失败: ${e.message}")
            "技能操作失败：${e.message}"
        }
    }

    /**
     * 把当前屏幕的 UI 结构学成一个技能页面。
     *
     * 用法：让用户把目标 App 打开到要操作的那一页，然后调用本动作。
     * 同一 App 多次学习会按「页面」分节累积（同页覆盖，不重复堆叠）。
     */
    private fun learnUi(store: SkillStore, args: JSONObject): String {
        val svc = com.aicompanion.screen.ScreenRecognitionService.getInstance()
            ?: return "无障碍服务没连上，学不了。请先在设置里开启星尘的无障碍服务。"
        val root = svc.rootInActiveWindow
            ?: return "拿不到当前界面。请确认：目标 App 在前台、无障碍服务已开启。"
        val pageClass = com.aicompanion.screen.ScreenRecognitionService.getCurrentPageClass()

        val page = com.aicompanion.screen.UiSkillBuilder.collect(root, pageClass)
            ?: return "这个界面没提取到有用元素（可能是纯图片界面或自绘界面，不支持）。"

        // 别把自己的界面学进去
        if (page.packageName == context.packageName) {
            return "当前是星尘自己的界面，跳过。请切到目标 App 再学。"
        }

        val md = com.aicompanion.screen.UiSkillBuilder.renderPage(page)
        val existing = store.getAll().firstOrNull { it.appPackage == page.packageName }
        val appName = args.optString("app_name", "").ifBlank {
            existing?.appName ?: guessAppName(page.packageName)
        }

        if (existing != null) {
            val merged = com.aicompanion.screen.UiSkillBuilder.mergePage(existing.content, md, page.pageKey)
            val pageCount = merged.lines().count { it.startsWith("## 页面：") }
            store.save(
                existing.copy(
                    content = merged,
                    description = "$appName 的界面结构与操作要点（已采集 $pageCount 页）",
                    updatedAt = System.currentTimeMillis(),
                    version = existing.version + 1,
                )
            )
            val pages = merged.lines().count { it.startsWith("## 页面：") }
            return "已更新技能「${existing.displayName}」：本次记录 ${page.pageKey}（共 ${page.elements.size} 个元素），" +
                "该技能现含 $pages 个页面。"
        }

        val skill = Skill(
            id = "ui_" + page.packageName.replace('.', '_'),
            name = "$appName 操作指南",
            description = "$appName 的界面结构与操作要点（自动采集）",
            scope = "app",
            appName = appName,
            appPackage = page.packageName,
            triggers = listOf(appName).filter { it.isNotBlank() },
            content = buildString {
                appendLine("# $appName 操作指南")
                appendLine()
                appendLine("包名：${page.packageName}")
                appendLine()
                appendLine("> 由界面自动采集生成。**一律用 device tap_text \"文字\" 定位**（列表顺序会变，别记坐标）；找不到先 swipe 再找；页面变了重学一次即可。")
                appendLine()
                append(md)
            },
            source = "auto",
        )
        store.save(skill)
        return "已为「$appName」创建技能：记录页面 ${page.pageKey}（${page.elements.size} 个元素）。" +
            "继续把这个 App 的其他页面也打开并各学一次，最后就是完整指南。"
    }

    private fun guessAppName(pkg: String): String = when (pkg) {
        "com.tencent.mm" -> "微信"
        "com.tencent.mobileqq" -> "QQ"
        "com.taobao.taobao" -> "淘宝"
        "com.jingdong.app.mall" -> "京东"
        "com.xingin.xhs" -> "小红书"
        "com.ss.android.ugc.aweme" -> "抖音"
        "com.sina.weibo" -> "微博"
        "com.eg.android.AlipayGphone" -> "支付宝"
        "com.netease.cloudmusic" -> "网易云音乐"
        "tv.danmaku.bili" -> "哔哩哔哩"
        "com.autonavi.minimap" -> "高德地图"
        "com.baidu.BaiduMap" -> "百度地图"
        "com.android.settings" -> "系统设置"
        else -> pkg.substringAfterLast('.')
    }

    /**
     * 技能去重：同一个 App 经常被重复生成（历史自动归纳 + UI 树采集各来一份，
     * 甚至同一个电话应用留下 4 个不同名字的版本）。这里按包名合并：
     * 内容取并集，保留最早那份的 id/名称，其余删除。
     */
    private fun dedupe(store: SkillStore): String {
        val all = store.getAll()
        // 分组键用"规范化后的名字"而不是包名：
        // 同一个拨号应用在历史数据里留下了「电话」「电话（联系人/拨号）」两份，
        // 包名不同但其实是同一个 App，只按包名去重会漏掉。
        fun norm(name: String): String = name
            .replace(Regex("[（(].*?[）)]"), "")
            .replace("操作知识", "")
            .replace("操作指南", "")
            .replace(Regex("[\\s·\\-]+"), "")
            .trim()
        val groups = all.groupBy { sk ->
            norm(sk.displayName).ifBlank { sk.appPackage }.ifBlank { sk.id }
        }
        var removed = 0
        val detail = StringBuilder()
        groups.forEach { (pkg, list) ->
            if (list.size <= 1) return@forEach
            val keeper = list.maxByOrNull { it.content.length }!!
            val mergedContent = list
                .sortedByDescending { it.content.length }
                .map { it.content.trim() }
                .distinct()
                .joinToString("\n\n")
            val pages = mergedContent.lines().count { it.startsWith("## 页面：") }
            store.save(
                keeper.copy(
                    content = mergedContent,
                    description = "${keeper.appName} 的界面结构与操作要点" +
                        if (pages > 0) "（已采集 $pages 页）" else "",
                    updatedAt = System.currentTimeMillis(),
                )
            )
            list.filter { it.id != keeper.id }.forEach {
                store.delete(it.id)
                removed++
            }
            detail.append("\n- ${keeper.displayName}（合并 ${list.size} 份 → 1）")
        }
        return if (removed == 0) "没有需要去重的技能。" else "已合并去重，删除 $removed 份重复技能：$detail"
    }

    private fun listSkills(store: SkillStore): String {
        val all = store.getAll()
        if (all.isEmpty()) return "技能库为空。可用 action=create 创建第一个技能。"
        val sb = StringBuilder("共 ${all.size} 个技能：\n")
        all.forEach { s ->
            sb.append("- ").append(s.displayName)
            sb.append(" [").append(if (s.enabled) "启用" else "已停用").append("]")
            if (s.appName.isNotBlank()) sb.append(" 适用App:").append(s.appName)
            if (s.description.isNotBlank()) sb.append(" — ").append(s.description)
            sb.append("  (id=").append(s.id).append(")")
            sb.append('\n')
        }
        sb.append("\n用 action=get 读取某个技能的完整内容。")
        return sb.toString()
    }

    private fun getSkill(store: SkillStore, args: JSONObject): String {
        val id = args.optString("id", "")
        val name = args.optString("name", "")
        val skill = (id.takeIf { it.isNotBlank() }?.let { store.getById(it) })
            ?: (name.takeIf { it.isNotBlank() }?.let { store.findByName(it) })
            ?: store.match(name.ifBlank { id }, limit = 1).firstOrNull()
        if (skill == null) {
            val names = store.getAll().take(10).joinToString("、") { it.displayName }
            return "未找到技能。当前技能库：${names.ifBlank { "（空）" }}"
        }
        return buildString {
            appendLine("【技能：${skill.displayName}】")
            if (skill.appName.isNotBlank()) appendLine("适用 App：${skill.appName} ${skill.appPackage}")
            if (skill.description.isNotBlank()) appendLine("描述：${skill.description}")
            if (skill.triggers.isNotEmpty()) appendLine("触发词：${skill.triggers.joinToString("、")}")
            appendLine()
            appendLine(skill.content)
            appendLine()
            appendLine("请按上述步骤执行，并用当前屏幕内容随时校验元素是否存在。")
        }
    }

    private fun searchSkills(store: SkillStore, args: JSONObject): String {
        val kw = args.optString("name", args.optString("requirement", ""))
        if (kw.isBlank()) return "请提供搜索关键词（name 或 requirement）"
        val hits = store.getEnabled().filter {
            it.name.contains(kw, true) || it.description.contains(kw, true) ||
                it.appName.contains(kw, true) || it.triggers.any { t -> t.contains(kw, true) } ||
                it.content.contains(kw, true)
        }
        if (hits.isEmpty()) return "没有匹配「$kw」的技能。"
        return "匹配「$kw」的技能：\n" + hits.joinToString("\n") {
            "- ${it.displayName}${if (it.description.isNotBlank()) "：" + it.description else ""} (id=${it.id})"
        }
    }

    private suspend fun createSkill(store: SkillStore, args: JSONObject): String {
        val name = args.optString("name", "")
        val requirement = args.optString("requirement", "")
        val content = args.optString("content", "")
        if (name.isBlank() && requirement.isBlank()) return "请至少提供 name 或 requirement"

        val appName = args.optString("app_name", "")
        val appPackage = args.optString("app_package", "")
        val description = args.optString("description", "")
        val triggers = args.optJSONArray("triggers")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
        } ?: emptyList()

        // 直接给了正文 → 不走 AI
        if (content.isNotBlank()) {
            val skill = Skill(
                id = Skill.makeId(name.ifBlank { requirement }, "sk"),
                name = name.ifBlank { requirement.take(20) },
                description = description,
                scope = if (appName.isBlank() && appPackage.isBlank()) "global" else "app",
                appName = appName,
                appPackage = appPackage,
                triggers = triggers.ifEmpty { listOf(name.ifBlank { requirement.take(12) }) },
                content = content,
                source = "ai"
            )
            return if (store.save(skill)) "技能已创建：${skill.displayName} (id=${skill.id})"
            else "技能保存失败"
        }

        val api = buildApiClient() ?: return "未配置聊天 API，无法让 AI 撰写技能正文。可改用参数 content 直接提供正文。"
        val result = SkillCreator.createFromDescription(
            context = context,
            apiClient = api,
            requirement = requirement.ifBlank { name },
            packageName = appPackage
        )
        return result.fold(
            onSuccess = { s ->
                // AI 未给出这些字段时，用调用方提供的信息补全
                if (appName.isNotBlank() || appPackage.isNotBlank() || triggers.isNotEmpty()) {
                    store.save(s.copy(
                        appName = s.appName.ifBlank { appName },
                        appPackage = s.appPackage.ifBlank { appPackage },
                        description = s.description.ifBlank { description },
                        triggers = (s.triggers + triggers).distinct()
                    ))
                }
                "技能已创建：${s.displayName} (id=${s.id})\n摘要：${s.description}"
            },
            onFailure = { "技能创建失败：${it.message}" }
        )
    }

    private suspend fun updateSkill(store: SkillStore, args: JSONObject): String {
        val id = args.optString("id", "")
        val name = args.optString("name", "")
        val existing = (id.takeIf { it.isNotBlank() }?.let { store.getById(it) })
            ?: (name.takeIf { it.isNotBlank() }?.let { store.findByName(it) })
            ?: return "未找到要修改的技能（请提供正确的 id 或 name）"

        val content = args.optString("content", "")
        if (content.isNotBlank()) {
            return if (store.save(existing.copy(content = content))) "技能已更新：${existing.displayName}"
            else "技能更新失败"
        }

        val requirement = args.optString("requirement", "")
        if (requirement.isBlank()) return "请提供 requirement（改进要求）或 content（新正文）"
        val api = buildApiClient() ?: return "未配置聊天 API，无法让 AI 改进技能。可用 content 直接覆盖。"
        val result = SkillCreator.createFromDescription(
            context = context,
            apiClient = api,
            requirement = requirement,
            existing = existing
        )
        return result.fold(
            onSuccess = { "技能已更新：${it.displayName}（v${it.version}）" },
            onFailure = { "技能更新失败：${it.message}" }
        )
    }

    private fun deleteSkill(store: SkillStore, args: JSONObject): String {
        val id = args.optString("id", "")
        val name = args.optString("name", "")
        val target = (id.takeIf { it.isNotBlank() }?.let { store.getById(it) })
            ?: (name.takeIf { it.isNotBlank() }?.let { store.findByName(it) })
            ?: return "未找到要删除的技能"
        return if (store.delete(target.id)) "技能已删除：${target.displayName}" else "删除失败"
    }

    private fun buildApiClient(): com.aicompanion.network.ApiClient? {
        val sm = SettingsManager(context)
        if (sm.chatApiUrl.isBlank()) return null
        return com.aicompanion.network.ApiClient(sm.chatApiUrl, sm.chatApiKey, sm.chatModel)
    }
}
