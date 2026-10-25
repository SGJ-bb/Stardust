/**
 * SPAKE25519 的 Kotlin 实现（与 BoringSSL `crypto/curve25519/spake25519.cc` 语义对齐）。
 *
 * 为什么自己写：adbd 的配对走这套协议，PSK 必须**逐字节**一致，否则 adbd 只会说"配对失败"。
 * 参考实现放在 `reference/adb_pairing/spake25519.cc`（BoringSSL, OpenSSL/ISC 许可）。
 *
 * 关键点（照抄自参考实现，不是猜的）：
 *  · M/N 是固定点，encoded 值见 BoringSSL 注释；alice 用 M 掩码、验 bob 用 N，bob 相反；
 *  · 私钥 = sc_reduce(random64)，再左移 3 位（乘 8，为了消掉余因子）；
 *  · 密码标量 = sc_reduce(SHA512(password))；BoringSSL 有个历史 bug 修补：
 *    按低 3 位把 L / 2L / 4L 加上去，使标量成为 8 的倍数（我们照做，否则两边 PSK 不同）；
 *  · 最终 key = SHA512(名字长度前缀 + 名字 + 双方消息 + DH 点 + 密码哈希)。
 *
 * 性能：用 BigInteger 实现，单次配对几毫秒级——配对是一次性操作，不需要常数时间。
 */
package com.aicompanion.adb

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

object Spake25519 {

    /** edwards25519 的基域 p = 2^255 - 19 */
    private val P = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19))

    /** 子群阶 L = 2^252 + 27742317777372353535851937790883648493 */
    private val L = BigInteger.TWO.pow(252)
        .add(BigInteger("27742317777372353535851937790883648493"))

    /** d = -121665/121666 mod p */
    private val D = BigInteger.valueOf(-121665).mod(P)
        .multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P)

    /** sqrt(-1) mod p */
    private val SQRT_M1 = BigInteger.valueOf(2)
        .modPow(P.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4)), P)

    /** 标准 ed25519 基点（压缩形式） */
    private val BASE_COMPRESSED = hex("5866666666666666666666666666666666666666666666666666666666666666")

    /** SPAKE2 的 M 点（alice 的掩码） */
    private val M_COMPRESSED =
        hex("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e")

    /** SPAKE2 的 N 点（bob 的掩码） */
    private val N_COMPRESSED =
        hex("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778")

    enum class Role { ALICE, BOB }

    data class Point(val x: BigInteger, val y: BigInteger)

    private val IDENTITY = Point(BigInteger.ZERO, BigInteger.ONE)

    /** 一侧的 SPAKE2 上下文 */
    class Ctx(val role: Role, myName: ByteArray, theirName: ByteArray) {
        private val myName = myName.copyOf()
        private val theirName = theirName.copyOf()
        private var privateKey: BigInteger = BigInteger.ZERO
        private var passwordScalar: BigInteger = BigInteger.ZERO
        private var passwordHash: ByteArray = ByteArray(64)
        private var myMsg: ByteArray = ByteArray(0)

        /** 生成我方消息（32 字节），返回它；交换前必须先调这个 */
        fun generateMsg(password: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
            val priv = ByteArray(64).also { random.nextBytes(it) }
            privateKey = leftShift3(scReduce(priv))

            val pwHash = MessageDigest.getInstance("SHA-512").digest(password)
            passwordHash = pwHash
            var ps = scReduce(pwHash)
            // BoringSSL 的历史修补：把标量凑成 8 的倍数（低 3 位决定加 L / 2L / 4L）
            val bits = ps.and(BigInteger.valueOf(7)).toInt()
            if (bits and 1 != 0) ps = ps.add(L)
            if (bits and 2 != 0) ps = ps.add(L.shiftLeft(1))
            if (bits and 4 != 0) ps = ps.add(L.shiftLeft(2))
            passwordScalar = ps

            val base = decompress(BASE_COMPRESSED) ?: error("base point 解析失败")
            val p = scalarMult(privateKey, base)
            val mask = scalarMult(passwordScalar, ownMaskPoint())
            myMsg = compress(add(p, mask))
            return myMsg
        }

        /** 收到对方消息，计算共享 key（64 字节） */
        fun processMsg(theirMsg: ByteArray): ByteArray {
            require(myMsg.isNotEmpty()) { "先调 generateMsg" }
            require(theirMsg.size == 32) { "对方消息必须是 32 字节，实际 ${theirMsg.size}" }
            val qStar = decompress(theirMsg) ?: error("对方点不在曲线上")
            val peersMask = scalarMult(passwordScalar, peerMaskPoint())
            val q = sub(qStar, peersMask)
            val shared = compress(scalarMult(privateKey, q))

            val sha = MessageDigest.getInstance("SHA-512")
            if (role == Role.ALICE) {
                updateWithLength(sha, myName)
                updateWithLength(sha, theirName)
                updateWithLength(sha, myMsg)
                updateWithLength(sha, theirMsg)
            } else {
                updateWithLength(sha, theirName)
                updateWithLength(sha, myName)
                updateWithLength(sha, theirMsg)
                updateWithLength(sha, myMsg)
            }
            updateWithLength(sha, shared)
            updateWithLength(sha, passwordHash)
            return sha.digest()
        }

        private fun ownMaskPoint(): Point =
            decompress(if (role == Role.ALICE) M_COMPRESSED else N_COMPRESSED) ?: error("M/N 解析失败")

        private fun peerMaskPoint(): Point =
            decompress(if (role == Role.ALICE) N_COMPRESSED else M_COMPRESSED) ?: error("M/N 解析失败")
    }

    /** adb 配对用的客户端/服务端名字（sizeof 含结尾 \0，见参考实现） */
    val CLIENT_NAME: ByteArray = "adb pair client".toByteArray(Charsets.US_ASCII) + 0
    val SERVER_NAME: ByteArray = "adb pair server".toByteArray(Charsets.US_ASCII) + 0

    fun client(): Ctx = Ctx(Role.ALICE, CLIENT_NAME, SERVER_NAME)
    fun server(): Ctx = Ctx(Role.BOB, SERVER_NAME, CLIENT_NAME)

    // ------------------------------------------------------------------ 数学

    /** 64 字节小端 → mod L 的标量 */
    internal fun scReduce(input64: ByteArray): BigInteger =
        leToInt(input64).mod(L)

    /** 32 字节标量左移 3 位（乘 8），保留低位 256 bit */
    internal fun leftShift3(scalar: BigInteger): BigInteger =
        scalar.shiftLeft(3).and(BigInteger.TWO.pow(256).subtract(BigInteger.ONE))

    internal fun decompress(b32: ByteArray): Point? {
        if (b32.size != 32) return null
        val yBytes = b32.copyOf()
        val sign = (yBytes[31].toInt() and 0x80) != 0
        yBytes[31] = (yBytes[31].toInt() and 0x7f).toByte()
        val y = leToInt(yBytes)
        if (y >= P) return null
        val y2 = y.multiply(y).mod(P)
        val u = y2.subtract(BigInteger.ONE).mod(P)
        val v = D.multiply(y2).add(BigInteger.ONE).mod(P)
        val v3 = v.multiply(v).multiply(v).mod(P)
        val v7 = v3.multiply(v3).multiply(v).mod(P)
        var x = u.multiply(v3).multiply(
            u.multiply(v7).mod(P).modPow(P.subtract(BigInteger.valueOf(5)).divide(BigInteger.valueOf(8)), P)
        ).mod(P)
        val vx2 = v.multiply(x).multiply(x).mod(P)
        if (vx2 != u) {
            if (vx2 == P.subtract(u).mod(P)) {
                x = x.multiply(SQRT_M1).mod(P)
            } else {
                return null
            }
        }
        if (x.testBit(0) != sign) x = P.subtract(x).mod(P)
        return Point(x, y)
    }

    internal fun compress(p: Point): ByteArray {
        val out = le(p.y, 32)
        if (p.x.testBit(0)) out[31] = (out[31].toInt() or 0x80).toByte()
        return out
    }

    internal fun add(a: Point, b: Point): Point {
        val x1y2 = a.x.multiply(b.y)
        val x2y1 = b.x.multiply(a.y)
        val x1x2 = a.x.multiply(b.x)
        val y1y2 = a.y.multiply(b.y)
        val dxy = D.multiply(x1x2).multiply(y1y2).mod(P)
        val x3 = x1y2.add(x2y1).mod(P)
            .multiply(BigInteger.ONE.add(dxy).mod(P).modInverse(P)).mod(P)
        // a = -1 ⇒ y3 = (y1y2 + x1x2) / (1 - d·x1x2·y1y2)
        val y3 = y1y2.add(x1x2).mod(P)
            .multiply(BigInteger.ONE.subtract(dxy).mod(P).modInverse(P)).mod(P)
        return Point(x3, y3)
    }

    internal fun sub(a: Point, b: Point): Point = add(a, Point(P.subtract(b.x).mod(P), b.y))

    internal fun scalarMult(k0: BigInteger, p: Point): Point {
        var k = k0.mod(L.shiftLeft(4)) // 允许 >L 的标量（密码标量可能到 8L-1）
        var result = IDENTITY
        var addend = p
        while (k.signum() > 0) {
            if (k.testBit(0)) result = add(result, addend)
            addend = add(addend, addend)
            k = k.shiftRight(1)
        }
        return result
    }

    internal fun identity(): Point = IDENTITY

    internal fun fromCompressed(b32: ByteArray): Point? = decompress(b32)

    // ------------------------------------------------------------------ 工具

    private fun updateWithLength(sha: MessageDigest, data: ByteArray) {
        val len = ByteArray(8)
        var l = data.size.toLong()
        for (i in 0 until 8) {
            len[i] = (l and 0xff).toByte()
            l = l shr 8
        }
        sha.update(len)
        sha.update(data)
    }

    internal fun leToInt(bytes: ByteArray): BigInteger {
        val be = bytes.reversedArray()
        return if (be.isEmpty()) BigInteger.ZERO else BigInteger(1, be)
    }

    /** BigInteger → 定长小端字节 */
    internal fun le(v: BigInteger, size: Int): ByteArray {
        val out = ByteArray(size)
        var x = v
        for (i in 0 until size) {
            out[i] = x.and(BigInteger.valueOf(0xFF)).toByte()
            x = x.shiftRight(8)
        }
        return out
    }

    internal fun hex(s: String): ByteArray {
        require(s.length % 2 == 0)
        return ByteArray(s.length / 2) { i ->
            ((Character.digit(s[i * 2], 16) shl 4) or Character.digit(s[i * 2 + 1], 16)).toByte()
        }
    }
}
