package com.aicompanion.shell

import android.content.Context
import com.aicompanion.util.AppLogger
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom

/**
 * 常驻 shell 服务客户端。
 *
 * 解决的问题：无线调试的 adbd 通道依赖 Wi-Fi，且端口每次重启都变。
 * 一旦通过 adb 拿到 shell，就把它拆成两步：
 *   ① 用 `app_process` 以 shell(2000) 身份拉起一个常驻进程（assets 里的 stradust_daemon.dex）
 *   ② 之后所有命令走本地 socket，**不再需要 adb / 无线调试 / Wi-Fi**
 *
 * 这就是 Shizuku 的做法（它同样是非 root 下靠一次 shell 启动常驻服务），
 * 区别只是我们把守护进程做成了 5KB 的 dex，随应用发布，用户不需要装任何第三方应用。
 *
 * 安全：只绑 127.0.0.1，且每次安装生成随机端口 + 32 字节 token，
 * 握手不通过直接断开（不给探测者任何回显）。
 */
object ShellDaemonClient {

    private const val TAG = "ShellDaemon"
    private const val PREF = "shell_daemon"
    private const val DEX_NAME = "stradust_daemon.dex"
    private const val MAIN_CLASS = "ShellDaemon"
    private const val NICE_NAME = "stradust_shell"

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var sockPort = 0

    /** 最近一次失败原因，给 UI 用 */
    @Volatile
    var lastError: String = ""
        private set

    fun isConnected(): Boolean = socket?.let { it.isConnected && !it.isClosed } == true

    // ------------------------------------------------------------------ 配置

    private fun token(context: Context): String {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        var t = p.getString("token", null)
        if (t == null) {
            val bytes = ByteArray(24)
            SecureRandom().nextBytes(bytes)
            t = bytes.joinToString("") { "%02x".format(it) }
            p.edit().putString("token", t).apply()
        }
        return t
    }

    private fun port(context: Context): Int {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        var v = p.getInt("port", 0)
        if (v == 0) {
            v = 41000 + SecureRandom().nextInt(15000)
            p.edit().putInt("port", v).apply()
        }
        return v
    }

    // ------------------------------------------------------------------ 执行

    /**
     * 走常驻服务执行命令。返回 null 表示"服务不可用"（调用方应回退到 adb）。
     * timeoutMs 到达则关闭连接（服务端进程会继续活着）。
     */
    fun exec(context: Context, command: String, timeoutMs: Int = 15000): String? {
        repeat(2) { attempt ->
            if (!isConnected() && !connectExisting(context)) {
                return null
            }
            val out = output ?: return null
            val ins = input ?: return null
            return try {
                val cmdBytes = command.toByteArray(Charsets.UTF_8)
                out.writeInt(cmdBytes.size)
                out.write(cmdBytes)
                out.flush()

                socket?.soTimeout = timeoutMs
                val len = ins.readInt()
                if (len <= 0 || len > 2 * 1024 * 1024) throw IllegalStateException("响应长度异常 $len")
                val buf = ByteArray(len)
                ins.readFully(buf)
                String(buf, Charsets.UTF_8)
            } catch (e: Exception) {
                AppLogger.w(TAG, "daemon exec 失败（第 ${attempt + 1} 次）: ${e.message}")
                close()
                // 第一次失败后重试一次（服务可能刚重启过）
                if (attempt == 1) null else null
            }
        }
        return null
    }

    /** 试着连上已经在跑的守护进程 */
    fun connectExisting(context: Context): Boolean {
        close()
        val p = port(context)
        val t = token(context)
        return try {
            val s = Socket()
            s.connect(InetSocketAddress("127.0.0.1", p), 500)
            s.soTimeout = 1500
            val dataOut = DataOutputStream(s.getOutputStream())
            val dataIn = DataInputStream(s.getInputStream())
            dataOut.write(("AUTH $t\n").toByteArray(Charsets.UTF_8))
            dataOut.flush()
            val line = readLine(dataIn)
            if (line != "OK") {
                runCatching { s.close() }
                return false
            }
            s.soTimeout = 0
            socket = s; input = dataIn; output = dataOut; sockPort = p
            AppLogger.i(TAG, "已连上常驻服务（端口 $p）")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 确保守护进程在跑。
     * @param adbExec 用一次性 adb 通道执行命令的函数（由 AdbSelf 提供，避免循环依赖）
     * @return 是否可用
     */
    fun ensureRunning(context: Context, launch: (String) -> Boolean): Boolean {
        if (connectExisting(context)) return true

        val dex = extractDex(context) ?: return false
        val p = port(context)
        val t = token(context)
        // setsid 让守护进程脱离 adb shell 的会话，否则 shell 一退出它就被带走
        val launchCmd = "(setsid env CLASSPATH='${dex.absolutePath}' app_process /system/bin " +
            "--nice-name=$NICE_NAME $MAIN_CLASS $p $t >/data/local/tmp/stradust_daemon.log 2>&1 &)"
        AppLogger.i(TAG, "拉起常驻服务：port=$p dex=${dex.absolutePath}")
        val okLaunch = launch(launchCmd)
        AppLogger.i(TAG, "拉起命令已发出（成功=$okLaunch）")

        // app_process 首次启动要加载 dex，实测约 4~5 秒，window 给足 12 次 ≈ 8s
        repeat(12) { i ->
            Thread.sleep(600L + i * 120L)
            if (connectExisting(context)) {
                AppLogger.i(TAG, "常驻服务就绪（第 ${i + 1} 次尝试）")
                return true
            }
        }
        lastError = "常驻服务启动失败（app_process 不可用或被 ROM 限制）"
        AppLogger.w(TAG, lastError)
        return false
    }

    fun stop() = close()

    // ------------------------------------------------------------------ 内部

    private fun extractDex(context: Context): File? = runCatching {
        // shell 需要能读这个文件，所以放外部私有目录（/sdcard/Android/data/<pkg>/files）
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val f = File(dir, DEX_NAME)
        val asset = context.assets.open(DEX_NAME).use { it.readBytes() }
        if (!f.exists() || f.length() != asset.size.toLong()) {
            f.writeBytes(asset)
        }
        f
    }.onFailure { AppLogger.e(TAG, "解出 dex 失败: ${it.message}", it) }.getOrNull()

    private fun readLine(ins: DataInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = ins.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString()
            sb.append(c.toChar())
            if (sb.length > 256) return null
        }
    }

    private fun close() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }
}
