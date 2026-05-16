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
import com.aicompanion.models.ToolDefinition
import com.aicompanion.plugin.PluginRegistry
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
                vibrationPattern = longArrayOf(0, 500, 500, 500)
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun getToolDefinitions(): List<ToolDefinition> {
        return PluginRegistry.getEnabledDefinitions()
    }

    fun executeTool(name: String, arguments: String): String {
        return try {
            PluginRegistry.executePlugin(name, arguments)
        } catch (e: Exception) {
            AppLogger.e(TAG, "executeTool $name failed: ${e.message}")
            "工具执行失败: ${e.message}"
        }
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
            notification.flags = notification.flags or Notification.FLAG_INSISTENT or Notification.FLAG_NO_CLEAR

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(id, notification)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val fullScreenIntent = Intent(context, com.aicompanion.ui.AlarmActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("alarm_label", title)
                        putExtra("alarm_message", message)
                    }
                    context.startActivity(fullScreenIntent)
                }
            } catch (_: Exception) {}
        }
    }
}