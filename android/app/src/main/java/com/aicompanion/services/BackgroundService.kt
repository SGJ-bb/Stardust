/** 后台服务: 系统监控启动(电量低通知)/每日日记自动生成检查/前台通知保活 */
package com.aicompanion.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.aicompanion.diary.DiaryManager
import com.aicompanion.settings.SettingsManager
import com.aicompanion.settings.DiaryTriggerMode
import com.aicompanion.ui.MainActivity

class BackgroundService : android.app.Service() {

    private var settingsManager: SettingsManager? = null
    private var diaryManager: DiaryManager? = null
    private var systemMonitor: com.aicompanion.services.SystemMonitor? = null
    private var lastDiaryCheckDay = ""

    companion object {
        private const val TAG = "BackgroundService"
        private const val NOTIFICATION_CHANNEL_ID = "background_service_channel"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_STOP = "com.aicompanion.action.STOP_BACKGROUND"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            settingsManager = SettingsManager(this)
            diaryManager = DiaryManager(this)
            systemMonitor = com.aicompanion.services.SystemMonitor(this).apply {
                startMonitoring()
                onBatteryLow = { percentage ->
                    Log.d(TAG, "Battery low in background: $percentage%")
                    val sm = settingsManager
                    if (sm?.chatApiUrl?.isNotBlank() == true) {
                        showBatteryNotification(percentage)
                    }
                }
            }
            Log.d(TAG, "Background service created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create background service: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent?.action == ACTION_STOP) {
                stopSelf()
                return START_NOT_STICKY
            }

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())

            checkDailyDiary()

            Log.d(TAG, "Background service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start background service: ${e.message}", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        systemMonitor?.stopMonitoring()
        settingsManager = null
        diaryManager = null
        systemMonitor = null
        Log.d(TAG, "Background service destroyed")
    }

    private fun checkDailyDiary() {
        val sm = settingsManager
        val dm = diaryManager ?: return
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val today = sdf.format(java.util.Date())
        if (today != lastDiaryCheckDay) {
            lastDiaryCheckDay = today
            val mode = sm?.diaryTriggerMode
            if (mode == DiaryTriggerMode.HOURLY || mode == DiaryTriggerMode.TWO_HOURS || mode == DiaryTriggerMode.MESSAGES_50) {
                dm.updateOrGenerateDailyDiary(emptyList(), 50)
            }
        }
    }

    private fun showBatteryNotification(percentage: Int) {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "system_alert_channel")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle("🔋 电量提醒")
            .setContentText("主人，手机只剩 $percentage% 的电量了，记得充电哦~")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(Notification.PRIORITY_HIGH)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(2002, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "星尘后台服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "让星尘在后台保持运行"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BackgroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("星尘")
            .setContentText("我在后台陪着你呢~")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "关闭后台", stopPendingIntent)
            .build()
    }
}