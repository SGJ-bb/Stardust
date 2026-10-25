package com.aicompanion.personality

import org.json.JSONArray
import org.json.JSONObject

/**
 * 人格系统数据模型（P0）
 *
 * 理论依据见 docs/PERSONALITY_SYSTEM.md：
 * - McAdams 三层人格：倾向性特质 / 特征性适应 / 叙事身份
 * - CAPS 行为签名：人格的一致性体现为稳定的 if-then 模式，而不是"处处一样"
 * - CharacterGLM：属性与行为分离，表达参数独立于形容词
 */

/** 字段来源。决定合并时的优先级：AUTHOR > INDUCED > BOOTSTRAP */
enum class Source { AUTHOR, BOOTSTRAP, INDUCED }

/** 行为签名里的情境枚举（首版 12 个，覆盖陪伴类对话高频场景） */
enum class Situation {
    PRAISE,        // 被夸
    CRITICISM,     // 被批评/被怼
    NEGLECT,       // 被冷落
    REQUEST_HELP,  // 求助
    TEASE,         // 撒娇打趣
    CONFLICT,      // 起争执
    LATE_NIGHT,    // 深夜
    FAREWELL,      // 告别
    BOUNDARY,      // 越界请求
    GOOD_NEWS,     // 好消息
    BAD_NEWS,      // 坏消息
    ROUTINE;       // 日常

    companion object {
        fun of(s: String?): Situation =
            entries.firstOrNull { it.name.equals(s?.trim(), ignoreCase = true) } ?: ROUTINE
    }
}

enum class CopingStyle { SEEK_SUPPORT, SOLITARY, DISTRACTION, CONFRONT }
enum class AttachmentStyle { SECURE, ANXIOUS, AVOIDANT, FEARFUL }
enum class ConflictStyle { TALK, COLD_WAR, YIELD, HUMOR }
enum class RelationStage { STRANGER, FAMILIAR, CLOSE, DEPENDENT }

private fun clamp01(v: Float, def: Float = 0.5f): Float =
    if (v.isFinite()) v.coerceIn(0f, 1f) else def

/**
 * L1 倾向性特质 + 表达参数。
 *
 * 表达参数是这套模型比"一句形容词"强的关键：它直接约束句式长度与语气，
 * 而不是让模型去猜"傲娇"应该有多傲。
 */
data class Traits(
    val openness: Float = 0.5f,
    val conscientiousness: Float = 0.5f,
    val extraversion: Float = 0.5f,
    val agreeableness: Float = 0.5f,
    val neuroticism: Float = 0.5f,
    // 表达参数
    val verbosity: Float = 0.4f,
    val warmth: Float = 0.5f,
    val directness: Float = 0.5f,
    val humor: Float = 0.35f,
    val initiative: Float = 0.4f,
) {
    fun clamped(): Traits = copy(
        openness = clamp01(openness),
        conscientiousness = clamp01(conscientiousness),
        extraversion = clamp01(extraversion),
        agreeableness = clamp01(agreeableness),
        neuroticism = clamp01(neuroticism),
        verbosity = clamp01(verbosity),
        warmth = clamp01(warmth),
        directness = clamp01(directness),
        humor = clamp01(humor),
        initiative = clamp01(initiative),
    )

    /** 取某字段（演化与合并需要按名字读写） */
    fun get(field: String): Float = when (field) {
        "openness" -> openness
        "conscientiousness" -> conscientiousness
        "extraversion" -> extraversion
        "agreeableness" -> agreeableness
        "neuroticism" -> neuroticism
        "verbosity" -> verbosity
        "warmth" -> warmth
        "directness" -> directness
        "humor" -> humor
        "initiative" -> initiative
        else -> 0.5f
    }

    fun with(field: String, value: Float): Traits {
        val v = clamp01(value)
        return when (field) {
            "openness" -> copy(openness = v)
            "conscientiousness" -> copy(conscientiousness = v)
            "extraversion" -> copy(extraversion = v)
            "agreeableness" -> copy(agreeableness = v)
            "neuroticism" -> copy(neuroticism = v)
            "verbosity" -> copy(verbosity = v)
            "warmth" -> copy(warmth = v)
            "directness" -> copy(directness = v)
            "humor" -> copy(humor = v)
            "initiative" -> copy(initiative = v)
            else -> this
        }
    }

    /** 五维（不含表达参数），用于 IPIP 漂移比对 */
    fun bigFive(): Map<String, Float> = mapOf(
        "openness" to openness,
        "conscientiousness" to conscientiousness,
        "extraversion" to extraversion,
        "agreeableness" to agreeableness,
        "neuroticism" to neuroticism,
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("openness", openness.toDouble())
        put("conscientiousness", conscientiousness.toDouble())
        put("extraversion", extraversion.toDouble())
        put("agreeableness", agreeableness.toDouble())
        put("neuroticism", neuroticism.toDouble())
        put("verbosity", verbosity.toDouble())
        put("warmth", warmth.toDouble())
        put("directness", directness.toDouble())
        put("humor", humor.toDouble())
        put("initiative", initiative.toDouble())
    }

    companion object {
        val FIELDS = listOf(
            "openness", "conscientiousness", "extraversion", "agreeableness", "neuroticism",
            "verbosity", "warmth", "directness", "humor", "initiative",
        )

        fun fromJson(o: JSONObject?): Traits {
            if (o == null) return Traits()
            return Traits(
                openness = o.optDouble("openness", 0.5).toFloat(),
                conscientiousness = o.optDouble("conscientiousness", 0.5).toFloat(),
                extraversion = o.optDouble("extraversion", 0.5).toFloat(),
                agreeableness = o.optDouble("agreeableness", 0.5).toFloat(),
                neuroticism = o.optDouble("neuroticism", 0.5).toFloat(),
                verbosity = o.optDouble("verbosity", 0.4).toFloat(),
                warmth = o.optDouble("warmth", 0.5).toFloat(),
                directness = o.optDouble("directness", 0.5).toFloat(),
                humor = o.optDouble("humor", 0.35).toFloat(),
                initiative = o.optDouble("initiative", 0.4).toFloat(),
            ).clamped()
        }
    }
}

data class ValueRank(val name: String, val weight: Float = 0.5f)

/** L2 特征性适应：有方向、有情境 */
data class Adaptations(
    val values: List<ValueRank> = emptyList(),
    val longTermGoals: List<String> = emptyList(),
    val currentConcerns: List<String> = emptyList(),
    val coping: CopingStyle = CopingStyle.SEEK_SUPPORT,
    val attachment: AttachmentStyle = AttachmentStyle.SECURE,
    val conflictStyle: ConflictStyle = ConflictStyle.TALK,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("values", JSONArray().apply { values.forEach { put(JSONObject().put("name", it.name).put("weight", it.weight.toDouble())) } })
        put("long_term_goals", JSONArray(longTermGoals))
        put("current_concerns", JSONArray(currentConcerns))
        put("coping", coping.name)
        put("attachment", attachment.name)
        put("conflict_style", conflictStyle.name)
    }

    companion object {
        fun fromJson(o: JSONObject?): Adaptations {
            if (o == null) return Adaptations()
            val vals = mutableListOf<ValueRank>()
            o.optJSONArray("values")?.let { a ->
                for (i in 0 until a.length()) {
                    val it2 = a.optJSONObject(i) ?: continue
                    val n = it2.optString("name", "").trim()
                    if (n.isNotEmpty()) vals.add(ValueRank(n, clamp01(it2.optDouble("weight", 0.5).toFloat())))
                }
            }
            fun strList(key: String): List<String> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).mapNotNull { a.optString(it, "").trim().takeIf { s -> s.isNotEmpty() } }
            }
            return Adaptations(
                values = vals,
                longTermGoals = strList("long_term_goals"),
                currentConcerns = strList("current_concerns"),
                coping = enumOf(o.optString("coping"), CopingStyle.SEEK_SUPPORT),
                attachment = enumOf(o.optString("attachment"), AttachmentStyle.SECURE),
                conflictStyle = enumOf(o.optString("conflict_style"), ConflictStyle.TALK),
            )
        }

        private inline fun <reified T : Enum<T>> enumOf(s: String?, def: T): T =
            enumValues<T>().firstOrNull { it.name.equals(s?.trim(), ignoreCase = true) } ?: def
    }
}

/** L3 叙事身份：我是谁。originAnchors 与 taboos 是硬边界，进化时不可改 */
data class Narrative(
    val selfStory: String = "",
    val originAnchors: List<String> = emptyList(),
    val taboos: List<String> = emptyList(),
    val catchphrases: List<String> = emptyList(),
    val selfImage: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("self_story", selfStory)
        put("origin_anchors", JSONArray(originAnchors))
        put("taboos", JSONArray(taboos))
        put("catchphrases", JSONArray(catchphrases))
        put("self_image", selfImage)
    }

    companion object {
        fun fromJson(o: JSONObject?): Narrative {
            if (o == null) return Narrative()
            fun strList(key: String): List<String> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).mapNotNull { a.optString(it, "").trim().takeIf { s -> s.isNotEmpty() } }
            }
            return Narrative(
                selfStory = o.optString("self_story", ""),
                originAnchors = strList("origin_anchors"),
                taboos = strList("taboos"),
                catchphrases = strList("catchphrases"),
                selfImage = o.optString("self_image", ""),
            )
        }
    }
}

/** CAPS 行为签名：情境 → 反应倾向 */
data class Signature(
    val situation: Situation,
    val response: String,
    val intensity: Float = 0.5f,
    val evidence: Int = 1,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("situation", situation.name)
        put("response", response)
        put("intensity", clamp01(intensity).toDouble())
        put("evidence", evidence)
    }

    companion object {
        fun fromJson(o: JSONObject): Signature = Signature(
            situation = Situation.of(o.optString("situation")),
            response = o.optString("response", "").trim(),
            intensity = clamp01(o.optDouble("intensity", 0.5).toFloat()),
            evidence = o.optInt("evidence", 1).coerceIn(0, 9999),
        )
    }
}

/** 关系状态（中速）。与既有 affectionLevel 双向同步 */
data class Relationship(
    val trust: Float = 0.2f,
    val intimacy: Float = 0.1f,
    val commitment: Float = 0.1f,
    val stage: RelationStage = RelationStage.STRANGER,
) {
    fun clamped(): Relationship = copy(
        trust = clamp01(trust, 0.2f),
        intimacy = clamp01(intimacy, 0.1f),
        commitment = clamp01(commitment, 0.1f),
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("trust", trust.toDouble())
        put("intimacy", intimacy.toDouble())
        put("commitment", commitment.toDouble())
        put("stage", stage.name)
    }

    companion object {
        fun fromJson(o: JSONObject?): Relationship {
            if (o == null) return Relationship()
            val st = RelationStage.entries.firstOrNull {
                it.name.equals(o.optString("stage"), ignoreCase = true)
            } ?: RelationStage.STRANGER
            return Relationship(
                trust = o.optDouble("trust", 0.2).toFloat(),
                intimacy = o.optDouble("intimacy", 0.1).toFloat(),
                commitment = o.optDouble("commitment", 0.1).toFloat(),
                stage = st,
            ).clamped()
        }

        /** 由既有好感度（0-100）换算，避免两套体系打架 */
        fun fromAffection(level: Int): Relationship {
            val t = (level / 100f).coerceIn(0f, 1f)
            val stage = when {
                t >= 0.75f -> RelationStage.DEPENDENT
                t >= 0.5f -> RelationStage.CLOSE
                t >= 0.25f -> RelationStage.FAMILIAR
                else -> RelationStage.STRANGER
            }
            return Relationship(
                trust = (0.2f + t * 0.7f).coerceIn(0f, 1f),
                intimacy = (0.1f + t * 0.8f).coerceIn(0f, 1f),
                commitment = (0.1f + t * 0.7f).coerceIn(0f, 1f),
                stage = stage,
            )
        }
    }
}

/** 瞬时状态（快变） */
data class Mood(val valence: Float = 0.5f, val arousal: Float = 0.5f, val energy: Float = 0.5f) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("valence", valence.toDouble())
        put("arousal", arousal.toDouble())
        put("energy", energy.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject?): Mood {
            if (o == null) return Mood()
            return Mood(
                // 情绪值允许为负（不快到 -1..1 的效价），这里单独处理
                valence = o.optDouble("valence", 0.5).toFloat().let { if (it.isFinite()) it.coerceIn(-1f, 1f) else 0.5f },
                arousal = clamp01(o.optDouble("arousal", 0.5).toFloat()),
                energy = clamp01(o.optDouble("energy", 0.5).toFloat()),
            )
        }
    }
}

/** 字段来源与置信度 */
data class FieldMeta(
    val source: Source = Source.BOOTSTRAP,
    val confidence: Float = 0.6f,
    val locked: Set<String> = emptySet(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("source", source.name)
        put("confidence", confidence.toDouble())
        put("locked", JSONArray(locked.toList()))
    }

    companion object {
        fun fromJson(o: JSONObject?): FieldMeta {
            if (o == null) return FieldMeta()
            val src = Source.entries.firstOrNull {
                it.name.equals(o.optString("source"), ignoreCase = true)
            } ?: Source.BOOTSTRAP
            val locked = mutableSetOf<String>()
            o.optJSONArray("locked")?.let { a -> for (i in 0 until a.length()) locked.add(a.optString(i)) }
            return FieldMeta(src, clamp01(o.optDouble("confidence", 0.6).toFloat(), 0.6f), locked)
        }
    }
}

/**
 * 完整人格画像。
 *
 * rawText 保留作者原文：即使结构化失败或全部字段为默认值，
 * 也能把原句原样注入，不至于把人格弄丢（降级安全）。
 */
data class PersonalityProfile(
    val name: String = "",
    val traits: Traits = Traits(),
    val adaptations: Adaptations = Adaptations(),
    val narrative: Narrative = Narrative(),
    val signatures: List<Signature> = emptyList(),
    val relationship: Relationship = Relationship(),
    val mood: Mood = Mood(),
    val meta: FieldMeta = FieldMeta(),
    val rawText: String = "",
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("traits", traits.toJson())
        put("adaptations", adaptations.toJson())
        put("narrative", narrative.toJson())
        put("signatures", JSONArray().apply { signatures.forEach { put(it.toJson()) } })
        put("relationship", relationship.toJson())
        put("mood", mood.toJson())
        put("meta", meta.toJson())
        put("raw_text", rawText)
        put("version", version)
        put("updated_at", updatedAt)
    }

    /** 该情境下的签名（按强度降序，取前 n 条） */
    fun signaturesFor(situation: Situation, n: Int = 3): List<Signature> =
        signatures.filter { it.situation == situation }
            .sortedByDescending { it.intensity }
            .take(n)

    /**
     * 是否有可注入的实质内容。
     *
     * 全是默认值的空画像不应参与注入——否则会往提示词里塞一堆"话量适中、语气平和"，
     * 反而稀释真正的人设信息。
     */
    fun hasContent(): Boolean =
        signatures.isNotEmpty() ||
            narrative.selfStory.isNotBlank() ||
            narrative.taboos.isNotEmpty() ||
            narrative.catchphrases.isNotEmpty() ||
            rawText.isNotBlank() ||
            adaptations.values.isNotEmpty() ||
            adaptations.longTermGoals.isNotEmpty() ||
            traits != Traits()

    companion object {
        /** 旧数据迁移：一个自由文本性格字段 → 完整画像（降级安全） */
        fun fromLegacy(
            name: String,
            personalityText: String,
            speechStyle: String = "",
        ): PersonalityProfile {
            val raw = buildString {
                if (personalityText.isNotBlank()) append(personalityText.trim())
                if (speechStyle.isNotBlank()) {
                    if (isNotEmpty()) append("；")
                    append(speechStyle.trim())
                }
            }
            // 从文本里能抽出来的有限信号（不强求，抽不到就用默认值）
            val t = Traits()
                .let { if (raw.contains("毒舌") || raw.contains("直")) it.copy(directness = 0.75f) else it }
                .let { if (raw.contains("温柔") || raw.contains("关心")) it.copy(warmth = 0.6f) else it }
                .let { if (raw.contains("简短") || raw.contains("少")) it.copy(verbosity = 0.25f) else it }
                .let { if (raw.contains("傲娇")) it.copy(warmth = 0.45f, directness = 0.7f) else it }
                .let { if (raw.contains("活泼") || raw.contains("话多")) it.copy(extraversion = 0.7f, verbosity = 0.6f) else it }
            return PersonalityProfile(
                name = name,
                traits = t,
                narrative = Narrative(selfStory = raw),
                meta = FieldMeta(Source.BOOTSTRAP, confidence = 0.35f),
                rawText = raw,
            )
        }

        fun fromJson(o: JSONObject?): PersonalityProfile {
            if (o == null) return PersonalityProfile()
            val sigs = mutableListOf<Signature>()
            o.optJSONArray("signatures")?.let { a ->
                for (i in 0 until a.length()) {
                    val so = a.optJSONObject(i) ?: continue
                    val sg = Signature.fromJson(so)
                    if (sg.response.isNotBlank()) sigs.add(sg)
                }
            }
            return PersonalityProfile(
                name = o.optString("name", ""),
                traits = Traits.fromJson(o.optJSONObject("traits")),
                adaptations = Adaptations.fromJson(o.optJSONObject("adaptations")),
                narrative = Narrative.fromJson(o.optJSONObject("narrative")),
                signatures = sigs,
                relationship = Relationship.fromJson(o.optJSONObject("relationship")),
                mood = Mood.fromJson(o.optJSONObject("mood")),
                meta = FieldMeta.fromJson(o.optJSONObject("meta")),
                rawText = o.optString("raw_text", ""),
                version = o.optInt("version", 1),
                updatedAt = o.optLong("updated_at", System.currentTimeMillis()),
            )
        }
    }
}
