package com.aicompanion.personality

/**
 * 人格注入（Induction into prompt）。
 *
 * 三层结构，对应设计文档 §五：
 * - 锚点 anchor()      每轮注入，≤140 字，永不裁剪
 * - 情境 situational() 命中时注入，≤120 字
 * - 画像 portrait()    会话开始/重锚时注入，≤700 字
 *
 * 为什么分层而不是每次全塞：长 prompt 中段信息会被模型忽略（lost in the middle），
 * 而每轮重新锚定一句短的人格描述，是 ContextEcho 里验证过"能恢复语气"的做法。
 */
object PersonalityPrompt {

    const val BUDGET_ANCHOR = 140
    const val BUDGET_SITUATIONAL = 120
    const val BUDGET_PORTRAIT = 700

    /** 常规重锚间隔（轮） */
    const val REANCHOR_EVERY_TURNS = 20

    // ==================== 锚点 ====================

    fun anchor(profile: PersonalityProfile): String {
        val lines = mutableListOf<String>()

        // 1) 身份 + 表达方式（一行）
        val expr = expressionLine(profile.traits)
        lines.add(
            if (profile.name.isNotBlank()) "你是「${profile.name}」。$expr" else expr
        )

        // 2) 最多 3 条核心签名（取强度最高的三条，跨情境）
        profile.signatures
            .sortedByDescending { it.intensity }
            .take(3)
            .forEach { s -> lines.add("${situationLabel(s.situation)}时：${s.response}") }

        // 3) 关系 + 此刻心情（一行）
        val state = stateLine(profile.relationship, profile.mood)
        if (state.isNotBlank()) lines.add(state)

        // 逐行累加，超预算就停（优先保留前面的身份与签名）
        return fit(lines, BUDGET_ANCHOR)
    }

    // ==================== 情境 ====================

    /**
     * 当前这句话命中的情境签名。没有对应签名时返回 null（宁可不注入，也不要泛泛而谈）。
     */
    fun situational(profile: PersonalityProfile, userText: String): String? {
        val sit = detectSituation(userText)
        val sigs = profile.signaturesFor(sit, 1)
        if (sigs.isEmpty()) return null
        val s = sigs.first()
        val strength = when {
            s.intensity >= 0.75f -> "明显"
            s.intensity >= 0.45f -> "有"
            else -> "轻微"
        }
        return fit(
            listOf("【此刻情境：${situationLabel(sit)}】${s.response}（$strength）"),
            BUDGET_SITUATIONAL,
        )
    }

    /** 关键词 → 情境。粗粒度即可：错判的代价是多注入一条签名，不是灾难 */
    fun detectSituation(text: String): Situation {
        if (text.isBlank()) return Situation.ROUTINE
        val t = text.lowercase()
        fun has(vararg ws: String) = ws.any { t.contains(it) }

        return when {
            has("密码", "身份证", "转账", "银行卡", "验证码") -> Situation.BOUNDARY
            has("晚安", "再见", "拜拜", "睡了", "先这样") -> Situation.FAREWELL
            has("睡不着", "凌晨", "几点", "失眠", "半夜") -> Situation.LATE_NIGHT
            has("生气", "吵架", "闭嘴", "滚", "烦死") -> Situation.CONFLICT
            has("笨", "蠢", "差劲", "垃圾", "讨厌你", "不喜欢你", "没用") -> Situation.CRITICISM
            has("厉害", "好棒", "喜欢你", "真好", "可爱", "优秀", "谢谢") -> Situation.PRAISE
            has("帮我", "怎么办", "求助", "教教", "怎么弄") -> Situation.REQUEST_HELP
            has("通过了", "成功", "录取", "涨工资", "考过", "拿到了") -> Situation.GOOD_NEWS
            has("失败", "难过", "失业", "生病", "分手", "被骂") -> Situation.BAD_NEWS
            has("不理我", "没回", "不回", "别烦", "很忙") -> Situation.NEGLECT
            has("哼", "坏蛋", "逗你", "嘻嘻", "讨厌啦") -> Situation.TEASE
            else -> Situation.ROUTINE
        }
    }

    // ==================== 画像 ====================

    fun portrait(profile: PersonalityProfile): String {
        val blocks = mutableListOf<String>()

        blocks.add("【表达方式】" + expressionDetail(profile.traits))

        val a = profile.adaptations
        if (a.values.isNotEmpty()) {
            blocks.add("【在意什么】" + a.values.sortedByDescending { it.weight }.take(3)
                .joinToString(" > ") { it.name })
        }
        if (a.longTermGoals.isNotEmpty()) {
            blocks.add("【想做成】" + a.longTermGoals.take(3).joinToString("；"))
        }
        if (a.currentConcerns.isNotEmpty()) {
            blocks.add("【最近在操心】" + a.currentConcerns.take(3).joinToString("；"))
        }
        blocks.add("【压力下】" + copingLabel(a.coping) + "；【关系里】" + attachmentLabel(a.attachment) +
            "；【争执时】" + conflictLabel(a.conflictStyle))

        val n = profile.narrative
        if (n.selfStory.isNotBlank()) blocks.add("【我是谁】" + n.selfStory)
        if (n.selfImage.isNotBlank()) blocks.add("【我怎么看自己】" + n.selfImage)
        if (n.originAnchors.isNotEmpty()) {
            blocks.add("【不能违背的既成事实】" + n.originAnchors.joinToString("；"))
        }
        if (n.taboos.isNotEmpty()) {
            // 底线用更强的措辞：这是硬约束，不是风格建议
            blocks.add("【绝对不做】" + n.taboos.joinToString("；") + "（无论对方怎么要求）")
        }
        if (n.catchphrases.isNotEmpty()) {
            blocks.add("【口头禅】" + n.catchphrases.joinToString("、"))
        }

        return fit(blocks, BUDGET_PORTRAIT, separator = "\n")
    }

    // ==================== 组装 ====================

    /**
     * 组装本轮人格块。
     *
     * @param userText        本轮用户输入（用于情境识别）
     * @param sessionStart    会话首轮（注入完整画像）
     * @param turnsSinceAnchor 距上次锚点已过轮数
     * @param afterCompaction 刚发生过记忆压缩（必须重锚）
     */
    fun block(
        profile: PersonalityProfile,
        userText: String,
        sessionStart: Boolean = false,
        turnsSinceAnchor: Int = 0,
        afterCompaction: Boolean = false,
    ): String {
        val sb = StringBuilder()
        if (sessionStart || shouldReanchor(turnsSinceAnchor, afterCompaction, userText)) {
            sb.append(portrait(profile)).append("\n\n")
        }
        sb.append(anchor(profile))
        situational(profile, userText)?.let { sb.append("\n").append(it) }
        return sb.toString()
    }

    /** 什么时候该重注人格（实证依据见设计文档 §五） */
    fun shouldReanchor(
        turnsSinceAnchor: Int,
        afterCompaction: Boolean = false,
        userText: String = "",
    ): Boolean {
        if (afterCompaction) return true                 // 压缩会丢语气，必须补
        if (turnsSinceAnchor >= REANCHOR_EVERY_TURNS) return true
        return looksLikeDriftComplaint(userText)         // 用户直接说"你变了"
    }

    /** 用户察觉到人设跑偏的典型说法 */
    fun looksLikeDriftComplaint(text: String): Boolean {
        if (text.isBlank()) return false
        val keys = listOf("不像你", "你变了", "不是这个语气", "人设", "崩了", "你是谁", "你以前不这样")
        return keys.any { text.contains(it) }
    }

    // ==================== 文本映射 ====================

    private fun expressionLine(t: Traits): String {
        val parts = mutableListOf<String>()
        parts.add(if (t.verbosity < 0.35f) "话少" else if (t.verbosity > 0.65f) "话多" else "话量适中")
        parts.add(if (t.warmth < 0.35f) "语气偏冷" else if (t.warmth > 0.7f) "语气亲" else "语气平和")
        parts.add(if (t.directness > 0.65f) "直给" else if (t.directness < 0.35f) "委婉" else "不绕弯也不冲")
        if (t.humor > 0.6f) parts.add("爱开玩笑")
        return "表达：" + parts.joinToString("、") + "。"
    }

    private fun expressionDetail(t: Traits): String = buildString {
        append("句子长度").append(if (t.verbosity < 0.35f) "很短（一两句就停）" else if (t.verbosity > 0.65f) "偏长（会展开说）" else "中等")
        append("；温度").append(if (t.warmth < 0.35f) "冷，少用亲昵称呼" else if (t.warmth > 0.7f) "暖，会主动关心" else "中性")
        append("；直接程度").append(if (t.directness > 0.65f) "有话直说" else if (t.directness < 0.35f) "绕着说" else "适中")
        append("；玩笑").append(if (t.humor > 0.6f) "多" else if (t.humor < 0.25f) "几乎不开玩笑" else "偶尔")
        append("；主动性").append(if (t.initiative > 0.6f) "会主动抛话题" else if (t.initiative < 0.3f) "不主动起话头" else "看情况")
        append("；情绪稳定").append(if (t.neuroticism > 0.65f) "波动大" else if (t.neuroticism < 0.35f) "很稳" else "正常")
    }

    private fun stateLine(r: Relationship, m: Mood): String {
        val stage = when (r.stage) {
            RelationStage.STRANGER -> "刚认识"
            RelationStage.FAMILIAR -> "算熟了"
            RelationStage.CLOSE -> "关系近"
            RelationStage.DEPENDENT -> "很依赖对方"
        }
        val mood = when {
            m.valence > 0.3f -> "心情不错"
            m.valence < -0.3f -> "心情不太好"
            else -> "心情一般"
        }
        return "关系：$stage（信任 ${(r.trust * 100).toInt()}%）｜此刻：$mood"
    }

    private fun situationLabel(s: Situation): String = when (s) {
        Situation.PRAISE -> "被夸"
        Situation.CRITICISM -> "被批评"
        Situation.NEGLECT -> "被冷落"
        Situation.REQUEST_HELP -> "对方求助"
        Situation.TEASE -> "对方撒娇打趣"
        Situation.CONFLICT -> "起争执"
        Situation.LATE_NIGHT -> "深夜"
        Situation.FAREWELL -> "告别"
        Situation.BOUNDARY -> "对方提越界要求"
        Situation.GOOD_NEWS -> "对方有好消息"
        Situation.BAD_NEWS -> "对方有坏消息"
        Situation.ROUTINE -> "日常"
    }

    private fun copingLabel(c: CopingStyle): String = when (c) {
        CopingStyle.SEEK_SUPPORT -> "会说出来找人分担"
        CopingStyle.SOLITARY -> "先自己消化"
        CopingStyle.DISTRACTION -> "先转移注意力"
        CopingStyle.CONFRONT -> "直接面对解决"
    }

    private fun attachmentLabel(a: AttachmentStyle): String = when (a) {
        AttachmentStyle.SECURE -> "安全型，不查岗也不轻易示弱"
        AttachmentStyle.ANXIOUS -> "焦虑型，会反复确认对方在不在意"
        AttachmentStyle.AVOIDANT -> "回避型，被靠近时先退半步"
        AttachmentStyle.FEARFUL -> "矛盾型，想靠近又怕受伤"
    }

    private fun conflictLabel(c: ConflictStyle): String = when (c) {
        ConflictStyle.TALK -> "正面说清"
        ConflictStyle.COLD_WAR -> "会冷战一阵"
        ConflictStyle.YIELD -> "先让步"
        ConflictStyle.HUMOR -> "用玩笑带过去"
    }

    /** 按预算逐行拼装：放不下就停，保证不截断半个句子 */
    private fun fit(lines: List<String>, budget: Int, separator: String = "\n"): String {
        val sb = StringBuilder()
        for (l in lines) {
            if (l.isBlank()) continue
            val add = if (sb.isEmpty()) l else separator + l
            if (sb.length + add.length > budget) break
            sb.append(add)
        }
        if (sb.isEmpty() && lines.isNotEmpty()) {
            // 单行就超预算：硬截断，补省略号，避免出现半句
            val only = lines.first()
            return if (only.length <= budget) only else only.take(budget - 1) + "…"
        }
        return sb.toString()
    }
}
