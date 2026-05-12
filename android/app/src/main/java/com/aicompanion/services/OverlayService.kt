package com.aicompanion.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.aicompanion.R
import com.aicompanion.overlay.OverlayWindow
import com.aicompanion.settings.SettingsManager
import com.aicompanion.voice.VoiceManager

class OverlayService : android.app.Service() {

    private var overlayWindow: OverlayWindow? = null
    private var settingsManager: SettingsManager? = null
    private var voiceManager: VoiceManager? = null

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_CHANNEL_ID = "overlay_service_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        try {
            settingsManager = SettingsManager(this)
            voiceManager = VoiceManager(this)
            overlayWindow = OverlayWindow(this)
            Log.d(TAG, "Service created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create service: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            overlayWindow?.show()
            Log.d(TAG, "Service started, overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${e.message}", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            overlayWindow?.cleanup()
            voiceManager?.cleanup()
            overlayWindow = null
            voiceManager = null
            settingsManager = null
            Log.d(TAG, "Service destroyed and references cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "AI桌宠悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI桌宠悬浮窗服务通知"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }
        return builder
            .setContentTitle("星尘")
            .setContentText("AI桌宠运行中")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
}
