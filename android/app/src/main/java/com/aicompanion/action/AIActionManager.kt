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

    /**
     * 工具执行唯一入口：所有模型发起的工具调用都过这里。
     *
     * 在这里串了三件事（顺序有意义）：
     * 1. 把调用翻成人话并登记到 [AgentBus] —— 用户界面上能看见 AI 在干什么；
     * 2. [ToolPolicy] 闸门 —— 不可逆的动作要用户点头，自毁动作直接拒；
     * 3. 记录耗时与结果 —— 失败原因回给模型，让它自己纠正而不是硬编"成功了"。
     */
    suspend fun executeTool(name: String, arguments: String): String {
        val summary = com.aicompanion.agent.ToolNarrator.narrate(name, arguments)
        return try {
            when (val decision = com.aicompanion.agent.ToolPolicy.check(name, arguments)) {
                is com.aicompanion.agent.ToolPolicy.Decision.Deny -> {
                    val id = com.aicompanion.agent.AgentBus.begin(name, summary)
                    com.aicompanion.agent.AgentBus.markStatus(
                        id, com.aicompanion.agent.AgentBus.Status.DENIED, decision.reason
                    )
                    AppLogger.w(TAG, "拦截 $name：${decision.reason}")
                    "已拒绝执行：${decision.reason}。换一个不违反这条限制的做法，或直接告诉用户。"
                }

                is com.aicompanion.agent.ToolPolicy.Decision.Confirm -> {
                    val id = com.aicompanion.agent.AgentBus.begin(name, summary)
                    com.aicompanion.agent.AgentBus.markStatus(
                        id, com.aicompanion.agent.AgentBus.Status.WAIT_CONFIRM, decision.reason
                    )
                    val allow = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.aicompanion.agent.AgentBus.requestConfirm(summary, decision.reason)
                    }
                    if (!allow) {
                        AppLogger.w(TAG, "用户未确认 $name：$summary")
                        "用户没有允许「$summary」（${decision.reason}）。这件事不要重试，" +
                            "可以问用户要不要换成别的方式，或让他自己动手。"
                    } else {
                        runToolInternal(id, name, arguments)
                    }
                }

                is com.aicompanion.agent.ToolPolicy.Decision.Allow -> {
                    val id = com.aicompanion.agent.AgentBus.begin(name, summary)
                    runToolInternal(id, name, arguments)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "executeTool $name failed: ${e.message}")
            "工具执行失败: ${e.message}"
        }
    }

    private suspend fun runToolInternal(id: String, name: String, arguments: String): String {
        val t0 = System.currentTimeMillis()
        return try {
            val out = PluginRegistry.executePlugin(name, arguments)
            // 工具"正常返回"不等于"做成了"：没有通道、回读不符、目标不存在都是失败，
            // 但都会以字符串形式返回。活动条如果一律记 OK，用户看到的就是假成功。
            val failed = looksLikeFailure(out)
            com.aicompanion.agent.AgentBus.finish(
                id, !failed, out.lineSequence().firstOrNull().orEmpty(), System.currentTimeMillis() - t0
            )
            out
        } catch (e: Exception) {
            com.aicompanion.agent.AgentBus.finish(
                id, false, e.message ?: e.javaClass.simpleName, System.currentTimeMillis() - t0
            )
            throw e
        }
    }

    /** 结果文本是否表达"没做成"（工具层用字符串表达失败，这里统一识别） */
    private fun looksLikeFailure(out: String): Boolean {
        if (out.isBlank()) return false
        val head = out.lineSequence().firstOrNull().orEmpty()
        val markers = listOf(
            "没有 shell 通道", "错误：", "无法确认", "失败", "没找到", "未找到", "不支持",
            "通道未建立", "网关未连接", "回读值与期望不符", "没有日历权限",
        )
        return markers.any { head.contains(it) }
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        }

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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        }

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