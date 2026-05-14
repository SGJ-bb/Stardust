/** 开机自启广播: 监听Android开机广播, 自动启动后台服务 */
package com.aicompanion.wakeup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.aicompanion.services.BackgroundService
import com.aicompanion.settings.SettingsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            WakeUpScheduler.rescheduleOnBoot(context)

            val settingsManager = SettingsManager(context)
            if (settingsManager.autoStart) {
                try {
                    val serviceIntent = Intent(context, BackgroundService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d("BootReceiver", "BackgroundService started on boot")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to start BackgroundService: ${e.message}", e)
                }
            }
        }
    }
}
