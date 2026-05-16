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
    override fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val minutes = args.optInt("minutes", 0)
        if (minutes <= 0) return "错误：请提供有效的分钟数"
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
    override fun execute(arguments: String): String {
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
    override fun execute(arguments: String): String {
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
    override fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val query = args.optString("query", "")
        if (query.isBlank()) return "错误：请提供搜索关键词"
        AppLogger.d("WebSearchPlugin", "search_web: query=$query")
        return searchEngine.searchAndSummarize(query)
    }
}

class SearchMemoryPlugin : ToolPlugin {
    override val name = "search_memory"
    override val description = "搜索用户的记忆池和历史日记"
    var onSearchMemory: ((String, Int) -> String)? = null
    override fun getDefinition() = ToolDefinition(
        name = "search_memory",
        description = "搜索用户的记忆池和历史日记。当你想了解用户之前提到过什么、用户的喜好习惯、过去的经历等信息时调用。可以多次调用不同query来获取完整信息。",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf("type" to "string", "description" to "搜索关键词，如「喜欢吃什么」「生日」「工作」等")
            ),
            "required" to listOf("query")
        )
    )
    override fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val query = args.optString("query", "")
        if (query.isBlank()) return "错误：请提供搜索关键词"
        return onSearchMemory?.invoke(query, 5) ?: "记忆搜索功能未初始化"
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
    override fun execute(arguments: String): String {
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
    override fun execute(arguments: String): String {
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
        AppLogger.d("NicknamePlugin", "summarize_nicknames: 生成了称呼列表 [$summary]")
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
        description = "发送一个表情包来表达你的情感。你有丰富的表情包可用（偷听、偷瞄、卖萌、吐槽、呆滞、哭泣、宕机、慌张、捂嘴笑、调侃、邪恶的笑、风趣调侃、骂人、鬼迷日眼的笑等）。当你有以下强烈情绪时请务必调用此工具而不是只用文字：开心/可爱/撒娇→卖萌揣手手；偷笑/窃喜→捂嘴笑；无语/嫌弃→吐槽；发呆/放空→呆滞；难过/委屈→哭泣；崩溃/卡住→宕机；慌张/紧张→慌张；好奇/八卦→偷听；害羞/腼腆→偷瞄；调侃/打趣→调侃；坏笑/腹黑→邪恶的笑；搞笑/幽默→风趣调侃；嫌弃/骂人→骂别人是猪；狡黠/贼笑→鬼迷日眼的笑。",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "emotion" to mapOf("type" to "string", "description" to "你想表达的情感，如：开心、难过、撒娇、吐槽、发呆、偷笑、慌张、好奇、害羞、调侃、坏笑、嫌弃等")
            ),
            "required" to listOf("emotion")
        )
    )
    override fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val emotion = args.optString("emotion", "")
        if (emotion.isBlank()) return "请提供要表达的情感"
        val stickers = stickerManager.searchStickersByKeyword(emotion)
        if (stickers.isEmpty()) return "没有找到匹配「$emotion」的表情包，用文字表达吧～"
        val sticker = stickers.first()
        if (sticker.filePath.isNotBlank()) {
            onStickerSent?.invoke(sticker.filePath)
        }
        return "已发送表情包：${sticker.description.ifBlank { sticker.emotion }}（${sticker.id.removePrefix("builtin_")}）"
    }
}
