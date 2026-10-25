package com.aicompanion.personality

import android.content.Context

/**
 * 人格运行时：把「一轮对话」翻译成「人格状态的一次更新」。
 *
 * 这是 P1 的关键一环——此前 [PersonalityDynamics] 只是纯数学，
 * 没有任何东西驱动它。现在每轮用户输入都会：
 * 1. 识别情境（CAPS 的 if 部分）
 * 2. 按情境产出 1 条证据（权重 < 1，所以要累积多轮才动特质）
 * 3. 更新心情（快）、关系（中）、特质（慢）
 *
 * 全部在后台线程跑，失败就当没发生——人格更新绝不能影响回复。
 */
object PersonalityRuntime {

    /** 情境 → 证据。weight 刻意压小：单轮不该改变人格 */
    private data class Signal(val field: String, val direction: Float, val weight: Float)

    private fun signalsFor(situation: Situation): List<Signal> = when (situation) {
        Situation.PRAISE -> listOf(
            Signal("warmth", +1f, 0.5f),
            Signal("humor", +1f, 0.25f),
        )
        Situation.CRITICISM -> listOf(
            // 被反复否定 → 更敏感；同时更直接（防备）
            Signal("neuroticism", +1f, 0.45f),
            Signal("directness", +1f, 0.25f),
        )
        Situation.NEGLECT -> listOf(
            Signal("neuroticism", +1f, 0.6f),
            Signal("warmth", -1f, 0.2f),
        )
        Situation.REQUEST_HELP -> listOf(
            Signal("conscientiousness", +1f, 0.5f),
            Signal("initiative", +1f, 0.3f),
        )
        Situation.TEASE -> listOf(
            Signal("humor", +1f, 0.55f),
            Signal("warmth", +1f, 0.25f),
        )
        Situation.CONFLICT -> listOf(
            Signal("directness", +1f, 0.4f),
            Signal("agreeableness", -1f, 0.35f),
        )
        Situation.BOUNDARY -> listOf(
            Signal("directness", +1f, 0.45f),
            Signal("conscientiousness", +1f, 0.2f),
        )
        Situation.GOOD_NEWS -> listOf(
            Signal("extraversion", +1f, 0.35f),
            Signal("warmth", +1f, 0.3f),
        )
        Situation.BAD_NEWS -> listOf(
            Signal("warmth", +1f, 0.45f),
            Signal("agreeableness", +1f, 0.3f),
        )
        Situation.LATE_NIGHT -> listOf(
            Signal("warmth", +1f, 0.25f),
        )
        Situation.FAREWELL, Situation.ROUTINE -> emptyList()
    }

    /** 情境 → 瞬时情绪目标（效价 -1..1，唤醒 0..1） */
    private fun moodTargetFor(situation: Situation, current: Mood): Mood = when (situation) {
        Situation.PRAISE -> Mood(0.7f, 0.55f, current.energy)
        Situation.CRITICISM -> Mood(-0.45f, 0.65f, current.energy)
        Situation.NEGLECT -> Mood(-0.5f, 0.3f, current.energy)
        Situation.CONFLICT -> Mood(-0.6f, 0.8f, current.energy)
        Situation.TEASE -> Mood(0.5f, 0.6f, current.energy)
        Situation.GOOD_NEWS -> Mood(0.8f, 0.7f, current.energy)
        Situation.BAD_NEWS -> Mood(-0.35f, 0.35f, current.energy)
        Situation.LATE_NIGHT -> Mood(current.valence, 0.25f, 0.25f)
        else -> Mood(current.valence, current.arousal, current.energy)
    }

    /**
     * 处理一轮用户输入。同步执行，调用方负责放到后台线程。
     */
    fun observeTurn(context: Context, personaId: String, userText: String): String {
        if (userText.isBlank()) return ""
        return runCatching {
            val store = PersonalityStore(context)
            val profile = store.load(personaId) ?: return ""
            if (!profile.hasContent()) return ""

            val now = System.currentTimeMillis()
            val situation = PersonalityPrompt.detectSituation(userText)

            // 1) 证据
            val todayEvidences = signalsFor(situation).map {
                PersonalityDynamics.TraitEvidence(it.field, it.direction, now, it.weight)
            }
            // 关键：把历史证据一起交给演化函数。只给当轮那 0.5 权重永远够不到门槛 3。
            store.appendEvidences(personaId, todayEvidences)
            val evidences = store.evidences(personaId)
            val history = store.history(personaId)

            // 2) 特质（慢）
            com.aicompanion.util.AppLogger.i(
                "Personality",
                "证据池=${evidences.size} 条，neuro网=${PersonalityDynamics.netEvidence(evidences, "neuroticism", now)}" +
                    " warmth网=${PersonalityDynamics.netEvidence(evidences, "warmth", now)}",
            )
            val (newTraits, changes) = PersonalityDynamics.evolveTraits(
                profile.traits, evidences, history, now,
            )

            // 3) 心情（快）
            val newMood = PersonalityDynamics.evolveMood(
                profile.mood, moodTargetFor(situation, profile.mood),
            )

            // 4) 签名证据计数：命中的情境签名记一次，用于以后排序
            val updatedSigs = profile.signatures.map {
                if (it.situation == situation) it.copy(evidence = it.evidence + 1) else it
            }

            // 5) 关系（中速）：这是"相处久了会变近"的实际来源。
            //    原先只更新心情和特质，关系一直停在初始值 —— 聊一百轮也不会更亲近。
            //    步长刻意很小：关系该是缓慢累积的，不是一次对话就跳一级。
            val (dt, di, dc) = when (situation) {
                Situation.PRAISE, Situation.GOOD_NEWS, Situation.TEASE -> Triple(0.010f, 0.012f, 0.006f)
                Situation.REQUEST_HELP, Situation.BAD_NEWS, Situation.LATE_NIGHT -> Triple(0.008f, 0.010f, 0.004f)
                Situation.CRITICISM, Situation.CONFLICT -> Triple(-0.008f, -0.006f, -0.002f)
                Situation.NEGLECT -> Triple(-0.006f, -0.008f, -0.003f)
                else -> Triple(0.004f, 0.004f, 0.002f)
            }
            val movedRel = Relationship(
                trust = (profile.relationship.trust + dt).coerceIn(0f, 1f),
                intimacy = (profile.relationship.intimacy + di).coerceIn(0f, 1f),
                commitment = (profile.relationship.commitment + dc).coerceIn(0f, 1f),
                stage = profile.relationship.stage,
            ).let {
                it.copy(stage = PersonalityDynamics.stageOf(it.trust, it.intimacy, it.commitment))
            }

            val updated = profile.copy(
                traits = newTraits,
                relationship = movedRel,
                mood = newMood,
                signatures = updatedSigs,
                updatedAt = now,
                version = profile.version + 1,
            )
            store.save(personaId, updated)
            store.appendHistory(personaId, changes)

            if (changes.isNotEmpty()) {
                com.aicompanion.util.AppLogger.i(
                    "Personality",
                    "特质微调：${changes.joinToString { "${it.field} ${"%.3f".format(it.before)}→${"%.3f".format(it.after)}" }}",
                )
            }
            if (movedRel.stage != profile.relationship.stage) {
                com.aicompanion.util.AppLogger.i("Personality", "关系推进：${profile.relationship.stage} → ${movedRel.stage}")
            }
            situation.name
        }.getOrElse {
            com.aicompanion.util.AppLogger.w("Personality", "observeTurn 失败：${it.message}")
            ""
        }
    }

    /**
     * 用 LLM 把角色设定结构化。返回是否成功。
     *
     * 只在「人格面板点重建」或「首次导入角色」时调用——
     * 不要在聊天主链路上自动触发，那会让首条回复变慢。
     */
    suspend fun bootstrap(
        context: Context,
        personaId: String,
        name: String,
        description: String,
        personalityText: String,
        speechStyle: String,
        callLlm: suspend (system: String, user: String) -> String?,
    ): Boolean {
        val prompt = PersonalityBootstrap.schemaPrompt(name, description, personalityText, speechStyle)
        val raw = callLlm(
            "你是人格建模助手。严格按要求的 JSON schema 输出，不要解释。",
            prompt,
        ) ?: return false
        val parsed = PersonalityBootstrap.parse(
            text = raw,
            name = name,
            rawText = listOf(personalityText, speechStyle).filter { it.isNotBlank() }.joinToString("；"),
            confidence = 0.65f,
        ) ?: return false

        val store = PersonalityStore(context)
        store.merge(personaId, parsed)
        com.aicompanion.util.AppLogger.i(
            "Personality",
            "结构化完成：${parsed.signatures.size} 条签名 / ${parsed.traits.bigFive().size} 维特质" +
                " / ${parsed.narrative.taboos.size} 条底线",
        )
        if (parsed.narrative.taboos.isEmpty()) {
            // 底线缺失 = 契约检查失效，必须让人知道（面板上会显示 0 条）
            com.aicompanion.util.AppLogger.w(
                "Personality",
                "本次结构化没有产出 taboos，建议在角色设定里手写 1-2 条底线后重建",
            )
        }
        return true
    }

    /** 是否有画像（供 UI 判断显示"生成"还是"重建"） */
    fun hasProfile(context: Context, personaId: String): Boolean =
        runCatching { PersonalityStore(context).load(personaId)?.hasContent() == true }.getOrDefault(false)

    fun load(context: Context, personaId: String): PersonalityProfile? =
        runCatching { PersonalityStore(context).load(personaId) }.getOrNull()
}
