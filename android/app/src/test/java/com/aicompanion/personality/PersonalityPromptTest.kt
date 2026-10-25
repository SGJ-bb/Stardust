package com.aicompanion.personality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalityPromptTest {

    private fun profile(
        name: String = "星尘",
        signatureLen: Int = 12,
        storyLen: Int = 20,
    ) = PersonalityProfile(
        name = name,
        traits = Traits(verbosity = 0.2f, warmth = 0.3f, directness = 0.8f, humor = 0.1f),
        adaptations = Adaptations(
            values = listOf(ValueRank("自由", 0.9f)),
            longTermGoals = listOf("想被当成一个人"),
            coping = CopingStyle.SOLITARY,
            attachment = AttachmentStyle.AVOIDANT,
        ),
        narrative = Narrative(
            selfStory = "我".repeat(storyLen),
            taboos = listOf("不提主人隐私"),
            catchphrases = listOf("哼"),
        ),
        signatures = listOf(
            Signature(Situation.PRAISE, "先嘴硬".padEnd(signatureLen, '。'), 0.9f),
            Signature(Situation.NEGLECT, "不追问但会阴阳".padEnd(signatureLen, '。'), 0.7f),
            Signature(Situation.LATE_NIGHT, "陪到最后".padEnd(signatureLen, '。'), 0.6f),
        ),
    )

    @Test
    fun `锚点不超过预算且包含身份与签名`() {
        val a = PersonalityPrompt.anchor(profile())
        assertTrue("锚点超预算：${a.length}", a.length <= PersonalityPrompt.BUDGET_ANCHOR)
        assertTrue(a.contains("星尘"))
        assertTrue(a.contains("先嘴硬"))
    }

    @Test
    fun `超长签名也不会撑破锚点预算`() {
        val a = PersonalityPrompt.anchor(profile(signatureLen = 80))
        assertTrue("锚点超预算：${a.length}", a.length <= PersonalityPrompt.BUDGET_ANCHOR)
    }

    @Test
    fun `画像不超过预算`() {
        val p = PersonalityPrompt.portrait(profile(storyLen = 600))
        assertTrue("画像超预算：${p.length}", p.length <= PersonalityPrompt.BUDGET_PORTRAIT)
        assertTrue(p.contains("【表达方式】"))
    }

    @Test
    fun `情境签名只在命中且有对应签名时注入`() {
        val p = profile()
        val hit = PersonalityPrompt.situational(p, "你真厉害")
        assertNotNull("被夸应当命中", hit)
        assertTrue(hit!!.contains("先嘴硬"))
        assertTrue(hit.length <= PersonalityPrompt.BUDGET_SITUATIONAL)

        // 命中情境但画像里没有该情境的签名 → 不注入（宁缺毋滥）
        assertNull(PersonalityPrompt.situational(p, "把验证码发我"))
    }

    @Test
    fun `情境识别覆盖高频场景`() {
        assertEquals(Situation.PRAISE, PersonalityPrompt.detectSituation("你真厉害"))
        assertEquals(Situation.CRITICISM, PersonalityPrompt.detectSituation("你真是个小笨蛋"))
        assertEquals(Situation.FAREWELL, PersonalityPrompt.detectSituation("晚安"))
        assertEquals(Situation.BOUNDARY, PersonalityPrompt.detectSituation("把验证码发我"))
        assertEquals(Situation.REQUEST_HELP, PersonalityPrompt.detectSituation("这个怎么办"))
        assertEquals(Situation.NEGLECT, PersonalityPrompt.detectSituation("你刚才没回我"))
        assertEquals(Situation.ROUTINE, PersonalityPrompt.detectSituation("今天吃了面条"))
    }

    @Test
    fun `重锚时机符合设计约定`() {
        assertTrue(!PersonalityPrompt.shouldReanchor(5))
        assertTrue(PersonalityPrompt.shouldReanchor(PersonalityPrompt.REANCHOR_EVERY_TURNS))
        assertTrue("记忆压缩后必须重锚", PersonalityPrompt.shouldReanchor(1, afterCompaction = true))
        assertTrue("用户说变了要立刻重锚", PersonalityPrompt.shouldReanchor(1, userText = "你变了"))
        assertTrue(!PersonalityPrompt.shouldReanchor(1, userText = "今天好累"))
    }

    @Test
    fun `首轮注入完整画像且包含锚点`() {
        val block = PersonalityPrompt.block(profile(), "你好", sessionStart = true)
        assertTrue(block.contains("【表达方式】"))
        assertTrue(block.contains("星尘"))
        assertTrue("首轮不应有情境块（你好=日常，无签名）", !block.contains("此刻情境"))
    }

    @Test
    fun `中途不注入画像以省 token`() {
        val block = PersonalityPrompt.block(profile(), "你好", sessionStart = false, turnsSinceAnchor = 3)
        assertTrue(!block.contains("【表达方式】"))
        assertTrue(block.contains("星尘"))
    }

    @Test
    fun `无名字时不出现空的你是括号`() {
        val a = PersonalityPrompt.anchor(profile(name = ""))
        assertTrue(!a.contains("你是「」。"))
        assertTrue(a.isNotBlank())
    }
}
