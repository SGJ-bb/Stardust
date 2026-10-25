package com.aicompanion.personality

/**
 * 人格演化（P0 数学部分）。
 *
 * 三条原则（设计文档 §六）：
 * 1. **门槛**：同类证据累计到阈值才允许动特质，单次事件不改人格。
 * 2. **惯性**：每次只朝目标走一小步（学习率 0.03），并设单步与 30 天累计上限。
 * 3. **回弹**：反向证据抵消正向证据，因此人格不会单向漂移。
 *
 * 状态层（mood / relationship）则用指数滑动平均，反应快得多——
 * "今天心情不好"不应该等于"这个人变冷了"。
 */
object PersonalityDynamics {

    /** 净证据门槛：同一方向累计到这个值才动 */
    const val MIN_NET_EVIDENCE = 3f

    /** 学习率：每轮向目标值靠近的比例 */
    const val LEARNING_RATE = 0.03f

    /** 单次更新最大变化量 */
    const val MAX_STEP = 0.02f

    /** 30 天内累计最大变化量（人类特质变化本就缓慢） */
    const val MAX_CHANGE_30D = 0.08f

    const val WINDOW_30D_MS = 30L * 24 * 3600 * 1000
    const val WINDOW_EVIDENCE_MS = 14L * 24 * 3600 * 1000

    /** 一条证据：direction > 0 表示"往高分方向"，weight 表示强度 */
    data class TraitEvidence(
        val field: String,
        val direction: Float,
        val at: Long,
        val weight: Float = 1f,
    )

    /** 一次已发生的变化，用于 30 天累计限幅 */
    data class TraitChange(
        val field: String,
        val before: Float,
        val after: Float,
        val at: Long,
    ) {
        val delta: Float get() = after - before
    }

    /** 窗口内某字段的净证据（正负相抵） */
    fun netEvidence(
        evidences: List<TraitEvidence>,
        field: String,
        now: Long,
        windowMs: Long = WINDOW_EVIDENCE_MS,
    ): Float = evidences
        .asSequence()
        .filter { it.field == field && now - it.at in 0..windowMs }
        .sumOf { (it.direction * it.weight).toDouble() }
        .toFloat()

    /**
     * 演化单个特质。
     *
     * @param current  当前值
     * @param field    字段名（与 [Traits.FIELDS] 一致）
     * @param evidences 历史证据
     * @param history  已发生的变化（用于 30 天累计限幅）
     */
    fun evolveTrait(
        current: Float,
        field: String,
        evidences: List<TraitEvidence>,
        history: List<TraitChange> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): Float {
        val net = netEvidence(evidences, field, now)
        // 门槛：证据不够或正负抵消，一律不动
        if (kotlin.math.abs(net) < MIN_NET_EVIDENCE) return current.coerceIn(0f, 1f)

        // 目标值：证据越多拉得越远，但最大 0.5，避免一次爆发改命
        val pull = (net / 10f).coerceIn(-0.5f, 0.5f)
        val target = (current + pull).coerceIn(0f, 1f)

        // 惯性：只走一小步
        var step = LEARNING_RATE * (target - current)
        step = step.coerceIn(-MAX_STEP, MAX_STEP)

        // 30 天累计限幅（同方向才受限；反向变化允许，否则改不回来）
        val used30d = history
            .asSequence()
            .filter { it.field == field && now - it.at in 0..WINDOW_30D_MS }
            .sumOf { it.delta.toDouble() }
            .toFloat()
        if (used30d * step > 0) {
            val used = kotlin.math.abs(used30d)
            val remaining = (MAX_CHANGE_30D - used).coerceAtLeast(0f)
            if (kotlin.math.abs(step) > remaining) {
                step = if (step > 0) remaining else -remaining
            }
        }

        return (current + step).coerceIn(0f, 1f)
    }

    /** 一次演化整份特质，返回新特质与变化记录 */
    fun evolveTraits(
        traits: Traits,
        evidences: List<TraitEvidence>,
        history: List<TraitChange> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): Pair<Traits, List<TraitChange>> {
        var t = traits
        val changes = mutableListOf<TraitChange>()
        Traits.FIELDS.forEach { f ->
            val before = t.get(f)
            val after = evolveTrait(before, f, evidences, history, now)
            if (kotlin.math.abs(after - before) > 1e-6f) {
                t = t.with(f, after)
                changes.add(TraitChange(f, before, after, now))
            }
        }
        return t to changes
    }

    /** 瞬时情绪：直接靠拢，无惯性 */
    fun evolveMood(current: Mood, target: Mood, alpha: Float = 0.5f): Mood = Mood(
        valence = lerp(current.valence, target.valence, alpha).coerceIn(-1f, 1f),
        arousal = lerp(current.arousal, target.arousal, alpha).coerceIn(0f, 1f),
        energy = lerp(current.energy, target.energy, alpha).coerceIn(0f, 1f),
    )

    /** 关系：慢一些的滑动平均（α≈0.15，约 4 次互动一个半衰期） */
    fun evolveRelationship(
        current: Relationship,
        target: Relationship,
        alpha: Float = 0.15f,
    ): Relationship {
        val trust = lerp(current.trust, target.trust, alpha)
        val intimacy = lerp(current.intimacy, target.intimacy, alpha)
        val commitment = lerp(current.commitment, target.commitment, alpha)
        return Relationship(trust, intimacy, commitment, stageOf(trust, intimacy, commitment))
    }

    /** 阶段由三项均值决定，避免单项突刺直接跨阶段 */
    fun stageOf(trust: Float, intimacy: Float, commitment: Float): RelationStage {
        val score = (trust + intimacy + commitment) / 3f
        return when {
            score >= 0.7f -> RelationStage.DEPENDENT
            score >= 0.45f -> RelationStage.CLOSE
            score >= 0.22f -> RelationStage.FAMILIAR
            else -> RelationStage.STRANGER
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float =
        a + (b - a) * t.coerceIn(0f, 1f)
}
