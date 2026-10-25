package com.aicompanion.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只测「模型返回文本 → 屏幕坐标」这一段纯逻辑。
 * 端到端（真截图 → 真模型）依赖配置与网络，放在真机联调里做。
 */
class VisionGroundingTest {

    private val scale = 0.5f          // 图片缩到一半
    private val maxW = 1080
    private val maxH = 2400

    @Test
    fun parsesPlainArrayAndScalesBack() {
        val json = """[{"text":"微信","x":100,"y":200},{"text":"通讯录","x":300,"y":400}]"""
        val out = VisionGrounding.parseTargets(json, scale, maxW, maxH)
        assertEquals(2, out.size)
        // 图片坐标 100 → 屏幕 200
        assertEquals("微信", out[0].text)
        assertEquals(200, out[0].x)
        assertEquals(400, out[0].y)
        assertEquals(600, out[1].x)
    }

    @Test
    fun parsesArrayWrappedInProse() {
        val text = "好的，识别结果如下：\n```json\n[{\"text\":\"返回\",\"x\":50,\"y\":60}]\n```\n以上就是全部。"
        val out = VisionGrounding.parseTargets(text, 1f, maxW, maxH)
        assertEquals(1, out.size)
        assertEquals("返回", out[0].text)
        assertEquals(50, out[0].x)
    }

    @Test
    fun acceptsLabelFieldAsFallback() {
        val json = """[{"label":"搜索","x":10,"y":20}]"""
        val out = VisionGrounding.parseTargets(json, 1f, maxW, maxH)
        assertEquals(1, out.size)
        assertEquals("搜索", out[0].text)
    }

    @Test
    fun skipsEntriesWithoutCoordinates() {
        val json = """[{"text":"有坐标","x":10,"y":10},{"text":"没坐标"},{"x":5,"y":5}]"""
        val out = VisionGrounding.parseTargets(json, 1f, maxW, maxH)
        // "没坐标" 被丢弃；"只有坐标没文字" 保留并给兜底名
        assertEquals(2, out.size)
        assertEquals("有坐标", out[0].text)
        assertEquals("图标", out[1].text)
        assertEquals(5, out[1].x)
    }

    @Test
    fun clampsOutOfRangeCoordinates() {
        val json = """[{"text":"边界","x":99999,"y":99999}]"""
        val out = VisionGrounding.parseTargets(json, 1f, maxW, maxH)
        assertEquals(1, out.size)
        assertEquals(maxW - 1, out[0].x)
        assertEquals(maxH - 1, out[0].y)
    }

    @Test
    fun returnsEmptyWhenNoArray() {
        assertTrue(VisionGrounding.parseTargets("抱歉我无法识别", 1f, maxW, maxH).isEmpty())
        assertTrue(VisionGrounding.parseTargets("", 1f, maxW, maxH).isEmpty())
    }
}
