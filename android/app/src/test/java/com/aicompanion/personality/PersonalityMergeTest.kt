package com.aicompanion.personality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PersonalityMergeTest {

    private fun induced(t: Traits, conf: Float = 0.5f) = PersonalityProfile(
        traits = t,
        meta = FieldMeta(Source.INDUCED, conf),
    )

    @Test
    fun `置信度高的来源获胜`() {
        val base = induced(Traits(warmth = 0.3f), conf = 0.5f)
        val incoming = PersonalityProfile(
            traits = Traits(warmth = 0.9f),
            meta = FieldMeta(Source.AUTHOR, 1.0f),
        )
        val merged = PersonalityMerge.merge(base, incoming)
        assertTrue(abs(merged.traits.warmth - 0.9f) < 1e-4)
        assertEquals(Source.AUTHOR, merged.meta.source)
    }

    @Test
    fun `置信度低的不覆盖`() {
        val base = PersonalityProfile(
            traits = Traits(warmth = 0.8f),
            meta = FieldMeta(Source.AUTHOR, 1.0f),
        )
        val incoming = induced(Traits(warmth = 0.2f), conf = 0.4f)
        val merged = PersonalityMerge.merge(base, incoming)
        assertTrue(abs(merged.traits.warmth - 0.8f) < 1e-4)
    }

    @Test
    fun `锁定字段永不覆盖`() {
        val base = PersonalityMerge.lock(
            PersonalityProfile(
                traits = Traits(warmth = 0.3f),
                meta = FieldMeta(Source.INDUCED, 0.5f),
            ),
            "traits.warmth",
        )
        val incoming = PersonalityProfile(
            traits = Traits(warmth = 0.95f),
            meta = FieldMeta(Source.AUTHOR, 1.0f),
        )
        val merged = PersonalityMerge.merge(base, incoming)
        assertTrue("锁定字段必须保持原值", abs(merged.traits.warmth - 0.3f) < 1e-4)
        // 其他未锁定字段仍可被作者覆盖
        assertTrue(abs(merged.traits.openness - incoming.traits.openness) < 1e-4)
    }

    @Test
    fun `底线与既成事实只有作者能改`() {
        val base = PersonalityProfile(
            narrative = Narrative(taboos = listOf("不提隐私"), originAnchors = listOf("来自北方")),
            meta = FieldMeta(Source.INDUCED, 0.5f),
        )
        val autoIncoming = PersonalityProfile(
            narrative = Narrative(taboos = listOf("说谎"), originAnchors = listOf("来自南方")),
            meta = FieldMeta(Source.INDUCED, 0.95f),
        )
        val m1 = PersonalityMerge.merge(base, autoIncoming)
        assertEquals("非作者来源不能改底线", listOf("不提隐私"), m1.narrative.taboos)
        assertEquals(listOf("来自北方"), m1.narrative.originAnchors)

        val authorIncoming = PersonalityProfile(
            narrative = Narrative(taboos = listOf("说谎"), originAnchors = listOf("来自南方")),
            meta = FieldMeta(Source.AUTHOR, 0.9f),
        )
        val m2 = PersonalityMerge.merge(base, authorIncoming)
        assertEquals(listOf("说谎"), m2.narrative.taboos)
    }

    @Test
    fun `同情境签名合并时证据数累加`() {
        val base = PersonalityProfile(
            signatures = listOf(Signature(Situation.PRAISE, "嘴上嫌弃", 0.5f, 1)),
            meta = FieldMeta(Source.BOOTSTRAP, 0.5f),
        )
        val incoming = PersonalityProfile(
            signatures = listOf(Signature(Situation.PRAISE, "先嘴硬再偷偷高兴", 0.85f, 2)),
            meta = FieldMeta(Source.INDUCED, 0.6f),
        )
        val merged = PersonalityMerge.merge(base, incoming)
        assertEquals(1, merged.signatures.size)
        val s = merged.signatures.first()
        assertEquals("先嘴硬再偷偷高兴", s.response)
        assertEquals(3, s.evidence)
    }

    @Test
    fun `不同情境签名都保留`() {
        val base = PersonalityProfile(
            signatures = listOf(Signature(Situation.PRAISE, "a", 0.5f)),
            meta = FieldMeta(Source.BOOTSTRAP, 0.5f),
        )
        val incoming = PersonalityProfile(
            signatures = listOf(Signature(Situation.CRITICISM, "b", 0.6f)),
            meta = FieldMeta(Source.INDUCED, 0.6f),
        )
        val merged = PersonalityMerge.merge(base, incoming)
        assertEquals(2, merged.signatures.size)
    }

    @Test
    fun `合并后版本号递增`() {
        val a = PersonalityProfile(version = 3)
        val b = PersonalityProfile(version = 5)
        assertEquals(6, PersonalityMerge.merge(a, b).version)
    }
}
