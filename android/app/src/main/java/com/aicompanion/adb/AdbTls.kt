/**
 * adb 的 TLS 升级层（无线调试端口）。
 *
 * ## 为什么用系统 JSSE 而不是 BouncyCastle
 *
 * 我们一开始用 BC 的底层 `TlsClientProtocol` 做这层：ClientHello 与官方 adb 抓包对照，
 * 密码套件（1303/1301/1302）、扩展（supported_groups / signature_algorithms /
 * key_share / psk_key_exchange_modes / supported_versions）全都一模一样，
 * 但 adbd 仍然直接回 `handshake_failure(40)`，连 ServerHello 都不给。
 *
 * 官方的 `adb` 客户端用的是 BoringSSL，而**安卓系统的 JSSE 实现就是 Conscrypt（同样是
 * BoringSSL 内核）**。换成系统 SSLSocket 后行为与官方 adb 一致，不用自己造 TLS 栈。
 *
 * ## 客户端证书
 *
 * adbd 在 TLS 层要求客户端出示证书，并把证书里的公钥与 `/data/misc/adb/adb_keys`
 * 比对。所以证书必须用**配对时交给系统的那把 AdbKey** 现造（见 [certDer]）。
 */
package com.aicompanion.adb

import android.util.Base64
import com.aicompanion.util.AppLogger
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Date

object AdbTls {

    private const val TAG = "AdbTls"

    @Volatile
    private var certDerCache: ByteArray? = null
    @Volatile
    private var certFingerprint: String? = null

    /** 为 AdbKey 生成自签证书（DER）；同一把钥匙结果固定 */
    fun certDer(key: AdbKey): ByteArray {
        val fp = key.fingerprint()
        certDerCache?.let { if (certFingerprint == fp) return it }
        synchronized(this) {
            certDerCache?.let { if (certFingerprint == fp) return it }
            val der = buildCert(key)
            certDerCache = der
            certFingerprint = fp
            AppLogger.i(TAG, "客户端证书已生成（${der.size}B，公钥 $fp）")
            return der
        }
    }

    private fun buildCert(key: AdbKey): ByteArray {
        val now = System.currentTimeMillis()
        val name = X500Name("CN=aicompanion-adb")
        val serial = BigInteger(63, SecureRandom()).abs().add(BigInteger.ONE)
        val builder = JcaX509v3CertificateBuilder(
            name, serial,
            Date(now - 24 * 3600_000L),
            Date(now + 3650L * 24 * 3600_000L),
            name, key.publicKey,
        )
        return builder.build(JcaSignatureSigner(key)).encoded
    }

    /** ContentSigner 的最小实现：签名交给标准 JCA（安卓上是 Conscrypt，不用 BC provider） */
    private class JcaSignatureSigner(private val key: AdbKey) : ContentSigner {
        private val buf = ByteArrayOutputStream()
        override fun getAlgorithmIdentifier() = org.bouncycastle.asn1.x509.AlgorithmIdentifier(
            PKCSObjectIdentifiers.sha256WithRSAEncryption, org.bouncycastle.asn1.DERNull.INSTANCE,
        )

        override fun getOutputStream(): OutputStream = buf
        override fun getSignature(): ByteArray {
            val s = Signature.getInstance("SHA256withRSA")
            s.initSign(key.privateKey)
            s.update(buf.toByteArray())
            return s.sign()
        }
    }

    /**
     * 把已连通的明文 socket 升级成 TLS（客户端证书 = AdbKey）。
     * 返回 SSLSocket，其输入/输出流就是后续 adb 协议的载体。
     */
    fun upgrade(socket: Socket, key: AdbKey, timeoutMs: Int = 8000): SSLSocket? {
        return try {
            val cert = java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(java.io.ByteArrayInputStream(certDer(key))) as X509Certificate
            val ctx = SSLContext.getInstance("TLSv1.3")
            ctx.init(
                arrayOf(AdbKeyManager(cert, key.privateKey)),
                arrayOf(TrustAll),
                SecureRandom(),
            )
            val ssl = ctx.socketFactory.createSocket(
                socket, socket.inetAddress.hostAddress, socket.port, true,
            ) as SSLSocket
            ssl.soTimeout = timeoutMs
            ssl.enabledProtocols = arrayOf("TLSv1.3")
            ssl.startHandshake()
            AppLogger.i(TAG, "TLS 升级完成：${ssl.session.protocol} / ${ssl.session.cipherSuite}")
            ssl.soTimeout = 0
            ssl
        } catch (e: Exception) {
            AppLogger.w(TAG, "TLS 升级失败: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** 只在服务端要求时提供客户端证书（adbd 一定会要求） */
    private class AdbKeyManager(
        private val cert: X509Certificate,
        private val key: PrivateKey,
    ) : X509ExtendedKeyManager() {
        private val alias = "adbkey"
        override fun getClientAliases(keyType: String?, issuers: Array<Principal>?): Array<String> =
            arrayOf(alias)

        override fun chooseClientAlias(
            keyType: Array<String>?, issuers: Array<Principal>?, socket: Socket?,
        ): String = alias

        override fun getServerAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? = null

        override fun chooseServerAlias(keyType: String?, issuers: Array<Principal>?, socket: Socket?): String? = null

        override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(cert)

        override fun getPrivateKey(alias: String?): PrivateKey = key
    }

    /** adbd 用的是它自己的自签证书；身份由 adb 协议 + 信任名单保证，这里不校验链路 */
    private object TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /** 仅调试用：把证书的 base64 打出来对照 */
    fun certBase64(key: AdbKey): String = Base64.encodeToString(certDer(key), Base64.NO_WRAP)
}
