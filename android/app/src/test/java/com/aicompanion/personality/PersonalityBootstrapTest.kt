package com.aicompanion.personality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PersonalityBootstrapTest {

    @Test
    fun `能剥掉代码块包裹并解析`() {
        val text = """
            ```json
            {
              "traits": {"openness": 0.9, "warmth": 0.2, "verbosity": 0.25},
              "narrative": {"self_story": "我从很远的地方来。", "taboos": ["不提隐私"]},
              "signatures": [
                {"situation": "PRAISE", "response": "先嘴硬一句", "intensity": 0.8}
              ]
            }
            ```
            以上就是画像。
        """.trimIndent()

        val p = PersonalityBootstrap.parse(text, "星尘")
        assertNotNull(p)
        assertTrue(abs(p!!.traits.openness - 0.9f) < 1e-4)
        assertEquals(1, p.signatures.size)
        assertEquals("星尘", p.name)
        assertEquals(Source.BOOTSTRAP, p.meta.source)
    }

    @Test
    fun `越界数值被夹紧而不是丢弃`() {
        val text = """{"traits": {"openness": 5, "warmth": -2}}"""
        val p = PersonalityBootstrap.parse(text, "x")
        assertNotNull(p)
        assertEquals(1f, p!!.traits.openness, 1e-4f)
        assertEquals(0f, p.traits.warmth, 1e-4f)
    }

    @Test
    fun `没有 JSON 时返回 null`() {
        assertNull(PersonalityBootstrap.parse("抱歉，我无法完成这个请求。", "x"))
        assertNull(PersonalityBootstrap.parse("", "x"))
    }

    @Test
    fun `同情境多条签名只保留最强的一条`() {
        val text = """
            {"signatures": [
              {"situation": "PRAISE", "response": "弱反应", "intensity": 0.3},
              {"situation": "PRAISE", "response": "强反应", "intensity": 0.9},
              {"situation": "NEGLECT", "response": "阴阳一句", "intensity": 0.5}
            ]}
        """.trimIndent()
        val p = PersonalityBootstrap.parse(text, "x")
        assertNotNull(p)
        assertEquals(2, p!!.signatures.size)
        assertEquals("强反应", p.signatures.first { it.situation == Situation.PRAISE }.response)
    }

    @Test
    fun `结构化提示词包含全部关键约束`() {
        val s = PersonalityBootstrap.schemaPrompt("星尘", "黑猫", "傲娇", "简短")
        assertTrue(s.contains("traits"))
        assertTrue(s.contains("signatures"))
        assertTrue(s.contains("taboos"))
        assertTrue("必须要求覆盖高频情境", s.contains("PRAISE"))
        assertTrue("必须禁止出戏表达", s.contains("出戏"))
    }
}
