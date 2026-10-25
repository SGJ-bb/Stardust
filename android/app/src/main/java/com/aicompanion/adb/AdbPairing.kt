/**
 * 免电脑配对（Android 11+ 无线调试的 6 位配对码）。
 *
 * ## 协议（照 AOSP `pairing_connection.cpp` / `tls_connection.cpp` / `aes_128_gcm.cpp` 对齐）
 *
 * ```
 * 1. TCP 连配对端口（mDNS 的 _adb-tls-pairing._tcp）
 * 2. TLS 1.3 握手：双方各自自签证书，并【接受任意对端证书】（证书只用于加密，不做认证）
 * 3. 从 TLS 导出 64 字节：ExportKeyingMaterial(label="adb-label\0", len=64)
 * 4. 口令 = 6 位配对码字节 + 上面 64 字节（追加）        ← 防止连接被劫持
 * 5. SPAKE2（SPAKE25519，client 角色）交换 32 字节消息，得到 64 字节 key
 * 6. AES-128-GCM 加密交换 PeerInfo（8192 字节定长，[0]=1 表示 RSA 公钥，之后是 adb 公钥 base64）
 * ```
 *
 * 几个"差一字节就静默失败"的点，全部来自源码：
 * - 导出密钥的 label 用 `sizeof()` 取长度，**包含结尾 \0**，即 10 字节 `adb-label\0`；
 * - AES key = HKDF-SHA256(ikm=SPAKE key, salt=全零32字节, info="adb pairing_auth aes-128-gcm key", L=16)；
 * - nonce = 8 字节小端计数器 + 4 个 0；GCM tag 16 字节，无 AAD；
 * - 包头 packed 6 字节：version(u8)=1、type(u8)、payload(u32 网络序)；type：SPAKE2_MSG=0、PEER_INFO=1。
 *
 * ## 为什么用 BouncyCastle
 *
 * 标准 JSSE 取不到 RFC5705 导出密钥（`SSL_export_keying_material`）。
 * BouncyCastle 的 JSSE provider 暴露了 `ProvSSLConnection.exportKeyingMaterial(String, byte[], int)`，
 * 所以这里用 BC 的 SSLContext 建连接，再反射拿到该方法。
 */
package com.aicompanion.adb

import android.content.Context
import com.aicompanion.util.AppLogger
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.tls.CertificateRequest
import org.bouncycastle.tls.DefaultTlsClient
import org.bouncycastle.tls.ProtocolVersion
import org.bouncycastle.tls.TlsAuthentication
import org.bouncycastle.tls.TlsClientProtocol
import org.bouncycastle.tls.TlsContext
import org.bouncycastle.tls.TlsCredentials
import org.bouncycastle.tls.TlsServerCertificate
import org.bouncycastle.tls.crypto.TlsCryptoParameters
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto

object AdbPairing {

    private const val TAG = "AdbPairing"

    /** AOSP `kMaxPeerInfoSize` */
    private const val PEER_INFO_SIZE = 8192

    /** PeerInfo.type：ADB_RSA_PUB_KEY */
    private const val PEER_TYPE_RSA_PUB = 1.toByte()

    /** AOSP: static constexpr char kExportedKeyLabel[] = "adb-label"; —— sizeof() 含结尾 \0 */
    private const val EXPORT_LABEL = "adb-label\u0000"

    private const val HKDF_INFO = "adb pairing_auth aes-128-gcm key"

    private const val TLS_VERSION = "TLSv1.3"
    private const val BC_PROVIDER = "BC"
    private const val BC_JSSE_PROVIDER = "BCJSSE"

    data class Result(val ok: Boolean, val message: String, val log: List<String>)

    fun pair(context: Context, host: String, port: Int, code: String): Result {
        val log = ArrayList<String>()
        fun step(s: String) {
            log.add(s)
            AppLogger.i(TAG, s)
        }
        var sock: java.net.Socket? = null
        var protocol: ExposedTlsClientProtocol? = null
        return try {
            var realPort = port
            if (realPort <= 0) {
                step("端口未指定，先读配对对话框上的端口…")
                val sniffed = sniffPortFromScreen()
                if (sniffed != null) {
                    realPort = sniffed
                    step("从屏幕读到配对端口 $realPort")
                } else {
                    step("屏幕没读到端口，再试 mDNS _adb-tls-pairing._tcp …")
                    val found = discoverPairingPort(context, 6000)
                    if (found == null) {
                        return Result(
                            false,
                            "没找到配对端口：请确认「使用配对码配对设备」对话框还开着，或手动填「配对码:端口」",
                            log,
                        )
                    }
                    realPort = found
                    step("mDNS 发现配对端口 $realPort")
                }
            }
            step("开始配对 → $host:$realPort（码 ${code.length} 位）")
            ensureProviders()

            val (cert, keyStore) = loadOrCreateCert(context)
            step("客户端证书就绪：${cert.subjectX500Principal.name}")

            // ---------- 1. TCP + TLS 1.3（BC 底层协议：JSSE 层禁止握手后取导出密钥） ----------
            val sock = java.net.Socket()
            sock.connect(InetSocketAddress(host, realPort), 6000)
            sock.soTimeout = 10_000

            val crypto = org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto(SecureRandom())
            val keyDer = android.util.Base64.decode(CLIENT_KEY_B64, android.util.Base64.DEFAULT)
            val certDer = android.util.Base64.decode(CLIENT_CERT_B64, android.util.Base64.DEFAULT)
            val privateKeyParam = org.bouncycastle.crypto.util.PrivateKeyFactory.createKey(keyDer)
            val tlsCert = crypto.createCertificate(certDer)

            // ⚠️ TLS 1.3 的证书链必须带（可为空的）certificate_request_context：
            // `Certificate(TlsCertificate[])` 会把 context 置 null，BC 在
            // `encode()` 里断言 `(context != null) == isTLSv13`，TLS1.3 下直接
            // 抛 IllegalStateException（表现为 internal_error(80)），握手当场断。
            // 握手期 client 的 context 就是空的 opaque8，所以传 ByteArray(0)。
            val tlsChain = org.bouncycastle.tls.Certificate(
                ByteArray(0),
                arrayOf(org.bouncycastle.tls.CertificateEntry(tlsCert, null)),
            )

            val protocol = ExposedTlsClientProtocol(sock.getInputStream(), sock.getOutputStream())
            val client = AdbTlsClient(
                crypto,
                privateKeyParam,
                tlsChain,
            )
            protocol.connect(client)
            step("TLS 完成：TLS 1.3（BC 底层协议）")

            // ---------- 2. 导出密钥 ----------
            //
            // ⚠️ 必须用回调里抓到的值。BC 在 `completeHandshake()` 结束后会**销毁**
            // exporter master secret，此时再调 `context.exportKeyingMaterial()` 只会抛
            // IllegalStateException("Export of key material only available from
            // notifyHandshakeComplete()")。所以 EKM 只能在下方的
            // `AdbTlsClient.notifyHandshakeComplete()` 里取。
            val ekm = client.exportedKeyMaterial
            if (ekm == null || ekm.isEmpty()) {
                return Result(false, "取不到导出密钥（notifyHandshakeComplete 未触发或导出失败）", log)
            }
            step("EKM(64) 前 8B = ${hex(ekm.copyOf(8))}")

            // ---------- 3. 口令 = 配对码 + EKM ----------
            val password = code.toByteArray(Charsets.UTF_8) + ekm
            step("口令长度 ${password.size}（${code.length} + 64）")

            val input = DataInputStream(protocol.inputStream)
            val output = protocol.outputStream

            // ---------- 4. SPAKE2 ----------
            val spake = Spake25519.client()
            val ourMsg = spake.generateMsg(password)
            step("SPAKE2 我方消息 ${ourMsg.size}B")
            writePacket(output, TYPE_SPAKE2, ourMsg)

            val (type1, msg1) = readPacket(input)
            if (type1 != TYPE_SPAKE2) return Result(false, "期望 SPAKE2 包，收到 type=$type1", log)
            step("SPAKE2 对方消息 ${msg1.size}B")
            val spakeKey = spake.processMsg(msg1)
            step("SPAKE2 共享密钥 ${spakeKey.size}B，前 8B = ${hex(spakeKey.copyOf(8))}")

            // ---------- 5. AES-128-GCM 交换 PeerInfo ----------
            val aesKey = hkdfSha256(spakeKey, ByteArray(32), HKDF_INFO.toByteArray(), 16)
            step("AES key = ${hex(aesKey)}")

            val pubkey = AdbKey.get(context).androidPubkeyBase64()
            val peerInfo = ByteArray(PEER_INFO_SIZE)
            peerInfo[0] = PEER_TYPE_RSA_PUB
            val pk = pubkey.toByteArray(Charsets.UTF_8)
            System.arraycopy(pk, 0, peerInfo, 1, minOf(pk.size, PEER_INFO_SIZE - 1))
            step("PeerInfo ${PEER_INFO_SIZE}B，公钥 ${pk.size}B")

            val enc = Aes128Gcm(aesKey)
            val cipherText = enc.seal(peerInfo)
            step("PeerInfo 密文 ${cipherText.size}B（明文+16 tag）")
            writePacket(output, TYPE_PEER_INFO, cipherText)

            val (type2, msg2) = readPacket(input)
            if (type2 != TYPE_PEER_INFO) return Result(false, "期望 PEER_INFO 包，收到 type=$type2", log)
            val plain = enc.open(msg2)
            if (plain == null || plain.isEmpty()) {
                return Result(false, "对方 PeerInfo 解密失败（${msg2.size}B）", log)
            }
            step("收到对方 PeerInfo ${plain.size}B，type=${plain[0]}")

            // 对方公钥（可选记录，便于排查）
            val peerKey = plain.copyOfRange(1, plain.size)
                .takeWhile { it != 0.toByte() }
                .toByteArray()
                .toString(Charsets.UTF_8)
            step("对方公钥（前 32 字符）：${peerKey.take(32)}…")

            Result(true, "配对成功", log)
        } catch (e: Exception) {
            log.add("异常：${e.javaClass.simpleName}: ${e.message}")
            AppLogger.e(TAG, "配对失败: ${e.message}", e)
            Result(false, "${e.javaClass.simpleName}: ${e.message}", log)
        } finally {
            runCatching { protocol?.close() }
            runCatching { sock?.close() }
        }
    }

    // ------------------------------------------------------------------ 包读写

    private const val TYPE_SPAKE2 = 0
    private const val TYPE_PEER_INFO = 1

    private fun writePacket(out: OutputStream, type: Int, payload: ByteArray) {
        val header = ByteArray(6)
        header[0] = 1                       // version
        header[1] = type.toByte()
        header[2] = ((payload.size ushr 24) and 0xFF).toByte()
        header[3] = ((payload.size ushr 16) and 0xFF).toByte()
        header[4] = ((payload.size ushr 8) and 0xFF).toByte()
        header[5] = (payload.size and 0xFF).toByte()
        out.write(header)
        out.write(payload)
        out.flush()
    }

    private fun readPacket(input: DataInputStream): Pair<Int, ByteArray> {
        val header = ByteArray(6)
        input.readFully(header)
        val version = header[0].toInt() and 0xFF
        val type = header[1].toInt() and 0xFF
        val len = ((header[2].toInt() and 0xFF) shl 24) or
            ((header[3].toInt() and 0xFF) shl 16) or
            ((header[4].toInt() and 0xFF) shl 8) or
            (header[5].toInt() and 0xFF)
        require(version == 1) { "包头版本不支持：$version" }
        require(len in 0..(PEER_INFO_SIZE * 2)) { "payload 长度异常：$len" }
        val payload = ByteArray(len)
        if (len > 0) input.readFully(payload)
        return type to payload
    }

    // ------------------------------------------------------------------ AES-128-GCM

    /** 照 `aes_128_gcm.cpp`：nonce = 8 字节小端计数器 + 4 个 0，tag 16 字节，无 AAD */
    private class Aes128Gcm(key: ByteArray) {
        private val keySpec = SecretKeySpec(key, "AES")
        private var encSeq = 0L
        private var decSeq = 0L

        fun seal(plain: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, nonce(encSeq)))
            encSeq++
            return c.doFinal(plain)
        }

        fun open(cipher: ByteArray): ByteArray? = runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, nonce(decSeq)))
            decSeq++
            c.doFinal(cipher)
        }.getOrNull()

        private fun nonce(seq: Long): ByteArray {
            val n = ByteArray(12)
            for (i in 0 until 8) n[i] = ((seq ushr (8 * i)) and 0xFF).toByte()  // 小端
            return n
        }
    }

    // ------------------------------------------------------------------ HKDF-SHA256

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, len: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        val out = ByteArray(len)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < len) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val n = minOf(t.size, len - pos)
            System.arraycopy(t, 0, out, pos, n)
            pos += n
            counter++
        }
        return out
    }

    // ------------------------------------------------------------------ TLS 辅助

    private fun ensureProviders() {
        runCatching {
            Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
            if (Security.getProvider(BC_PROVIDER) == null) {
                Security.addProvider(
                    Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                        .getDeclaredConstructor().newInstance() as java.security.Provider
                )
            }
            if (Security.getProvider(BC_JSSE_PROVIDER) == null) {
                Security.addProvider(
                    Class.forName("org.bouncycastle.jsse.provider.BouncyCastleJsseProvider")
                        .getDeclaredConstructor().newInstance() as java.security.Provider
                )
            }
        }.onFailure { AppLogger.e(TAG, "注册 BC provider 失败: ${it.message}") }
    }

    private val PASSWORD = "aicompanion".toCharArray()

    /**
     * 客户端自签证书：持久化复用，避免每次配对都生成新的（生成的 RSA 2048 有几百毫秒开销）。
     * 证书本身不被校验，只用于 TLS 互相加密。
     */
    /**
     * 客户端证书：**预生成的内嵌常量**（DER base64）。
     *
     * 为什么不在运行时用 BC 生成：Android 自带一个精简版 `BC` provider 占着这个名字，
     * `JcaContentSignerBuilder("SHA256withRSA").setProvider("BC")` 会报
     * "no such algorithm: SHA256WITHRSA for provider BC"。
     * 内嵌常量后只用标准 API（CertificateFactory / KeyFactory）解析，稳且快。
     *
     * 证书只用于 TLS 加密（AOSP 双方都接受任意对端证书），不承担身份认证。
     */
    private const val CLIENT_CERT_B64 = "MIIDHTCCAgWgAwIBAgIUAYBTRjri5w8TuI/lQgg+sXXCJPUwDQYJKoZIhvcNAQELBQAwHjEcMBoGA1UEAwwTYWljb21wYW5pb24tcGFpcmluZzAeFw0yNjEwMjQwMzIzMjhaFw0zNjEwMjEwMzIzMjhaMB4xHDAaBgNVBAMME2FpY29tcGFuaW9uLXBhaXJpbmcwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCxDasM20iR6Osc2WQY+W2pe5ezm0N0xN7AHT2tKHLP7jrmYv02p+t6d9u8oAX9ZnBJpORLxEnq+JWeA29ONlHvprQ46LAIImoWfc1iTwAH6/U8H7XCRFBU9XE297bh+/7CQh8y690J4eQpzfXPqRWntSVUg0BPtuFbtZ0gNo1vIzsf5u5s5t3GpmfiygWOE3ZA1D15+AlH1Eubc8MEODTHmK0WUUdYKluLnDxQkpNfG2lFi578Kx/hvxEraiOlG/mwz58DlM+0ZAvmgG4/gIlTWMrJE3OLszsh/EudfODwGNgrFkCTC2+2ebKfs8gbaaGESCfx0ynqsnRHddqJtd7nAgMBAAGjUzBRMB0GA1UdDgQWBBTcIVTUd8l5dAaRNyAGNf7lFx9GNTAfBgNVHSMEGDAWgBTcIVTUd8l5dAaRNyAGNf7lFx9GNTAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQCdkHPvsAJeAov2QOmK51SwhQko/T9NyNMS9Jle7Nc2NruRIzrAoNq0xWwW5FKDc3vfqpCmI7LqQB8sGlAOpp/Y/4QI9PF06uf+XL5d/g0AJRZ0vOpxgUKC+xD0A4rWiqw/WU5G9yDtL1okpz7Lx7Pp1SNY7iCqMC28kqSVCme3zpWpl853n2XQyYlUNvQTc0GFRzcAu3Lptco97Wrw0jFyZrhEwv5zHjHDI6XtlpkZr5vgGeOZOzyIi2Vi3BwPKOBJqdYRVaPDFvDL9pLzlEIl0qcaUsl2ixm/cqfwUoD8jSfPVV8NZh6zMDFj4KWFQq7wdvMCizEd/PlwAocG5H4G"

    private const val CLIENT_KEY_B64 = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCxDasM20iR6Osc2WQY+W2pe5ezm0N0xN7AHT2tKHLP7jrmYv02p+t6d9u8oAX9ZnBJpORLxEnq+JWeA29ONlHvprQ46LAIImoWfc1iTwAH6/U8H7XCRFBU9XE297bh+/7CQh8y690J4eQpzfXPqRWntSVUg0BPtuFbtZ0gNo1vIzsf5u5s5t3GpmfiygWOE3ZA1D15+AlH1Eubc8MEODTHmK0WUUdYKluLnDxQkpNfG2lFi578Kx/hvxEraiOlG/mwz58DlM+0ZAvmgG4/gIlTWMrJE3OLszsh/EudfODwGNgrFkCTC2+2ebKfs8gbaaGESCfx0ynqsnRHddqJtd7nAgMBAAECggEAA30oUbrSw0QoS53PVEoV/NWQXEmwrOM9ZQcue2HlPLhFENwCgVykl0LuhiIqRTHB240URODN5l7fpi143SrU26Zh4jQPDRn773oY3WujZISRzkRa12kqWctg8gjrBjtFkMCvGCHJJLQjaJQNRkNw/l9RKG2/+Pa6vrGcVwhzBZtPE/kBEvdVq/xlmc+O+wxkBhLvZ5rC3mNZfnjGBT3g91vLURZ7ktJdCCCVNmr5JjemjpbBSLLzq1+epWU0eHJOYQZL9uam6b789HEYYJgmTFR22mx1HaFeN13oDzUAse2uPnI7bmu5q0kiFKDrQHLfzayXvgwjKqPWGP0IfxwaAQKBgQD0lLv5regDn++kd4wypJmKgPfp6XXfVx8uhen7blsif5FJUKFzKrdaO0ECUStF8okQc2ADfxzXDv9VwoadoxTEk96F27jiP25gIMIhAkD+uTanu5pRiM3IRaYdibn7bLrc3SZFNTrc32SjStJtMZPByh0I9D2W8SQ4gFFofmYnNwKBgQC5UdWt9JtFVIUPqwL9qoHOtztMCbxZg6M9WJmnWxgaThuzrqzdqRnGJOD26So4WHoQ5qO0EGED1NDGpjMgEs8HmJ3fQvq36ZhdKOe2Bd2qVk0A1nH5f5Jy8QqgYHbLrAPH900sn+hX6DKUiGRS8IzgElSjc4bVmJBaT89sAyZ90QKBgQCjLBOYyOAwguRzWlqLcusIz3AWe23rWglwlWD8IKzmgCsiuYts9UOsDDob3KE9Ikk1U3PsAULxa40H3ybGRoXVfD2KGoPnVaBCu7goI4PPLSv8jZmv6dA4W9cuf7M3vVuRfG1K4O9rahKPKpzmwpusPzZNwfzRFgIvO9SUHTfz0wKBgCmvpNdKjJrd1kid+UBCWIy9XtNN5VZlbBTilz20GgngxA55Hl2GKkwOA6TG8j5WeK7QksZwYMJR3tywihOO548X33tXP7qbCzD5gJXLX/9vBkdH9b9f2PuOCQvmZxKNkRQsJuoA1mzfyHDs9VRK1AWbWfqihBQMC3ylM01isAfhAoGAVGx5bkhsgbO/4Z5U66fCT+omWnNMlELTAkuVzSVRZZhmBjcA1bt4Ltz18MZzy2Ifq2yajC/1blHggEhtnWCwuGpQIxxc8NaqfvlZyd/QnmkNatyy1GzqX44qixOAbnPZ2K0IYXy5mjLtXWMAVaMHpA/I9qd2f1Xbajkd7hZKaYQ="

    private fun loadOrCreateCert(context: Context): Pair<X509Certificate, KeyStore> {
        val cert = java.security.cert.CertificateFactory.getInstance("X.509")
            .generateCertificate(
                java.io.ByteArrayInputStream(
                    android.util.Base64.decode(CLIENT_CERT_B64, android.util.Base64.DEFAULT)
                )
            ) as X509Certificate
        val key = java.security.KeyFactory.getInstance("RSA").generatePrivate(
            java.security.spec.PKCS8EncodedKeySpec(
                android.util.Base64.decode(CLIENT_KEY_B64, android.util.Base64.DEFAULT)
            )
        )
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("client", key, PASSWORD, arrayOf(cert))
        return cert to ks
    }

    /**
     * 从配对对话框的界面文字里读端口。
     *
     * 为什么不用 mDNS：实测多台机器上 `_adb-tls-pairing._tcp` 三轮都拿不到广播（国产 ROM 常见），
     * 而应用进程又读不到 `/proc/net/tcp`（SELinux 拒绝），所以最可靠的来源是屏幕本身——
     * 对话框上就写着「IP 地址和端口 192.168.x.x:46771」，我们自己的无障碍服务能直接读到。
     *
     * 匹配顺序：先找 `a.b.c.d:port`，再退回「端口…12345」这种纯数字写法。
     */
    private fun sniffPortFromScreen(): Int? = runCatching {
        com.aicompanion.screen.ScreenRecognitionService.refreshAndWait(1200, false)
        val text = com.aicompanion.screen.ScreenRecognitionService.getLastScreenText()
        if (text.isBlank()) return null
        val ipPort = Regex("""(?:\d{1,3}\.){3}\d{1,3}:(\d{2,5})""").find(text)
        val portOnly = Regex("""端口[^\d]{0,6}(\d{2,5})""").find(text)
        (ipPort ?: portOnly)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }.getOrNull()

    /**
     * mDNS 找配对端口（对话框开着时才会广播）。
     *
     * 实测 NSD 不是每次都一次成功：进程刚起、上一次发现没释放时会拿到空列表。
     * 所以重试 3 轮，每轮 3.5s，命中即返回。
     */
    private fun discoverPairingPort(context: Context, timeoutMs: Long): Int? {
        repeat(3) { attempt ->
            val latch = java.util.concurrent.CountDownLatch(1)
            var result: Int? = null
            AdbDiscovery.discover(context, AdbDiscovery.SERVICE_PAIRING, timeoutMs) { list ->
                result = list.firstOrNull { it.port > 0 }?.port
                AppLogger.i(TAG, "第 ${attempt + 1} 轮发现：${list.size} 个服务，端口=${result ?: "-"}")
                latch.countDown()
            }
            runCatching {
                latch.await(timeoutMs + 2000, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            if (result != null) return result
            // 让 NSD 会话释放干净再试
            Thread.sleep(700)
        }
        return null
    }

    /**
     * 底层 TLS 客户端：TLS 1.3 + 自签证书 + 接受任意对端证书。
     * 必须写成具名子类：`context` 是 AbstractTlsPeer 的 protected 字段，
     * 匿名 object 里拿不到（还会被函数的 Android Context 参数遮蔽）。
     */
    private class AdbTlsClient(
        private val bcCrypto: BcTlsCrypto,
        private val privateKeyParam: org.bouncycastle.crypto.params.AsymmetricKeyParameter,
        private val clientCert: org.bouncycastle.tls.Certificate,
    ) : DefaultTlsClient(bcCrypto) {

        /** 握手回调里抓取的 64 字节导出密钥；握完手 BC 会销毁源密钥，只能此时取 */
        var exportedKeyMaterial: ByteArray? = null
            private set

        override fun getSupportedVersions(): Array<ProtocolVersion> =
            arrayOf(ProtocolVersion.TLSv13)

        /**
         * 唯一的合法取 EKM 时机（BC 的 `checkExportSecret` 在握手结束后必抛）。
         * label 必须含结尾 \0（AOSP 用 sizeof() 取长度），context 为空数组。
         */
        override fun notifyHandshakeComplete() {
            try {
                exportedKeyMaterial =
                    context.exportKeyingMaterial("adb-label\u0000", ByteArray(0), 64)
                AppLogger.i(TAG, "回调内取 EKM 成功：${exportedKeyMaterial?.size ?: 0}B")
            } catch (t: Throwable) {
                AppLogger.e(TAG, "回调内取 EKM 失败: ${t.message}", t)
            }
        }

        override fun getAuthentication(): TlsAuthentication = object : TlsAuthentication {
            override fun notifyServerCertificate(serverCertificate: TlsServerCertificate) {
                // 接受任意对端证书：配对认证靠 SPAKE2，不靠证书
            }

            override fun getClientCredentials(
                certificateRequest: CertificateRequest,
            ): TlsCredentials = BcDefaultTlsCredentialedSigner(
                TlsCryptoParameters(context),
                bcCrypto,
                privateKeyParam,
                clientCert,
                // ⚠️ 必须显式给算法：传 null 时 BC 在 TLS1.3 的
                // CertificateVerify 阶段拿不到签名方案，抛 internal_error(80)，
                // 表现为对端"拒绝"。RSA 证书在 TLS1.3 只允许 PSS（不能用 PKCS#1 v1.5），
                // 所以固定 rsa_pss_rsae_sha256（BoringSSL/adbd 均支持）。
                org.bouncycastle.tls.SignatureAndHashAlgorithm.rsa_pss_rsae_sha256,
            )
        }
    }

    /** 只为把 protected 的 `context` 暴露出来（BC 的 TlsClientProtocol.getContext 是 protected） */
    private class ExposedTlsClientProtocol(
        input: InputStream,
        output: OutputStream,
    ) : TlsClientProtocol(input, output) {
        fun tlsContext(): TlsContext = context
    }

    private fun hex(b: ByteArray): String =
        b.joinToString("") { "%02x".format(it) }
}
