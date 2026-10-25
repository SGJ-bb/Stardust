/**
 * 无障碍服务自愈。
 *
 * 为什么需要它：Android 会把 `enabled_accessibility_services` 记住，**正常情况下重启后自己就恢复了**，
 * 根本不需要用户每次重配。真正会丢的场景是：
 *  - 某些 ROM（含小米的部分版本）在低内存/更新后把服务踢掉；
 *  - 覆盖安装 APK 时服务被杀，个别 ROM 会顺手把开关关掉；
 *  - 用户手动关过。
 *
 * 有了 `WRITE_SECURE_SETTINGS`（由 `tools/adb_elevate.bat` 一次性授予，**重启保留**），
 * 应用就能自己把开关写回去 —— 这才是"重启不用折腾"的正解，而不是每次都连电脑。
 */
package com.aicompanion.screen

import android.content.Context
import android.provider.Settings
import com.aicompanion.util.AppLogger

object A11ySelfHeal {

    private const val TAG = "A11ySelfHeal"

    /** 优先用伪装服务（微信才放行节点树），退路是普通服务 */
    val DISGUISED = "com.aicompanion/com.google.android.accessibility.selecttospeak.SelectToSpeakService"
    /** 加强档：类名挂成 TalkBack。微信的自绘列表只在"读屏身份"下才补描述，读不到内容时换这个试 */
    val DISGUISED_TALKBACK = "com.aicompanion/com.google.android.marvin.talkback.TalkBackService"
    val PLAIN = "com.aicompanion/com.aicompanion.screen.ScreenRecognitionService"

    private val candidates = listOf(DISGUISED, DISGUISED_TALKBACK, PLAIN)

    enum class Result { OK, HEALED, NO_PERMISSION, FAILED }

    fun isEnabled(context: Context): Boolean {
        val cur = runCatching {
            Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services")
        }.getOrNull().orEmpty()
        return candidates.any { cur.contains(it) }
    }

    /** 当前生效的是哪个组件（用于日志/UI 展示） */
    fun current(context: Context): String {
        val cur = runCatching {
            Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services")
        }.getOrNull().orEmpty()
        return candidates.firstOrNull { cur.contains(it) }.orEmpty()
    }

    /**
     * 尝试把无障碍服务开关写回去。需要 `WRITE_SECURE_SETTINGS`。
     * 没有权限时返回 [Result.NO_PERMISSION]，上层应提示用户跑一次 `adb_elevate.bat`。
     */
    fun heal(context: Context): Result {
        // 注意判据是「当前是不是伪装服务」，不是「有没有启用任何一个」。
        // 只启用普通服务时 isEnabled 也是 true，但那样微信照样不给节点树 ——
        // 必须让它有机会把普通服务替换成伪装服务，否则永远停在"能用但读不到微信"的状态。
        if (current(context) == DISGUISED) {
            AppLogger.d(TAG, "伪装无障碍服务已启用，无需自愈")
            return Result.OK
        }
        return try {
            val cr = context.contentResolver
            val cur = Settings.Secure.getString(cr, "enabled_accessibility_services").orEmpty()
            // 保留别的服务，只追加我们的
            val merged = if (cur.isBlank()) {
                DISGUISED
            } else {
                val list = cur.split(':').filter { it.isNotBlank() && !candidates.contains(it) }
                (list + DISGUISED).joinToString(":")
            }
            Settings.Secure.putString(cr, "enabled_accessibility_services", merged)
            Settings.Secure.putInt(cr, "accessibility_enabled", 1)
            val ok = isEnabled(context)
            AppLogger.i(TAG, "自愈${if (ok) "成功" else "失败"}：$merged")
            if (ok) Result.HEALED else Result.FAILED
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "缺少 WRITE_SECURE_SETTINGS，无法自愈：${e.message}")
            Result.NO_PERMISSION
        } catch (e: Exception) {
            AppLogger.e(TAG, "自愈异常: ${e.message}")
            Result.FAILED
        }
    }

    /** 是否具备自愈权限（可用于 UI 提示） */
    fun canHeal(context: Context): Boolean = runCatching {
        // 读一次当前值再写回同值：能写就说明有权限
        val cr = context.contentResolver
        val cur = Settings.Secure.getString(cr, "enabled_accessibility_services").orEmpty()
        Settings.Secure.putString(cr, "enabled_accessibility_services", cur)
        true
    }.getOrDefault(false)
}
