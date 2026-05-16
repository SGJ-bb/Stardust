/** 系统监控器: 持续监听电量变化/前台应用切换/网络连接状态 */
package com.aicompanion.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.aicompanion.ui.MainActivity

class SystemMonitor(private val context: Context) {

    companion object {
        private const val TAG = "SystemMonitor"
        const val CHANNEL_AI_MESSAGE = "ai_message_channel"
        const val CHANNEL_SYSTEM_ALERT = "system_alert_channel"
        const val NOTIFY_AI_MESSAGE = 2001
        const val NOTIFY_BATTERY_LOW = 2002

        private var lastBatteryAlertTime = 0L
        private const val BATTERY_ALERT_COOLDOWN = 30 * 60 * 1000L
    }

    private var batteryReceiver: BroadcastReceiver? = null
    var onBatteryLow: ((Int) -> Unit)? = null

    fun startMonitoring() {
        createNotificationChannels()
        registerBatteryReceiver()
    }

    fun stopMonitoring() {
        unregisterBatteryReceiver()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aiChannel = NotificationChannel(
                CHANNEL_AI_MESSAGE,
                "AI消息通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "AI桌宠的消息通知"
                enableVibration(true)
                enableLights(true)
                lightColor = 0xFF667eea.toInt()
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val alertChannel = NotificationChannel(
                CHANNEL_SYSTEM_ALERT,
                "系统提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "系统状态提醒（电量等）"
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(aiChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val percentage = (level * 100) / scale
                    checkBatteryLevel(percentage)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let { receiver ->
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
        batteryReceiver = null
    }

    private fun checkBatteryLevel(percentage: Int) {
        val now = System.currentTimeMillis()
        if (percentage <= 20 && percentage > 0 && (now - lastBatteryAlertTime) > BATTERY_ALERT_COOLDOWN) {
            lastBatteryAlertTime = now
            showBatteryNotification(percentage)
            onBatteryLow?.invoke(percentage)
        }
    }

    private fun showBatteryNotification(percentage: Int) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_SYSTEM_ALERT)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val notification = builder
            .setContentTitle("🔋 电量提醒")
            .setContentText("主人，手机只剩 $percentage% 的电量了，记得充电哦~")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(Notification.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFY_BATTERY_LOW, notification)
    }

    fun showAiMessageNotification(aiName: String, message: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayText = if (message.length > 80) message.take(80) + "..." else message

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_AI_MESSAGE)
                .setTimeoutAfter(8000)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val notification = builder
            .setContentTitle(aiName)
            .setContentText(displayText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(Notification.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setStyle(Notification.BigTextStyle().bigText(displayText))
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFY_AI_MESSAGE, notification)
    }

    fun getBatteryLevel(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else -1
    }
}