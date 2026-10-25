/**
 * ADB 客户端密钥：RSA-2048，负责「签名 adbd 下发的 token」和「导出 adb 格式公钥」。
 *
 * 为什么公钥要特殊编码：adb 认的不是 PEM，而是 Android 的 RSAPublicKey 结构
 * （len + n0inv + n[64] + rr[64] + exponent，全部 32 位小端）。格式错一个字节，
 * 设备就会静默拒绝，日志里只有一句 auth failed。
 */
package com.aicompanion.adb

import android.content.Context
import com.aicompanion.util.AppLogger
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class AdbKey(val privateKey: RSAPrivateKey, val publicKey: RSAPublicKey) {

    companion object {
        private const val TAG = "AdbKey"
        private const val DIR = "adb"
        private const val FILE = "adb_key.pem"

        @Volatile
        private var cached: AdbKey? = null

        /** 取本机密钥；首次调用生成并落盘 */
        fun get(context: Context): AdbKey {
            cached?.let { return it }
            synchronized(this) {
                cached?.let { return it }
                val f = File(File(context.filesDir, DIR), FILE)
                val loaded = runCatching { load(f) }.getOrNull()
                val key = loaded ?: generate(f)
                cached = key
                return key
            }
        }

        private fun load(f: File): AdbKey? {
            if (!f.exists()) return null
            val text = f.readText()
            val priv = text.substringAfter("<private>").substringBefore("</private>").trim()
            val pub = text.substringAfter("<public>").substringBefore("</public>").trim()
            if (priv.isEmpty() || pub.isEmpty()) return null
            val kf = KeyFactory.getInstance("RSA")
            val p = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(priv)))
            val q = kf.generatePublic(X509EncodedKeySpec(Base64.getMimeDecoder().decode(pub)))
            return AdbKey(p as RSAPrivateKey, q as RSAPublicKey)
        }

        /** 从 PKCS#8 PEM（电脑上的 ~/.android/adbkey）导入，用于"复用已信任密钥" */
        fun importPem(context: Context, pem: String): Boolean = runCatching {
            val b64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s+".toRegex(), "")
            val der = Base64.getDecoder().decode(b64)
            val kf = KeyFactory.getInstance("RSA")
            // Conscrypt 的私钥实现不一定实现 RSAPrivateCrtKey，所以模数/指数直接从 DER 里取
            val priv = kf.generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateKey
            val (n, e) = parsePkcs8RsaModulus(der)
                ?: error("无法从 PKCS#8 解析 RSA 模数")
            val pub = kf.generatePublic(java.security.spec.RSAPublicKeySpec(n, e)) as RSAPublicKey
            val key = AdbKey(priv, pub)
            save(File(File(context.filesDir, DIR), FILE), priv, pub)
            cached = key
            AppLogger.i(TAG, "已导入外部密钥，指纹 ${key.fingerprint().take(16)}…")
            true
        }.getOrElse {
            AppLogger.e(TAG, "导入密钥失败: ${it.message}", it)
            false
        }

        /** 极简 DER 解析：PKCS#8 → (modulus, publicExponent) */
        private fun parsePkcs8RsaModulus(der: ByteArray): Pair<BigInteger, BigInteger>? = runCatching {
            var i = 0
            fun tag(): Int = der[i++].toInt() and 0xff
            fun len(): Int {
                var l = der[i++].toInt() and 0xff
                if (l and 0x80 != 0) {
                    val n = l and 0x7f
                    l = 0
                    repeat(n) { l = (l shl 8) or (der[i++].toInt() and 0xff) }
                }
                return l
            }
            fun skip(): Unit { tag(); val l = len(); i += l }
            fun int(): BigInteger { tag(); val l = len(); val v = der.copyOfRange(i, i + l); i += l; return BigInteger(v) }
            tag(); len()                 // outer SEQUENCE
            int()                        // version
            skip()                       // AlgorithmIdentifier
            tag(); val octLen = len()     // OCTET STRING
            val inner = der.copyOfRange(i, i + octLen)
            var j = 0
            fun itag(): Int = inner[j++].toInt() and 0xff
            fun ilen(): Int {
                var l = inner[j++].toInt() and 0xff
                if (l and 0x80 != 0) { val n = l and 0x7f; l = 0; repeat(n) { l = (l shl 8) or (inner[j++].toInt() and 0xff) } }
                return l
            }
            fun iint(): BigInteger { itag(); val l = ilen(); val v = inner.copyOfRange(j, j + l); j += l; return BigInteger(v) }
            itag(); ilen()               // RSAPrivateKey SEQUENCE
            iint()                       // version
            val n = iint()
            val e = iint()
            n to e
        }.getOrNull()

        private fun save(f: File, priv: RSAPrivateKey, pub: RSAPublicKey) {
            runCatching {
                f.parentFile?.mkdirs()
                f.writeText(
                    "<private>\n" + Base64.getMimeEncoder().encodeToString(priv.encoded) +
                        "\n</private>\n<public>\n" + Base64.getMimeEncoder().encodeToString(pub.encoded) +
                        "\n</public>\n"
                )
            }.onFailure { AppLogger.e(TAG, "保存密钥失败: ${it.message}", it) }
        }

        private fun generate(f: File): AdbKey {
            val gen = KeyPairGenerator.getInstance("RSA")
            gen.initialize(2048)
            val kp = gen.generateKeyPair()
            val key = AdbKey(kp.private as RSAPrivateKey, kp.public as RSAPublicKey)
            save(f, kp.private as RSAPrivateKey, kp.public as RSAPublicKey)
            AppLogger.i(TAG, "已生成新的 ADB 密钥（SHA-256 ${key.fingerprint().take(16)}…）")
            return key
        }
    }

    /** adbd 要求：对 20 字节 token 做 SHA1withRSA（PKCS#1 v1.5） */
    fun signToken(token: ByteArray): ByteArray {
        val s = Signature.getInstance("SHA1withRSA")
        s.initSign(privateKey)
        s.update(token)
        return s.sign()
    }

    /** 公钥指纹（给自己看的，便于核对设备侧那份是否一致） */
    fun fingerprint(): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(publicKey.encoded).joinToString(":") { "%02X".format(it) }
    }

    /** adb_keys 文件里的一行（base64 结构 + 可选注释） */
    fun adbKeyLine(): String = androidPubkeyBase64() + " aicompanion@android"

    /**
     * Android RSAPublicKey 结构 → base64。
     * 布局：len(词数) | n0inv | n[64] | rr[64] | exponent，全部 32 位小端。
     */
    fun androidPubkeyBase64(): String {
        val n = publicKey.modulus
        val e = publicKey.publicExponent
        val words = (n.bitLength() + 31) / 32
        val buf = ByteArray(4 + 4 + words * 4 + words * 4 + 4)
        var p = 0
        p = putIntLe(buf, p, words.toLong())
        // n0inv = -1 / n mod 2^32
        val n0 = n.mod(BigInteger.ONE.shiftLeft(32)).toLong() and 0xFFFFFFFFL
        p = putIntLe(buf, p, (0x100000000L - modInverse32(n0)) and 0xFFFFFFFFL)
        p = putWordsLe(buf, p, n, words)
        // rr = R^2 mod n，R = 2^(32*words)
        val rr = BigInteger.ONE.shiftLeft(64 * words).mod(n)
        p = putWordsLe(buf, p, rr, words)
        p = putIntLe(buf, p, e.toLong())
        return Base64.getEncoder().encodeToString(buf.copyOf(p))
    }

    /** 牛顿迭代求 a 在 2^32 下的逆元（a 必须为奇数） */
    private fun modInverse32(a: Long): Long {
        var x = 1L
        repeat(5) { x = (x * (2 - a * x)) and 0xFFFFFFFFL }
        return x and 0xFFFFFFFFL
    }

    private fun putIntLe(buf: ByteArray, off: Int, v: Long): Int {
        buf[off] = (v and 0xFF).toByte()
        buf[off + 1] = ((v shr 8) and 0xFF).toByte()
        buf[off + 2] = ((v shr 16) and 0xFF).toByte()
        buf[off + 3] = ((v shr 24) and 0xFF).toByte()
        return off + 4
    }

    private fun putWordsLe(buf: ByteArray, off: Int, v: BigInteger, words: Int): Int {
        var p = off
        for (i in 0 until words) {
            val w = v.shiftRight(32 * i).and(BigInteger.valueOf(0xFFFFFFFFL)).toLong()
            p = putIntLe(buf, p, w)
        }
        return p
    }
}
