/**
 * Shizuku 桥：免 root 拿到 ADB（shell UID 2000）级权限。
 *
 * 为什么值得接：正式版 Android 上，「改系统设置 / input 注入 / screencap / pm grant / am force-stop」
 * 这些能力既不属于普通应用，也不需要 root —— 它们属于 shell 用户。Shizuku 就是用一次「无线调试授权」
 * 把 shell 身份借给应用，从而在**不 root、不刷机**的前提下拿到这套能力。
 *
 * 使用前提（用户侧一次性操作）：
 *   1. 安装 Shizuku（应用商店或 shizuku.rikka.app）；
 *   2. 用「无线调试」或 USB 启动它的服务；
 *   3. 在本应用里点「授权」并在 Shizuku 弹窗里允许。
 *
 * 代码侧要求（缺一不可，缺了就会永远返回「未安装」）：
 *   · 依赖 dev.rikka.shizuku:api + :provider；
 *   · AndroidManifest 里声明 rikka.shizuku.ShizukuProvider（authorities = ${applicationId}.shizuku）；
 *   · 权限请求走 Shizuku.requestPermission，结果通过 listener 回来。
 */
package com.aicompanion.shell

import android.content.pm.PackageManager
import com.aicompanion.util.AppLogger
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArrayList

object ShizukuBridge {

    private const val TAG = "ShizukuBridge"

    /** 请求权限用的 requestCode（Shizuku 会把它回传给 listener） */
    const val REQUEST_CODE = 4101

    private val listeners = CopyOnWriteArrayList<(granted: Boolean) -> Unit>()

    @Volatile
    private var resultListenerAttached = false

    /** Shizuku 服务是否在跑（装了但没启动也是 false） */
    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        AppLogger.w(TAG, "pingBinder 失败: ${t.message}")
        false
    }

    /** 是否已授权本应用 */
    fun hasPermission(): Boolean = try {
        if (!Shizuku.pingBinder()) {
            false
        } else if (Shizuku.isPreV11()) {
            // v11 之前没有权限模型，能连上就等于已授权
            true
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    } catch (t: Throwable) {
        AppLogger.w(TAG, "checkSelfPermission 失败: ${t.message}")
        false
    }

    /** Shizuku 服务版本，用于排障展示 */
    fun version(): Int = try {
        if (Shizuku.pingBinder()) Shizuku.getVersion() else -1
    } catch (_: Throwable) {
        -1
    }

    /** 发起授权（会弹出 Shizuku 自己的确认页） */
    fun requestPermission(onResult: ((Boolean) -> Unit)? = null) {
        if (onResult != null) {
            listeners.add(onResult)
            attachListener()
        }
        try {
            if (!Shizuku.pingBinder()) {
                AppLogger.w(TAG, "Shizuku 服务未运行，无法请求授权")
                notify(false)
                return
            }
            if (Shizuku.isPreV11()) {
                notify(true)
                return
            }
            if (hasPermission()) {
                notify(true)
                return
            }
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "requestPermission 失败: ${t.message}", t)
            notify(false)
        }
    }

    /** 是否应展示「为什么要这个权限」的解释（用户点过拒绝时） */
    fun shouldShowRationale(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11() && Shizuku.shouldShowRequestPermissionRationale()
    } catch (_: Throwable) {
        false
    }

    private fun attachListener() {
        if (resultListenerAttached) return
        try {
            Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode != REQUEST_CODE) return@addRequestPermissionResultListener
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                AppLogger.i(TAG, "Shizuku 授权结果: $granted")
                notify(granted)
            }
            resultListenerAttached = true
        } catch (t: Throwable) {
            AppLogger.w(TAG, "注册授权监听失败: ${t.message}")
        }
    }

    private fun notify(granted: Boolean) {
        val snapshot = listeners.toList()
        listeners.clear()
        snapshot.forEach { runCatching { it(granted) } }
    }

    /**
     * 以 shell 身份起一个进程（sh -c <command>）。
     * 返回 null 表示 Shizuku 通道不可用（未装 / 未启动 / 未授权）。
     */
    fun newProcess(command: String): Process? = try {
        if (!hasPermission()) null
        else Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
    } catch (t: Throwable) {
        AppLogger.e(TAG, "newProcess 失败: ${t.message}", t)
        null
    }

    /** 供 UI 展示的一句话状态 */
    fun statusText(): String = when {
        !isBinderAlive() -> "未安装 / 服务未启动"
        !hasPermission() -> "已连接，待授权"
        else -> "已授权（ADB 级，v${version()}）"
    }
}
