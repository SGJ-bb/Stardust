package com.aicompanion.stats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PersonaStatsManager(private val context: Context, private val personaId: String) {

    companion object {
        private const val PREFS_NAME_TEMPLATE = "persona_stats_%s"
        private const val KEY_TOTAL_MESSAGES = "total_messages"
        private const val KEY_USER_MESSAGES = "user_messages"
        private const val KEY_AI_MESSAGES = "ai_messages"
        private const val KEY_STICKERS_SENT = "stickers_sent"
        private const val KEY_STICKERS_RECEIVED = "stickers_received"
        private const val KEY_FIRST_CHAT_TIME = "first_chat_time"
        private const val KEY_LAST_CHAT_TIME = "last_chat_time"
        private const val KEY_TOTAL_CHAT_DAYS = "total_chat_days"
        private const val KEY_CHAT_DAYS_SET = "chat_days_set"
        private const val KEY_LONGEST_STREAK = "longest_streak"
        private const val KEY_CURRENT_STREAK = "current_streak"
        private const val KEY_EMOTION_MAP = "emotion_map"
        private const val KEY_TOTAL_WORDS_USER = "total_words_user"
        private const val KEY_TOTAL_WORDS_AI = "total_words_ai"
        private const val KEY_PEAK_CHAT_HOUR = "peak_chat_hour"
        private const val KEY_HOUR_MAP = "hour_map"
        private const val KEY_MOOD_TREND = "mood_trend"
    }

    private val prefs = context.getSharedPreferences(
        PREFS_NAME_TEMPLATE.format(personaId), Context.MODE_PRIVATE
    )

    var totalMessages: Int
        get() = prefs.getInt(KEY_TOTAL_MESSAGES, 0)
        set(v) = prefs.edit().putInt(KEY_TOTAL_MESSAGES, v).apply()

    var userMessages: Int
        get() = prefs.getInt(KEY_USER_MESSAGES, 0)
        set(v) = prefs.edit().putInt(KEY_USER_MESSAGES, v).apply()

    var aiMessages: Int
        get() = prefs.getInt(KEY_AI_MESSAGES, 0)
        set(v) = prefs.edit().putInt(KEY_AI_MESSAGES, v).apply()

    var stickersSent: Int
        get() = prefs.getInt(KEY_STICKERS_SENT, 0)
        set(v) = prefs.edit().putInt(KEY_STICKERS_SENT, v).apply()

    var stickersReceived: Int
        get() = prefs.getInt(KEY_STICKERS_RECEIVED, 0)
        set(v) = prefs.edit().putInt(KEY_STICKERS_RECEIVED, v).apply()

    var firstChatTime: Long
        get() = prefs.getLong(KEY_FIRST_CHAT_TIME, 0L)
        set(v) = prefs.edit().putLong(KEY_FIRST_CHAT_TIME, v).apply()

    var lastChatTime: Long
        get() = prefs.getLong(KEY_LAST_CHAT_TIME, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_CHAT_TIME, v).apply()

    var totalChatDays: Int
        get() = prefs.getInt(KEY_TOTAL_CHAT_DAYS, 0)
        set(v) = prefs.edit().putInt(KEY_TOTAL_CHAT_DAYS, v).apply()

    var longestStreak: Int
        get() = prefs.getInt(KEY_LONGEST_STREAK, 0)
        set(v) = prefs.edit().putInt(KEY_LONGEST_STREAK, v).apply()

    var currentStreak: Int
        get() = prefs.getInt(KEY_CURRENT_STREAK, 0)
        set(v) = prefs.edit().putInt(KEY_CURRENT_STREAK, v).apply()

    var totalWordsUser: Long
        get() = prefs.getLong(KEY_TOTAL_WORDS_USER, 0L)
        set(v) = prefs.edit().putLong(KEY_TOTAL_WORDS_USER, v).apply()

    var totalWordsAi: Long
        get() = prefs.getLong(KEY_TOTAL_WORDS_AI, 0L)
        set(v) = prefs.edit().putLong(KEY_TOTAL_WORDS_AI, v).apply()

    fun recordUserMessage(text: String) {
        userMessages = userMessages + 1
        totalMessages = totalMessages + 1
        totalWordsUser = totalWordsUser + text.length
        recordChatDay()
        recordHour()
    }

    fun recordAiMessage(text: String) {
        aiMessages = aiMessages + 1
        totalMessages = totalMessages + 1
        totalWordsAi = totalWordsAi + text.length
        recordChatDay()
        recordHour()
    }

    fun recordStickerSent() {
        stickersSent = stickersSent + 1
    }

    fun recordStickerReceived() {
        stickersReceived = stickersReceived + 1
    }

    fun recordEmotion(emotion: String) {
        val map = getEmotionMap().toMutableMap()
        map[emotion] = (map[emotion] ?: 0) + 1
        saveEmotionMap(map)
        saveMoodTrend(emotion)
    }

    fun getEmotionMap(): Map<String, Int> {
        val json = prefs.getString(KEY_EMOTION_MAP, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Int>()
            obj.keys().forEach { key -> map[key] = obj.getInt(key) }
            map
        } catch (_: Exception) { emptyMap() }
    }

    fun getTopEmotion(): Pair<String, Int>? {
        val map = getEmotionMap()
        return map.maxByOrNull { it.value }?.toPair()
    }

    fun getEmotionPercentages(): Map<String, Float> {
        val map = getEmotionMap()
        val total = map.values.sum().toFloat()
        if (total == 0f) return emptyMap()
        return map.mapValues { it.value / total * 100 }
    }

    fun getHourDistribution(): Map<Int, Int> {
        val json = prefs.getString(KEY_HOUR_MAP, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<Int, Int>()
            obj.keys().forEach { key -> map[key.toInt()] = obj.getInt(key) }
            map
        } catch (_: Exception) { emptyMap() }
    }

    fun getPeakChatHour(): Int {
        return prefs.getInt(KEY_PEAK_CHAT_HOUR, -1)
    }

    fun getMoodTrend(): List<String> {
        val json = prefs.getString(KEY_MOOD_TREND, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    fun getRecentMoodTrend(limit: Int = 20): List<String> {
        return getMoodTrend().takeLast(limit)
    }

    fun getDaysSinceFirstChat(): Int {
        val first = firstChatTime
        if (first == 0L) return 0
        val now = System.currentTimeMillis()
        return ((now - first) / (1000 * 60 * 60 * 24)).toInt()
    }

    fun getAvgMessagesPerDay(): Float {
        val days = getDaysSinceFirstChat()
        if (days == 0) return totalMessages.toFloat()
        return totalMessages.toFloat() / days
    }

    private fun recordChatDay() {
        val now = System.currentTimeMillis()
        if (firstChatTime == 0L) firstChatTime = now
        lastChatTime = now

        val dayStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            .format(java.util.Date(now))

        val daysSet = prefs.getStringSet(KEY_CHAT_DAYS_SET, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (dayStr !in daysSet) {
            daysSet.add(dayStr)
            prefs.edit().putStringSet(KEY_CHAT_DAYS_SET, daysSet).apply()
            totalChatDays = daysSet.size
            updateStreak(daysSet)
        }
    }

    private fun updateStreak(daysSet: Set<String>) {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        var streak = 0
        val sortedDays = daysSet.sortedDescending()
        if (sortedDays.isEmpty()) return

        val today = sdf.format(cal.time)
        if (sortedDays.first() != today) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            val yesterday = sdf.format(cal.time)
            if (sortedDays.first() != yesterday) {
                currentStreak = 0
                return
            }
        }

        cal.time = java.util.Date()
        for (i in 0 until 365) {
            val checkDay = sdf.format(cal.time)
            if (checkDay in daysSet) {
                streak++
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        currentStreak = streak
        if (streak > longestStreak) longestStreak = streak
    }

    private fun recordHour() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val map = getHourDistribution().toMutableMap()
        map[hour] = (map[hour] ?: 0) + 1
        saveHourMap(map)

        val peakEntry = map.maxByOrNull { it.value }
        if (peakEntry != null) {
            prefs.edit().putInt(KEY_PEAK_CHAT_HOUR, peakEntry.key).apply()
        }
    }

    private fun saveEmotionMap(map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_EMOTION_MAP, obj.toString()).apply()
    }

    private fun saveHourMap(map: Map<Int, Int>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k.toString(), v) }
        prefs.edit().putString(KEY_HOUR_MAP, obj.toString()).apply()
    }

    private fun saveMoodTrend(emotion: String) {
        val trend = getMoodTrend().toMutableList()
        trend.add(emotion)
        if (trend.size > 100) {
            val keep = trend.takeLast(100)
            val arr = JSONArray()
            keep.forEach { arr.put(it) }
            prefs.edit().putString(KEY_MOOD_TREND, arr.toString()).apply()
        } else {
            val arr = JSONArray()
            trend.forEach { arr.put(it) }
            prefs.edit().putString(KEY_MOOD_TREND, arr.toString()).apply()
        }
    }
}
