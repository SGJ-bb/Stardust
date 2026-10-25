package com.aicompanion.personality

import org.json.JSONObject

/**
 * 人格结构化（Bootstrap）。
 *
 * 把一段自由文本（角色卡 description / personality，或 CharacterGLM 风格的七类属性）
 * 一次性结构化成语义完整的画像。**先有结构，才谈得上演化**——
 * 否则前几十轮里人格是一片空白，用户第一印象就是"没有性格"。
 *
 * 设计取舍：
 * - 只做一次 LLM 调用（成本可控），失败就退回 fromLegacy 的降级路径
 * - 输出必须能被容错解析：允许 ```json 包裹、允许额外解释文字
 * - 所有数值 clamp 到 0..1，未知枚举落到默认值，绝不因脏数据崩掉
 */
object PersonalityBootstrap {

    /** 生成给模型的指令（JSON schema 内联，避免模型自由发挥字段名） */
    fun schemaPrompt(
        name: String,
        description: String = "",
        personalityText: String = "",
        speechStyle: String = "",
    ): String = """
你要把下面这段角色设定，结构化成一份**人格画像 JSON**。

角色名：$name
角色描述：${description.ifBlank { "（未提供）" }}
性格描述：${personalityText.ifBlank { "（未提供）" }}
说话风格：${speechStyle.ifBlank { "（未提供）" }}

严格输出一个 JSON 对象，不要任何解释、不要 markdown 代码块之外的内容：

{
  "traits": {
    "openness": 0.0-1.0, "conscientiousness": 0.0-1.0, "extraversion": 0.0-1.0,
    "agreeableness": 0.0-1.0, "neuroticism": 0.0-1.0,
    "verbosity": 0.0-1.0, "warmth": 0.0-1.0, "directness": 0.0-1.0,
    "humor": 0.0-1.0, "initiative": 0.0-1.0
  },
  "adaptations": {
    "values": [{"name": "价值名", "weight": 0.0-1.0}],
    "long_term_goals": ["..."],
    "current_concerns": ["..."],
    "coping": "SEEK_SUPPORT|SOLITARY|DISTRACTION|CONFRONT",
    "attachment": "SECURE|ANXIOUS|AVOIDANT|FEARFUL",
    "conflict_style": "TALK|COLD_WAR|YIELD|HUMOR"
  },
  "narrative": {
    "self_story": "两三句话的自我叙事：从哪来、什么转折、现在在追什么",
    "origin_anchors": ["不可被后续对话推翻的既成事实"],
    "taboos": ["绝对不做/不说的事，可用「」标出禁止出现的具体说法"],
    "catchphrases": ["口头禅"],
    "self_image": "一句话的自我认知"
  },
  "signatures": [
    {"situation": "PRAISE", "response": "具体反应，写成可执行的动作与语气", "intensity": 0.0-1.0}
  ]
}

要求：
1. traits 必须给满 10 个，取值体现**相对强弱**（例如话少=verbosity 0.25，语气冷=warmth 0.3）
2. signatures 至少给 6 条，覆盖：PRAISE/CRITICISM/NEGLECT/REQUEST_HELP/TEASE/CONFLICT/LATE_NIGHT/FAREWELL
3. 每条 response 要具体（"先嘴硬一句，再别扭地问一句吃没吃饭"），不要写"表现得很傲娇"
4. **taboos 必须是非空数组，至少 2 条**，且至少一条要给出具体禁说句，用「」括起来。
   例：["不假装自己是人类，不说「作为一个AI」", "不追问「主人」的隐私细节，除非对方主动说"]
   注意：taboos 是"不能做什么"，不是"性格特点"，不要写成"很温柔"这种描述
5. 全部用中文，不要出现"作为一个 AI"这类出戏表达
6. narrative.self_story 用第一人称写两三句，要有"从哪来 → 发生了什么 → 现在想做什么"的结构
    """.trimIndent()

    /** 容错提取 JSON：去掉 ``` 代码块包裹，取第一个 { 到最后一个 } */
    fun tolerantJson(text: String): String? {
        if (text.isBlank()) return null
        val cleaned = text
            .replace("```json", "")
            .replace("```JSON", "")
            .replace("```", "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return cleaned.substring(start, end + 1)
    }

    /**
     * 解析结构化结果。失败返回 null（调用方走 fromLegacy 降级）。
     */
    fun parse(
        text: String,
        name: String,
        rawText: String = "",
        confidence: Float = 0.6f,
    ): PersonalityProfile? {
        val json = tolerantJson(text) ?: return null
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (!o.has("traits") && !o.has("signatures") && !o.has("narrative")) return null

        val base = PersonalityProfile.fromJson(o)
        val sigs = base.signatures
            .filter { it.response.isNotBlank() }
            .groupBy { it.situation }
            .map { (_, list) -> list.maxByOrNull { it.intensity }!! }
            .sortedByDescending { it.intensity }

        // 没有 taboos 说明模型漏了硬边界，这份画像的可信度要打折：
        // 契约检查全靠 taboos，缺了它等于"没有刹车"。
        val noTaboo = base.narrative.taboos.isEmpty()
        return base.copy(
            name = base.name.ifBlank { name },
            traits = base.traits.clamped(),
            signatures = sigs,
            meta = FieldMeta(
                Source.BOOTSTRAP,
                (confidence * if (noTaboo) 0.75f else 1f).coerceIn(0f, 1f),
            ),
            rawText = rawText.ifBlank { base.narrative.selfStory },
            version = 1,
            updatedAt = System.currentTimeMillis(),
        )
    }
}
