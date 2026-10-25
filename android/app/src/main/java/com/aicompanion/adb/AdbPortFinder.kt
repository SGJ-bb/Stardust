/**
 * 无线调试「连接端口」自动发现。
 *
 * 为什么需要它：adbd 的 TLS 端口是 `bind(0)` 随机分配的，**重启后必变**；
 * 而官方发现方式是 mDNS（`_adb-tls-connect._tcp`），实测小米/华为等 ROM 上
 * 经常三轮都拿不到广播，应用进程又读不到 `/proc/net/tcp`（SELinux 拒绝）。
 *
 * 所以这里用「缓存 → 邻近试探 → 全量扫」三级策略，目标是用户零操作：
 *  1. 缓存上次成功的端口（同一 adbd 实例内端口不变，大多数情况一次命中）；
 *  2. 邻近试探：端口是顺序分配的，重启后通常落在旧值附近；
 *  3. 全量扫回环端口 → 对每个开着的端口做 TLS 握手探测，
 *     能被我们握手成功的就是 adbd 的 TLS 端口（别的服务不会完成 TLS 1.3 握手）。
 *
 * 扫描只做 TCP connect + TLS ClientHello，不发送任何业务数据，对其它服务无副作用。
 */
package com.aicompanion.adb

import android.content.Context
import com.aicompanion.util.AppLogger
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

object AdbPortFinder {

    private const val TAG = "AdbPortFinder"
    private const val PREF = "adb_tls_conn"
    private const val KEY_PORT = "last_port"

    /** Android 的临时端口范围（net.ipv4.ip_local_port_range 的常见值） */
    private val SCAN_RANGE = 32768..60999

    fun lastPort(context: Context): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_PORT, 0)

    fun savePort(context: Context, port: Int) {
        // 明文 5555 是兜底通道，不稳定，不写缓存（否则下次会优先走它）
        if (port > 0 && port != 5555) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putInt(KEY_PORT, port).apply()
        }
    }

    fun forget(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_PORT).apply()
    }

    /**
     * 找出可用的 adb-TLS 端口。
     * @param onProgress 进度回调（UI 线程安全由调用方负责，内部在后台线程执行）
     */
    fun find(context: Context, key: AdbKey, onProgress: ((String) -> Unit)? = null): Int? {
        // 1. 缓存
        val cached = lastPort(context).takeIf { it != 5555 } ?: 0
        if (cached > 0) {
            onProgress?.invoke("试上次的端口 $cached…")
            if (probeAdb(cached, key)) {
                AppLogger.i(TAG, "缓存端口命中：$cached")
                return cached
            }
            AppLogger.i(TAG, "缓存端口 $cached 已失效，开始重新找")
        }

        // 2. 邻近试探（端口顺序分配，重启后一般不会跑太远）
        if (cached > 0) {
            onProgress?.invoke("在旧端口附近试探…")
            val near = ArrayList<Int>()
            for (d in 1..240) {
                near.add(cached + d)
                near.add(cached - d)
            }
            val hit = findIn(near.filter { it in SCAN_RANGE }, key, 48)
            if (hit > 0) {
                AppLogger.i(TAG, "邻近端口命中：$hit")
                return hit
            }
        }

        // 3. 全量扫：先快速找出所有开着的回环端口，再逐个探测
        onProgress?.invoke("扫描本机端口（约 1 万次探测）…")
        val open = scanOpenPorts()
        AppLogger.i(TAG, "回环开着的端口：$open")
        for (p in open) {
            if (probeAdb(p, key)) {
                AppLogger.i(TAG, "TLS 探测命中：$p")
                return p
            }
        }
        // 4. 兜底：明文口 5555（adb tcpip 设的，不在临时端口段里；需密钥已被信任）
        onProgress?.invoke("试明文口 5555…")
        if (probeAdb(5555, key, connectTimeoutMs = 300, firstMsgTimeoutMs = 1200)) {
            AppLogger.i(TAG, "明文 5555 可用")
            return 5555
        }
        return null
    }

    /** 批量探测：多线程，命中即返回 */
    private fun findIn(ports: List<Int>, key: AdbKey, threads: Int): Int {
        if (ports.isEmpty()) return 0
        val queue = ConcurrentLinkedQueue(ports)
        val found = AtomicInteger(0)
        val workers = (1..threads).map {
            Thread {
                while (true) {
                    if (found.get() > 0) return@Thread
                    val p = queue.poll() ?: return@Thread
                    if (probeAdb(p, key, connectTimeoutMs = 120, firstMsgTimeoutMs = 900)) {
                        found.compareAndSet(0, p)
                        return@Thread
                    }
                }
            }
        }
        workers.forEach { it.isDaemon = true; it.start() }
        workers.forEach { runCatching { it.join() } }
        return found.get()
    }

    /** 扫回环上"有服务在听"的端口（不区分服务类型） */
    private fun scanOpenPorts(): List<Int> {
        val ports = SCAN_RANGE
        val queue = ConcurrentLinkedQueue(ports.toList())
        val open = ConcurrentLinkedQueue<Int>()
        val workers = (1..64).map {
            Thread {
                while (true) {
                    val p = queue.poll() ?: return@Thread
                    if (isOpen(p)) open.add(p)
                }
            }
        }
        workers.forEach { it.isDaemon = true; it.start() }
        workers.forEach { runCatching { it.join() } }
        return open.sorted()
    }

    private fun isOpen(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 90) }
        true
    } catch (_: Exception) {
        false
    }

    /**
     * 判断某端口是不是 adb 端口：发一个明文 CNXN，看它回什么。
     *
     * 不能"先 TLS 再说"——无线调试口是先明文再升级（A_STLS），
     * 直接发 ClientHello 会被 adbd 当成非法包头断开（实测 handshake_failure）。
     * 回 A_STLS 或 CNXN 都算 adb 端口；回别的东西/超时就不是。
     */
    fun probeAdb(
        port: Int,
        key: AdbKey,
        connectTimeoutMs: Int = 200,
        firstMsgTimeoutMs: Int = 1200,
    ): Boolean {
        val s = Socket()
        return try {
            s.connect(InetSocketAddress("127.0.0.1", port), connectTimeoutMs)
            s.soTimeout = firstMsgTimeoutMs
            AdbProtocol.write(
                s.getOutputStream(), AdbProtocol.A_CNXN, AdbProtocol.A_VERSION,
                AdbProtocol.MAX_PAYLOAD, "host::probe\u0000".toByteArray(Charsets.UTF_8),
            )
            val m = AdbProtocol.read(
                java.io.DataInputStream(s.getInputStream()),
                System.currentTimeMillis() + firstMsgTimeoutMs,
            )
            // A_STLS=无线调试口；CNXN=免认证的明文口；A_AUTH=需要签名的明文口(5555)
            m.command == AdbProtocol.A_STLS || m.command == AdbProtocol.A_CNXN ||
                m.command == AdbProtocol.A_AUTH
        } catch (_: Exception) {
            false
        } finally {
            runCatching { s.close() }
        }
    }
}
