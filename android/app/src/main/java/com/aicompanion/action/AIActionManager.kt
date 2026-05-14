/** AI动作管理器: 闹钟设置(关键词检测→解析时间→AlarmManager)/日程提醒/网页搜索(关键词提取→搜索摘要) */
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
import com.aicompanion.search.WebSearchEngine
import org.json.JSONArray
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
    private val webSearch = WebSearchEngine()

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

    fun isSearchRequest(message: String): Boolean {
        val lower = message.lowercase()
        val patterns = listOf(
            "搜索", "查一下", "帮我查", "查查", "搜一下", "百度一下",
            "搜索一下", "查一查", "帮我搜", "查资料", "查信息",
            "search", "google", "bing", "百度", "谷歌",
            "搜一搜", "查找", "查询", "搜搜"
        )
        return patterns.any { lower.contains(it) }
    }

    fun extractSearchKeywords(message: String): String {
        val clean = message.replace(Regex("^(搜索|查一下|帮我查|查查|搜一下|百度一下|搜索一下|查一查|帮我搜|查资料|查信息|搜一搜|查找|查询|搜搜)[：:,.。!！\\s]*"), "")
        return clean.trim().ifEmpty { message }
    }

    fun searchAndSummarize(query: String, apiClient: Any? = null): String {
        return try {
            val result = webSearch.search(query)
            if (result.success && result.results.isNotEmpty()) {
                val sb = StringBuilder()
                sb.appendLine("🔍 搜索结果：")
                result.results.take(3).forEachIndexed { i, item ->
                    sb.appendLine("${i + 1}. ${item.title}")
                    sb.appendLine("   ${item.snippet.take(150)}")
                }
                sb.toString()
            } else {
                val baike = webSearch.searchBaike(query)
                if (baike.success && baike.results.isNotEmpty()) {
                    val item = baike.results[0]
                    "📖 ${item.title}\n${item.snippet.take(300)}"
                } else {
                    "😅 没搜到相关内容，换个关键词试试？"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "search failed: ${e.message}")
            "😅 搜索失败了：${e.message}"
        }
    }

    val currentAlarmId: Int
        get() = prefs.getInt("last_alarm_id", REQUEST_ALARM)

    fun isAlarmRequest(message: String): Boolean {
        val lower = message.lowercase()
        val patterns = listOf(
            "闹钟", "定闹", "设闹", "订闹", "提醒我",
            "分钟后提醒", "小时后提醒",
            "set alarm", "alarm"
        )
        return patterns.any { lower.contains(it) }
    }

    fun parseAlarmInfo(message: String): AlarmInfo? {
        val now = Calendar.getInstance()

        val timePattern24 = Regex("(\\d{1,2})[:：]?(\\d{2})?(?:分)?[的]*闹钟|闹钟[定设]?[到在](\\d{1,2})[:：]?(\\d{2})")
        val hourMinPattern = Regex("(\\d{1,2})[：:](\\d{2})")
        val delayPattern = Regex("(\\d+)\\s*分钟\\s*(后|之后)")

        val delayMatch = delayPattern.find(message)
        if (delayMatch != null) {
            val minutes = delayMatch.groupValues[1].toIntOrNull() ?: return null
            val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, minutes) }
            return AlarmInfo(
                hour = cal.get(Calendar.HOUR_OF_DAY),
                minute = cal.get(Calendar.MINUTE),
                label = "提醒",
                isDelay = true
            )
        }

        val hmMatch = hourMinPattern.find(message)
        if (hmMatch != null) {
            val hour = hmMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: return null
            val minute = hmMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
            val fullPattern = Regex("(\\d{1,2})[:：](\\d{2})\\s*(?:分)?\\s*闹钟|闹钟.*?(\\d{1,2})[:：](\\d{2})")
            val label = if (message.contains("起床") || message.contains("起") || message.contains("醒")) "起床" else "闹钟"
            return AlarmInfo(hour = hour, minute = minute, label = label)
        }

        return null
    }

    fun setAlarm(info: AlarmInfo): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, info.hour)
            set(Calendar.MINUTE, info.minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val id = prefs.getInt("last_alarm_id", REQUEST_ALARM) + 1
        prefs.edit().putInt("last_alarm_id", id).apply()

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_TITLE, "⏰ ${info.label}")
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
        return "✅ 已帮你设置 ${info.label}（$timeStr）"
    }

    fun isScheduleRequest(message: String): Boolean {
        val lower = message.lowercase()
        val patterns = listOf(
            "安排", "日程", "记一下", "记着", "记住", "提醒我",
            "明天", "后天", "下周", "下个月", "周一", "周二", "周三",
            "周四", "周五", "周六", "周日", "星期",
            "上午", "下午", "晚上"
        )
        val count = patterns.count { lower.contains(it) }
        return count >= 2
    }

    fun parseScheduleInfo(message: String): ScheduleInfo? {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance()

        val datePatterns = listOf(
            "大后天" to { cal.add(Calendar.DAY_OF_YEAR, 3) },
            "后天" to { cal.add(Calendar.DAY_OF_YEAR, 2) },
            "明天" to { cal.add(Calendar.DAY_OF_YEAR, 1) },
            "下周一" to { cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); cal.add(Calendar.WEEK_OF_YEAR, 1) },
            "下周二" to { cal.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY); cal.add(Calendar.WEEK_OF_YEAR, 1) },
            "下周三" to { cal.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY); cal.add(Calendar.WEEK_OF_YEAR, 1) },
            "下周四" to { cal.set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY); cal.add(Calendar.WEEK_OF_YEAR, 1) },
            "下周五" to { cal.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY); cal.add(Calendar.WEEK_OF_YEAR, 1) },
            "下周六" to { cal.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY); cal.add(Calendar.WEEK_OF_YEAR, 1) },
            "下周日" to { cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY); cal.add(Calendar.WEEK_OF_YEAR, 1) },
            "周一" to { cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) },
            "周二" to { cal.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY) },
            "周三" to { cal.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY) },
            "周四" to { cal.set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY) },
            "周五" to { cal.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY) },
            "周六" to { cal.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY) },
            "周日" to { cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY) },
            "下周" to { cal.add(Calendar.WEEK_OF_YEAR, 1) }
        )

        for ((keyword, action) in datePatterns) {
            if (message.contains(keyword)) {
                action()
                break
            }
        }

        val hourMinPattern = Regex("(\\d{1,2})[：:](\\d{2})")
        val hourPattern = Regex("(\\d{1,2})\\s*点")
        val amPmPattern = Regex("(上午|下午|晚上)")

        val hmMatch = hourMinPattern.find(message)
        var hour: Int
        var minute: Int

        if (hmMatch != null) {
            hour = hmMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 9
            minute = hmMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
        } else {
            val hMatch = hourPattern.find(message)
            hour = hMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
            minute = 0
        }

        val amPmMatch = amPmPattern.find(message)
        if (amPmMatch != null) {
            val period = amPmMatch.groupValues[1]
            when {
                period == "下午" || period == "晚上" -> if (hour < 12) hour += 12
                period == "上午" -> if (hour >= 12) hour -= 12
            }
        }

        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)

        val description = message
            .replace(Regex("(帮我|请|给我|记一下|安排|日程|提醒我)"), "")
            .replace(Regex("(大后天|后天|明天|下周[一二三四五六日]?|周[一二三四五六日]|上午|下午|晚上|\\d{1,2}[：:]\\d{2}|\\d{1,2}点)"), "")
            .trim()
            .replace(Regex("^[，,、\\s]+"), "")
            .replace(Regex("[，,、\\s]+$"), "")
            .ifEmpty { "日程" }

        return ScheduleInfo(
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
            month = cal.get(Calendar.MONTH),
            year = cal.get(Calendar.YEAR),
            description = description
        )
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
            putExtra(EXTRA_TITLE, "📅 日程提醒")
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
        return "✅ 已记下：${info.description}（$dateStr）"
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

            val openIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

            val pendingIntent = PendingIntent.getActivity(
                context, id, openIntent,
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
                .setContentIntent(pendingIntent)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(id, notification)
        }
    }
}