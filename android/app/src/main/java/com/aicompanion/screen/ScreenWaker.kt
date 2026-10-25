/**
 * 熄屏自唤醒。
 *
 * 为什么不能直接用 PowerManager：
 * `PowerManager.wakeUp()` 是 @SystemApi，需要 `android.permission.DEVICE_POWER`，
 * 普通应用调用只会拿到 SecurityException。所以按可行性从高到低走三条路：
 *
 *  ① 命令通道（shell 身份）：`input keyevent KEYCODE_WAKEUP`
 *     —— 已授权 Shizuku / 自建 ADB / root 时可用，最可靠；
 *  ② 命令通道解除非安全锁屏：`wm dismiss-keyguard`
 *     —— 只对「无密码/无图案」的锁屏有效，有密码时必须人工解锁；
 *  ③ 带 turnScreenOn 的 Activity（最后兜底）
 *     —— 后台启动 Activity 在 Android 10+ 受限制，可能被系统拦掉，所以只当兜底。
 *
 * 设计原则：**永远不假装成功**。返回里带 needsUnlock，让上层明确告诉用户"已唤醒，但要解锁"。
 */
package com.aicompanion.screen

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.aicompanion.shell.ShellExecutor
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.runBlocking

object ScreenWaker {

    private const val TAG = "ScreenWaker"

    data class State(
        /** 屏幕亮着且可用（未锁屏） */
        val ready: Boolean,
        /** 是否执行过唤醒动作 */
        val woke: Boolean,
        /** 亮着但被锁屏挡住 */
        val needsUnlock: Boolean,
        /** 采取的手段，便于排查 */
        val how: String,
    )

    fun isScreenOn(context: Context): Boolean = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isInteractive
    }.getOrDefault(true)

    fun isLocked(context: Context): Boolean = runCatching {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        km.isKeyguardLocked
    }.getOrDefault(false)

    private fun isSecureLock(context: Context): Boolean = runCatching {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        km.isDeviceSecure
    }.getOrDefault(false)

    /**
     * 纯应用侧的唤醒尝试：SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP。
     * 这两个常量早已标记 deprecated，但绝大多数 ROM 仍然照常生效，而且只需要
     * WAKE_LOCK（普通权限，装包即授予）—— 所以放在最前面试，能成就不必麻烦用户授权。
     */
    @Suppress("DEPRECATION")
    private fun wakeByWakeLock(context: Context): Boolean = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "aicompanion:wake"
        )
        lock.acquire(3000L)   // 带超时，绝不泄漏
        true
    }.getOrElse {
        AppLogger.w(TAG, "WakeLock 唤醒失败: ${it.message}")
        false
    }

    private fun shell(context: Context, cmd: String, timeoutMs: Long = 6000L): Boolean =
        runCatching {
            runBlocking {
                val r = ShellExecutor.run(context, cmd, timeoutMs)
                r.exitCode == 0
            }
        }.getOrElse {
            AppLogger.w(TAG, "命令失败($cmd): ${it.message}")
            false
        }

    /**
     * 确保屏幕可用；必要时唤醒。
     * 调用方应在**后台线程**上调用（内部会阻塞最多 [timeoutMs]）。
     */
    fun ensureAwake(context: Context, timeoutMs: Long = 3000L): State {
        var woke = false
        var how = ""

        if (!isScreenOn(context)) {
            AppLogger.i(TAG, "检测到熄屏，尝试唤醒")
            // ⓪ 纯应用侧：WakeLock（不需要任何授权）
            if (wakeByWakeLock(context) && waitInteractive(context, 900L)) {
                woke = true
                how = "wakelock:ACQUIRE_CAUSES_WAKEUP"
            }
            // ① 命令通道（最可靠）
            if (!woke && shell(context, "input keyevent KEYCODE_WAKEUP")) {
                how = "shell:KEYCODE_WAKEUP"
                woke = waitInteractive(context, 1500L)
            }
            if (woke) {
                // 已经亮起来了，不用再走后面的兜底
            } else {
                // ③ 兜底：turnScreenOn 的 Activity
                val started = runCatching {
                    context.startActivity(
                        Intent(context, WakeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    )
                    true
                }.getOrElse {
                    AppLogger.w(TAG, "启动唤醒界面失败: ${it.message}")
                    false
                }
                if (started && waitInteractive(context, 1500L)) {
                    woke = true
                    how = "activity:turnScreenOn"
                } else if (started) {
                    how = "activity:turnScreenOn(未生效)"
                }
            }
            if (!woke) AppLogger.w(TAG, "唤醒失败：命令通道不可用且后台启动被拦")
        }

        // 锁屏：无密码时可以直接解开；有密码只能请用户解锁
        var needsUnlock = false
        if (isLocked(context)) {
            if (isSecureLock(context)) {
                needsUnlock = true
                AppLogger.w(TAG, "设备有密码锁，无法自动解锁")
            } else {
                val ok = shell(context, "wm dismiss-keyguard")
                // 等锁屏消失
                var i = 0
                while (i < 10 && isLocked(context)) {
                    Thread.sleep(120)
                    i++
                }
                if (isLocked(context)) {
                    needsUnlock = true
                } else if (ok) {
                    how = if (how.isBlank()) "shell:dismiss-keyguard" else "$how + dismiss-keyguard"
                }
            }
        }

        val ready = isScreenOn(context) && !isLocked(context)
        val state = State(ready = ready, woke = woke, needsUnlock = needsUnlock, how = how)
        AppLogger.i(TAG, "唤醒结果 ready=${state.ready} woke=$woke needsUnlock=$needsUnlock how=$how")
        return state
    }

    private fun waitInteractive(context: Context, timeoutMs: Long): Boolean {
        var waited = 0L
        while (waited < timeoutMs) {
            if (isScreenOn(context)) return true
            Thread.sleep(100)
            waited += 100
        }
        return isScreenOn(context)
    }

    private var holdLock: PowerManager.WakeLock? = null

    /**
     * 操作期间保持屏幕常亮（不强制点亮，只在亮着时别让它熄）。
     * 目的：避免"刚识别完 → 屏幕睡了 → 点击落空"这种偶发失败。
     * 带超时，异常路径也不会永久持有。
     */
    fun keepAwake(context: Context, timeoutMs: Long = 60_000L) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) return
            holdLock?.let { if (it.isHeld) return }
            @Suppress("DEPRECATION")
            val lock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "aicompanion:hold")
            lock.setReferenceCounted(false)
            lock.acquire(timeoutMs)
            holdLock = lock
        }.onFailure { AppLogger.w(TAG, "keepAwake 失败: ${it.message}") }
    }

    fun releaseAwake() {
        runCatching {
            holdLock?.let { if (it.isHeld) it.release() }
            holdLock = null
        }
    }

    /** 给上层用的可读提示 */
    fun describe(s: State): String = when {
        s.ready -> if (s.woke) "已唤醒屏幕并可操作（${s.how}）" else "屏幕可用"
        s.needsUnlock -> "屏幕已点亮，但设备设了密码锁，需要你手动解锁后重试"
        else -> "屏幕仍是熄屏状态，且没有可用的唤醒通道（需要 Shizuku / ADB 授权）"
    }
}
