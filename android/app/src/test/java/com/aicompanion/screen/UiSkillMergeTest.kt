package com.aicompanion.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住"同一页反复学习不膨胀"这条行为。
 *
 * 真机上踩过的坑：拿不到 Activity 名时页名靠猜，同一页两次学出两个名字，
 * 于是技能里堆出两节几乎一样的内容，坐标还互相矛盾。
 */
class UiSkillMergeTest {

    private fun page(key: String, rows: List<String>) = buildString {
        appendLine("## 页面：$key")
        appendLine()
        appendLine("### 可见文本（带坐标，可按文字定位后点击）")
        appendLine("| 文字 | 坐标 |")
        appendLine("|---|---|")
        rows.forEach { appendLine("| $it | (100, 200) |") }
    }

    @Test
    fun `页名相同时替换不追加`() {
        val existing = "# 设置\n\n" + page("设置", listOf("WLAN", "蓝牙"))
        val merged = UiSkillBuilder.mergePage(existing, page("设置", listOf("WLAN", "蓝牙", "移动网络")), "设置")
        assertEquals(1, Regex("## 页面：").findAll(merged).count())
        assertTrue(merged.contains("移动网络"))
    }

    @Test
    fun `页名不同但内容重合度高时也替换`() {
        val existing = "# 设置\n\n" + page("搜索系统设置项", listOf("WLAN", "蓝牙", "移动网络", "VPN"))
        val merged = UiSkillBuilder.mergePage(
            existing,
            page("设置", listOf("WLAN", "蓝牙", "移动网络", "VPN")),
            "设置",
        )
        assertEquals("同一页不该出现两节", 1, Regex("## 页面：").findAll(merged).count())
        assertTrue(merged.contains("## 页面：设置"))
        assertTrue("旧页名应被替换掉", !merged.contains("## 页面：搜索系统设置项"))
    }

    @Test
    fun `内容不同的页面会各自保留`() {
        val existing = "# 设置\n\n" + page("设置", listOf("WLAN", "蓝牙", "移动网络", "VPN"))
        val merged = UiSkillBuilder.mergePage(
            existing,
            page("WLAN", listOf("CCN-2.4G", "CCN-5G", "添加网络", "高级设置")),
            "WLAN",
        )
        assertEquals(2, Regex("## 页面：").findAll(merged).count())
    }

    @Test
    fun `反复学习同一页数量不变`() {
        var doc = "# 设置"
        repeat(5) {
            doc = UiSkillBuilder.mergePage(doc, page("设置", listOf("WLAN", "蓝牙")), "设置")
        }
        assertEquals(1, Regex("## 页面：").findAll(doc).count())
    }
}
