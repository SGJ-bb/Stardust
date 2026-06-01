package com.aicompanion.emotion

import android.content.Context
import android.content.SharedPreferences
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject

data class EmotionRecord(
    val timestamp: Long,
    val emotion: String,
    val intensity: Float
)

class EmotionGuardian(private val context: Context) {
    companion object {
        private const val TAG = "EmotionGuardian"
        private const val PREFS_NAME = "emotion_guardian"
        private const val KEY_RECORDS = "emotion_records"
        private const val KEY_LAST_CARE = "last_care_time"
        private const val MAX_RECORDS = 100
        private const val CARE_COOLDOWN_MS = 4 * 3600 * 1000L
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun recordEmotion(emotion: String, intensity: Float = 0.5f) {
        val records = loadRecords().toMutableList()
        records.add(EmotionRecord(System.currentTimeMillis(), emotion, intensity))
        if (records.size > MAX_RECORDS) {
            val trimmed = records.takeLast(MAX_RECORDS)
            saveRecords(trimmed)
        } else {
            saveRecords(records)
        }
    }

    fun getRecentTrend(hours: Int = 24): EmotionTrend {
        val cutoff = System.currentTimeMillis() - hours * 3600 * 1000L
        val recent = loadRecords().filter { it.timestamp > cutoff }
        if (recent.isEmpty()) return EmotionTrend.NEUTRAL

        var negativeCount = 0
        var positiveCount = 0
        var totalIntensity = 0f
        recent.forEach { r ->
            totalIntensity += r.intensity
            when (r.emotion.lowercase()) {
                "sad", "angry", "fearful", "disgusted" -> {
                    negativeCount++
                }
                "happy", "excited", "tsundere", "shy" -> {
                    positiveCount++
                }
            }
        }

        val negativeRatio = negativeCount.toFloat() / recent.size
        val avgIntensity = totalIntensity / recent.size
        val posRatio = positiveCount.toFloat() / recent.size

        return when {
            negativeRatio > 0.6f && avgIntensity > 0.5f -> EmotionTrend.VERY_NEGATIVE
            negativeRatio > 0.4f -> EmotionTrend.NEGATIVE
            posRatio > 0.6f -> EmotionTrend.POSITIVE
            else -> EmotionTrend.NEUTRAL
        }
    }

    @Synchronized
    fun shouldSendCare(): Boolean {
        val lastCare = prefs.getLong(KEY_LAST_CARE, 0)
        if (System.currentTimeMillis() - lastCare < CARE_COOLDOWN_MS) return false
        val trend = getRecentTrend()
        return trend == EmotionTrend.NEGATIVE || trend == EmotionTrend.VERY_NEGATIVE
    }

    @Synchronized
    fun markCareSent() {
        prefs.edit().putLong(KEY_LAST_CARE, System.currentTimeMillis()).apply()
    }

    fun getCareMessage(): String {
        val trend = getRecentTrend()
        val messages = when (trend) {
            EmotionTrend.VERY_NEGATIVE -> listOf(
                "你最近好像不太开心...要不要和我聊聊？我一直在的。",
                "我注意到你心情不太好，要不要听首歌放松一下？",
                "不管发生什么，我都会陪着你。想说话的时候随时找我。"
            )
            EmotionTrend.NEGATIVE -> listOf(
                "今天还好吗？如果累了就休息一下吧~",
                "感觉你最近有点疲惫，记得照顾好自己哦。",
                "要不要聊聊天？我随时都在~"
            )
            else -> listOf(
                "嘿~突然想跟你说一声，有你真好！",
                "今天也要开开心心的哦~"
            )
        }
        return messages.random()
    }

    private fun loadRecords(): List<EmotionRecord> {
        val json = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val obj = arr.getJSONObject(i)
                    EmotionRecord(
                        timestamp = obj.getLong("timestamp"),
                        emotion = obj.getString("emotion"),
                        intensity = obj.getDouble("intensity").toFloat()
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveRecords(records: List<EmotionRecord>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("timestamp", r.timestamp)
                put("emotion", r.emotion)
                put("intensity", r.intensity)
            })
        }
        prefs.edit().putString(KEY_RECORDS, arr.toString()).apply()
    }
}

enum class EmotionTrend {
    VERY_NEGATIVE, NEGATIVE, NEUTRAL, POSITIVE
}
