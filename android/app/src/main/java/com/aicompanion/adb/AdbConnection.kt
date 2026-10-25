/**
 * adb 连接：TCP → CNXN → AUTH → shell 执行。
 *
 * 走的是 adbd 的标准线协议，身份就是 shell(2000)：
 * 应用没有提权，只是"自己当 adb 客户端"，链路比 Shizuku 还短（少了 binder 代理）。
 */
package com.aicompanion.adb

import com.aicompanion.util.AppLogger
import java.io.Closeable
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class AdbShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val completed: Boolean,
) {
    val ok: Boolean get() = completed && exitCode == 0

    fun summary(max: Int = 400): String {
        val body = (stdout.ifBlank { stderr }).trim()
        val cut = if (body.length > max) body.take(max) + "…" else body
        return "exit=$exitCode ${if (completed) "" else "(超时) "}$cut".trim()
    }
}

class AdbConnection(
    private val host: String,
    private val port: Int,
    /**
     * 是否允许「就地升级 TLS」。
     *
     * 无线调试端口的真实流程是（官方 adb 的 `adb tls connect`）：
     *   ① TCP 明文连上 → 发 CNXN
     *   ② adbd 回 A_STLS（"要 TLS"）
     *   ③ **在同一条 socket 上**开始 TLS 1.3 握手（客户端出示以 AdbKey 为密钥的证书）
     *   ④ TLS 建立后，在 TLS 流里重新发 CNXN → AUTH → …
     *
     * 注意不能"先 TLS 再说"：adbd 会把 ClientHello 当成非法 adb 包头直接断开。
     */
    private val allowTlsUpgrade: Boolean = true,
    /** 明文阶段是否先发 A_STLS 再发 CNXN（不同 ROM 的排序可能不同） */
    private val preStlsFirst: Boolean = false,
) : Closeable {

    companion object {
        private const val TAG = "AdbConnection"
    }

    private var socket: Socket? = null
    private var tlsSocket: javax.net.ssl.SSLSocket? = null
    private var input: DataInputStream? = null
    private var output: OutputStream? = null
    private var nextLocalId = 1

    var deviceBanner: String = ""
        private set

    val isConnected: Boolean get() = socket?.isConnected == true && socket?.isClosed == false

    /** 握手：拿不到 CNXN 就算失败（AUTH 会被自动应答） */
    fun connect(key: AdbKey, timeoutMs: Int = 8000): Boolean {
        closeQuietly()
        val s = Socket()
        try {
            s.connect(InetSocketAddress(host, port), timeoutMs)
            s.soTimeout = timeoutMs
            s.tcpNoDelay = true
            socket = s

            var ins = DataInputStream(s.getInputStream())
            input = ins
            output = s.getOutputStream()
            if (preStlsFirst) {
                // 先声明要 TLS，再发 CNXN（另一种排序假设）
                runCatching {
                    AdbProtocol.write(
                        output!!, AdbProtocol.A_STLS, AdbProtocol.A_STLS_VERSION, 0, ByteArray(0)
                    )
                }
            }
            // 无线调试(TLS)端口的正确开场：**只发 A_STLS**，先不发明文 CNXN。
            // 依据 AOSP `adb.cpp` handle_packet(A_STLS) → send_tls_request → 握手。
            if (preStlsFirst) {
                AdbProtocol.write(
                    output!!, AdbProtocol.A_STLS, AdbProtocol.A_STLS_VERSION, 0, ByteArray(0)
                )
            } else {
                AdbProtocol.write(
                    output!!, AdbProtocol.A_CNXN, AdbProtocol.A_VERSION, AdbProtocol.MAX_PAYLOAD,
                    "host::features=cmd,shell_v2\u0000".toByteArray(Charsets.UTF_8)
                )
            }
            var signedOnce = false
            var sentPubkey = false
            // 没 root 的真机靠"设备弹窗允许"来信任密钥；人要时间点，所以一旦走了
            // RSAPUBLICKEY 分支就把等待放宽到 60s（否则用户还没点就超时了）。
            var deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                val m = AdbProtocol.read(ins, deadline)
                when (m.command) {
                    AdbProtocol.A_CNXN -> {
                        deviceBanner = String(m.data, Charsets.UTF_8).trim('\u0000')
                        AppLogger.i(TAG, "已连接：${deviceBanner.take(120)}")
                        s.soTimeout = 0
                        return true
                    }
                    AdbProtocol.A_AUTH -> {
                        // 流程（真机实测出来的）：
                        //  ① adbd 发 token → 先签名（若已信任，这一步就过了）
                        //  ② 不被信任 → 再发 token → 这时递公钥（设备弹窗）
                        //  ③ 用户点「允许」→ adbd **再发一次 token** → 这次必须签名，才能拿到 CNXN
                        // 之前在 ③ 处又发了一次公钥，于是永远等不到 CNXN，直到超时。
                        val needSign = m.arg0 == AdbProtocol.AUTH_TOKEN &&
                            (sentPubkey || !signedOnce)
                        if (needSign) {
                            if (!signedOnce) signedOnce = true
                            AppLogger.i(TAG, "AUTH：签名 token")
                            AdbProtocol.write(
                                output!!, AdbProtocol.A_AUTH, AdbProtocol.AUTH_SIGNATURE, 0,
                                key.signToken(m.data)
                            )
                        } else {
                            // 密钥不被信任：把公钥递过去，设备会弹「允许 USB 调试」。
                            // 这里必须给足等待时间——用户没点之前我们是收不到 CNXN 的。
                            sentPubkey = true
                            AppLogger.w(TAG, "密钥未被信任，已发送公钥；请现在点手机上的「允许」，我会等 3 分钟")
                            AdbProtocol.write(
                                output!!, AdbProtocol.A_AUTH, AdbProtocol.AUTH_RSAPUBLICKEY, 0,
                                (key.androidPubkeyBase64() + " aicompanion@android\u0000")
                                    .toByteArray(Charsets.UTF_8)
                            )
                            // 必须等够人点按钮的时间：adbd 只有在"授权通过且连接完成"后才把密钥
                            // 写进 adb_keys；等太短会导致批准了却没落盘，下次又弹窗（真机实测）。
                            deadline = System.currentTimeMillis() + 180_000
                        }
                    }
                    AdbProtocol.A_STLS -> {
                        if (!allowTlsUpgrade) {
                            AppLogger.w(TAG, "设备要求 TLS，但本次不允许升级")
                            closeQuietly()
                            return false
                        }
                        // 就地升级：同一条 socket 上开始 TLS 1.3，
                        // 然后**在 TLS 流里重新发 CNXN**（adbd 会忘掉明文阶段的状态）。
                        // 官方 adb 收到（或主动发出）A_STLS 后才开始握手；我们回一条同版本 A_STLS，
                        // 告诉服务端"同意升级"。arg0 必须是 0x01000000，不是 1。
                        runCatching {
                            AdbProtocol.write(
                                output!!, AdbProtocol.A_STLS, AdbProtocol.A_STLS_VERSION, 0, ByteArray(0)
                            )
                        }
                        AppLogger.i(TAG, "adbd 要求 TLS，已回 A_STLS，就地升级（TLS 1.3 + 客户端证书）…")
                        val ssl = AdbTls.upgrade(s, key, timeoutMs) ?: run {
                            AppLogger.w(TAG, "TLS 升级失败（$host:$port）")
                            closeQuietly()
                            return false
                        }
                        tlsSocket = ssl
                        s.soTimeout = 0
                        ins = DataInputStream(ssl.inputStream)
                        input = ins
                        output = ssl.outputStream
                        // 依据 AOSP `adb/client/auth.cpp` 与 `adb.cpp`：
                        //   TLS 握手成功后客户端注释是 "Waiting for CNXN packet..."，
                        //   即**不再发 CNXN**；设备端在 TLS 里主动把 CNXN 作为首个包发过来
                        //   （`handle_new_connection`: if (t->use_tls) send_tls_request(t) 之后
                        //    由 TLS 层给出这条 CNXN）。
                        // 之前多发一条 CNXN 会让 adbd 认为要再协商一次 TLS，回一条 A_STLS，
                        // 后续 OPEN 就永远得不到处理 —— 这就是卡了很久的原因。
                        s.soTimeout = 3000
                        val first = runCatching {
                            AdbProtocol.read(ins, System.currentTimeMillis() + 3000)
                        }.getOrNull()
                        s.soTimeout = 0
                        if (first != null && first.command == AdbProtocol.A_CNXN) {
                            deviceBanner = String(first.data, Charsets.UTF_8).trim('\u0000')
                            AppLogger.i(TAG, "TLS 内已连接（未发 CNXN）：${deviceBanner.take(80)}")
                            return true
                        }
                        AppLogger.w(TAG, "TLS 后首包不是 CNXN：${first?.let { cmdName(it.command) }}")
                        deadline = System.currentTimeMillis() + timeoutMs
                        continue
                    }
                    AdbProtocol.A_STLS -> {
                        runCatching {
                            AdbProtocol.write(
                                output!!, AdbProtocol.A_STLS, AdbProtocol.A_STLS_VERSION, 0, ByteArray(0)
                            )
                        }
                        AppLogger.i(TAG, "握手阶段收到 A_STLS，已回确认")
                    }
                    else -> AppLogger.d(TAG, "握手忽略命令 ${Integer.toHexString(m.command)}")
                }
            }
        } catch (e: Exception) {
            val hint = when {
                e is java.net.SocketTimeoutException ->
                    "超时：端口没开或没在设备上点「允许」"
                e is java.net.ConnectException ->
                    "连不上 $host:$port：先执行 adb tcpip 5555"
                else -> e.message ?: e.javaClass.simpleName
            }
            AppLogger.e(TAG, "连接 $host:$port 失败: $hint")
            closeQuietly()
            return false
        }
    }

    /**
     * 执行一条 shell 命令（shell v2，带 stderr 与退出码）。
     * 注意：这是"把任意命令交给 shell"，调用方必须自己做白名单。
     */
    fun exec(command: String, timeoutMs: Int = 15000, pty: Boolean = false): AdbShellResult {
        val s = socket ?: return AdbShellResult(-1, "", "未连接", false)
        val out = output ?: return AdbShellResult(-1, "", "未连接", false)
        val ins = input ?: return AdbShellResult(-1, "", "未连接", false)
        val localId = nextLocalId++
        var remoteId = 0
        val raw = StringBuilder()

        // 用 legacy shell 服务：负载就是纯 stdout，没有 v2 的分帧/流 id，
        // 退出码用 echo 标记带回（v2 的分帧在不同 adbd 版本上表现不一致，这里不赌）。
        val wrapped = "({ $command ; } 2>&1; echo \"__ADB_EXIT__$?\")"
        // PTY 与非 PTY 的区别很关键：adbd 的非 PTY shell 里 app_process 起不来
        // （实测日志都来不及写就没了），要拉起常驻进程必须走 shell,pty:
        val svcName = (if (pty) "shell,pty:" else "shell:") + wrapped
        val tlsMode = tlsSocket != null
        var timedOut = false
        var gotAny = false
        // TLS 流不能靠 soTimeout 轮询（半条记录被超时打断会把 TLS 状态弄坏），
        // 改成"看门狗到点直接关 socket"，读操作随之抛异常退出。
        val watchdog = if (tlsMode) java.util.Timer(true) else null
        watchdog?.schedule(object : java.util.TimerTask() {
            override fun run() {
                timedOut = true
                runCatching { socket?.close() }
            }
        }, timeoutMs.toLong())
        // OPEN 可能落在服务端"还没 online"的窗口里被静默丢弃（AOSP: if (!t->online) break），
        // 所以没收到任何回应时重发一次。
        val resend = if (useTlsFlag()) java.util.Timer(true) else null
        resend?.schedule(object : java.util.TimerTask() {
            override fun run() {
                if (!gotAny) {
                    AppLogger.i(TAG, "OPEN 无响应，重发一次")
                    runCatching {
                        AdbProtocol.write(
                            out, AdbProtocol.A_OPEN, localId, 0,
                            (svcName + "\u0000").toByteArray(Charsets.UTF_8)
                        )
                    }
                }
            }
        }, 1200)
        try {
            AdbProtocol.write(
                out, AdbProtocol.A_OPEN, localId, 0,
                (svcName + "\u0000").toByteArray(Charsets.UTF_8)
            )
            AppLogger.i(TAG, "OPEN local=$localId tls=$tlsMode cmd=${command.take(40)}")
            s.soTimeout = if (tlsMode) 0 else 200
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                if (!tlsMode && System.currentTimeMillis() > deadline) break
                val m = try {
                    AdbProtocol.read(ins, deadline).also { gotAny = true }
                } catch (_: SocketTimeoutException) {
                    if (tlsMode) continue     // TLS 下 read 不会因为"暂时没数据"抛，这里兜底
                    break                    // 明文通道：到 deadline 了，不再空转
                }
                AppLogger.d(TAG, "MSG ${cmdName(m.command)} arg0=${m.arg0} arg1=${m.arg1} len=${m.data.size}")
                if (m.command == AdbProtocol.A_CNXN) {
                    deviceBanner = String(m.data, Charsets.UTF_8).trim('\u0000')
                    AppLogger.i(TAG, "exec 中收到设备横幅：${deviceBanner.take(60)}")
                    continue
                }
                if (m.command == AdbProtocol.A_STLS) {
                    // 这条是服务端在 TLS 建立后的 STLS 确认，**不能回**：
                    // 回了会被它当成协议错误直接断连（实测）。
                    AppLogger.i(TAG, "exec 中收到 A_STLS 确认，忽略")
                    continue
                }
                // 只认本流：arg1 是我们给的 local id
                if (m.arg1 != localId) {
                    if (m.command == AdbProtocol.A_WRTE) {
                        runCatching { AdbProtocol.write(out, AdbProtocol.A_OKAY, m.arg0, m.arg1, ByteArray(0)) }
                    }
                    continue
                }
                when (m.command) {
                    AdbProtocol.A_OKAY -> if (m.arg0 != 0) remoteId = m.arg0
                    AdbProtocol.A_WRTE -> {
                        if (m.arg0 != 0) remoteId = m.arg0
                        if (m.data.isNotEmpty()) raw.append(String(m.data, Charsets.UTF_8))
                        runCatching { AdbProtocol.write(out, AdbProtocol.A_OKAY, localId, remoteId, ByteArray(0)) }
                    }
                    AdbProtocol.A_CLSE -> {
                        return parseResult(raw.toString())
                    }
                }
            }
            AppLogger.w(TAG, "命令超时：${command.take(60)}")
            return parseResult(raw.toString()).let {
                AdbShellResult(it.exitCode, it.stdout, it.stderr, false)
            }
        } catch (e: Exception) {
            if (timedOut) {
                AppLogger.w(TAG, "命令超时（TLS 看门狗关闭连接）：${command.take(60)}")
                return AdbShellResult(-1, raw.toString(), "超时", false)
            }
            AppLogger.e(TAG, "执行失败: ${e.message}")
            return AdbShellResult(-1, raw.toString(), e.message ?: "执行失败", false)
        } finally {
            resend?.cancel()
            watchdog?.cancel()
            runCatching { s.soTimeout = 0 }
        }
    }

    /** 拆出 echo 标记里的退出码 */
    private fun parseResult(text: String): AdbShellResult {
        val marker = "__ADB_EXIT__"
        val idx = text.lastIndexOf(marker)
        if (idx < 0) return AdbShellResult(-1, text, "", true)
        val code = text.substring(idx + marker.length).trim().takeWhile { it.isDigit() }.toIntOrNull() ?: -1
        val body = text.substring(0, idx)
        return AdbShellResult(code, body, "", true)
    }

    private fun be32(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    private fun cmdName(c: Int): String = when (c) {
        AdbProtocol.A_CNXN -> "CNXN"
        AdbProtocol.A_AUTH -> "AUTH"
        AdbProtocol.A_OPEN -> "OPEN"
        AdbProtocol.A_OKAY -> "OKAY"
        AdbProtocol.A_CLSE -> "CLSE"
        AdbProtocol.A_WRTE -> "WRTE"
        else -> Integer.toHexString(c)
    }

    override fun close() = closeQuietly()

    private fun useTlsFlag(): Boolean = tlsSocket != null

    private fun closeQuietly() {
        runCatching { tlsSocket?.close() }
        tlsSocket = null
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }
}
