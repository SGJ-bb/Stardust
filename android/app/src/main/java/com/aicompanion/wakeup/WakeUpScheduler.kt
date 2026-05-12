package com.aicompanion.wakeup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aicompanion.R
import com.aicompanion.ui.MainActivity
import java.util.*

class WakeUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WAKE_UP) {
            showWakeUpNotification(context)
        }
    }

    private fun showWakeUpNotification(context: Context) {
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

        val prefs = context.getSharedPreferences("wakeup_settings", Context.MODE_PRIVATE)
        val customMessage = prefs.getString("wake_message", null)
        
        val greeting = customMessage ?: run {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            when {
                hour < 6 -> "夜深了，星尘还在等你..."
                hour < 12 -> "早上好！星尘想和你说说话~"
                hour < 18 -> "下午好呀！今天过得怎么样？"
                else -> "晚上好，今天辛苦啦~"
            }
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("星尘唤醒")
            .setContentText(greeting)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_WAKEUP, notification)
    }

    companion object {
        const val ACTION_WAKE_UP = "com.aicompanion.ACTION_WAKE_UP"
        const val NOTIFICATION_ID_WAKEUP = 1001
    }
}

object WakeUpScheduler {
    private const val PREFS_NAME = "wakeup_settings"
    private const val KEY_WAKEUP_ENABLED = "wakeup_enabled"
    private const val KEY_WAKEUP_HOUR = "wakeup_hour"
    private const val KEY_WAKEUP_MINUTE = "wakeup_minute"

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
