/** 好感度管理器: 根据用户消息数量/心情/互动频率计算好感度, 行为评估影响好感度变化 */
package com.aicompanion.affection

import android.content.Context
import android.content.SharedPreferences
import com.aicompanion.models.Emotion
import com.aicompanion.util.AppLogger

data class DailyStats(
    val messagesToday: Int = 0,
    val affectionChange: Int = 0
)

class AffectionManager(private val context: Context, private val personaId: String = "default") {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "affection_data_$personaId",
        Context.MODE_PRIVATE
    )

    var affectionLevel: Int
        get() = prefs.getInt("affection_level", 50)
        private set(value) {
            prefs.edit().putInt("affection_level", value.coerceIn(0, 100)).apply()
        }

    var totalInteractionDays: Int
        get() = prefs.getInt("total_days", 0)
        private set(value) {
            prefs.edit().putInt("total_days", value).apply()
        }

    var firstUseDate: Long
        get() = prefs.getLong("first_use_date", 0L)
        private set(value) {
            prefs.edit().putLong("first_use_date", value).apply()
        }

    var lastActiveDate: String
        get() = prefs.getString("last_active_date", "") ?: ""
        private set(value) {
            prefs.edit().putString("last_active_date", value).apply()
        }

    var messagesToday: Int
        get() = prefs.getInt("messages_today", 0)
        private set(value) {
            prefs.edit().putInt("messages_today", value).apply()
        }

    var affectionChangeToday: Int
        get() = prefs.getInt("affection_change_today", 0)
        private set(value) {
            prefs.edit().putInt("affection_change_today", value).apply()
        }

    init {
        if (firstUseDate == 0L) {
            firstUseDate = System.currentTimeMillis()
        }
        updateDailyStats()
    }

    fun updateDailyStats() {
        val today = getTodayString()
        if (lastActiveDate != today) {
            lastActiveDate = today
            totalInteractionDays += 1
            messagesToday = 0
            affectionChangeToday = 0
            addAffection(1)
        }
    }

    fun addMessage() {
        messagesToday += 1
    }

    fun addAffection(amount: Int, reason: String = "") {
        val current = affectionLevel
        val newValue = (current + amount).coerceIn(0, 100)
        affectionLevel = newValue
        AppLogger.w("Affection", "addAffection: +$amount -> $newValue")

        prefs.edit().putString("last_affection_change", "$reason: $current -> $newValue").apply()
    }

    fun shouldTriggerPersonalityEvolution(): Boolean {
        val personaPrefs = context.getSharedPreferences("persona_data_$personaId", Context.MODE_PRIVATE)
        val enabled = personaPrefs.getBoolean("personality_evolution_enabled", true)
        if (!enabled) return false

        val lastEvolution = personaPrefs.getInt("last_evolution_affection", 50)
        val current = affectionLevel
        if (current >= lastEvolution + 5) {
            personaPrefs.edit().putInt("last_evolution_affection", current).apply()
            AppLogger.i("Affection", "personalityEvolution triggered: affection=$current")
            return true
        }
        return false
    }

    fun shouldTriggerPersonalitySummary(): Boolean {
        val globalPrefs = context.getSharedPreferences("companion_settings", Context.MODE_PRIVATE)
        val userDef = globalPrefs.getString("user_personality_def", "") ?: ""
        if (userDef.isNotBlank()) return false

        val lastSummary = globalPrefs.getInt("last_personality_summary_affection", 0)
        val current = affectionLevel
        if (current >= lastSummary + 10) {
            globalPrefs.edit().putInt("last_personality_summary_affection", current).apply()
            AppLogger.i("Affection", "personalitySummary triggered: affection=$current")
            return true
        }
        return false
    }

    fun decreaseAffection(amount: Int, reason: String = "") {
        val current = affectionLevel
        if (current >= 90) {
            return
        }
        val newValue = (current - amount).coerceIn(0, 100)
        affectionLevel = newValue
        AppLogger.w("Affection", "decreaseAffection: -$amount -> $newValue")

        prefs.edit().putString("last_affection_change", "$reason: $current -> $newValue").apply()
    }

    fun evaluateUserBehavior(message: String, emotion: Emotion, isOffensive: Boolean = false) {
        if (isOffensive) {
            decreaseAffection(2, "用户说了冒犯的话")
            AppLogger.w("Affection", "evaluateBehavior: offensive, emotion=$emotion")
            return
        }

        when (emotion) {
            Emotion.HAPPY -> addAffection(1, "用户心情好")
            Emotion.SAD -> {}
            Emotion.ANGRY -> decreaseAffection(1, "用户对AI生气")
            Emotion.TSUNDERE -> addAffection(1, "傲娇互动")
            Emotion.SURPRISED -> {}
            Emotion.NEUTRAL -> {}
        }

        val positiveWords = listOf("谢谢", "爱你", "可爱", "棒", "喜欢", "好乖")
        val negativeWords = listOf("滚", "烦", "笨", "傻", "讨厌", "闭嘴", "别吵")

        val lowerMsg = message.lowercase()
        if (positiveWords.any { lowerMsg.contains(it) }) {
            addAffection(1, "用户说了好话")
        }
        if (negativeWords.any { lowerMsg.contains(it) }) {
            decreaseAffection(2, "用户说了不好的话")
        }
        AppLogger.w("Affection", "evaluateBehavior: emotion=$emotion, affection=$affectionLevel")
    }

    fun getAffectionTitle(): String {
        return when (affectionLevel) {
            in 0..20 -> "陌生人"
            in 21..40 -> "认识的人"
            in 41..60 -> "朋友"
            in 61..80 -> "好朋友"
            in 81..95 -> "亲密伙伴"
            in 96..100 -> "最重要的人"
            else -> "未知"
        }
    }

    fun getAffectionColor(): Int {
        return when (affectionLevel) {
            in 0..30 -> android.graphics.Color.parseColor("#f44336")
            in 31..60 -> android.graphics.Color.parseColor("#ff9800")
            in 61..80 -> android.graphics.Color.parseColor("#4caf50")
            in 81..100 -> android.graphics.Color.parseColor("#e91e63")
            else -> android.graphics.Color.GRAY
        }
    }

    fun getDaysSinceFirstUse(): Int {
        val diff = System.currentTimeMillis() - firstUseDate
        return (diff / (1000 * 60 * 60 * 24)).toInt() + 1
    }

    fun getDailyStats(): DailyStats {
        return DailyStats(messagesToday = messagesToday, affectionChange = affectionChangeToday)
    }

    private fun getTodayString(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }
}
