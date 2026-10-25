package com.aicompanion.prompt

import com.aicompanion.personality.Narrative
import com.aicompanion.personality.PersonalityProfile
import com.aicompanion.personality.Signature
import com.aicompanion.personality.Situation
import com.aicompanion.personality.Traits
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证"接入"这一步：结构化画像必须真正替换掉旧的一句话性格，
 * 同时没有画像时必须回退，旧角色卡不受影响。
 */
class PersonalityInjectionTest {

    private fun identity(profile: PersonalityProfile? = null) = IdentityBlock(
        name = "星尘",
        personality = "傲娇毒舌",              // 旧字段仍然存在
        speechStyle = "说话简短",
        customPrompt = "",
        userNickname = "主人",
        profile = profile,
    )

    @Test
    fun `无画像时走旧的自由文本路径`() {
        val s = PromptBuilder.buildPersonaBase(identity(null))
        assertTrue(s.contains("性格傲娇毒舌"))
        assertTrue(s.contains("说话简短"))
    }

    @Test
    fun `空画像也回退，避免注入一堆默认值`() {
        val empty = PersonalityProfile()   // hasContent() == false
        val s = PromptBuilder.buildPersonaBase(identity(empty))
        assertTrue("空画像不该走结构化路径", s.contains("性格傲娇毒舌"))
    }

    @Test
    fun `有画像时基础注入是锚点`() {
        val p = PersonalityProfile(
            name = "星尘",
            traits = Traits(verbosity = 0.2f, warmth = 0.3f),
            narrative = Narrative(selfStory = "我是一只黑猫。"),
            signatures = listOf(Signature(Situation.PRAISE, "先嘴硬一句", 0.8f)),
        )
        val s = PromptBuilder.buildPersonaBase(identity(p))
        assertTrue("应包含身份", s.contains("你是「星尘」"))
        assertTrue("应包含表达参数", s.contains("表达："))
        assertTrue("应包含行为签名", s.contains("先嘴硬一句"))
        assertTrue("不再出现旧的性格串", !s.contains("性格傲娇毒舌"))
        assertTrue("锚点要短", s.length < 400)
    }

    @Test
    fun `完整注入包含画像三层`() {
        val p = PersonalityProfile(
            name = "星尘",
            traits = Traits(verbosity = 0.2f),
            narrative = Narrative(
                selfStory = "我是一只黑猫。",
                taboos = listOf("不提主人隐私"),
                catchphrases = listOf("哼"),
            ),
            signatures = listOf(Signature(Situation.NEGLECT, "不追问，但会阴阳一下", 0.7f)),
        )
        val s = PromptBuilder.buildPersonaFull(identity(p))
        assertTrue(s.contains("【表达方式】"))
        assertTrue(s.contains("【我是谁】"))
        assertTrue(s.contains("【绝对不做】"))
        assertTrue(s.contains("【口头禅】"))
        // 用户信息不能被吞掉
        assertTrue(s.contains("主人"))
    }
}
