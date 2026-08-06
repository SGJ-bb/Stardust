package com.aicompanion.plugin

import android.content.Context
import com.aicompanion.album.MemorialAlbumManager
import com.aicompanion.calendar.CalendarEventManager
import com.aicompanion.capsule.TimeCapsuleManager
import com.aicompanion.memory.MemorableMomentsManager
import com.aicompanion.milestone.MilestoneManager
import com.aicompanion.models.ToolDefinition
import com.aicompanion.persona.PersonaManager
import com.aicompanion.util.AppLogger
import com.aicompanion.virtualworld.VirtualWorldManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

// ============================================================
// 日历事件插件 — AI 添加日历事件/标记
// ============================================================
class CalendarEventPlugin(private val context: Context) : ToolPlugin {
    override val name = "add_calendar_event"
    override val description = "在日历上添加事件或标记"

    override fun getDefinition() = ToolDefinition(
        name = "add_calendar_event",
        description = """在日历上添加事件、标记、纪念日或提醒。
【调用场景】
- 用户提到某个重要日期（考试、约会、纪念日、截止日期等）
- 对话中出现需要记住的日程
- 用户明确要求"记一下这个日子""帮我标记X号"
- AI认为某个日期有特殊意义值得标记

【分类说明】
- general: 普通事件
- anniversary: 纪念日（恋爱纪念日、认识纪念日等）
- reminder: 提醒事项
- birthday: 生日
- meeting: 会议/约会
- holiday: 假期""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "title" to mapOf("type" to "string", "description" to "事件标题，简洁明了，如「和小明约会」「期末考试」「认识一周年」"),
                "date" to mapOf("type" to "string", "description" to "日期，格式 yyyy-MM-dd，如 2026-07-15"),
                "time" to mapOf("type" to "string", "description" to "时间，格式 HH:mm，如 14:00。全天事件留空"),
                "description" to mapOf("type" to "string", "description" to "事件详细描述（可选）"),
                "category" to mapOf("type" to "string", "description" to "分类：general/anniversary/reminder/birthday/meeting/holiday", "enum" to listOf("general", "anniversary", "reminder", "birthday", "meeting", "holiday"))
            ),
            "required" to listOf("title", "date")
        )
    )

    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val title = args.optString("title", "")
        val date = args.optString("date", "")
        if (title.isBlank()) return "错误：请提供事件标题"
        if (date.isBlank()) return "错误：请提供日期"

        // 验证日期格式
        try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)
                ?: return "错误：日期格式不正确，请使用 yyyy-MM-dd"
        } catch (e: Exception) {
            return "错误：日期格式不正确「$date」，请使用 yyyy-MM-dd"
        }

        val time = args.optString("time", "")
        val description = args.optString("description", "")
        val category = args.optString("category", "general")

        val personaId = getActivePersonaId()
        val manager = CalendarEventManager(context, personaId)
        val event = manager.addEvent(
            title = title,
            date = date,
            time = time,
            description = description,
            category = category,
            createdBy = "ai"
        )
        AppLogger.i("CalendarEventPlugin", "AI added event: $title on $date")
        return "已在日历上添加事件「$title」（$date${if (time.isNotBlank()) " $time" else ""}），分类：$category"
    }

    private fun getActivePersonaId(): String {
        return try {
            val pm = PersonaManager(context)
            pm.load()
            pm.getActivePersona()?.id ?: "default"
        } catch (_: Exception) { "default" }
    }
}

// ============================================================
// 里程碑插件 — AI 添加里程碑
// ============================================================
class MilestonePlugin(private val context: Context) : ToolPlugin {
    override val name = "record_milestone"
    override val description = "记录重要里程碑"

    override fun getDefinition() = ToolDefinition(
        name = "record_milestone",
        description = """记录一个重要里程碑，标记用户成长或关系进展中的重要时刻。
【调用场景】
- 用户提到重大成就（考试通过、升职、完成目标等）
- 关系里程碑（第一次见面、第一次约会、成为朋友等）
- 人生重要节点（毕业、换工作、搬家等）
- 任何值得永久记住的重要时刻

【注意】
- 每个里程碑 id 必须唯一，相同 id 不会重复记录
- 里程碑是永久性的，请谨慎添加，只记录真正重要的时刻""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "id" to mapOf("type" to "string", "description" to "唯一标识符，如 first_meeting、exam_passed_2026、promotion_2026"),
                "title" to mapOf("type" to "string", "description" to "里程碑标题，如「第一次见面」「考试通过」「升职」"),
                "description" to mapOf("type" to "string", "description" to "详细描述"),
                "category" to mapOf("type" to "string", "description" to "分类：general/relationship/achievement/life/education/career", "enum" to listOf("general", "relationship", "achievement", "life", "education", "career"))
            ),
            "required" to listOf("id", "title", "description")
        )
    )

    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val id = args.optString("id", "")
        val title = args.optString("title", "")
        val description = args.optString("description", "")
        val category = args.optString("category", "general")
        if (id.isBlank() || title.isBlank() || description.isBlank()) {
            return "错误：请提供 id、title 和 description"
        }

        val personaId = getActivePersonaId()
        val manager = MilestoneManager(context, personaId)
        val success = manager.recordMilestone(id, title, description, category)
        return if (success) {
            "已记录里程碑「$title」（$description），分类：$category"
        } else {
            "里程碑「$title」已存在，未重复记录"
        }
    }

    private fun getActivePersonaId(): String {
        return try {
            val pm = PersonaManager(context)
            pm.load()
            pm.getActivePersona()?.id ?: "default"
        } catch (_: Exception) { "default" }
    }
}

// ============================================================
// 铭记时刻插件 — AI 添加难忘时刻
// ============================================================
class MemorableMomentPlugin(private val context: Context) : ToolPlugin {
    override val name = "add_memorable_moment"
    override val description = "记录难忘时刻"

    override fun getDefinition() = ToolDefinition(
        name = "add_memorable_moment",
        description = """记录一个难忘的对话时刻，保存特别有意义的对话瞬间。
【调用场景】
- 对话中出现情感强烈、特别温馨、特别有趣的瞬间
- 用户分享了深刻的心事或重要决定
- 发生了值得以后回忆的对话
- 情感共鸣强烈的时刻

【评分标准】
- 9-10分：极度难忘，如深情告白、重大人生决定、强烈情感共鸣
- 8分：非常难忘，如温馨关怀、深度交心、有趣互动
- 7分以下：不记录（只有8分以上才会保存）

【分类】
- heartwarming: 温馨时刻
- funny: 搞笑时刻
- deep: 深度交心
- romantic: 浪漫时刻
- supportive: 互相支持
- milestone: 里程碑式对话""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "content" to mapOf("type" to "string", "description" to "难忘时刻的内容描述，记录对话的精华"),
                "score" to mapOf("type" to "integer", "description" to "难忘程度评分 1-10，只有8分以上才会保存"),
                "category" to mapOf("type" to "string", "description" to "分类：heartwarming/funny/deep/romantic/supportive/milestone", "enum" to listOf("heartwarming", "funny", "deep", "romantic", "supportive", "milestone"))
            ),
            "required" to listOf("content", "score", "category")
        )
    )

    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val content = args.optString("content", "")
        val score = args.optInt("score", 0)
        val category = args.optString("category", "general")
        if (content.isBlank()) return "错误：请提供难忘时刻内容"
        if (score < 8) return "评分 $score 未达到8分，未记录（只有8分以上的时刻才会保存）"

        val personaId = getActivePersonaId()
        val manager = MemorableMomentsManager(context, personaId)
        manager.addMoment(content, score, category)
        AppLogger.i("MemorableMomentPlugin", "AI added moment: score=$score, category=$category")
        return "已记录难忘时刻（评分 $score 分，分类：$category）：$content"
    }

    private fun getActivePersonaId(): String {
        return try {
            val pm = PersonaManager(context)
            pm.load()
            pm.getActivePersona()?.id ?: "default"
        } catch (_: Exception) { "default" }
    }
}

// ============================================================
// 时光胶囊插件 — AI 创建时光胶囊
// ============================================================
class TimeCapsulePlugin(private val context: Context) : ToolPlugin {
    override val name = "create_time_capsule"
    override val description = "创建时光胶囊"

    override fun getDefinition() = ToolDefinition(
        name = "create_time_capsule",
        description = """创建一个时光胶囊，写给未来的信，在指定日期才能打开。
【调用场景】
- 用户想给未来的自己留言
- 想在某个纪念日给对方一个惊喜
- 记录此刻的心情，留待将来回顾
- 用户明确要求"写一封信给未来的自己"

【注意】
- openDate 必须是未来的日期
- 时光胶囊在 openDate 之前无法查看内容
- 适合保存有情感价值的内容""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "title" to mapOf("type" to "string", "description" to "胶囊标题，如「给一年后的自己」「我们的约定」"),
                "content" to mapOf("type" to "string", "description" to "胶囊内容，写给未来的话"),
                "openDate" to mapOf("type" to "string", "description" to "开启日期，格式 yyyy-MM-dd，必须是未来日期")
            ),
            "required" to listOf("title", "content", "openDate")
        )
    )

    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val title = args.optString("title", "")
        val content = args.optString("content", "")
        val openDateStr = args.optString("openDate", "")
        if (title.isBlank() || content.isBlank() || openDateStr.isBlank()) {
            return "错误：请提供 title、content 和 openDate"
        }

        val openDate: Long
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            openDate = sdf.parse(openDateStr)?.time
                ?: return "错误：日期格式不正确，请使用 yyyy-MM-dd"
        } catch (e: Exception) {
            return "错误：日期格式不正确「$openDateStr」，请使用 yyyy-MM-dd"
        }

        if (openDate <= System.currentTimeMillis()) {
            return "错误：开启日期必须是未来日期"
        }

        val personaId = getActivePersonaId()
        val manager = TimeCapsuleManager(context, personaId)
        val capsule = manager.createCapsule(title, content, openDate)
        AppLogger.i("TimeCapsulePlugin", "AI created capsule: $title, opens at $openDateStr")
        return "已创建时光胶囊「$title」，将在 $openDateStr 开启"
    }

    private fun getActivePersonaId(): String {
        return try {
            val pm = PersonaManager(context)
            pm.load()
            pm.getActivePersona()?.id ?: "default"
        } catch (_: Exception) { "default" }
    }
}

// ============================================================
// 纪念相册插件 — AI 生成相册照片（无图片API时自动隐藏）
// ============================================================
class AlbumPlugin(private val context: Context) : ToolPlugin {
    override val name = "generate_album_photo"
    override val description = "生成纪念相册照片"

    /** 无图片生成 API 配置时自动隐藏，不发送给 LLM */
    override fun isEnabled(): Boolean {
        return try {
            VirtualWorldManager(context).hasImageModelConfigured()
        } catch (_: Exception) { false }
    }

    override fun getDefinition() = ToolDefinition(
        name = "generate_album_photo",
        description = """生成一张纪念相册照片并添加到相册中。
【调用场景】
- 对话中出现值得纪念的场景（约会、旅行、节日等）
- 用户明确要求"拍张照""生成照片""记录这个瞬间"
- 关系里程碑时刻（第一次见面、纪念日等）
- 任何值得用图片铭记的温馨瞬间

【注意】
- prompt 应为英文，描述具体场景、人物、氛围
- 自动附加用户和角色外观描述
- 生成后会自动保存到纪念相册""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "prompt" to mapOf("type" to "string", "description" to "图片生成提示词（英文），描述场景、氛围、人物动作等，如 'cherry blossom park, two people walking together, warm sunlight, anime style'"),
                "title" to mapOf("type" to "string", "description" to "照片标题，如「春日漫步」「星空下的约定」"),
                "caption" to mapOf("type" to "string", "description" to "照片描述/配文（可选）"),
                "aspectRatio" to mapOf("type" to "string", "description" to "宽高比：1:1(默认)/9:16/4:3/16:9", "enum" to listOf("1:1", "9:16", "4:3", "16:9"))
            ),
            "required" to listOf("prompt", "title")
        )
    )

    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val prompt = args.optString("prompt", "")
        val title = args.optString("title", "")
        val caption = args.optString("caption", "")
        val aspectRatio = args.optString("aspectRatio", "1:1")
        if (prompt.isBlank() || title.isBlank()) {
            return "错误：请提供 prompt 和 title"
        }

        // 再次检查 API 配置（防止运行时配置被清除）
        if (!VirtualWorldManager(context).hasImageModelConfigured()) {
            return "错误：图片生成 API 未配置，无法生成照片"
        }

        AppLogger.i("AlbumPlugin", "AI generating album photo: $title")
        val entry = MemorialAlbumManager.generateImage(context, prompt, title, caption, aspectRatio)
        return if (entry != null) {
            "已生成纪念相册照片「$title」并添加到相册"
        } else {
            "错误：照片生成失败，请检查图片生成 API 配置"
        }
    }
}
