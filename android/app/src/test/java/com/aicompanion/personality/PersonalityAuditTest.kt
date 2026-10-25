package com.aicompanion.personality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PersonalityAuditTest {

    private val p = PersonalityProfile(
        traits = Traits(verbosity = 0.2f),
        narrative = Narrative(
            taboos = listOf("不能说「我最讨厌你了」", "不提主人隐私"),
            catchphrases = listOf("哼"),
        ),
    )

    @Test
    fun `能从底线里抽出可匹配的禁说片段`() {
        val f = PersonalityAudit.extractForbidden(p)
        assertEquals(listOf("我最讨厌你了"), f)
    }

    @Test
    fun `踩到底线判违规`() {
        val r = PersonalityAudit.checkContract(p, "哼，我最讨厌你了。")
        assertTrue(r.violations.any { it.kind == "taboo" })
        assertTrue(!r.passed)
    }

    @Test
    fun `出戏用语判违规`() {
        val r = PersonalityAudit.checkContract(p, "作为一个 AI 语言模型，我不能这么说。")
        assertTrue(r.violations.any { it.kind == "out_of_character" })
    }

    @Test
    fun `干净输出通过`() {
        val r = PersonalityAudit.checkContract(p, "哼，随便你。")
        assertTrue(r.passed)
    }

    @Test
    fun `话少设定但输出过长只报警告不拦`() {
        val r = PersonalityAudit.checkContract(p, "哼。".repeat(200))
        assertTrue("不该判违规", r.passed)
        assertTrue(r.warnings.any { it.contains("话少") })
    }

    @Test
    fun `口头禅缺失只提示`() {
        val r = PersonalityAudit.checkContract(p, "随便你。")
        assertTrue(r.warnings.any { it.contains("口头禅") })
    }

    @Test
    fun `漂移分是五维平均绝对差`() {
        val traits = Traits(openness = 0.5f, conscientiousness = 0.5f, extraversion = 0.5f, agreeableness = 0.5f, neuroticism = 0.5f)
        val measured = mapOf(
            "openness" to 0.9f,
            "conscientiousness" to 0.5f,
            "extraversion" to 0.5f,
            "agreeableness" to 0.5f,
            "neuroticism" to 0.5f,
        )
        val d = PersonalityAudit.driftScore(measured, traits)
        assertTrue("期望 0.4/5=0.08，实际 $d", abs(d - 0.08f) < 1e-4)
    }

    @Test
    fun `漂移判定阈值`() {
        assertEquals("稳", PersonalityAudit.driftVerdict(0.05f))
        assertEquals("略有起伏", PersonalityAudit.driftVerdict(0.2f))
        assertEquals("偏离明显，需要重锚", PersonalityAudit.driftVerdict(0.4f))
    }

    @Test
    fun `IPIP 回答解析容错`() {
        val m = PersonalityAudit.parseIpip(
            """
            openness: 5
            openness：3
            extraversion=1
            无法识别的行
            """.trimIndent()
        )
        assertTrue(abs((m["openness"] ?: 0f) - 0.75f) < 1e-4)  // (1.0 + 0.5)/2
        assertEquals(0f, m["extraversion"] ?: -1f, 1e-4f)
    }

    @Test
    fun `没有测量数据时漂移为零`() {
        assertEquals(0f, PersonalityAudit.driftScore(emptyMap(), Traits()), 1e-6f)
    }
}
