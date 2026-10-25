package com.aicompanion.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * SPAKE25519 的自检。
 *
 * 说明：BoringSSL 官方测试里没有固定向量（源码里写着 TODO），所以这里做两类验证：
 *  1) 数学自检 —— 基点阶数、加法与倍点一致、M/N 编解码往返；
 *  2) 协议自检 —— alice/bob 双方用同一口令能算出**相同**的 64 字节 key，错口令则不同。
 * 真正的"与 adbd 兼容"必须真机配对验证，这一步只能证明实现自洽且编码正确。
 */
class Spake25519Test {

    private val L = BigInteger.TWO.pow(252)
        .add(BigInteger("27742317777372353535851937790883648493"))

    @Test
    fun basePointRoundTrip() {
        val b = Spake25519.fromCompressed(Spake25519.hex(
            "5866666666666666666666666666666666666666666666666666666666666666"))
        assertNotNull("基点应能解析", b)
        val back = Spake25519.compress(b!!)
        assertArrayEquals(
            Spake25519.hex("5866666666666666666666666666666666666666666666666666666666666666"),
            back
        )
    }

    @Test
    fun basePointHasOrderL() {
        val b = Spake25519.fromCompressed(Spake25519.hex(
            "5866666666666666666666666666666666666666666666666666666666666666"))!!
        val identity = Spake25519.scalarMult(L, b)
        // 单位元：(0, 1)
        assertEquals(BigInteger.ZERO, identity.x)
        assertEquals(BigInteger.ONE, identity.y)
    }

    @Test
    fun doubleEqualsAddToSelf() {
        val b = Spake25519.fromCompressed(Spake25519.hex(
            "5866666666666666666666666666666666666666666666666666666666666666"))!!
        val twice = Spake25519.add(b, b)
        val viaScalar = Spake25519.scalarMult(BigInteger.valueOf(2), b)
        assertEquals(viaScalar.x, twice.x)
        assertEquals(viaScalar.y, twice.y)
    }

    @Test
    fun maskPointsRoundTrip() {
        val m = Spake25519.hex("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e")
        val n = Spake25519.hex("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778")
        val pm = Spake25519.fromCompressed(m)
        val pn = Spake25519.fromCompressed(n)
        assertNotNull("M 应能解析", pm)
        assertNotNull("N 应能解析", pn)
        assertArrayEquals(m, Spake25519.compress(pm!!))
        assertArrayEquals(n, Spake25519.compress(pn!!))
        // M 与 N 不能相同（否则掩码失效）
        assertNotEquals(Spake25519.compress(pm), Spake25519.compress(pn))
    }

    @Test
    fun aliceAndBobDeriveSameKey() {
        val password = "976948".toByteArray(Charsets.US_ASCII)

        val alice = Spake25519.client()
        val bob = Spake25519.server()

        val msgA = alice.generateMsg(password)
        val msgB = bob.generateMsg(password)
        assertEquals(32, msgA.size)
        assertEquals(32, msgB.size)
        assertNotEquals(
            "双方消息不应相同",
            msgA.joinToString("") { "%02x".format(it) },
            msgB.joinToString("") { "%02x".format(it) }
        )

        val keyA = alice.processMsg(msgB)
        val keyB = bob.processMsg(msgA)
        assertEquals("PSK 应是 64 字节", 64, keyA.size)
        assertArrayEquals("双方 PSK 必须一致", keyA, keyB)

        // 同一口令两次握手应得到不同 PSK（因为私钥是随机的）
        val alice2 = Spake25519.client()
        val msgA2 = alice2.generateMsg(password)
        val keyA2 = alice2.processMsg(msgB)
        assertNotEquals(
            "不同会话的 PSK 不应相同",
            keyA.joinToString("") { "%02x".format(it) },
            keyA2.joinToString("") { "%02x".format(it) }
        )
        assertNotEquals(msgA.joinToString(""), msgA2.joinToString(""))
    }

    @Test
    fun wrongPasswordProducesDifferentKey() {
        val alice = Spake25519.client()
        val bob = Spake25519.server()
        val msgA = alice.generateMsg("123456".toByteArray())
        val msgB = bob.generateMsg("654321".toByteArray())
        val keyA = alice.processMsg(msgB)
        val keyB = bob.processMsg(msgA)
        assertTrue(
            "不同口令算出的 PSK 必须不同（否则配对会在无提示的情况下通过）",
            !keyA.contentEquals(keyB)
        )
    }

    @Test
    fun rejectsInvalidPoint() {
        // 全 0xff 不是合法的压缩点
        val bad = ByteArray(32) { 0xff.toByte() }
        assertTrue("非法点应被拒绝", Spake25519.fromCompressed(bad) == null)
    }
}
