package com.aicompanion.action

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.aicompanion.models.ToolDefinition
import com.aicompanion.search.WebSearchEngine
import com.aicompanion.util.AppLogger
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AIActionManager(private val context: Context) {

    companion object {
        private const val TAG = "AIActionManager"
        const val CHANNEL_REMINDER = "reminder_channel"
        const val REQUEST_ALARM = 5001
        const val REQUEST_SCHEDULE = 6001
        const val ACTION_ALARM_TRIGGER = "com.aicompanion.action.ALARM_TRIGGER"
        const val ACTION_SCHEDULE_TRIGGER = "com.aicompanion.action.SCHEDULE_TRIGGER"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_ID = "extra_id"
    }

    private val prefs = context.getSharedPreferences("schedule_data", Context.MODE_PRIVATE)
    private val webSearch = WebSearchEngine(context)
    var onNicknamesGenerated: ((List<String>) -> Unit)? = null
    var onSearchMemory: ((String, Int) -> String)? = null

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_REMINDER, "提醒通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "闹钟和日程提醒"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun getToolDefinitions(): List<ToolDefinition> {
        val tools = mutableListOf<ToolDefinition>()
        tools.addAll(listOf(
            ToolDefinition(
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
            ),
            ToolDefinition(
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
            ),
            ToolDefinition(
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
        ))

        val sm = com.aicompanion.settings.SettingsManager(context)
        if (sm.searchEnabled) {
            tools.add(
                ToolDefinition(
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
            )
        }

        tools.add(
            ToolDefinition(
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
        )

        tools.add(
            ToolDefinition(
                name = "get_current_time",
                description = "获取当前系统时间。当用户问时间相关的问题时调用此工具。",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>(),
                    "required" to emptyList<String>()
                )
            )
        )

        tools.add(
            ToolDefinition(
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
        )

        return tools
    }

    fun executeTool(name: String, arguments: String): String {
        return try {
            val args = JSONObject(arguments)
            when (name) {
                "set_alarm" -> executeSetAlarm(args)
                "set_alarm_at_time" -> executeSetAlarmAtTime(args)
                "add_schedule" -> executeAddSchedule(args)
                "search_web" -> executeSearch(args)
                "search_memory" -> executeSearchMemory(args)
                "get_current_time" -> executeGetCurrentTime()
                "summarize_nicknames" -> executeSummarizeNicknames(args)
                else -> "未知工具: $name"
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "executeTool $name failed: ${e.message}")
            "工具执行失败: ${e.message}"
        }
    }

    private fun executeSetAlarm(args: JSONObject): String {
        val minutes = args.optInt("minutes", 0)
        if (minutes <= 0) return "错误：请提供有效的分钟数"
        val label = args.optString("label", "提醒")

        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, minutes) }
        val info = AlarmInfo(
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            label = label,
            isDelay = true
        )
        return setAlarm(info)
    }

    private fun executeSetAlarmAtTime(args: JSONObject): String {
        val hour = args.optInt("hour", -1)
        val minute = args.optInt("minute", 0)
        if (hour < 0 || hour > 23) return "错误：小时必须在0-23之间"
        val label = args.optString("label", "闹钟")

        val info = AlarmInfo(hour = hour, minute = minute, label = label)
        return setAlarm(info)
    }

    private fun executeAddSchedule(args: JSONObject): String {
        val description = args.optString("description", "")
        val datetime = args.optString("datetime", "")
        if (description.isBlank()) return "错误：请提供日程描述"

        val cal = Calendar.getInstance()
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            cal.time = sdf.parse(datetime) ?: return "错误：无法解析日期时间「$datetime」，请使用格式 yyyy-MM-dd HH:mm"
        } catch (e: Exception) {
            return "错误：日期格式不正确「$datetime」，请使用格式如 2026-05-16 15:00"
        }

        val info = ScheduleInfo(
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
            month = cal.get(Calendar.MONTH),
            year = cal.get(Calendar.YEAR),
            description = description
        )
        return addSchedule(info)
    }

    private fun executeSearch(args: JSONObject): String {
        val query = args.optString("query", "")
        if (query.isBlank()) return "错误：请提供搜索关键词"
        AppLogger.d(TAG, "search_web: query=$query")
        val result = webSearch.searchAndSummarize(query)
        AppLogger.d(TAG, "search_web: result ${result.length} chars")
        return result
    }

    private fun executeSearchMemory(args: JSONObject): String {
        val query = args.optString("query", "")
        if (query.isBlank()) return "错误：请提供搜索关键词"
        AppLogger.d(TAG, "search_memory: query=$query")
        val callback = onSearchMemory ?: return "记忆搜索功能未初始化"
        return callback(query, 5)
    }

    private fun executeGetCurrentTime(): String {
        val now = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy年M月d日 EEEE HH:mm", Locale.CHINESE)
        return "当前时间：${sdf.format(now.time)}"
    }

    private fun executeSummarizeNicknames(args: JSONObject): String {
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
        AppLogger.d(TAG, "summarize_nicknames: 生成了称呼列表 [$summary]")
        return "已收到你为主人建议的称呼：$summary。系统已保存这些称呼，你可以在后续的对话中自由选择使用其中一个来称呼主人。"
    }

    fun setAlarm(info: AlarmInfo): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, info.hour)
            set(Calendar.MINUTE, info.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val id = prefs.getInt("last_alarm_id", REQUEST_ALARM) + 1
        prefs.edit().putInt("last_alarm_id", id).apply()

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_TITLE, "\u23F0 ${info.label}")
            putExtra(EXTRA_MESSAGE, "${info.hour}：${String.format("%02d", info.minute)} 的闹钟响了！")
            putExtra(EXTRA_ID, id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            pendingIntent
        )

        val timeStr = "${info.hour}:${String.format("%02d", info.minute)}"
        val triggerAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(cal.time)
        AppLogger.d(TAG, "setAlarm: id=$id, label=${info.label}, trigger=$triggerAt, delayMs=${cal.timeInMillis - System.currentTimeMillis()}")
        return "闹钟已设置：${info.label}将在 $timeStr 触发"
    }

    fun addSchedule(info: ScheduleInfo): String {
        val id = prefs.getInt("last_schedule_id", REQUEST_SCHEDULE) + 1
        prefs.edit().putInt("last_schedule_id", id).apply()

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, info.year)
            set(Calendar.MONTH, info.month)
            set(Calendar.DAY_OF_MONTH, info.dayOfMonth)
            set(Calendar.HOUR_OF_DAY, info.hour)
            set(Calendar.MINUTE, info.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val scheduleKey = "schedule_$id"
        val scheduleJson = JSONObject().apply {
            put("id", id)
            put("description", info.description)
            put("year", info.year)
            put("month", info.month)
            put("day", info.dayOfMonth)
            put("hour", info.hour)
            put("minute", info.minute)
            put("timestamp", cal.timeInMillis)
        }
        prefs.edit().putString(scheduleKey, scheduleJson.toString()).commit()

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SCHEDULE_TRIGGER
            putExtra(EXTRA_TITLE, "\uD83D\uDCC5 日程提醒")
            putExtra(EXTRA_MESSAGE, info.description)
            putExtra(EXTRA_ID, id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, id + 1000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            pendingIntent
        )

        val dateStr = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(cal.time)
        return "日程已添加：${info.description}（$dateStr）"
    }

    fun getAllSchedules(): List<ScheduleInfo> {
        val list = mutableListOf<ScheduleInfo>()
        val allKeys = prefs.all.keys.filter { it.startsWith("schedule_") }
        for (key in allKeys) {
            try {
                val json = JSONObject(prefs.getString(key, "{}"))
                list.add(ScheduleInfo(
                    hour = json.optInt("hour"),
                    minute = json.optInt("minute"),
                    dayOfMonth = json.optInt("day"),
                    month = json.optInt("month"),
                    year = json.optInt("year"),
                    description = json.optString("description", "")
                ))
            } catch (_: Exception) {}
        }
        return list
    }

    data class AlarmInfo(
        val hour: Int,
        val minute: Int,
        val label: String = "闹钟",
        val isDelay: Boolean = false
    )

    data class ScheduleInfo(
        val hour: Int,
        val minute: Int,
        val dayOfMonth: Int,
        val month: Int,
        val year: Int,
        val description: String
    )

    class ReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val title = intent.getStringExtra(EXTRA_TITLE) ?: "提醒"
            val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
            val id = intent.getIntExtra(EXTRA_ID, 0)
            AppLogger.d(TAG, "ReminderReceiver: ALARM FIRED! id=$id, title=$title, action=${intent.action}")

            val alarmIntent = Intent(context, com.aicompanion.ui.AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("alarm_label", title)
                putExtra("alarm_message", message)
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                context, id, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val openIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

            val contentPendingIntent = PendingIntent.getActivity(
                context, id + 10000, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_REMINDER)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }

            val notification = builder
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setPriority(Notification.PRIORITY_MAX)
                .setDefaults(Notification.DEFAULT_ALL)
                .setCategory(Notification.CATEGORY_ALARM)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(id, notification)
        }
    }
}