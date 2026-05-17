/** 唤醒调度器: 使用WorkManager定时唤醒后台服务, 确保AI桌宠保持活跃 */
package com.aicompanion.wakeup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aicompanion.R
import com.aicompanion.network.ApiClient
import com.aicompanion.ui.MainActivity
import com.aicompanion.models.ChatResponse
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class WakeUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WAKE_UP) {
            val taskName = intent.getStringExtra("task_name") ?: ""
            val taskDesc = intent.getStringExtra("task_description") ?: ""
            if (taskName.isNotBlank()) {
                showTaskNotification(context, taskName, taskDesc)
            } else {
                showWakeUpNotification(context, true)
            }
            val taskManager = WakeUpTaskManager(context)
            taskManager.load()
            taskManager.scheduleAll()
        }
    }

    private fun showWakeUpNotification(context: Context, tryAI: Boolean = true) {
        val channelId = "ai_wakeup_channel"
        val notificationManager = NotificationManagerCompat.from(context)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "AI唤醒",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "AI主动发起对话的提醒"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("auto_wakeup", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefs = context.getSharedPreferences("companion_settings", Context.MODE_PRIVATE)
        val customMessage = prefs.getString("wake_message", null)

        val activeId = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("active_persona_id", "default") ?: "default"
        val personaPrefs = context.getSharedPreferences("persona_data_$activeId", Context.MODE_PRIVATE)
        val aiName = personaPrefs.getString("persona_name", null) ?: "星尘"

        val baseGreeting = customMessage ?: run {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            when {
                hour < 6 -> "夜深了，星尘还在等你..."
                hour < 12 -> "早上好！星尘想和你说说话~"
                hour < 18 -> "下午好呀！今天过得怎么样？"
                else -> "晚上好，今天辛苦啦~"
            }
        }

        var notificationText = baseGreeting

        if (tryAI) {
            val settingsPrefs = context.getSharedPreferences("companion_settings", Context.MODE_PRIVATE)
            val apiUrl = settingsPrefs.getString("chat_api_url", "") ?: ""
            val apiModel = settingsPrefs.getString("chat_model", "gpt-4o-mini") ?: "gpt-4o-mini"
            val apiKey = try {
                val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val securePrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                    context, "companion_secure_prefs", masterKey,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                securePrefs.getString("chat_api_key", "") ?: ""
            } catch (_: Exception) {
                context.getSharedPreferences("companion_secure_prefs", Context.MODE_PRIVATE)
                    .getString("chat_api_key", "") ?: ""
            }

            if (apiUrl.isNotBlank()) {
                Thread {
                    try {
                        val aiClient = ApiClient(apiUrl, apiKey, apiModel)
                        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        val timeHint = when {
                            hour < 6 -> "凌晨"
                            hour < 12 -> "早上"
                            hour < 18 -> "下午"
                            else -> "晚上"
                        }
                        val activePersonaId = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            .getString("active_persona_id", "default") ?: "default"
                        val pm = com.aicompanion.persona.PersonaManager(context)
                        pm.load()
                        val persona = pm.getPersona(activePersonaId)
                        val personaPrompt = if (persona != null) {
                            buildString {
                                append("你是「${persona.name}」。")
                                if (persona.personality.isNotBlank()) append("\n性格：${persona.personality}")
                                if (persona.speechStyle.isNotBlank()) append("\n说话风格：${persona.speechStyle}")
                                if (persona.prompt.isNotBlank()) append("\n${persona.prompt}")
                            }
                        } else {
                            "你是「星尘」。"
                        }
                        val result = aiClient.sendProactiveChat(
                            persona?.name ?: "星尘",
                            personaPrompt,
                            "现在是$timeHint，你想主动找用户打个招呼。回复1-2句话，自然可爱，语气像认识很久的朋友。不需要自我介绍。直接说想说的话就好。",
                            "（用户现在没在看手机，你想主动找ta说话）"
                        )
                        if (result != null && result.text.isNotBlank()) {
                            val aiText = result.text
                            if (aiText.length <= 60) {
                                notificationText = aiText
                                val notification = NotificationCompat.Builder(context, channelId)
                                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                                    .setContentTitle("$aiName 唤醒")
                                    .setContentText(notificationText)
                                    .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
                                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                                    .setContentIntent(pendingIntent)
                                    .setAutoCancel(true)
                                    .build()
                                notificationManager.notify(NOTIFICATION_ID_WAKEUP, notification)
                                return@Thread
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("WakeUp", "AI wakeup failed: ${e.message}")
                    }
                }.apply { start() }
            }
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$aiName 唤醒")
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_WAKEUP, notification)
    }

    private fun showTaskNotification(context: Context, taskName: String, taskDesc: String) {
        val channelId = "ai_wakeup_channel"
        val notificationManager = NotificationManagerCompat.from(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "AI唤醒",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "AI定时任务提醒"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val activeId2 = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("active_persona_id", "default") ?: "default"
        val personaPrefs2 = context.getSharedPreferences("persona_data_$activeId2", Context.MODE_PRIVATE)
        val aiName = personaPrefs2.getString("persona_name", null)
            ?: context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("ai_name", "星尘") ?: "星尘"

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("auto_wakeup", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$aiName：$taskName")
            .setContentText(taskDesc)
            .setStyle(NotificationCompat.BigTextStyle().bigText(taskDesc))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(10000)
            .build()

        notificationManager.notify(taskName.hashCode() and 0x7FFFFFFF, notification)
    }

    companion object {
        const val ACTION_WAKE_UP = "com.aicompanion.ACTION_WAKE_UP"
        const val NOTIFICATION_ID_WAKEUP = 1001
    }
}

object WakeUpScheduler {
    private const val PREFS_NAME = "companion_settings"
    private const val KEY_WAKEUP_ENABLED = "wake_enabled"
    private const val KEY_WAKEUP_HOUR = "wake_hour"
    private const val KEY_WAKEUP_MINUTE = "wake_minute"

    fun isWakeupEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WAKEUP_ENABLED, false)
    }

    fun getWakeupTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hour = prefs.getInt(KEY_WAKEUP_HOUR, 9)
        val minute = prefs.getInt(KEY_WAKEUP_MINUTE, 0)
        return Pair(hour, minute)
    }

    fun setWakeupSettings(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_WAKEUP_ENABLED, enabled)
            putInt(KEY_WAKEUP_HOUR, hour)
            putInt(KEY_WAKEUP_MINUTE, minute)
            apply()
        }
        if (enabled) {
            scheduleWakeup(context, hour, minute)
        } else {
            cancelWakeup(context)
        }
    }

    fun scheduleWakeup(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val intent = Intent(context, WakeUpReceiver::class.java).apply {
            action = WakeUpReceiver.ACTION_WAKE_UP
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }

    fun cancelWakeup(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, WakeUpReceiver::class.java).apply {
            action = WakeUpReceiver.ACTION_WAKE_UP
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleOnBoot(context: Context) {
        if (isWakeupEnabled(context)) {
            val (hour, minute) = getWakeupTime(context)
            scheduleWakeup(context, hour, minute)
        }
    }
}
