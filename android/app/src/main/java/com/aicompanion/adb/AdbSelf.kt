/**
 * 自建 ADB 通道门面：状态、连接复用、能力白名单、审计日志。
 *
 * 设计原则：
 *  · 只对外暴露 **固定动作**（settings/pm/input/screencap），不把"任意 shell"交给上层；
 *  · 每次执行都留审计记录（时间/命令/耗时/退出码/来源），出问题能回溯；
 *  · 失败不抛异常给 UI，返回结构化结果 + 一句人话原因。
 */
package com.aicompanion.adb

import android.content.Context
import com.aicompanion.util.AppLogger
import java.io.File

data class AdbSelfState(
    val connected: Boolean = false,
    val uid: String = "",
    val banner: String = "",
    val error: String = "",
)

object AdbSelf {

    private const val TAG = "AdbSelf"

    @Volatile
    var state: AdbSelfState = AdbSelfState()
        private set

    private var conn: AdbConnection? = null
    private val lock = Any()

    /** 审计：最近 50 条（只在内存，重启即清） */
    private val audit = ArrayDeque<String>()

    fun auditLog(): List<String> = synchronized(audit) { audit.toList() }

    fun keyLine(context: Context): String = AdbKey.get(context).adbKeyLine()

    fun keyFingerprint(context: Context): String = AdbKey.get(context).fingerprint()

    /** 把公钥写到应用外部目录，方便用 adb 取走 */
    fun exportPubkey(context: Context): File? = runCatching {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "")
        val f = File(dir, "adb_pubkey.txt")
        f.writeText(keyLine(context) + "\n")
        f
    }.onFailure { AppLogger.e(TAG, "导出公钥失败: ${it.message}", it) }.getOrNull()

    fun connect(context: Context, host: String = "127.0.0.1", port: Int = 5555): AdbSelfState {
        synchronized(lock) {
            // 首次授权时：adbd 在"批准 + 连接完成"后才落盘密钥，
            // 某些 ROM 批准后需要新连接才给 CNXN —— 这里最多试两次。
            repeat(2) { attempt ->
                conn?.close()
                conn = null
                if (attempt > 0) {
                    AppLogger.i(TAG, "第 ${attempt + 1} 次尝试连接（授权后重连）")
                    try { Thread.sleep(1500) } catch (_: Exception) {}
                }
                val st = tryConnect(context, host, port)
                if (st.connected) return st
            }
            return state
        }
    }

    private fun tryConnect(context: Context, host: String, port: Int): AdbSelfState {
        run {
            val c = AdbConnection(host, port)
            val ok = c.connect(AdbKey.get(context))
            if (!ok) {
                state = AdbSelfState(false, "", "", "连不上 $host:$port（端口已变 / 无线调试已关 / 密钥未被信任）")
                return state
            }
            val id = c.exec("id", 6000)
            // 只保留可打印 ASCII：协议噪声/控制字符不往 UI 上带
            val uid = id.stdout
                .lineSequence()
                .map { line -> line.filter { it.code in 32..126 }.trim() }
                .firstOrNull { it.contains("uid=") }
                ?.substringBefore(" ")
                ?: id.stdout.trim().take(40)
            conn = c
            state = AdbSelfState(true, uid, c.deviceBanner, "")
            record("connect $host:$port → $uid")
            return state
        }
    }

    fun disconnect() {
        synchronized(lock) {
            conn?.close()
            conn = null
            state = AdbSelfState()
        }
    }

    /**
     * 无线调试（TLS）连接：端口已知时用这个。
     * 与明文 5555 的区别只有一个——外面多了 TLS 1.3 + 客户端证书，
     * 证书用本应用的 AdbKey 现造（配对时交给系统的就是这把钥匙）。
     */
    fun connectTls(context: Context, port: Int): AdbSelfState = synchronized(lock) {
        conn?.close()
        conn = null
        val st = tryConnect(context, "127.0.0.1", port)
        if (st.connected) AdbPortFinder.savePort(context, port)
        st
    }

    /**
     * 零操作连接：自动找端口 → TLS 连接 → 缓存。
     *
     * 这是给用户的主入口：配对过之后，只点一次（或由能力调用自动触发），
     * 之后端口变了也会自己重新找。
     */
    fun autoConnect(context: Context, onProgress: ((String) -> Unit)? = null): AdbSelfState {
        synchronized(lock) {
            if (conn?.isConnected == true) return state
        }
        val key = AdbKey.get(context)
        val port = AdbPortFinder.find(context, key, onProgress)
        if (port == null) {
            val st = AdbSelfState(
                false, "", "",
                "没找到 adb 端口：请确认「无线调试」开着（Android 11+）或已跑过 adb tcpip",
            )
            synchronized(lock) { state = st }
            onProgress?.invoke(st.error)
            return st
        }
        onProgress?.invoke("找到端口 $port，正在建立 TLS 通道…")
        val st = connectTls(context, port)
        if (!st.connected) AdbPortFinder.forget(context)
        onProgress?.invoke(if (st.connected) "已连接 · ${st.uid}" else st.error)
        return st
    }

    /** 通道就绪：常驻服务在跑（不依赖 adb/Wi-Fi），或 adb 连接还活着 */
    fun isReady(): Boolean =
        com.aicompanion.shell.ShellDaemonClient.isConnected() ||
            synchronized(lock) { conn?.isConnected == true }

    /**
     * 底层执行：优先走常驻服务（不依赖 adb/Wi-Fi），不可用时回退到 adb 通道。
     *
     * 为什么这么排：adbd 通道会随 Wi-Fi 开关消失、端口每次重启都变，
     * 而常驻服务一旦起来就一直活到下次重启，日常操作再也不碰网络。
     */
    private fun run(context: Context, command: String, timeoutMs: Int = 12000): AdbShellResult {
        // 1) 常驻服务
        val viaDaemon = com.aicompanion.shell.ShellDaemonClient.exec(context, command, timeoutMs)
        if (viaDaemon != null) {
            record("[$command] (daemon) ${viaDaemon.take(80)}")
            return parseDaemonResponse(viaDaemon)
        }
        // 2) 尝试拉起常驻服务（需要一次 adb shell）
        if (com.aicompanion.shell.ShellDaemonClient.ensureRunning(context) { cmd ->
                adbRun(context, cmd, 8000, pty = true).ok
            }) {
            val again = com.aicompanion.shell.ShellDaemonClient.exec(context, command, timeoutMs)
            if (again != null) {
                record("[$command] (daemon/new) ${again.take(80)}")
                return parseDaemonResponse(again)
            }
        }
        // 3) 回退 adb 通道
        return adbRun(context, command, timeoutMs)
    }

    /** 把守护进程的 "EXIT <code>\n<输出>" 解析成统一结果 */
    private fun parseDaemonResponse(resp: String): AdbShellResult {
        val firstLine = resp.lineSequence().firstOrNull() ?: ""
        val code = firstLine.removePrefix("EXIT ").trim().toIntOrNull() ?: -1
        val body = resp.substringAfter('\n', "")
        return AdbShellResult(code, body, "", true)
    }

    /** 纯 adb 通道执行（不经过常驻服务，避免递归） */
    private fun adbRun(context: Context, command: String, timeoutMs: Int = 12000, pty: Boolean = false): AdbShellResult {
        val c = synchronized(lock) { conn }
        if (c == null || !c.isConnected) {
            // 没有连接就先自动建（无线调试 TLS 通道，端口自己找），
            // 这样上层调用方完全不用关心"通道是怎么起来的"。
            val st = autoConnect(context) { AppLogger.i(TAG, "autoConnect: $it") }
            if (!st.connected) return AdbShellResult(-1, "", st.error, false)
        }
        val started = System.currentTimeMillis()
        val r = synchronized(lock) { conn?.exec(command, timeoutMs, pty) }
            ?: return AdbShellResult(-1, "", "未连接", false)
        record("[$command] exit=${r.exitCode} ${System.currentTimeMillis() - started}ms")
        return r
    }

    private fun record(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        synchronized(audit) {
            audit.addLast("$ts  $line")
            while (audit.size > 50) audit.removeFirst()
        }
    }

    /** 底层执行入口：给 ShellExecutor 的 adb 通道用（调用方负责白名单） */
    fun execRaw(context: Context, command: String, timeoutMs: Int = 15000): AdbShellResult =
        run(context, command, timeoutMs)

    /**
     * 能力自检：把能力层每条路径都真跑一遍，返回可读报告。
     * 用于真机验收——比"编译过了"有意义得多。
     */
    fun selfTest(context: Context, port: Int = 5555): List<String> {
        val out = ArrayList<String>()
        val st = connect(context, "127.0.0.1", port)
        out.add("connect: connected=${st.connected} uid=${st.uid.ifBlank { "-" }} ${st.error}")
        if (!st.connected) return out

        val probe = "stra_adb_probe"
        settingsPut(context, "global", probe, "42").let { out.add("settings put: exit=${it.exitCode}") }
        settingsGet(context, "global", probe).let { out.add("settings get: exit=${it.exitCode} value=${it.stdout.trim()}") }
        run(context, "settings delete global $probe").let { out.add("settings delete: exit=${it.exitCode}") }

        val shot = screenshot(context, "adb_selftest.png")
        val okPng = shot != null && shot.length() > 8
        out.add("screencap: ${if (okPng) "ok ${shot!!.length()} bytes" else "失败"}")

        inputTap(context, 5, 5).let { out.add("input tap: exit=${it.exitCode}") }
        return out
    }

    // ------------------------------------------------------------ 能力层（白名单）

    /** 改系统设置：等价 Shizuku 的 settings put（需 WRITE_SECURE_SETTINGS 或 shell 身份，这里走 shell） */
    fun settingsPut(context: Context, namespace: String, key: String, value: String): AdbShellResult {
        if (namespace !in setOf("system", "secure", "global")) return AdbShellResult(-1, "", "非法 namespace", false)
        return run(context, "settings put $namespace ${escape(key)} ${escape(value)}")
    }

    fun settingsGet(context: Context, namespace: String, key: String): AdbShellResult =
        run(context, "settings get $namespace ${escape(key)}")

    fun pmGrant(context: Context, pkg: String, permission: String): AdbShellResult =
        run(context, "pm grant ${escape(pkg)} ${escape(permission)}")

    fun forceStop(context: Context, pkg: String): AdbShellResult =
        run(context, "am force-stop ${escape(pkg)}")

    fun inputTap(context: Context, x: Int, y: Int): AdbShellResult =
        run(context, "input tap $x $y")

    fun inputText(context: Context, text: String): AdbShellResult =
        run(context, "input text ${escape(text.replace(" ", "%s"))}")

    /**
     * 截图：直接让 shell 写到 /data/local/tmp（Android 11+ 应用外部目录对 shell 有 FUSE 限制），
     * 再用 base64 把字节传回来 —— legacy shell 会把二进制当字符串，必须走 base64。
     */
    fun screenshot(context: Context, name: String = "adb_shot.png"): File? {
        val tmp = "/data/local/tmp/adb_shot_${System.currentTimeMillis()}.png"
        val r = run(context, "screencap -p $tmp && base64 $tmp")
        run(context, "rm -f $tmp")
        if (!r.ok || r.stdout.isBlank()) return null
        val b64 = r.stdout.filter { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        val bytes = runCatching { android.util.Base64.decode(b64, android.util.Base64.DEFAULT) }.getOrNull()
            ?: return null
        if (bytes.size < 8) return null
        val isPng = bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
        if (!isPng) return null
        val out = File(context.getExternalFilesDir(null) ?: context.filesDir, name)
        runCatching { out.writeBytes(bytes) }
        return out
    }

    /** 单引号包裹，避免空格/特殊字符被 shell 拆开 */
    private fun escape(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
