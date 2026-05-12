package com.aicompanion.gamify

import android.content.Context
import android.content.SharedPreferences
import com.aicompanion.models.Achievement
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class AchievementManager(context: Context) {

    companion object {
        private const val TAG = "AchievementManager"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("achievement_data", Context.MODE_PRIVATE)
    private var achievements: MutableList<Achievement> = mutableListOf()

    private val allAchievements = listOf(
        Achievement("first_chat", "初次对话", "发送第一条消息", "💬", "chat", 1),
        Achievement("chat_10", "健谈伙伴", "累计发送10条消息", "🗣", "chat", 10),
        Achievement("chat_50", "话痨达人", "累计发送50条消息", "📢", "chat", 50),
        Achievement("chat_100", "无话不说", "累计发送100条消息", "🌟", "chat", 100),
        Achievement("streak_3", "三日之约", "连续打卡3天", "🔥", "checkin", 3),
        Achievement("streak_7", "七日同行", "连续打卡7天", "🏅", "checkin", 7),
        Achievement("streak_30", "月之羁绊", "连续打卡30天", "👑", "checkin", 30),
        Achievement("affection_30", "初见好感", "好感度达到30", "❤", "affection", 30),
        Achievement("affection_60", "亲密伙伴", "好感度达到60", "💕", "affection", 60),
        Achievement("affection_90", "灵魂羁绊", "好感度达到90", "💖", "affection", 90),
        Achievement("memory_3", "回忆初现", "累积3条记忆", "📝", "memory", 3),
        Achievement("memory_10", "记忆之书", "累积10条记忆", "📚", "memory", 10),
        Achievement("diary_5", "生活记录者", "生成5篇日记", "📔", "diary", 5),
        Achievement("diary_20", "回忆史官", "生成20篇日记", "📕", "diary", 20),
        Achievement("egg_1", "彩蛋猎人", "触发一个隐藏彩蛋", "🥚", "hidden", 1),
        Achievement("egg_3", "秘密探索者", "触发三个隐藏彩蛋", "🔮", "hidden", 3),
        Achievement("late_night", "夜猫子", "在凌晨和AI聊天", "🦉", "hidden", 1),
        Achievement("early_bird", "早起鸟", "早上6点前和AI打招呼", "🌅", "hidden", 1),
        Achievement("feedback_5", "品味鉴定师", "给AI的回复点赞5次", "👍", "feedback", 5),
        Achievement("feedback_20", "最佳拍档", "给AI的回复点赞20次", "🤝", "feedback", 20),
        Achievement("pomodoro_1", "专注新人", "完成1个番茄钟", "🍅", "pomodoro", 1),
        Achievement("pomodoro_5", "效率大师", "完成5个番茄钟", "⏱", "pomodoro", 5),
        Achievement("pomodoro_15", "心流之王", "完成15个番茄钟", "🧠", "pomodoro", 15)
    )

    init {
        try {
            loadAchievements()
            Log.d(TAG, "Initialized: ${achievements.size} achievements, ${achievements.count { it.unlocked }} unlocked")
        } catch (e: Exception) {
            Log.e(TAG, "Init failed: ${e.javaClass.simpleName}: ${e.message}", e)
            achievements = allAchievements.toMutableList()
        }
    }

    private fun loadAchievements() {
        val json = prefs.getString("achievements", "[]") ?: "[]"
        try {
            val arr = JSONArray(json)
            val loaded = (0 until arr.length()).mapNotNull { i ->
                try { Achievement.fromJson(arr.getJSONObject(i)) }
                catch (_: Exception) { null }
            }
            if (loaded.isEmpty()) {
                achievements = allAchievements.toMutableList()
                save()
            } else {
                achievements = allAchievements.map { template ->
                    loaded.find { it.id == template.id } ?: template
                }.toMutableList()
            }
        } catch (_: Exception) {
            achievements = allAchievements.toMutableList()
            save()
        }
    }

    fun getAchievements(): List<Achievement> = achievements.toList()

    fun getUnlocked(): List<Achievement> = achievements.filter { it.unlocked }

    fun getByCategory(category: String): List<Achievement> = achievements.filter { it.category == category }

    fun updateProgress(type: String, value: Int): Achievement? {
        val toCheck = when (type) {
            "chat" -> achievements.filter { it.category == "chat" }
            "checkin" -> achievements.filter { it.category == "checkin" }
            "affection" -> achievements.filter { it.category == "affection" }
            "memory" -> achievements.filter { it.category == "memory" }
            "diary" -> achievements.filter { it.category == "diary" }
            "feedback" -> achievements.filter { it.category == "feedback" }
            "pomodoro" -> achievements.filter { it.category == "pomodoro" }
            else -> emptyList()
        }

        var newlyUnlocked: Achievement? = null
        for (ach in toCheck) {
            if (ach.unlocked) continue
            ach.progress = maxOf(ach.progress, value)
            if (ach.progress >= ach.unlockCondition) {
                ach.unlocked = true
                ach.unlockedAt = System.currentTimeMillis()
                newlyUnlocked = ach
            }
        }
        save()
        return newlyUnlocked
    }

    fun unlockAchievement(id: String): Achievement? {
        val ach = achievements.find { it.id == id } ?: return null
        if (ach.unlocked) return null
        ach.unlocked = true
        ach.unlockedAt = System.currentTimeMillis()
        ach.progress = ach.unlockCondition
        save()
        return ach
    }

    private fun save() {
        val arr = JSONArray()
        achievements.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("achievements", arr.toString()).apply()
    }

    val totalCount: Int get() = achievements.size
    val unlockedCount: Int get() = achievements.count { it.unlocked }
}