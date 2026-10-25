/** 语音唤醒的统一入口：设置解析、服务启停、唤醒后的动作约定。 */
package com.aicompanion.voice.wake

import android.content.Context
import android.content.Intent
import android.os.Build
import com.aicompanion.settings.SettingsManager

/** 应用可见性：唤醒时决定是「原地开麦」还是「拉起界面」 */
object AppState {
    @Volatile
    var mainVisible: Boolean = false
}

object VoiceWake {

    /** MainActivity 收到该 extra 时自动开始语音输入 */
    const val EXTRA_VOICE_WAKE = "voice_wake_trigger"

    /** 调试用：让 MainActivity 用 assets 里的 wav 跑一次唤醒自测 */
    const val EXTRA_TEST_ASSET = "voice_wake_test_asset"

    /** 服务 → MainActivity 的进程内广播（MainActivity 在前台时优先走这条） */
    const val ACTION_WAKE = "com.aicompanion.action.VOICE_WAKE"

    /** 自测结果广播，extra: ok(boolean) / keyword(String) / message(String) */
    const val ACTION_TEST_RESULT = "com.aicompanion.action.VOICE_WAKE_TEST"

    const val EXTRA_RESULT_OK = "ok"
    const val EXTRA_RESULT_KEYWORD = "keyword"
    const val EXTRA_RESULT_MESSAGE = "message"

    /** 默认唤醒词：与角色的名字呼应，用户可在设置里改 */
    const val DEFAULT_WORDS = "星尘星尘,你好星尘"

    /** 唤醒词文本 → sherpa 关键词行 */
    fun keywordLines(context: Context, words: String, threshold: Float): List<String> =
        PinyinConverter.toKeywordLines(context, words.ifBlank { DEFAULT_WORDS }, 3.0f, threshold)

    /** 展示用的第一个唤醒词（通知里用） */
    fun primaryWord(words: String): String =
        words.split(Regex("[,，;；、\\n\\r]+")).firstOrNull { it.isNotBlank() }?.trim()
            ?: DEFAULT_WORDS.split(",")[0]

    fun start(context: Context) {
        val intent = Intent(context, VoiceWakeService::class.java).setAction(VoiceWakeService.ACTION_START)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e("VoiceWake", "启动唤醒服务失败: ${e.message}", e)
        }
    }

    fun stop(context: Context) {
        try {
            context.stopService(Intent(context, VoiceWakeService::class.java))
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e("VoiceWake", "停止唤醒服务失败: ${e.message}", e)
        }
    }

    /** 唤醒词/灵敏度改动后让服务重建流 */
    fun reload(context: Context) {
        try {
            val intent = Intent(context, VoiceWakeService::class.java)
                .setAction(VoiceWakeService.ACTION_RELOAD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {
        }
    }

    /** 按设置同步服务状态（MainActivity.onResume / 开机自启 / 设置开关都调它） */
    fun sync(context: Context, settings: SettingsManager) {
        if (settings.voiceWakeEnabled) start(context) else stop(context)
    }
}
