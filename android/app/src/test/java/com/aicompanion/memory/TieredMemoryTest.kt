package com.aicompanion.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分层记忆的单元测试（JVM，不需要设备/AI/网络）。
 *
 * 这些断言钉住的是"看起来对、实际容易错"的部分：
 * 时间窗算错一天、遗忘曲线符号反了、分层注入把归档条目又塞回去——
 * 这些在界面上完全看不出来，只会表现为「AI 一周后还记得太清楚」或「刚说过就忘」。
 */
class TieredMemoryTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 3600 * 1000

    private fun e(
        content: String,
        daysAgo: Double,
        tier: MemoryTier = MemoryTier.EPISODIC,
        importance: Float = 0.5f,
        recall: Int = 0,
        archived: Boolean = false,
    ) = MemoryEntry(
        id = content.take(6),
        content = content,
        timestamp = now - (daysAgo * day).toLong(),
        tier = tier,
        importance = importance,
        recallCount = recall,
        archived = archived,
    )

    @Test
    fun `新记忆强度接近满分`() {
        val s = TieredMemory.strengthOf(e("刚说的话", 0.0), now)
        assertTrue("刚发生的记忆应该是满强度，实际 $s", s > 0.9f)
    }

    @Test
    fun `八天前的小事强度明显下降`() {
        val s = TieredMemory.strengthOf(e("八天前的小事", 8.0, importance = 0.3f), now)
        assertTrue("8 天后应明显减弱，实际 $s", s < 0.45f)
    }

    @Test
    fun `反复回忆会显著延长保持时间`() {
        val plain = TieredMemory.strengthOf(e("没再提过", 8.0, importance = 0.3f), now)
        val recalled = TieredMemory.strengthOf(e("常被提起", 8.0, importance = 0.3f, recall = 5), now)
        assertTrue(
            "回忆 5 次的强度应显著高于没回忆过的（$plain vs $recalled）",
            recalled > plain * 1.5f
        )
    }

    @Test
    fun `重要性高的事忘得更慢`() {
        val low = TieredMemory.strengthOf(e("无关紧要", 6.0, importance = 0.1f), now)
        val high = TieredMemory.strengthOf(e("很重要", 6.0, importance = 1.0f), now)
        assertTrue("高重要性应衰减更慢（$low vs $high）", high > low)
    }

    @Test
    fun `长期印象不参与遗忘衰减`() {
        val s = TieredMemory.strengthOf(
            e("他怕打针", 400.0, tier = MemoryTier.SEMANTIC, importance = 0.9f), now
        )
        assertTrue("语义层不该被时间冲淡，实际 $s", s >= 0.85f)
    }

    @Test
    fun `七天是情景记忆的巩固分界`() {
        val list = listOf(
            e("六天前的事", 6.0),
            e("八天前的事", 8.0),
            e("已归档的事", 10.0, archived = true),
        )
        val due = TieredMemory.pendingEpisodic(list, now)
        assertEquals("只应命中 8 天前那条（归档的不再重复巩固）", 1, due.size)
        assertEquals("八天前的事", due[0].content)
    }

    @Test
    fun `三十天是要点抽象为印象的分界`() {
        val list = listOf(
            e("29天前的要点", 29.0, tier = MemoryTier.CONSOLIDATED),
            e("32天前的要点", 32.0, tier = MemoryTier.CONSOLIDATED),
        )
        val due = TieredMemory.pendingConsolidated(list, now)
        assertEquals(1, due.size)
        assertEquals("32天前的要点", due[0].content)
    }

    @Test
    fun `三层注入各就各位且排除归档条目`() {
        val list = listOf(
            e("他喜欢喝冰美式", 200.0, tier = MemoryTier.SEMANTIC, importance = 0.9f),
            e("上周约好周末看电影", 10.0, tier = MemoryTier.CONSOLIDATED),
            e("今天他加班到十点", 0.5),
            e("这段应该被隐藏", 2.0, archived = true),
        )
        val block = TieredMemory.buildTieredBlock(list, maxChars = 1200, now = now)

        assertTrue("应有长期印象段", block.contains("长期印象") && block.contains("冰美式"))
        assertTrue("应有点要段", block.contains("过去一段时间的要点") && block.contains("看电影"))
        assertTrue("应有近期段", block.contains("最近发生的事") && block.contains("加班到十点"))
        assertFalse("归档条目不该出现在上下文里", block.contains("这段应该被隐藏"))
    }

    @Test
    fun `超出预算时不会无限膨胀`() {
        val list = (1..30).map {
            e("第${it}条比较长的记忆内容，用来检验预算控制是否真的生效", it * 0.2)
        }
        val block = TieredMemory.buildTieredBlock(list, maxChars = 200, now = now)
        assertTrue("预算 200 时不该产出 ${block.length} 字", block.length < 700)
    }

    @Test
    fun `回忆一次后强度回升且计数加一`() {
        val old = e("被提到的旧事", 5.0, importance = 0.3f)
        val before = TieredMemory.strengthOf(old, now)
        val recalled = TieredMemory.onRecalled(old, now)
        val after = TieredMemory.strengthOf(recalled, now)

        assertTrue("回忆后应变强（$before → $after）", after > before)
        assertEquals("回忆计数应加一", 1, recalled.recallCount)
    }

    @Test
    fun `空记忆不产生注入块`() {
        assertTrue(TieredMemory.buildTieredBlock(emptyList(), 500, now).isBlank())
    }

    @Test
    fun `旧数据缺少分层字段时默认落到情景层`() {
        // 模拟升级前的 JSON：没有 tier / strength 等字段
        val legacy = org.json.JSONObject().apply {
            put("id", "legacy1")
            put("content", "升级前的旧记忆")
            put("timestamp", now - 3 * day)
            put("importance", 0.7)
        }
        val entry = MemoryEntry.fromJson(legacy)
        assertEquals(MemoryTier.EPISODIC, entry.tier)
        assertEquals(1.0f, entry.strength, 0.001f)
        assertFalse(entry.archived)
        assertTrue("旧数据不该因为缺字段就丢内容", entry.content.isNotBlank())
    }

    @Test
    fun `分层字段能完整往返序列化`() {
        val original = MemoryEntry(
            id = "x1",
            content = "他喜欢在晚上写代码",
            timestamp = now - 40 * day,
            tier = MemoryTier.SEMANTIC,
            strength = 0.92f,
            recallCount = 3,
            lastRecallAt = now - day,
            archived = false,
            derivedFrom = listOf("a", "b"),
            importance = 0.9f,
        )
        val back = MemoryEntry.fromJson(original.toJson())
        assertEquals(MemoryTier.SEMANTIC, back.tier)
        assertEquals(3, back.recallCount)
        assertEquals(listOf("a", "b"), back.derivedFrom)
        assertEquals(0.92f, back.strength, 0.01f)
    }
}
