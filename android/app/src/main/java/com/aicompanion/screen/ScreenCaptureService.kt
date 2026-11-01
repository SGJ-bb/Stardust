package com.aicompanion.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.aicompanion.util.AppLogger

/**
 * 承载 MediaProjection 的前台服务。
 *
 * targetSdk 34 要求：调用 `getMediaProjection()` 之前，必须已经有一个
 * `foregroundServiceType=mediaProjection` 的前台服务在跑，否则直接抛 SecurityException。
 * 所以授权回来的结果要先进这个服务，由它 startForeground + attach。
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureSvc"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFY_ID = 0x5C01
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("屏幕读取已开启")
            .setContentText("用于识别界面上的文字（不会上传画面）")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFY_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFY_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (code != 0 && data != null) {
            ScreenCaptureManager.attach(this, code, data)
            AppLogger.i(TAG, "截屏服务已拿到授权 code=$code ready=${ScreenCaptureManager.isReady}")
        } else {
            AppLogger.w(TAG, "截屏服务收到空的授权数据")
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "屏幕读取", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }
}
