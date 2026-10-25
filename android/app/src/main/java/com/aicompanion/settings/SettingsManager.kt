/** 设置管理器: SharedPreferences统一封装, API配置/功能开关/偏好设置读写 */
package com.aicompanion.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.aicompanion.settings.ScheduledWake

class SettingsManager(context: Context) {

    companion object {
        /** TTS 播放模式：AI 回复后直接朗读 */
        const val TTS_MODE_AUTO_PLAY = "auto_play"
        /** TTS 播放模式：只生成语音气泡，用户点击才播放 */
        const val TTS_MODE_BUBBLE_ONLY = "bubble_only"

        /** 回复呈现方式：分段拟人（按句拆成多条气泡，默认） */
        const val REPLY_MODE_SPLIT = 0
        /** 回复呈现方式：一次性整段输出（单个气泡） */
        const val REPLY_MODE_SINGLE = 1
        /** 回复呈现方式：流式逐字输出（边生成边显示） */
        const val REPLY_MODE_STREAM = 2

        /** 「AI 工具调用最大轮次」的默认值与取值范围（设置页滑块与此保持一致） */
        const val DEFAULT_TOOL_LOOP_MAX_ROUNDS = 20
        const val MAX_TOOL_LOOP_ROUNDS = 50
    }

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        "companion_settings",
        Context.MODE_PRIVATE
    )

    private val securePrefs: SharedPreferences get() = SecurePrefsHelper.get(appContext)

    fun isSecureStorageAvailable(): Boolean = !SecurePrefsHelper.isFallbackActive()

    /**
     * AI 操作模式：auto=全程允许 / confirm=需确认后才执行。
     * 默认 auto（最大权限）：为便于自动化测试，默认不再向用户确认，直接按 AI 决策执行。
     */
    var aiOperationMode: String
        get() = prefs.getString("ai_operation_mode", "auto") ?: "auto"
        set(value) { prefs.edit().putString("ai_operation_mode", value).apply() }

    /** AI 操作动作间冷却时间（毫秒） */
    var aiOperationCooldownMs: Int
        get() = prefs.getInt("ai_operation_cooldown_ms", 200)
        set(value) { prefs.edit().putInt("ai_operation_cooldown_ms", value.coerceIn(0, 5000)).apply() }

    /** AI 操作最大轮次（复杂任务可调高） */
    var aiOperationMaxRounds: Int
        get() = prefs.getInt("ai_operation_max_rounds", 15)
        set(value) { prefs.edit().putInt("ai_operation_max_rounds", value.coerceIn(1, 50)).apply() }

    /**
     * 聊天 Agent 的「工具调用」最大轮次。
     * 之前硬编码为 3（ApiClient.sendChatWithToolLoop），导致多步任务（如操作手机）
     * 在第 3 次工具调用后被迫中断并吐一句敷衍话。
     * 现已可调：默认 20 轮，范围 1~50。
     * 老用户若从未拖过滑块，prefs 里没有该 key，会自动用上新的默认值 20。
     */
    var aiToolLoopMaxRounds: Int
        get() = prefs.getInt("ai_tool_loop_max_rounds", DEFAULT_TOOL_LOOP_MAX_ROUNDS)
        set(value) {
            val v = value.coerceIn(1, MAX_TOOL_LOOP_ROUNDS)
            prefs.edit().putInt("ai_tool_loop_max_rounds", v).apply()
            com.aicompanion.network.ApiClient.defaultToolLoopMaxIterations = v
        }

    /**
     * AI 回复呈现方式：
     * · [REPLY_MODE_SPLIT]（默认）= 按句拆成多条气泡，模拟真人一句一句打字
     * · [REPLY_MODE_SINGLE]       = 整段回复只发一个气泡
     * · [REPLY_MODE_STREAM]       = 边生成边显示（SSE 流式），单气泡 + 闪烁光标
     *
     * 旧版本代码里有一个 app_prefs.pref_ai_no_split 开关（读取处存在但从未被写入，
     * 属于"死配置"），这里做一次迁移：若旧开关为 true 则默认切到「一次性」。
     */
    var aiReplyMode: Int
        get() {
            if (prefs.contains("ai_reply_mode")) {
                return prefs.getInt("ai_reply_mode", REPLY_MODE_SPLIT).coerceIn(0, 2)
            }
            val legacyNoSplit = try {
                appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getBoolean("pref_ai_no_split", false)
            } catch (_: Exception) { false }
            val migrated = if (legacyNoSplit) REPLY_MODE_SINGLE else REPLY_MODE_SPLIT
            prefs.edit().putInt("ai_reply_mode", migrated).apply()
            return migrated
        }
        set(value) {
            prefs.edit().putInt("ai_reply_mode", value.coerceIn(0, 2)).apply()
        }

    /** 是否启用流式回复 */
    val isStreamReply: Boolean get() = aiReplyMode == REPLY_MODE_STREAM

    /**
     * 手机操作策略：
     * · reactive（默认）= 每一步执行后重新读取屏幕再规划（观察-行动闭环）
     * · single_shot     = 旧行为，一次规划整段动作序列后盲执行
     */
    var phoneOpStrategy: String
        get() = prefs.getString("phone_op_strategy", "reactive") ?: "reactive"
        set(value) { prefs.edit().putString("phone_op_strategy", value).apply() }

    /**
     * 喂给 LLM 的屏幕格式：tree = 结构化 UI 树(稳定 id/层级/role)，text = 压扁文本(旧版降级)。
     * 默认 tree，即"让 AI 真正看到 UI 树"。
     */
    var phoneOpScreenFormat: String
        get() = prefs.getString("phone_op_screen_format", "tree") ?: "tree"
        set(value) { prefs.edit().putString("phone_op_screen_format", value).apply() }

    /** 反应式循环中，检测到页面变化就中断剩余动作、重新观察 */
    var phoneOpObserveOnPageChange: Boolean
        get() = prefs.getBoolean("phone_op_observe_on_change", true)
        set(value) { prefs.edit().putBoolean("phone_op_observe_on_change", value).apply() }

    /** UI 树采集最大深度（旧实现硬编码 3，真实 App 层级常达 10+ 层） */
    var uiTreeMaxDepth: Int
        get() = prefs.getInt("ui_tree_max_depth", 8)
        set(value) { prefs.edit().putInt("ui_tree_max_depth", value.coerceIn(3, 20)).apply() }

    /** UI 树单次采集最多登记多少可点击元素（旧实现硬编码 50 且到顶后静默丢弃整棵剩余子树） */
    var uiTreeMaxElements: Int
        get() = prefs.getInt("ui_tree_max_elements", 150)
        set(value) { prefs.edit().putInt("ui_tree_max_elements", value.coerceIn(30, 400)).apply() }

    /** Skill（技能库）开关：AI 创建/使用技能 */
    var skillEnabled: Boolean
        get() = prefs.getBoolean("skill_enabled", true)
        set(value) { prefs.edit().putBoolean("skill_enabled", value).apply() }

    /** 允许 AI 读写文件（工作区限应用自己的目录，越界会被拒绝） */
    var fileOpsEnabled: Boolean
        get() = prefs.getBoolean("file_ops_enabled", true)
        set(value) { prefs.edit().putBoolean("file_ops_enabled", value).apply() }

    /** 允许 AI 在设备上执行 shell 命令（默认关闭） */
    var shellEnabled: Boolean
        get() = prefs.getBoolean("shell_enabled", false)
        set(value) { prefs.edit().putBoolean("shell_enabled", value).apply() }

    /** 允许执行被判定为高危的 shell 命令（默认关闭） */
    var shellAllowDangerous: Boolean
        get() = prefs.getBoolean("shell_allow_dangerous", false)
        set(value) { prefs.edit().putBoolean("shell_allow_dangerous", value).apply() }

    /** shell 执行通道：auto / app / root / shizuku */
    var shellMode: String
        get() = prefs.getString("shell_mode", "auto") ?: "auto"
        set(value) { prefs.edit().putString("shell_mode", value).apply() }

    /** shell 执行超时（毫秒） */
    var shellTimeoutMs: Int
        get() = prefs.getInt("shell_timeout_ms", 20000)
        set(value) { prefs.edit().putInt("shell_timeout_ms", value.coerceIn(3000, 120000)).apply() }

    /**
     * 异常检测开关（检测到支付/验证码/登录页时暂停）。
     * 默认关闭（最大权限）：为便于自动化测试，默认不做敏感页拦截；需防护时可手动打开。
     */
    var abnormalDetectionEnabled: Boolean
        get() = prefs.getBoolean("abnormal_detection_enabled", false)
        set(value) { prefs.edit().putBoolean("abnormal_detection_enabled", value).apply() }

    /** 长按手势开关 */
    var longPressEnabled: Boolean
        get() = prefs.getBoolean("long_press_enabled", true)
        set(value) { prefs.edit().putBoolean("long_press_enabled", value).apply() }

    var screenRecognitionEnabled: Boolean
        get() = prefs.getBoolean("screen_recognition", false)
        set(value) {
            prefs.edit().putBoolean("screen_recognition", value).apply()
            onScreenRecognitionChanged?.invoke(value)
        }

    var voiceRecognitionEnabled: Boolean
        get() = prefs.getBoolean("voice_recognition", false)
        set(value) {
            prefs.edit().putBoolean("voice_recognition", value).apply()
            onVoiceRecognitionChanged?.invoke(value)
        }

    var asrMode: String
        get() = prefs.getString("asr_mode", com.aicompanion.voice.LocalAsrManager.MODE_CLOUD) ?: com.aicompanion.voice.LocalAsrManager.MODE_CLOUD
        set(value) { prefs.edit().putString("asr_mode", value).apply() }

    // ---------------------------------------------------------------- 语音唤醒

    /** 语音唤醒总开关（离线 KWS 常驻监听） */
    var voiceWakeEnabled: Boolean
        get() = prefs.getBoolean("voice_wake_enabled", false)
        set(value) { prefs.edit().putBoolean("voice_wake_enabled", value).apply() }

    /** 唤醒词：多个用逗号或换行分隔，支持中文（运行时转拼音）或直接写拼音 */
    var voiceWakeWords: String
        get() = prefs.getString("voice_wake_words", com.aicompanion.voice.wake.VoiceWake.DEFAULT_WORDS)
            ?: com.aicompanion.voice.wake.VoiceWake.DEFAULT_WORDS
        set(value) { prefs.edit().putString("voice_wake_words", value.trim()).apply() }

    /** 触发阈值：越低越灵敏、越容易误唤醒（0.05~0.90） */
    var voiceWakeThreshold: Float
        get() = prefs.getFloat("voice_wake_threshold", 0.25f).coerceIn(0.05f, 0.90f)
        set(value) { prefs.edit().putFloat("voice_wake_threshold", value.coerceIn(0.05f, 0.90f)).apply() }

    // ---------------------------------------------------------------- 功能引导进度

    /** 已读过的引导步骤，key = "topicId#stepIndex" */
    fun guideDone(): Set<String> =
        prefs.getStringSet("guide_done_steps", emptySet()) ?: emptySet()

    fun markGuideDone(key: String) {
        prefs.edit().putStringSet("guide_done_steps", guideDone() + key).apply()
    }

    fun clearGuideProgress() {
        prefs.edit().remove("guide_done_steps").apply()
    }

    /** 唤醒后是否震动提示 */
    var voiceWakeVibrate: Boolean
        get() = prefs.getBoolean("voice_wake_vibrate", true)
        set(value) { prefs.edit().putBoolean("voice_wake_vibrate", value).apply() }

    var offlineModeEnabled: Boolean
        get() = prefs.getBoolean("offline_mode", false)
        set(value) {
            prefs.edit().putBoolean("offline_mode", value).apply()
        }

    var isTTSEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
        set(value) {
            prefs.edit().putBoolean("tts_enabled", value).apply()
        }

    var ttsEnabled: Boolean
        get() = isTTSEnabled
        set(value) { isTTSEnabled = value }

    var ttsEngineMode: String
        get() = prefs.getString("tts_engine_mode", com.aicompanion.voice.TtsManager.ENGINE_EDGE) ?: com.aicompanion.voice.TtsManager.ENGINE_EDGE
        set(value) { prefs.edit().putString("tts_engine_mode", value).apply() }

    /** TTS 播放模式：auto_play（直接朗读）/ bubble_only（仅语音气泡） */
    var ttsPlayMode: String
        get() = prefs.getString("tts_play_mode", TTS_MODE_AUTO_PLAY) ?: TTS_MODE_AUTO_PLAY
        set(value) { prefs.edit().putString("tts_play_mode", value).apply() }

    var offlineMode: Boolean
        get() = offlineModeEnabled
        set(value) { offlineModeEnabled = value }

    var chatApiUrl: String
        get() = prefs.getString("chat_api_url", "") ?: ""
        set(value) { prefs.edit().putString("chat_api_url", value).apply() }

    var chatApiKey: String
        get() = securePrefs.getString("chat_api_key", "") ?: ""
        set(value) { securePrefs.edit().putString("chat_api_key", value).apply() }

    var chatModel: String
        get() = prefs.getString("chat_model", "gpt-4o-mini") ?: "gpt-4o-mini"
        set(value) { prefs.edit().putString("chat_model", value).apply() }

    var apiProvider: String
        get() = prefs.getString("api_provider", "custom") ?: "custom"
        set(value) { prefs.edit().putString("api_provider", value).apply() }

    var ttsProvider: String
        get() = prefs.getString("tts_provider", "custom") ?: "custom"
        set(value) { prefs.edit().putString("tts_provider", value).apply() }

    var asrProvider: String
        get() = prefs.getString("asr_provider", "custom") ?: "custom"
        set(value) { prefs.edit().putString("asr_provider", value).apply() }

    var imageGenProvider: String
        get() = prefs.getString("image_gen_provider", "custom") ?: "custom"
        set(value) { prefs.edit().putString("image_gen_provider", value).apply() }

    var imageRecogProvider: String
        get() = prefs.getString("image_recog_provider", "custom") ?: "custom"
        set(value) { prefs.edit().putString("image_recog_provider", value).apply() }

    var screenApiUrl: String
        get() = prefs.getString("screen_api_url", "") ?: ""
        set(value) { prefs.edit().putString("screen_api_url", value).apply() }

    var screenModel: String
        get() = prefs.getString("screen_model", "gpt-4o") ?: "gpt-4o"
        set(value) { prefs.edit().putString("screen_model", value).apply() }

    var asrApiUrl: String
        get() = prefs.getString("asr_api_url", "") ?: ""
        set(value) { prefs.edit().putString("asr_api_url", value).apply() }

    var asrModel: String
        get() = prefs.getString("asr_model", "whisper-1") ?: "whisper-1"
        set(value) { prefs.edit().putString("asr_model", value).apply() }

    var useLocalOcr: Boolean
        get() = prefs.getBoolean("use_local_ocr", true)
        set(value) { prefs.edit().putBoolean("use_local_ocr", value).apply() }

    var useChatModelForVision: Boolean
        get() = prefs.getBoolean("use_chat_model_for_vision", true)
        set(value) { prefs.edit().putBoolean("use_chat_model_for_vision", value).apply() }

    var ttsApiUrl: String
        get() = prefs.getString("tts_api_url", "") ?: ""
        set(value) { prefs.edit().putString("tts_api_url", value).apply() }

    var ttsModel: String
        get() = prefs.getString("tts_model", "tts-1") ?: "tts-1"
        set(value) { prefs.edit().putString("tts_model", value).apply() }

    var ttsVoice: String
        get() = prefs.getString("tts_voice", "alloy") ?: "alloy"
        set(value) { prefs.edit().putString("tts_voice", value).apply() }

    var userPersonalityDef: String
        get() = prefs.getString("user_personality_def", "") ?: ""
        set(value) { prefs.edit().putString("user_personality_def", value).apply() }

    var aiSummarizedPersonality: String
        get() = prefs.getString("ai_summarized_personality", "") ?: ""
        set(value) { prefs.edit().putString("ai_summarized_personality", value).apply() }

    fun getAiSummarizedPersonality(personaId: String): String {
        if (personaId == "default") return aiSummarizedPersonality
        return prefs.getString("ai_summarized_personality_$personaId", "") ?: ""
    }

    fun setAiSummarizedPersonality(personaId: String, value: String) {
        if (personaId == "default") {
            aiSummarizedPersonality = value
        } else {
            prefs.edit().putString("ai_summarized_personality_$personaId", value).apply()
        }
    }

    var lastPersonalitySummaryAffection: Int
        get() = prefs.getInt("last_personality_summary_affection", 0)
        set(value) { prefs.edit().putInt("last_personality_summary_affection", value).apply() }

    var screenApiKey: String
        get() = securePrefs.getString("screen_api_key", "") ?: ""
        set(value) { securePrefs.edit().putString("screen_api_key", value).apply() }

    var asrApiKey: String
        get() = securePrefs.getString("asr_api_key", "") ?: ""
        set(value) { securePrefs.edit().putString("asr_api_key", value).apply() }

    var ttsApiKey: String
        get() = securePrefs.getString("tts_api_key", "") ?: ""
        set(value) { securePrefs.edit().putString("tts_api_key", value).apply() }

    var ttsVoiceName: String
        get() = prefs.getString("tts_voice_name", "") ?: ""
        set(value) { prefs.edit().putString("tts_voice_name", value).apply() }

    var llmTemperature: Float
        get() = prefs.getFloat("llm_temperature", 1.05f)
        set(value) { prefs.edit().putFloat("llm_temperature", value.coerceIn(0f, 2f)).apply() }

    var llmTopP: Float
        get() = prefs.getFloat("llm_top_p", 0.92f)
        set(value) { prefs.edit().putFloat("llm_top_p", value.coerceIn(0f, 1f)).apply() }

    var llmFrequencyPenalty: Float
        get() = prefs.getFloat("llm_frequency_penalty", 0.35f)
        set(value) { prefs.edit().putFloat("llm_frequency_penalty", value.coerceIn(-2f, 2f)).apply() }

    var llmPresencePenalty: Float
        get() = prefs.getFloat("llm_presence_penalty", 0.5f)
        set(value) { prefs.edit().putFloat("llm_presence_penalty", value.coerceIn(-2f, 2f)).apply() }

    var llmMaxTokens: Int
        get() = prefs.getInt("llm_max_tokens", 500)
        set(value) {
            val limit = ProviderProfile.getMaxTokensLimit(apiProvider)
            prefs.edit().putInt("llm_max_tokens", value.coerceIn(100, limit)).apply()
        }

    var contextTurns: Int
        get() = prefs.getInt("context_turns", 10)
        set(value) { prefs.edit().putInt("context_turns", value.coerceIn(5, 50)).apply() }

    fun getEffectiveMaxTokensLimit(): Int = ProviderProfile.getMaxTokensLimit(apiProvider)

    fun getCurrentProfile(): ProviderProfile = ProviderProfile.getProfile(apiProvider)

    var ttsPitch: Float
        get() = prefs.getFloat("tts_pitch", 1.0f)
        set(value) { prefs.edit().putFloat("tts_pitch", value.coerceIn(0.5f, 2.0f)).apply() }

    var ttsRate: Float
        get() = prefs.getFloat("tts_rate", 1.0f)
        set(value) { prefs.edit().putFloat("tts_rate", value.coerceIn(0.5f, 2.0f)).apply() }

    var appLoggingEnabled: Boolean
        get() = prefs.getBoolean("app_logging_enabled", true)
        set(value) = prefs.edit().putBoolean("app_logging_enabled", value).apply()

    var appDebugVerbose: Boolean
        get() = prefs.getBoolean("app_debug_verbose", false)
        set(value) = prefs.edit().putBoolean("app_debug_verbose", value).apply()

    var emotionAnalysisEnabled: Boolean
        get() = prefs.getBoolean("emotion_analysis_enabled", false)
        set(value) = prefs.edit().putBoolean("emotion_analysis_enabled", value).apply()

    var llmEmotionAnalysisEnabled: Boolean
        get() = emotionAnalysisEnabled || prefs.getBoolean("llm_emotion_analysis_enabled", true)
        set(value) { prefs.edit().putBoolean("llm_emotion_analysis_enabled", value).apply() }

    var searchProvider: String
        get() = prefs.getString("search_provider", "duckduckgo") ?: "duckduckgo"
        set(value) { prefs.edit().putString("search_provider", value).apply() }

    var searchApiUrl: String
        get() = prefs.getString("search_api_url", "") ?: ""
        set(value) { prefs.edit().putString("search_api_url", value).apply() }

    var searchApiKey: String
        get() = securePrefs.getString("search_api_key", "") ?: ""
        set(value) { securePrefs.edit().putString("search_api_key", value).apply() }

    var searchEngineId: String
        get() = prefs.getString("search_engine_id", "") ?: ""
        set(value) { prefs.edit().putString("search_engine_id", value).apply() }

    var searchEnabled: Boolean
        get() = prefs.getBoolean("search_enabled", true)
        set(value) { prefs.edit().putBoolean("search_enabled", value).apply() }

    var live2dEnabled: Boolean
        get() = prefs.getBoolean("live2d_enabled", true)
        set(value) {
            prefs.edit().putBoolean("live2d_enabled", value).apply()
            onLive2DEnabledChanged?.invoke(value)
        }

    // 开机自启动（默认开启）
    var autoStart: Boolean
        get() = prefs.getBoolean("auto_start", true)
        set(value) { prefs.edit().putBoolean("auto_start", value).apply() }

    // 后台运行（默认开启）
    var backgroundRunning: Boolean
        get() = prefs.getBoolean("background_running", true)
        set(value) { prefs.edit().putBoolean("background_running", value).apply() }

    // 日记触发模式
    var diaryTriggerMode: DiaryTriggerMode
        get() {
            val value = prefs.getString("diary_trigger_mode", "daily_10pm") ?: "daily_10pm"
            return try {
                when (value.uppercase()) {
                    "TURNS_75" -> DiaryTriggerMode.MSG_50
                    "DAILY_10PM_AND_TURNS_75" -> DiaryTriggerMode.MSG_50
                    else -> DiaryTriggerMode.valueOf(value.uppercase())
                }
            } catch (e: Exception) {
                DiaryTriggerMode.DAILY_10PM
            }
        }
        set(value) {
            prefs.edit().putString("diary_trigger_mode", value.name.lowercase()).apply()
        }

    var simpleScreenMode: Boolean
        get() = prefs.getBoolean("simple_screen_mode", false)
        set(value) { prefs.edit().putBoolean("simple_screen_mode", value).apply() }

    var nagFrequency: NagFrequency
        get() {
            val value = prefs.getString("nag_frequency", "medium") ?: "medium"
            return try {
                NagFrequency.valueOf(value.uppercase())
            } catch (e: Exception) {
                NagFrequency.MEDIUM
            }
        }
        set(value) {
            prefs.edit().putString("nag_frequency", value.name.lowercase()).apply()
        }

    var userId: String
        get() = prefs.getString("user_id", "") ?: generateUserId()
        set(value) {
            prefs.edit().putString("user_id", value).apply()
        }

    var userGender: String
        get() = prefs.getString("user_gender", "") ?: ""
        set(value) { prefs.edit().putString("user_gender", value).apply() }

    var userBirthday: String
        get() = prefs.getString("user_birthday", "") ?: ""
        set(value) { prefs.edit().putString("user_birthday", value).apply() }

    var userAppearance: String
        get() = prefs.getString("user_appearance", "") ?: ""
        set(value) { prefs.edit().putString("user_appearance", value).apply() }

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) { prefs.edit().putBoolean("onboarding_completed", value).apply() }

    var chatBackground: String
        get() = prefs.getString("chat_background", "") ?: ""
        set(value) { prefs.edit().putString("chat_background", value).apply() }

    // 定时唤醒
    var wakeEnabled: Boolean
        get() = prefs.getBoolean("wake_enabled", false)
        set(value) { prefs.edit().putBoolean("wake_enabled", value).apply() }

    var wakeHour: Int
        get() = prefs.getInt("wake_hour", 8)
        set(value) {
            if (value in 0..23) {
                prefs.edit().putInt("wake_hour", value).apply()
            }
        }

    var wakeMinute: Int
        get() = prefs.getInt("wake_minute", 0)
        set(value) {
            if (value in 0..59) {
                prefs.edit().putInt("wake_minute", value).apply()
            }
        }

    var wakeMessage: String
        get() = prefs.getString("wake_message", "") ?: "早上好！今天想聊点什么？"
        set(value) {
            prefs.edit().putString("wake_message", value.take(500)).apply()
        }

    fun getScheduledWakes(): List<ScheduledWake> {
        val json = prefs.getString("scheduled_wakes", "[]") ?: "[]"
        return try {
            val array = org.json.JSONArray(json)
            val list = mutableListOf<ScheduledWake>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(ScheduledWake(
                    id = obj.optLong("id", System.currentTimeMillis()),
                    time = obj.getString("time"),
                    message = obj.getString("message"),
                    enabled = obj.optBoolean("enabled", true),
                    daysOfWeek = obj.optInt("daysOfWeek", -1) // -1 = 每天
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setScheduledWakes(wakes: List<ScheduledWake>) {
        val array = org.json.JSONArray()
        wakes.forEach { w ->
            val obj = org.json.JSONObject()
            obj.put("id", w.id)
            obj.put("time", w.time)
            obj.put("message", w.message)
            obj.put("enabled", w.enabled)
            obj.put("daysOfWeek", w.daysOfWeek)
            array.put(obj)
        }
        prefs.edit().putString("scheduled_wakes", array.toString()).apply()
    }

    var onScreenRecognitionChanged: ((Boolean) -> Unit)? = null
    var onVoiceRecognitionChanged: ((Boolean) -> Unit)? = null
    var onLive2DEnabledChanged: ((Boolean) -> Unit)? = null
    var onBatteryLow: (() -> Unit)? = null

    fun shouldTriggerNag(): Boolean {
        return when (nagFrequency) {
            NagFrequency.LOW -> Math.random() < 0.1
            NagFrequency.MEDIUM -> Math.random() < 0.3
            NagFrequency.HIGH -> Math.random() < 0.6
            NagFrequency.OFF -> false
        }
    }

    val isNagEnabled: Boolean get() = nagFrequency != NagFrequency.OFF

    fun getNagFrequency(): String = when (nagFrequency) {
        NagFrequency.HIGH -> "frequent"
        NagFrequency.LOW -> "infrequent"
        NagFrequency.MEDIUM -> "normal"
        NagFrequency.OFF -> "normal"
    }

    fun clearAllData() {
        prefs.edit().clear().apply()
        try {
            SecurePrefsHelper.clearAll(appContext)
        } catch (_: Exception) {}
    }

    private fun generateUserId(): String {
        val id = "anon_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
        prefs.edit().putString("user_id", id).apply()
        return id
    }
}

enum class NagFrequency {
    LOW, MEDIUM, HIGH, OFF
}

enum class DiaryTriggerMode {
    MANUAL,
    MSG_50,
    HOURLY,
    EVERY_2H,
    DAILY_10PM
}

data class ScheduledWake(
    val id: Long,
    val time: String, // "HH:mm" format
    val message: String,
    val enabled: Boolean = true,
    val daysOfWeek: Int = -1 // -1 = 每天, 0=周日, 1=周一, etc.
)

