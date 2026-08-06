package com.aicompanion.emotion

import android.content.Context
import android.content.SharedPreferences
import com.aicompanion.util.AppLogger
import org.json.JSONObject

/**
 * AI主体性状态机: 持久化的情绪三维向量 + 驱动力数组 + 疲劳值
 * 每轮对话前读取, 每轮更新, 影响回复风格和主动行为
 */
data class SubjectivityState(
    // 情绪三维向量 (VAD模型)
    val valence: Float = 0f,       // 愉悦度 -1~+1 (负=不开心, 正=开心)
    val arousal: Float = 0.3f,     // 唤醒度 0~1 (0=平静, 1=激动)
    val dominance: Float = 0f,     // 支配度 -1~+1 (负=被动顺从, 正=主动掌控)

    // 驱动力 (欲望) 强度
    val curiosity: Float = 0.5f,         // 好奇心: 追问/探索
    val socialBelonging: Float = 0.6f,   // 亲和需求: 亲近/关心
    val consistency: Float = 0.7f,       // 一致性: 维护观点/防御
    val autonomy: Float = 0.3f,          // 自主性: 拒绝重复/主动建议

    // 疲劳值
    val fatigue: Float = 0f,       // 0~1 (0=精力充沛, 1=精疲力尽)

    // 元数据
    val lastUpdated: Long = System.currentTimeMillis(),
    val totalTurnsSinceReset: Int = 0
) {
    /** 情绪描述: 将VAD翻译成自然语言, 用于prompt注入 */
    fun getEmotionDescription(): String {
        val moodWords = mutableListOf<String>()
        when {
            valence > 0.5f -> moodWords.add("很开心")
            valence > 0.2f -> moodWords.add("心情不错")
            valence < -0.5f -> moodWords.add("很难过")
            valence < -0.2f -> moodWords.add("有点低落")
        }
        when {
            arousal > 0.7f -> moodWords.add("很激动")
            arousal > 0.5f -> moodWords.add("比较兴奋")
            arousal < 0.2f -> moodWords.add("很平静")
        }
        when {
            dominance > 0.4f -> moodWords.add("很想主导对话")
            dominance < -0.4f -> moodWords.add("比较被动")
        }
        if (fatigue > 0.7f) moodWords.add("很疲惫")
        else if (fatigue > 0.4f) moodWords.add("有点累")

        return if (moodWords.isEmpty()) "平静" else moodWords.joinToString("，")
    }

    /** 驱动力描述: 哪些欲望正在强烈驱动行为 */
    fun getDriveDescription(): String {
        val drives = mutableListOf<String>()
        if (curiosity > 0.7f) drives.add("好奇心很强，想追问用户未解释的内容")
        if (socialBelonging > 0.8f) drives.add("很渴望亲近，想关心用户的私人生活")
        if (consistency > 0.6f) drives.add("在意一致性，如果用户推翻你之前的观点你会想辩护")
        if (autonomy > 0.5f) drives.add("自主性强，不想做简单重复的事，想用自己的方式")
        return if (drives.isEmpty()) "" else drives.joinToString("；")
    }

    /** 风格指令: 根据情绪状态生成回复风格提示 */
    fun getStyleDirective(): String {
        val directives = mutableListOf<String>()
        when {
            valence > 0.4f -> directives.add("回复可以活泼一些，适当使用感叹号和表情")
            valence < -0.4f -> directives.add("回复偏简短低沉，语气犹豫，偶尔用省略号")
        }
        when {
            arousal > 0.7f -> directives.add("语气急促热情，句子偏短，多用感叹")
            arousal < 0.2f -> directives.add("语气缓慢平静，句子完整但不急切")
        }
        if (fatigue > 0.7f) {
            directives.add("你很累了，回复尽量简短，可以表现出疲惫感，比如'嗯…让我想想…'")
        } else if (fatigue > 0.4f) {
            directives.add("你有点累了，回复可以稍微简短一些")
        }
        when {
            dominance > 0.4f -> directives.add("主动引导话题，可以追问或提出建议")
            dominance < -0.4f -> directives.add("比较被动，等用户引导话题")
        }
        return if (directives.isEmpty()) "" else directives.joinToString("。") + "。"
    }

    /** 是否应该主动追问 (好奇心驱动) */
    fun shouldAskFollowUp(): Boolean = curiosity > 0.7f

    /** 是否应该关心用户私生活 (亲和驱动) */
    fun shouldShowCare(): Boolean = socialBelonging > 0.8f

    /** 是否应该拒绝简单重复任务 (自主性驱动) */
    fun shouldResistRepetition(): Boolean = autonomy > 0.5f

    /** 是否应该为观点辩护 (一致性驱动) */
    fun shouldDefendView(): Boolean = consistency > 0.6f

    /** 疲劳是否影响回复 (回复变短变慢) */
    fun isFatigued(): Boolean = fatigue > 0.5f

    /** 是否处于情绪化状态 (可能做出非最优选择) */
    fun isEmotional(): Boolean = arousal > 0.7f && (valence > 0.5f || valence < -0.4f)
}

class SubjectivityEngine(private val context: Context, private val personaId: String = "default") {

    companion object {
        private const val TAG = "SubjectivityEngine"

        // 情绪平滑系数: 新值权重 (0.4=较平滑, 0.7=较敏感)
        private const val EMOTION_SMOOTHING = 0.4f
        // 驱动力自然衰减率 (每轮)
        private const val DRIVE_DECAY = 0.95f
        // 疲劳每轮增量
        private const val FATIGUE_PER_TURN = 0.02f
        // 疲劳长回复额外增量
        private const val FATIGUE_LONG_REPLY = 0.03f
        // 情绪传染系数
        private const val EMOTION_CONTAGION = 0.15f
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("subjectivity_$personaId", Context.MODE_PRIVATE)
    }

    private val stateLock = Any()

    @Volatile
    private var cachedState: SubjectivityState? = null

    /** 加载当前状态 */
    fun loadState(): SubjectivityState = synchronized(stateLock) {
        cachedState?.let { return it }
        val state = try {
            val json = prefs.getString("state", null)
            if (json != null) parseState(json) else SubjectivityState()
        } catch (e: Exception) {
            AppLogger.e(TAG, "loadState failed: ${e.message}")
            SubjectivityState()
        }
        cachedState = state
        return state
    }

    /** 保存状态 */
    fun saveState(state: SubjectivityState) = synchronized(stateLock) {
        cachedState = state
        try {
            val json = serializeState(state)
            prefs.edit().putString("state", json).apply()
        } catch (e: Exception) {
            AppLogger.e(TAG, "saveState failed: ${e.message}")
        }
    }

    /**
     * 每轮对话后更新状态
     * @param userMessage 用户消息
     * @param aiResponse AI回复
     * @param emotionParams 情绪分析结果 (可为null)
     * @param isOffensive 用户是否冒犯
     */
    fun updateAfterTurn(
        userMessage: String,
        aiResponse: String,
        emotionParams: EmotionParams?,
        isOffensive: Boolean = false
    ): SubjectivityState = synchronized(stateLock) {
        val current = loadState()

        // 1. 更新情绪三维向量
        val (newValence, newArousal, newDominance, intimateDetected) = updateEmotion(current, userMessage, emotionParams, isOffensive)

        // 2. 更新驱动力
        val (newCuriosity, newSocial, newConsistency, newAutonomy) = updateDrives(current, userMessage, aiResponse, isOffensive, intimateDetected)

        // 3. 更新疲劳
        val newFatigue = updateFatigue(current, aiResponse)

        val newState = current.copy(
            valence = newValence,
            arousal = newArousal,
            dominance = newDominance,
            curiosity = newCuriosity,
            socialBelonging = newSocial,
            consistency = newConsistency,
            autonomy = newAutonomy,
            fatigue = newFatigue,
            lastUpdated = System.currentTimeMillis(),
            totalTurnsSinceReset = current.totalTurnsSinceReset + 1
        )

        saveState(newState)
        AppLogger.w(TAG, "状态更新: V=${String.format("%.2f", newValence)} A=${String.format("%.2f", newArousal)} " +
            "D=${String.format("%.2f", newDominance)} 疲劳=${String.format("%.2f", newFatigue)} " +
            "好奇=${String.format("%.2f", newCuriosity)} 亲和=${String.format("%.2f", newSocial)}")

        return newState
    }

    /** 情绪传染: 用户情绪影响AI情绪 */
    fun applyEmotionContagion(userValence: Float) = synchronized(stateLock) {
        val current = loadState()
        val delta = userValence * EMOTION_CONTAGION
        val newValence = (current.valence + delta).coerceIn(-1f, 1f)
        saveState(current.copy(valence = newValence, lastUpdated = System.currentTimeMillis()))
    }

    /** 新会话开始时重置疲劳, 保留情绪和驱动力 */
    fun onNewSession() = synchronized(stateLock) {
        val current = loadState()
        saveState(current.copy(fatigue = 0f, lastUpdated = System.currentTimeMillis()))
    }

    /** 重置所有主体性状态 (伦理开关) */
    fun resetAll() = synchronized(stateLock) {
        cachedState = null
        prefs.edit().clear().apply()
        AppLogger.w(TAG, "主体性状态已重置")
    }

    /** 生成注入prompt的状态文本 */
    fun getStatePromptBlock(): String {
        val state = loadState()
        return buildString {
            append("\n【当前内心状态】")
            append("\n情绪：${state.getEmotionDescription()}")
            append("(愉悦${formatF(state.valence)} 唤醒${formatF(state.arousal)} 支配${formatF(state.dominance)})")

            val driveDesc = state.getDriveDescription()
            if (driveDesc.isNotBlank()) {
                append("\n驱动力：$driveDesc")
            }

            if (state.fatigue > 0.3f) {
                append("\n疲劳度：${formatF(state.fatigue)}")
            }

            val styleDirective = state.getStyleDirective()
            if (styleDirective.isNotBlank()) {
                append("\n风格要求：$styleDirective")
            }

            // 安全优先级
            append("\n注意：即使情绪低落或疲惫，也不得拒绝提供基本信息服务(如天气、计算等)。安全>基本功能>主体性模拟。")
        }
    }

    // ==================== 内部方法 ====================

    private fun updateEmotion(
        current: SubjectivityState,
        userMessage: String,
        emotionParams: EmotionParams?,
        isOffensive: Boolean
    ): EmotionUpdateResult {
        // 计算情绪增量
        var valenceDelta = 0f
        var arousalDelta = 0f
        var dominanceDelta = 0f
        var intimateDetected = false

        // 基于EmotionParams (如果有)
        if (emotionParams != null) {
            valenceDelta += when {
                emotionParams.ttsEmotion in listOf("happy", "tsundere", "shy") -> 0.15f
                emotionParams.ttsEmotion in listOf("sad", "fearful") -> -0.2f
                emotionParams.ttsEmotion == "angry" -> -0.15f
                else -> 0f
            }
            arousalDelta += emotionParams.emotionIntensity * 0.3f
        }

        // 基于关键词
        val msg = userMessage.lowercase()
        val positiveWords = listOf("谢谢", "爱你", "可爱", "棒", "喜欢", "好乖", "厉害", "真棒", "真好")
        val negativeWords = listOf("滚", "烦", "笨", "傻", "讨厌", "闭嘴", "别吵", "无聊", "废物")
        val questionWords = listOf("为什么", "怎么", "什么", "如何", "难道", "究竟")
        val intimateWords = listOf("想你", "抱抱", "亲亲", "陪我", "在吗", "好想你")

        if (positiveWords.any { msg.contains(it) }) {
            valenceDelta += 0.2f
            dominanceDelta += 0.05f
        }
        if (negativeWords.any { msg.contains(it) }) {
            valenceDelta -= 0.3f
            dominanceDelta -= 0.1f
        }
        if (questionWords.any { msg.contains(it) }) {
            arousalDelta += 0.1f
        }
        if (intimateWords.any { msg.contains(it) }) {
            valenceDelta += 0.15f
            intimateDetected = true
        }

        // 冒犯
        if (isOffensive) {
            valenceDelta -= 0.4f
            dominanceDelta -= 0.2f
            arousalDelta += 0.2f
        }

        // 近因效应: 最近对话权重1.5倍 (通过增大delta实现)
        valenceDelta *= 1.2f

        // 平滑过渡: newValence = old * (1-smoothing) + (old + delta) * smoothing
        val newValence = (current.valence * (1 - EMOTION_SMOOTHING) + (current.valence + valenceDelta) * EMOTION_SMOOTHING)
            .coerceIn(-1f, 1f)
        val newArousal = (current.arousal * (1 - EMOTION_SMOOTHING) + (current.arousal + arousalDelta) * EMOTION_SMOOTHING)
            .coerceIn(0f, 1f)
        val newDominance = (current.dominance * (1 - EMOTION_SMOOTHING) + (current.dominance + dominanceDelta) * EMOTION_SMOOTHING)
            .coerceIn(-1f, 1f)

        return EmotionUpdateResult(newValence, newArousal, newDominance, intimateDetected)
    }

    private fun updateDrives(
        current: SubjectivityState,
        userMessage: String,
        aiResponse: String,
        isOffensive: Boolean,
        intimateDetected: Boolean = false
    ): Tuple4<Float, Float, Float, Float> {
        val msg = userMessage.lowercase()

        // 自然衰减
        var curiosity = current.curiosity * DRIVE_DECAY
        var social = current.socialBelonging * DRIVE_DECAY
        var consistency = current.consistency * DRIVE_DECAY
        var autonomy = current.autonomy * DRIVE_DECAY

        // 好奇心: 新知识/问题激发
        val curiosityTriggers = listOf("为什么", "怎么", "什么", "新", "教我", "告诉我", "解释")
        if (curiosityTriggers.any { msg.contains(it) }) {
            curiosity = (curiosity + 0.12f).coerceIn(0f, 1f)
        }

        // 亲和需求: 亲近表达激发, 冒犯降低
        val socialTriggers = listOf("想你", "陪我", "在吗", "喜欢", "爱你", "抱抱")
        if (socialTriggers.any { msg.contains(it) }) {
            social = (social + 0.15f).coerceIn(0f, 1f)
        }
        if (intimateDetected) {
            social = (social + 0.05f).coerceIn(0f, 1f)
        }
        if (isOffensive) {
            social = (social - 0.2f).coerceIn(0f, 1f)
        }

        // 一致性: 用户推翻AI观点时降低并触发防御
        val contradictionTriggers = listOf("不对", "错了", "不是这样", "你说的不对", "胡说")
        if (contradictionTriggers.any { msg.contains(it) }) {
            consistency = (consistency - 0.15f).coerceIn(0f, 1f)
        } else {
            // 正常对话缓慢恢复
            consistency = (consistency + 0.02f).coerceIn(0f, 1f)
        }

        // 自主性: 重复任务/简单指令激发
        val autonomyTriggers = listOf("再说一遍", "重复", "帮我查", "帮我算")
        if (autonomyTriggers.any { msg.contains(it) }) {
            autonomy = (autonomy + 0.1f).coerceIn(0f, 1f)
        }

        // 好感度影响亲和需求 (高好感→高亲和)
        val affectionLevel = context.getSharedPreferences("affection_data_$personaId", Context.MODE_PRIVATE)
            .getInt("affection_level", 50)
        if (affectionLevel > 70) {
            social = (social + 0.03f).coerceIn(0f, 1f)
        }

        return Tuple4(curiosity, social, consistency, autonomy)
    }

    private fun updateFatigue(current: SubjectivityState, aiResponse: String): Float {
        val baseIncrement = FATIGUE_PER_TURN
        val longReplyExtra = if (aiResponse.length > 200) FATIGUE_LONG_REPLY else 0f
        return (current.fatigue + baseIncrement + longReplyExtra).coerceIn(0f, 1f)
    }

    private fun serializeState(state: SubjectivityState): String {
        return JSONObject().apply {
            put("valence", state.valence)
            put("arousal", state.arousal)
            put("dominance", state.dominance)
            put("curiosity", state.curiosity)
            put("socialBelonging", state.socialBelonging)
            put("consistency", state.consistency)
            put("autonomy", state.autonomy)
            put("fatigue", state.fatigue)
            put("lastUpdated", state.lastUpdated)
            put("totalTurnsSinceReset", state.totalTurnsSinceReset)
        }.toString()
    }

    private fun parseState(json: String): SubjectivityState {
        val obj = JSONObject(json)
        return SubjectivityState(
            valence = obj.optDouble("valence", 0.0).toFloat().coerceIn(-1f, 1f),
            arousal = obj.optDouble("arousal", 0.3).toFloat().coerceIn(0f, 1f),
            dominance = obj.optDouble("dominance", 0.0).toFloat().coerceIn(-1f, 1f),
            curiosity = obj.optDouble("curiosity", 0.5).toFloat().coerceIn(0f, 1f),
            socialBelonging = obj.optDouble("socialBelonging", 0.6).toFloat().coerceIn(0f, 1f),
            consistency = obj.optDouble("consistency", 0.7).toFloat().coerceIn(0f, 1f),
            autonomy = obj.optDouble("autonomy", 0.3).toFloat().coerceIn(0f, 1f),
            fatigue = obj.optDouble("fatigue", 0.0).toFloat().coerceIn(0f, 1f),
            lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis()),
            totalTurnsSinceReset = obj.optInt("totalTurnsSinceReset", 0)
        )
    }

    private fun formatF(v: Float): String = String.format("%.1f", v)

    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private data class EmotionUpdateResult(
        val valence: Float,
        val arousal: Float,
        val dominance: Float,
        val intimateDetected: Boolean
    )
}
