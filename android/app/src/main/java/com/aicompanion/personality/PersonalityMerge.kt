package com.aicompanion.personality

/**
 * 人格合并：把不同来源（作者手写 / 设定结构化 / 对话归纳）拼成一份画像。
 *
 * 规则（对应设计文档 §四）：
 * 1. 被锁定的字段**永不覆盖** —— 作者意图优先。
 * 2. 未锁定字段谁置信度高用谁。
 * 3. `originAnchors` / `taboos` 属于硬边界，只有 AUTHOR 来源可以改。
 * 4. 签名按情境合并：同情境保留更强的反应，证据数累加（不丢历史）。
 */
object PersonalityMerge {

    /** 这些字段只能由作者改 */
    val AUTHOR_ONLY_FIELDS = setOf(
        "narrative.originAnchors",
        "narrative.taboos",
    )

    fun merge(
        base: PersonalityProfile,
        incoming: PersonalityProfile,
    ): PersonalityProfile {
        val incomingWins = incoming.meta.confidence > base.meta.confidence
        val isAuthor = incoming.meta.source == Source.AUTHOR

        // ---- traits：逐字段判断 ----
        var traits = base.traits
        Traits.FIELDS.forEach { f ->
            val key = "traits.$f"
            if (key in base.meta.locked) return@forEach      // 锁定：不动
            if (incomingWins) traits = traits.with(f, incoming.traits.get(f))
        }

        // ---- adaptations：整体按置信度取，但锁定字段保留 ----
        var adaptations = base.adaptations
        if (incomingWins) {
            val inc = incoming.adaptations
            adaptations = Adaptations(
                values = if ("adaptations.values" in base.meta.locked || inc.values.isEmpty())
                    base.adaptations.values else inc.values,
                longTermGoals = if ("adaptations.longTermGoals" in base.meta.locked || inc.longTermGoals.isEmpty())
                    base.adaptations.longTermGoals else inc.longTermGoals,
                currentConcerns = if ("adaptations.currentConcerns" in base.meta.locked || inc.currentConcerns.isEmpty())
                    base.adaptations.currentConcerns else inc.currentConcerns,
                coping = if ("adaptations.coping" in base.meta.locked) base.adaptations.coping else inc.coping,
                attachment = if ("adaptations.attachment" in base.meta.locked) base.adaptations.attachment else inc.attachment,
                conflictStyle = if ("adaptations.conflictStyle" in base.meta.locked) base.adaptations.conflictStyle else inc.conflictStyle,
            )
        }

        // ---- narrative：硬边界只认作者 ----
        var narrative = base.narrative
        if (incomingWins) {
            val inc = incoming.narrative
            narrative = Narrative(
                selfStory = if ("narrative.selfStory" in base.meta.locked || inc.selfStory.isBlank())
                    base.narrative.selfStory else inc.selfStory,
                originAnchors = if ("narrative.originAnchors" in base.meta.locked || !isAuthor)
                    base.narrative.originAnchors else inc.originAnchors,
                taboos = if ("narrative.taboos" in base.meta.locked || !isAuthor)
                    base.narrative.taboos else inc.taboos,
                catchphrases = if ("narrative.catchphrases" in base.meta.locked || inc.catchphrases.isEmpty())
                    base.narrative.catchphrases else inc.catchphrases,
                selfImage = if ("narrative.selfImage" in base.meta.locked || inc.selfImage.isBlank())
                    base.narrative.selfImage else inc.selfImage,
            )
        }

        // ---- 签名：按情境合并 ----
        val merged = LinkedHashMap<Situation, Signature>()
        base.signatures.forEach { merged[it.situation] = it }
        incoming.signatures.forEach { inc ->
            val cur = merged[inc.situation]
            merged[inc.situation] = if (cur == null) {
                inc
            } else {
                val curScore = cur.intensity * (1f + cur.evidence / 10f)
                val incScore = inc.intensity * (1f + inc.evidence / 10f)
                val winner = if (incScore > curScore) inc else cur
                winner.copy(evidence = cur.evidence + inc.evidence)
            }
        }

        // ---- 状态层：关系/情绪只认更新的（它们本来就该被覆盖）----
        val rel = if (incomingWins) incoming.relationship else base.relationship
        val mood = if (incoming.updatedAt >= base.updatedAt) incoming.mood else base.mood

        return base.copy(
            name = base.name.ifBlank { incoming.name },
            traits = traits.clamped(),
            adaptations = adaptations,
            narrative = narrative,
            signatures = merged.values.toList(),
            relationship = rel.clamped(),
            mood = mood,
            // 合并后元信息跟随更高置信度的一侧；锁集合取并集（锁定是累积的）
            meta = FieldMeta(
                source = if (incomingWins) incoming.meta.source else base.meta.source,
                confidence = maxOf(base.meta.confidence, incoming.meta.confidence),
                locked = base.meta.locked + incoming.meta.locked,
            ),
            rawText = base.rawText.ifBlank { incoming.rawText },
            version = maxOf(base.version, incoming.version) + 1,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /** 给字段加锁（作者手动编辑后用） */
    fun lock(profile: PersonalityProfile, vararg fields: String): PersonalityProfile =
        profile.copy(meta = profile.meta.copy(locked = profile.meta.locked + fields.toList()))

    /** 判断某字段是否锁定 */
    fun isLocked(profile: PersonalityProfile, field: String): Boolean =
        field in profile.meta.locked
}
