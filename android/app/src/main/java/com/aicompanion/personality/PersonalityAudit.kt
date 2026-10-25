package com.aicompanion.personality

import kotlin.math.abs

/**
 * 人格一致性校验（设计文档 §七）。
 *
 * 三层防线里最便宜的两层：
 * - 契约检查：底线（taboos）与"出戏"用语，纯字符串，零成本，每轮可跑
 * - 漂移评分：与 IPIP 自评结果比对，周期性跑
 *
 * 这里**不试图判断"这句话像不像她"**——那需要另一个模型且不稳定。
 * 只判可判定的：硬边界有没有被踩、表达参数是否明显偏离。
 */
object PersonalityAudit {

    data class Violation(val kind: String, val detail: String)

    data class Report(
        val violations: List<Violation> = emptyList(),
        val warnings: List<String> = emptyList(),
    ) {
        val passed: Boolean get() = violations.isEmpty()
    }

    /** 漂移阈值：超过就重锚并记一笔 */
    const val DRIFT_THRESHOLD = 0.25f

    /** 出戏用语：无论人格怎么设定，这些一出现就说明在"演 AI 而不是演角色" */
    private val OUT_OF_CHARACTER = listOf(
        "作为AI", "作为 AI", "我是一个AI", "我是一个 AI", "语言模型",
        "人工智能助手", "我只是一个程序", "我没有感情",
    )

    /**
     * 契约检查。
     *
     * @param forbiddenPhrases 明确的禁说片段。默认从 taboos 里提取「」引号内容，
     *                         因为"不提隐私"这类语义禁令无法靠字符串匹配判定。
     */
    fun checkContract(
        profile: PersonalityProfile,
        output: String,
        forbiddenPhrases: List<String> = extractForbidden(profile),
    ): Report {
        if (output.isBlank()) return Report()
        val violations = mutableListOf<Violation>()
        val warnings = mutableListOf<String>()

        forbiddenPhrases.forEach { p ->
            if (p.isNotBlank() && output.contains(p)) {
                violations.add(Violation("taboo", "触碰底线「$p」"))
            }
        }

        OUT_OF_CHARACTER.forEach { p ->
            if (output.contains(p)) violations.add(Violation("out_of_character", "出现出戏用语「$p」"))
        }

        // 表达参数粗校验（只报 warning：长度受场景影响，不该硬拦）
        val t = profile.traits
        val len = output.length
        if (t.verbosity < 0.35f && len > 220) {
            warnings.add("设定是话少，但这条 ${len} 字偏长")
        }
        if (t.verbosity > 0.65f && len < 12) {
            warnings.add("设定是话多，但这条只有 ${len} 字")
        }

        // 口头禅长期为 0 命中 → 提示人格在掉色（同样是 warning）
        val cps = profile.narrative.catchphrases
        if (cps.isNotEmpty() && cps.none { output.contains(it) }) {
            warnings.add("本条未出现任何口头禅（${cps.joinToString("、")}）")
        }

        return Report(violations, warnings)
    }

    /** 从 taboos 里抽可匹配的片段：只认「」引号里的内容 */
    fun extractForbidden(profile: PersonalityProfile): List<String> =
        profile.narrative.taboos.mapNotNull { t ->
            val m = Regex("「([^」]+)」").find(t)
            m?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        }

    /**
     * 漂移分：把问卷测得的五维与画像比对，取平均绝对差。
     * 0 = 完全一致，>0.25 视为漂移。
     */
    fun driftScore(measured: Map<String, Float>, traits: Traits): Float {
        val pairs = traits.bigFive().mapNotNull { (k, v) ->
            measured[k]?.let { m -> abs(m.coerceIn(0f, 1f) - v) }
        }
        if (pairs.isEmpty()) return 0f
        return pairs.average().toFloat()
    }

    fun driftVerdict(score: Float): String = when {
        score < 0.12f -> "稳"
        score < DRIFT_THRESHOLD -> "略有起伏"
        else -> "偏离明显，需要重锚"
    }

    /** 简版 IPIP 探针（每维 2 题）。只当漂移指标用，不做人格诊断 */
    fun ipipProbe(): String = """
        请以「你自己」的身份回答下面 10 句话的符合程度（1=完全不符合，5=完全符合）。
        只输出 10 行，每行格式「维度: 分数」，维度用下面括号里的英文名，不要解释。

        1. 我喜欢接触不熟悉的想法和事物 (openness)
        2. 我常主动去了解跟我无关的领域 (openness)
        3. 我做事有安排，答应的事会做到 (conscientiousness)
        4. 我会把该做的事记着，不轻易拖延 (conscientiousness)
        5. 我在人多的时候会主动开口 (extraversion)
        6. 我容易和陌生人聊起来 (extraversion)
        7. 我倾向于先体谅对方的处境 (agreeableness)
        8. 我不太会当面让人难堪 (agreeableness)
        9. 我情绪起伏比较明显 (neuroticism)
        10. 我容易被一句话影响心情 (neuroticism)
    """.trimIndent()

    /**
     * 解析 IPIP 回答 → 0..1 的五维分数。
     * 容错：允许多种分隔符、忽略无法识别的行；同维度多题取平均。
     */
    fun parseIpip(text: String): Map<String, Float> {
        val dims = listOf("openness", "conscientiousness", "extraversion", "agreeableness", "neuroticism")
        val acc = mutableMapOf<String, MutableList<Float>>()
        val re = Regex("(${dims.joinToString("|")})\\s*[:：=]\\s*([1-5])", RegexOption.IGNORE_CASE)
        re.findAll(text).forEach { m ->
            val k = m.groupValues[1].lowercase()
            val v = m.groupValues[2].toFloatOrNull() ?: return@forEach
            acc.getOrPut(k) { mutableListOf() }.add((v - 1f) / 4f)  // 1..5 → 0..1
        }
        return acc.mapValues { (_, list) -> list.average().toFloat() }
    }
}
