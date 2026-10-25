package com.aicompanion.personality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PersonalityProfileTest {

    @Test
    fun `旧数据迁移保留原文并抽取可识别信号`() {
        val p = PersonalityProfile.fromLegacy(
            name = "星尘",
            personalityText = "傲娇毒舌但内心温柔关心主人",
            speechStyle = "说话简短",
        )
        // 原文必须原样保留（结构化失败时的降级路径）
        assertTrue(p.rawText.contains("傲娇毒舌"))
        assertTrue(p.rawText.contains("说话简短"))
        assertTrue(p.narrative.selfStory.isNotBlank())
        // 能抽到的信号应当生效
        assertTrue("毒舌/傲娇 → 直给", p.traits.directness >= 0.7f)
        assertTrue("简短 → 话少", p.traits.verbosity <= 0.3f)
        // 迁移来的置信度必须低，之后才会被归纳/作者覆盖
        assertTrue(p.meta.confidence < 0.5f)
    }

    @Test
    fun `JSON 往返不丢字段`() {
        val src = PersonalityProfile(
            name = "测试",
            traits = Traits(openness = 0.8f, warmth = 0.3f, verbosity = 0.2f),
            adaptations = Adaptations(
                values = listOf(ValueRank("自由", 0.9f), ValueRank("安全", 0.5f)),
                longTermGoals = listOf("考上研"),
                coping = CopingStyle.SOLITARY,
                attachment = AttachmentStyle.AVOIDANT,
            ),
            narrative = Narrative(
                selfStory = "我从很远的地方来。",
                taboos = listOf("不能说「我最讨厌你了」"),
                catchphrases = listOf("哼"),
            ),
            signatures = listOf(
                Signature(Situation.PRAISE, "先嘴硬一句", 0.8f, 3),
                Signature(Situation.NEGLECT, "不追问，但会阴阳一下", 0.6f, 2),
            ),
            relationship = Relationship(0.6f, 0.4f, 0.3f, RelationStage.CLOSE),
        )

        val back = PersonalityProfile.fromJson(src.toJson())

        assertTrue(abs(back.traits.openness - 0.8f) < 1e-4)
        assertTrue(abs(back.traits.warmth - 0.3f) < 1e-4)
        assertEquals(2, back.adaptations.values.size)
        assertEquals(CopingStyle.SOLITARY, back.adaptations.coping)
        assertEquals(AttachmentStyle.AVOIDANT, back.adaptations.attachment)
        assertEquals(1, back.narrative.taboos.size)
        assertEquals(2, back.signatures.size)
        assertEquals(RelationStage.CLOSE, back.relationship.stage)
        assertTrue(abs(back.relationship.trust - 0.6f) < 1e-4)
    }

    @Test
    fun `好感度换算到关系阶段`() {
        assertEquals(RelationStage.STRANGER, Relationship.fromAffection(0).stage)
        assertEquals(RelationStage.FAMILIAR, Relationship.fromAffection(30).stage)
        assertEquals(RelationStage.CLOSE, Relationship.fromAffection(55).stage)
        assertEquals(RelationStage.DEPENDENT, Relationship.fromAffection(85).stage)
    }

    @Test
    fun `异常数值被夹到合法区间`() {
        val t = Traits(openness = Float.NaN, warmth = 5f, verbosity = -3f).clamped()
        assertEquals(0.5f, t.openness, 1e-4f)
        assertEquals(1f, t.warmth, 1e-4f)
        assertEquals(0f, t.verbosity, 1e-4f)
    }

    @Test
    fun `签名按强度取前 N 条`() {
        val p = PersonalityProfile(
            signatures = listOf(
                Signature(Situation.PRAISE, "a", 0.3f),
                Signature(Situation.PRAISE, "b", 0.9f),
                Signature(Situation.PRAISE, "c", 0.6f),
                Signature(Situation.CRITICISM, "d", 0.95f),
            )
        )
        val got = p.signaturesFor(Situation.PRAISE, 2)
        assertEquals(2, got.size)
        assertEquals("b", got[0].response)
        assertEquals("c", got[1].response)
    }

    @Test
    fun `未知枚举值落到默认值而不是崩溃`() {
        val json = org.json.JSONObject(
            """{"adaptations":{"coping":"WHATEVER","attachment":"???"},"signatures":[{"situation":"NOPE","response":"x"}]}"""
        )
        val p = PersonalityProfile.fromJson(json)
        assertEquals(CopingStyle.SEEK_SUPPORT, p.adaptations.coping)
        assertEquals(AttachmentStyle.SECURE, p.adaptations.attachment)
        assertEquals(Situation.ROUTINE, p.signatures.first().situation)
    }
}
