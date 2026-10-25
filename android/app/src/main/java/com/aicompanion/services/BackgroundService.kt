/** 后台服务: 每日日记自动生成检查/前台通知保活 */
package com.aicompanion.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.aicompanion.diary.DiaryManager
import com.aicompanion.settings.SettingsManager
import com.aicompanion.settings.DiaryTriggerMode
import com.aicompanion.ui.MainActivity
import com.aicompanion.util.AppLogger

class BackgroundService : android.app.Service() {

    private var settingsManager: SettingsManager? = null
    private var lastDiaryCheckDay = ""

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "background_service_channel"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_STOP = "com.aicompanion.action.STOP_BACKGROUND"
        private const val PREFS_NAME = "background_service_prefs"
        private const val KEY_LAST_DIARY_DAY = "last_diary_check_day"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            settingsManager = SettingsManager(this)
            lastDiaryCheckDay = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_LAST_DIARY_DAY, "") ?: ""
        } catch (e: Exception) {
            AppLogger.e("BgService", "Failed to create background service: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val action = intent?.action

            if (action == ACTION_STOP) {
                stopSelf()
                return START_NOT_STICKY
            }

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())

            checkDailyDiary()

        } catch (e: Exception) {
            AppLogger.e("BgService", "Failed to start background service: ${e.message}", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        settingsManager = null
    }

    private fun checkDailyDiary() {
        val sm = settingsManager ?: return
        val activePersonaId = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("active_persona_id", "default") ?: "default"
        val dm = DiaryManager(this, activePersonaId)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val today = sdf.format(java.util.Date())
        if (today == lastDiaryCheckDay) return

        lastDiaryCheckDay = today
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_LAST_DIARY_DAY, today).apply()

        val mode = sm.diaryTriggerMode
        if (mode == DiaryTriggerMode.MANUAL) {
            AppLogger.w("BgService", "checkDailyDiary: 手动模式，跳过")
            return
        }
        if (mode == DiaryTriggerMode.DAILY_10PM) {
            // 22:00 的自动写日记依赖前台 Handler 定时器，App 进程不在时不会执行；
            // 这里补一条后台兜底：到点且今天还没有日记时才生成。
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (hour < 22 || dm.getDiaryByDate(today) != null) {
                AppLogger.w("BgService", "checkDailyDiary: DAILY_10PM 未到点或已有日记，跳过")
                return
            }
        }

        // 用今天的真实聊天记录生成；没有聊天就跳过，避免写出空洞内容
        val texts = try {
            com.aicompanion.storage.ChatHistoryStorage(this)
                .getMessages("persona", activePersonaId, today)
                .map { it.text }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            AppLogger.e("BgService", "checkDailyDiary 读取聊天记录失败: ${e.message}")
            emptyList()
        }
        if (texts.isEmpty()) {
            AppLogger.w("BgService", "checkDailyDiary: 今天没有聊天记录，跳过")
            return
        }

        dm.updateOrGenerateDailyDiary(texts, 50, force = true)
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
