/** 设置管理器: SharedPreferences统一封装, API配置/功能开关/偏好设置读写 */
package com.aicompanion.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aicompanion.settings.ScheduledWake

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "companion_settings",
        Context.MODE_PRIVATE
    )

    private var securePrefsAvailable = true

    private val securePrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "companion_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            securePrefsAvailable = false
            com.aicompanion.util.AppLogger.e("SettingsManager", "EncryptedSharedPreferences unavailable: ${e.message}")
            context.getSharedPreferences("companion_secure_fallback", Context.MODE_PRIVATE)
        }
    }

    fun isSecureStorageAvailable(): Boolean = securePrefsAvailable

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
            prefs.edit().putInt("llm_max_tokens", value.coerceIn(50, limit)).apply()
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

    var emotionAnalysisEnabled: Boolean
        get() = prefs.getBoolean("emotion_analysis_enabled", false)
        set(value) { prefs.edit().putBoolean("emotion_analysis_enabled", value).apply() }

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
                    "DAILY_10PM_AND_TURNS_75" -> DiaryTriggerMode.DAILY_10PM
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

    var languageStyle: LanguageStyle
        get() {
            val value = prefs.getString("language_style", "normal") ?: "normal"
            return try {
                LanguageStyle.valueOf(value.uppercase())
            } catch (e: Exception) {
                LanguageStyle.NORMAL
            }
        }
        set(value) {
            prefs.edit().putString("language_style", value.name.lowercase()).apply()
        }

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

enum class LanguageStyle {
    NORMAL, TSUNDERE, CUTE
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

