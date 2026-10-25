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

            val taskManager = com.aicompanion.wakeup.WakeUpTaskManager(context)
            taskManager.rescheduleOnBoot()

            val settingsManager = SettingsManager(context)

            // 语音唤醒：开机自启（受设置开关控制，缺录音权限时服务自己会提示）
            if (settingsManager.voiceWakeEnabled) {
                com.aicompanion.voice.wake.VoiceWake.start(context)
            }

            // 无障碍自愈：正常情况系统会自己恢复，但个别 ROM 在重启/覆盖安装后会关掉开关。
            // 有 WRITE_SECURE_SETTINGS（一次性授予、重启保留）就自己写回，用户不用再碰电脑。
            val pending = goAsync()
            Thread {
                try {
                    // 等无障碍管理器初始化完（太早写会被系统覆盖）
                    Thread.sleep(8_000)
                    val r = com.aicompanion.screen.A11ySelfHeal.heal(context.applicationContext)
                    Log.i("BootReceiver", "无障碍自愈: $r (当前=${com.aicompanion.screen.A11ySelfHeal.current(context)})")
                } catch (_: Exception) {
                } finally {
                    pending.finish()
                }
            }.start()

            if (settingsManager.autoStart && settingsManager.backgroundRunning) {
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
