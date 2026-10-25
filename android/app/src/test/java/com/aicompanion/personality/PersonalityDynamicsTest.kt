package com.aicompanion.personality

import com.aicompanion.personality.PersonalityDynamics.TraitChange
import com.aicompanion.personality.PersonalityDynamics.TraitEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PersonalityDynamicsTest {

    private val now = 1_700_000_000_000L
    private fun ev(dir: Float, agoDays: Int = 1, w: Float = 1f) =
        TraitEvidence("warmth", dir, now - agoDays * 24L * 3600 * 1000, w)

    @Test
    fun `证据不足时特质完全不动`() {
        val after = PersonalityDynamics.evolveTrait(0.5f, "warmth", listOf(ev(1f), ev(1f)), emptyList(), now)
        assertEquals(0.5f, after, 1e-6f)
    }

    @Test
    fun `证据达标后只走一小步且不超过单步上限`() {
        val after = PersonalityDynamics.evolveTrait(
            0.5f, "warmth", listOf(ev(1f), ev(1f), ev(1f)), emptyList(), now,
        )
        assertTrue("应当上升", after > 0.5f)
        assertTrue("单步不得超过上限", after - 0.5f <= PersonalityDynamics.MAX_STEP + 1e-6f)
    }

    @Test
    fun `反向证据抵消，人格回弹而不是单向漂移`() {
        val after = PersonalityDynamics.evolveTrait(
            0.5f, "warmth", listOf(ev(1f), ev(1f), ev(-1f)), emptyList(), now,
        )
        assertEquals("净证据 1 < 门槛 3，不应变化", 0.5f, after, 1e-6f)

        val after2 = PersonalityDynamics.evolveTrait(
            0.6f, "warmth", listOf(ev(1f), ev(1f), ev(1f), ev(-1f), ev(-1f), ev(-1f)), emptyList(), now,
        )
        assertEquals("正负完全抵消", 0.6f, after2, 1e-6f)
    }

    @Test
    fun `30 天累计变化被限幅`() {
        val history = listOf(TraitChange("warmth", 0.50f, 0.58f, now - 5L * 24 * 3600 * 1000))
        val after = PersonalityDynamics.evolveTrait(
            0.58f, "warmth", listOf(ev(1f), ev(1f), ev(1f)), history, now,
        )
        assertEquals("30 天额度已用尽，不能再涨", 0.58f, after, 1e-6f)
    }

    @Test
    fun `超出窗口的旧变化不再占用额度`() {
        val history = listOf(TraitChange("warmth", 0.50f, 0.58f, now - 40L * 24 * 3600 * 1000))
        val after = PersonalityDynamics.evolveTrait(
            0.58f, "warmth", listOf(ev(1f), ev(1f), ev(1f)), history, now,
        )
        assertTrue("40 天前的变化已过期，允许继续演化", after > 0.58f)
    }

    @Test
    fun `情绪变化远快于特质`() {
        val moodDelta = abs(
            PersonalityDynamics.evolveMood(Mood(0f, 0.5f, 0.5f), Mood(1f, 0.5f, 0.5f), 0.5f).valence - 0f
        )
        val traitDelta = abs(
            PersonalityDynamics.evolveTrait(0f, "warmth", listOf(ev(1f), ev(1f), ev(1f)), emptyList(), now) - 0f
        )
        assertTrue("情绪应立刻靠拢（0.5），特质只能微动", moodDelta > traitDelta * 10)
    }

    @Test
    fun `关系随互动累积推进阶段`() {
        var r = Relationship(0.2f, 0.1f, 0.1f, RelationStage.STRANGER)
        val target = Relationship(0.8f, 0.6f, 0.6f)
        repeat(10) { r = PersonalityDynamics.evolveRelationship(r, target) }
        assertTrue("信任应持续上升", r.trust > 0.6f)
        assertTrue("阶段应推进到亲近以上", r.stage == RelationStage.CLOSE || r.stage == RelationStage.DEPENDENT)
    }

    @Test
    fun `关系阶段由三项均值决定`() {
        assertEquals(RelationStage.STRANGER, PersonalityDynamics.stageOf(0.1f, 0.1f, 0.1f))
        assertEquals(RelationStage.CLOSE, PersonalityDynamics.stageOf(0.5f, 0.5f, 0.5f))
        // 单项突刺不会直接跨到依赖
        assertEquals(RelationStage.FAMILIAR, PersonalityDynamics.stageOf(0.9f, 0.1f, 0.1f))
    }

    @Test
    fun `整份特质演化只改有证据的字段`() {
        val t = Traits(warmth = 0.5f, humor = 0.5f)
        val (after, changes) = PersonalityDynamics.evolveTraits(
            t, listOf(ev(1f), ev(1f), ev(1f)), emptyList(), now,
        )
        assertTrue(after.warmth > 0.5f)
        assertEquals("没有证据的字段不该变", 0.5f, after.humor, 1e-6f)
        assertTrue(changes.none { it.field == "humor" })
    }
}
