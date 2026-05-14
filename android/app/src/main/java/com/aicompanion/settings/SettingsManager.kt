/** 设置管理器: SharedPreferences统一封装, API配置/功能开关/偏好设置读写 */
package com.aicompanion.settings

import android.content.Context
import android.content.SharedPreferences
import com.aicompanion.settings.ScheduledWake

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "companion_settings",
        Context.MODE_PRIVATE
    )

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

    var offlineMode: Boolean
        get() = offlineModeEnabled
        set(value) { offlineModeEnabled = value }

    var chatApiUrl: String
        get() = prefs.getString("chat_api_url", "") ?: ""
        set(value) { prefs.edit().putString("chat_api_url", value).apply() }

    var chatApiKey: String
        get() = prefs.getString("chat_api_key", "") ?: ""
        set(value) { prefs.edit().putString("chat_api_key", value).apply() }

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

    var ttsApiUrl: String
        get() = prefs.getString("tts_api_url", "") ?: ""
        set(value) { prefs.edit().putString("tts_api_url", value).apply() }

    var ttsModel: String
        get() = prefs.getString("tts_model", "tts-1") ?: "tts-1"
        set(value) { prefs.edit().putString("tts_model", value).apply() }

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
            val value = prefs.getString("diary_trigger_mode", "messages_50") ?: "messages_50"
            return try {
                DiaryTriggerMode.valueOf(value.uppercase())
            } catch (e: Exception) {
                DiaryTriggerMode.MESSAGES_50
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
    MANUAL,       // 仅手动
    MESSAGES_50,  // 每50条消息
    HOURLY,       // 每小时
    TWO_HOURS,    // 每2小时
    DAILY_10PM    // 每晚10点
}

data class ScheduledWake(
    val id: Long,
    val time: String, // "HH:mm" format
    val message: String,
    val enabled: Boolean = true,
    val daysOfWeek: Int = -1 // -1 = 每天, 0=周日, 1=周一, etc.
)

