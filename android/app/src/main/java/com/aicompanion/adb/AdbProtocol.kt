/** adb 线协议：24 字节包头（小端）+ payload。 */
package com.aicompanion.adb

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException

internal object AdbProtocol {

    const val A_CNXN = 0x4e584e43
    const val A_AUTH = 0x48545541
    const val A_OPEN = 0x4e45504f
    const val A_OKAY = 0x59414b4f
    const val A_CLSE = 0x45534c43
    const val A_WRTE = 0x45545257
    const val A_STLS = 0x534c5453

    /** 协议版本 1.0.31（现代 adb 通用） */
    const val A_VERSION = 0x01000000

    /** A_STLS 的 arg0：TLS 版本，官方 adb 用 1.0.0 编码 0x01000000（不是 1！） */
    const val A_STLS_VERSION = 0x01000000

    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2
    const val AUTH_RSAPUBLICKEY = 3

    const val MAX_PAYLOAD = 4096

    data class Msg(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    /**
     * 读一条消息。
     * deadline 是绝对时间戳：读超时**不能**中断在帧中间，否则半个包头被吃掉，之后所有帧错位。
     * 所以这里用手写 readExact：超时只代表"暂时没数据"，继续等，直到 deadline。
     */
    fun read(input: InputStream, deadlineMs: Long = System.currentTimeMillis() + 30_000): Msg {
        val header = ByteArray(24)
        readExact(input, header, deadlineMs)
        val command = le32(header, 0)
        val arg0 = le32(header, 4)
        val arg1 = le32(header, 8)
        val len = le32(header, 12)
        val magic = le32(header, 20)
        if (magic != (command xor -1)) throw EOFException("magic mismatch")
        if (len < 0 || len > 1024 * 1024) throw EOFException("bad length $len")
        val data = ByteArray(len)
        if (len > 0) readExact(input, data, deadlineMs)
        return Msg(command, arg0, arg1, data)
    }

    fun write(out: OutputStream, command: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val header = ByteArray(24)
        putLe32(header, 0, command)
        putLe32(header, 4, arg0)
        putLe32(header, 8, arg1)
        putLe32(header, 12, data.size)
        putLe32(header, 16, checksum(data))
        putLe32(header, 20, command xor -1)
        out.write(header)
        if (data.isNotEmpty()) out.write(data)
        out.flush()
    }

    private fun readExact(input: InputStream, buf: ByteArray, deadlineMs: Long) {
        var off = 0
        while (off < buf.size) {
            if (System.currentTimeMillis() > deadlineMs) throw SocketTimeoutException("read deadline")
            val n = try {
                input.read(buf, off, buf.size - off)
            } catch (_: SocketTimeoutException) {
                continue    // 只是还没数据，不是帧结束
            }
            if (n < 0) throw EOFException("EOF at $off/${buf.size}")
            off += n
        }
    }

    private fun checksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) sum += b.toInt() and 0xFF
        return sum
    }

    private fun le32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun putLe32(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
        b[off + 2] = ((v shr 16) and 0xFF).toByte()
        b[off + 3] = ((v shr 24) and 0xFF).toByte()
    }
}
