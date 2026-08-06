package com.aicompanion.plugin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aicompanion.action.AIActionManager
import com.aicompanion.models.ToolDefinition
import com.aicompanion.search.WebSearchEngine
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import com.aicompanion.sticker.StickerManager
import com.aicompanion.virtualworld.VirtualWorldManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmPlugin(private val context: Context) : ToolPlugin {
    override val name = "set_alarm"
    override val description = "设置一个闹钟提醒"
    override fun getDefinition() = ToolDefinition(
        name = "set_alarm",
        description = "设置一个闹钟提醒。当用户说「X分钟后提醒我」「设个闹钟」等时调用此工具。",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "minutes" to mapOf("type" to "integer", "description" to "多少分钟后触发提醒"),
                "label" to mapOf("type" to "string", "description" to "提醒的标签，如「喝水」「开会」")
            ),
            "required" to listOf("minutes")
        )
    )
    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val minutes = args.optInt("minutes", 0)
        if (minutes <= 0) return "错误：请提供有效的分钟数"
        if (minutes > 1440) return "错误：闹钟时间不能超过24小时（1440分钟）"
        val label = args.optString("label", "提醒")
        val actionMgr = AIActionManager(context)
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, minutes) }
        return actionMgr.setAlarm(AIActionManager.AlarmInfo(
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            label = label,
            isDelay = true
        ))
    }
}

class AlarmAtTimePlugin(private val context: Context) : ToolPlugin {
    override val name = "set_alarm_at_time"
    override val description = "在指定时间设置闹钟"
    override fun getDefinition() = ToolDefinition(
        name = "set_alarm_at_time",
        description = "在指定时间设置闹钟。当用户说「定个X点的闹钟」「X:X叫我」时调用此工具。",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "hour" to mapOf("type" to "integer", "description" to "小时（24小时制，0-23）"),
                "minute" to mapOf("type" to "integer", "description" to "分钟（0-59）"),
                "label" to mapOf("type" to "string", "description" to "提醒标签，如「起床」「午休结束」")
            ),
            "required" to listOf("hour", "minute")
        )
    )
    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val hour = args.optInt("hour", -1)
        val minute = args.optInt("minute", 0)
        if (hour < 0 || hour > 23) return "错误：小时必须在0-23之间"
        val label = args.optString("label", "闹钟")
        val actionMgr = AIActionManager(context)
        return actionMgr.setAlarm(AIActionManager.AlarmInfo(hour = hour, minute = minute, label = label))
    }
}

class SchedulePlugin(private val context: Context) : ToolPlugin {
    override val name = "add_schedule"
    override val description = "添加日程安排"
    override fun getDefinition() = ToolDefinition(
        name = "add_schedule",
        description = "添加日程安排。当用户说「帮我记一下」「安排」等时调用此工具。",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "description" to mapOf("type" to "string", "description" to "日程内容描述"),
                "datetime" to mapOf("type" to "string", "description" to "日程时间，格式为「yyyy-MM-dd HH:mm」，如「2026-05-16 15:00」")
            ),
            "required" to listOf("description", "datetime")
        )
    )
    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val description = args.optString("description", "")
        val datetime = args.optString("datetime", "")
        if (description.isBlank()) return "错误：请提供日程描述"
        val actionMgr = AIActionManager(context)
        val cal = Calendar.getInstance()
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            cal.time = sdf.parse(datetime) ?: return "错误：无法解析日期时间「$datetime」，请使用格式 yyyy-MM-dd HH:mm"
        } catch (e: Exception) {
            return "错误：日期格式不正确「$datetime」，请使用格式如 2026-05-16 15:00"
        }
        return actionMgr.addSchedule(AIActionManager.ScheduleInfo(
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
            month = cal.get(Calendar.MONTH),
            year = cal.get(Calendar.YEAR),
            description = description
        ))
    }
}

class WebSearchPlugin(private val context: Context) : ToolPlugin {
    override val name = "search_web"
    override val description = "搜索互联网获取实时信息"
    private val searchEngine = WebSearchEngine(context)
    override fun isEnabled(): Boolean = SettingsManager(context).searchEnabled
    override fun getDefinition() = ToolDefinition(
        name = "search_web",
        description = "搜索互联网获取实时信息。当用户询问的问题需要最新信息、百科知识、新闻等时调用此工具。",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf("type" to "string", "description" to "搜索关键词，用简短精确的词组")
            ),
            "required" to listOf("query")
        )
    )
    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val query = args.optString("query", "")
        if (query.isBlank()) return "错误：请提供搜索关键词"
        AppLogger.w("WebSearchPlugin", "search_web: query=$query")
        return withContext(Dispatchers.IO) {
            searchEngine.searchAndSummarize(query)
        }
    }
}

class SearchMemoryPlugin : ToolPlugin {
    override val name = "search_memory"
    override val description = "搜索用户的短期记忆"
    var onSearchMemory: (suspend (String, Int) -> String)? = null
    override fun getDefinition() = ToolDefinition(
        name = "search_memory",
        description = "搜索用户的短期记忆池。用于查找用户最近聊天中提到过的信息、偏好、约定等。注意：短期记忆只包含近期对话中提取的关键信息，如需查找更早的长期记忆请使用search_diary工具。",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf("type" to "string", "description" to "搜索关键词，如「喜欢吃什么」「约定」「工作」等")
            ),
            "required" to listOf("query")
        )
    )
    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val query = args.optString("query", "")
        if (query.isBlank()) return "错误：请提供搜索关键词"
        return try {
            onSearchMemory?.invoke(query, 5) ?: "记忆搜索功能未初始化"
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e("SearchMemoryPlugin", "[Plugin-Memory] search_memory执行失败: ${e.javaClass.simpleName}: ${e.message} | query='${query.take(30)}'")
            "记忆搜索出错: ${e.message}"
        }
    }
}

class SearchDiaryPlugin : ToolPlugin {
    override val name = "search_diary"
    override val description = "搜索用户的日记记录（长期记忆）"
    var onSearchDiary: (suspend (String, Int) -> String)? = null
    override fun getDefinition() = ToolDefinition(
        name = "search_diary",
        description = "搜索用户的日记记录，这是用户的长期记忆。当你需要回忆用户过去几天的经历、心情变化、重要事件时调用此工具。日记包含每日总结、情绪记录和关键事件。可以多次调用不同query来获取更完整的信息。",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf("type" to "string", "description" to "搜索关键词，如「上周的心情」「最近开心的事」「关于工作的记录」等"),
                "top_k" to mapOf("type" to "integer", "description" to "返回结果数量，默认3")
            ),
            "required" to listOf("query")
        )
    )
    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val query = args.optString("query", "")
        if (query.isBlank()) return "错误：请提供搜索关键词"
        val topK = args.optInt("top_k", 3).coerceIn(1, 10)
        return try {
            onSearchDiary?.invoke(query, topK) ?: "日记搜索功能未初始化"
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e("SearchDiaryPlugin", "[Plugin-Diary] search_diary执行失败: ${e.javaClass.simpleName}: ${e.message} | query='${query.take(30)}'")
            "日记搜索出错: ${e.message}"
        }
    }
}

class CurrentTimePlugin : ToolPlugin {
    override val name = "get_current_time"
    override val description = "获取当前系统时间"
    override fun getDefinition() = ToolDefinition(
        name = "get_current_time",
        description = "获取当前系统时间。当用户问时间相关的问题时调用此工具。",
        parameters = mapOf(
            "type" to "object",
            "properties" to emptyMap<String, Any>(),
            "required" to emptyList<String>()
        )
    )
    override suspend fun execute(arguments: String): String {
        val now = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy年M月d日 EEEE HH:mm", Locale.CHINESE)
        return "当前时间：${sdf.format(now.time)}"
    }
}

class NicknamePlugin : ToolPlugin {
    override val name = "summarize_nicknames"
    override val description = "为主人总结出适合的称呼/昵称列表"
    var onNicknamesGenerated: ((List<String>) -> Unit)? = null
    override fun getDefinition() = ToolDefinition(
        name = "summarize_nicknames",
        description = "根据和主人的聊天对话，为主人总结出适合的称呼/昵称列表。当主人没有设置称呼时，你可以通过聊天中观察到的信息（如名字、身份、习惯、性格等）为主人生成多个可选的称呼。调用此工具可以提交你的称呼建议。",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "nicknames" to mapOf(
                    "type" to "array",
                    "description" to "你为主人总结的称呼列表，每个称呼建议应简洁自然。可以基于聊天中提到的名字、身份特征或亲密关系来创造。",
                    "items" to mapOf("type" to "string")
                )
            ),
            "required" to listOf("nicknames")
        )
    )
    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val nicknamesArray = args.optJSONArray("nicknames")
        if (nicknamesArray == null || nicknamesArray.length() == 0) {
            return "没有提交称呼建议。如果有想法了随时可以再调用。"
        }
        val nicknames = mutableListOf<String>()
        for (i in 0 until nicknamesArray.length()) {
            val n = nicknamesArray.optString(i, "").trim()
            if (n.isNotBlank()) nicknames.add(n)
        }
        onNicknamesGenerated?.invoke(nicknames)
        val summary = nicknames.joinToString("、")
        return "已收到你为主人建议的称呼：$summary。系统已保存这些称呼，你可以在后续的对话中自由选择使用其中一个来称呼主人。"
    }
}

class SendStickerPlugin(private val context: Context) : ToolPlugin {
    override val name = "send_sticker"
    override val description = "发送一个表情包"
    var onStickerSent: ((String) -> Unit)? = null
    private val stickerManager by lazy {
        try { com.aicompanion.AppContainer.stickerManager.also { it.loadStickers() } }
        catch (_: Exception) { StickerManager(context).also { it.loadStickers() } }
    }
    override fun getDefinition() = ToolDefinition(
        name = "send_sticker",
        description = "发送一个表情包来表达你的情感。当用户让你发表情包、发图、发可爱图片时，必须调用此工具发送表情包，不要只用文字描述。你有丰富的表情包可用（偷听、偷瞄、卖萌、吐槽、呆滞、哭泣、宕机、慌张、捂嘴笑、调侃、邪恶的笑、风趣调侃、骂人、鬼迷日眼的笑等）。当你有以下强烈情绪时也请务必调用此工具而不是只用文字：开心/可爱/撒娇→卖萌揣手手；偷笑/窃喜→捂嘴笑；无语/嫌弃→吐槽；发呆/放空→呆滞；难过/委屈→哭泣；崩溃/卡住→宕机；慌张/紧张→慌张；好奇/八卦→偷听；害羞/腼腆→偷瞄；调侃/打趣→调侃；坏笑/腹黑→邪恶的笑；搞笑/幽默→风趣调侃；嫌弃/骂人→骂别人是猪；狡黠/贼笑→鬼迷日眼的笑。重要：用户说发表情包、发个表情、来个表情包等时，必须调用此工具！",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "emotion" to mapOf("type" to "string", "description" to "你想表达的情感，如：开心、难过、撒娇、吐槽、发呆、偷笑、慌张、好奇、害羞、调侃、坏笑、嫌弃等")
            ),
            "required" to listOf("emotion")
        )
    )
    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val emotion = args.optString("emotion", "")
        AppLogger.w("SendStickerPlugin", "send_sticker: emotion=$emotion")
        if (emotion.isBlank()) return "请提供要表达的情感"
        val stickers = stickerManager.searchStickersByKeyword(emotion)
        AppLogger.w("SendStickerPlugin", "send_sticker: 搜索到${stickers.size}个表情包")
        if (stickers.isEmpty()) return "没有找到匹配「$emotion」的表情包，用文字表达吧～"
        val sticker = stickers.random()
        AppLogger.w("SendStickerPlugin", "send_sticker: 选中 ${sticker.id}, path=${sticker.filePath}, onStickerSent=${onStickerSent != null}")
        if (sticker.filePath.isNotBlank()) {
            onStickerSent?.invoke(sticker.filePath)
        } else {
            AppLogger.w("SendStickerPlugin", "send_sticker: filePath为空，无法发送")
        }
        return "已发送表情包：${sticker.description.ifBlank { sticker.emotion }}（${sticker.id.removePrefix("builtin_")}）"
    }
}

class GenerateImagePlugin(private val context: Context) : ToolPlugin {
    override val name = "generate_image"
    override val description = "AI生成图片"
    var onImageGenerated: ((String) -> Unit)? = null
    var associatedEventId: String? = null
    var worldId: String = ""

    private var _enabledCached: Boolean? = null
    private var _enabledCheckedAt = 0L
    private val CACHE_DURATION_MS = 30_000L // 30秒缓存

    companion object {
        private val _generatedImagePaths = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val generatedImagePaths: List<String> get() = _generatedImagePaths.toList()

        fun consumeGeneratedImagePaths(): List<String> {
            val paths = _generatedImagePaths.toList()
            _generatedImagePaths.clear()
            return paths
        }

        @Deprecated("Use consumeGeneratedImagePaths() for multi-image support")
        fun consumeGeneratedImagePath(): String? = _generatedImagePaths.poll()

        internal fun addGeneratedImagePath(path: String) {
            _generatedImagePaths.add(path)
        }
    }

    override fun isEnabled(): Boolean {
        // 30秒内缓存结果，避免频繁实例化 VirtualWorldManager
        val now = System.currentTimeMillis()
        if (_enabledCached != null && (now - _enabledCheckedAt) < CACHE_DURATION_MS) {
            return _enabledCached!!
        }
        val vwManager = com.aicompanion.virtualworld.VirtualWorldManager(context, worldId)
        var result = vwManager.hasImageModelConfigured()
        if (!result) {
            val globalVw = com.aicompanion.virtualworld.VirtualWorldManager(context, "")
            result = globalVw.hasImageModelConfigured()
        }
        _enabledCached = result
        _enabledCheckedAt = now
        return result
    }

    override fun getDefinition() = ToolDefinition(
        name = "generate_image",
        description = """根据文字描述生成一张图片。严格按以下条件判断是否调用：

【必须调用的场景】
- 用户明确要求画图、生成图片、发照片、发图
- 虚拟世界推演中发生重大剧情转折（初次相遇/告白/战斗/死亡/婚礼等）
- 场景有强烈的视觉画面感且对理解剧情有帮助（星空下的对话、雨中的奔跑、樱花飘落等）
- 用户描述了一个具体画面想看效果

【不要调用的场景】
- 普通日常对话（吃饭、聊天、学习）
- 纯文字就能表达清楚的场景
- 连续多条记录都已生成过图片（避免刷屏）
- 内容涉及敏感/暴力/成人内容

【prompt编写规范】
- 用英文描述，包含主体、动作、环境、氛围、光影
- 示例："a girl with silver hair standing under cherry blossoms at sunset, soft golden light, anime style"
- 风格保持一致：anime/manga风格，与角色设定匹配""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "prompt" to mapOf("type" to "string", "description" to "图片描述，用英文描述效果更好，如：a girl standing under cherry blossoms, anime style, soft lighting"),
                "style" to mapOf("type" to "string", "description" to "风格提示，可选。如：anime style, realistic, watercolor, pixel art等")
            ),
            "required" to listOf("prompt")
        )
    )

    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val prompt = args.optString("prompt", "")
        if (prompt.isBlank()) return "请提供图片描述"
        val style = args.optString("style", "")
        val fullPrompt = if (style.isNotBlank()) "$prompt, $style" else prompt

        val vwManager = VirtualWorldManager(context, worldId)
        val effectiveManager = if (vwManager.hasImageModelConfigured()) vwManager
            else VirtualWorldManager(context, "")

        if (!effectiveManager.hasImageModelConfigured()) {
            return "图片生成API未配置，请在虚拟世界设置中配置图片生成API"
        }

        val eventId = associatedEventId ?: java.util.UUID.randomUUID().toString()
        AppLogger.w("GenerateImagePlugin", "generate_image: prompt=$fullPrompt, eventId=$eventId")

        return try {
            val result = withContext(Dispatchers.IO) {
                effectiveManager.generateImageForEvent(fullPrompt, eventId)
            }
            if (result != null) {
                addGeneratedImagePath(result)
                onImageGenerated?.invoke(result)
                "图片已生成成功，路径：$result"
            } else {
                "图片生成失败，请稍后再试"
            }
        } catch (e: Exception) {
            AppLogger.e("GenerateImagePlugin", "[Plugin-Image] generate_image图片生成失败: ${e.javaClass.simpleName}: ${e.message} | prompt='${fullPrompt.take(50)}'")
            "图片生成失败：${e.message}"
        }
    }
}
