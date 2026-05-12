package com.aicompanion.models

import org.json.JSONObject

enum class Emotion { HAPPY, ANGRY, SAD, SURPRISED, TSUNDERE, NEUTRAL }

enum class Action { TAIL_FLICK, EAR_TWITCH, BLUSH, STRETCH, YAWN, IDLE, TAP }

enum class TextureQuality { LOW, MEDIUM, HIGH }

data class ChatResponse(
    val text: String,
    val emotion: Emotion,
    val action: Action,
    val audioUrl: String? = null
)

data class MemoryFact(
    val id: String,
    val userId: String,
    val fact: String,
    val timestamp: Long,
    val category: String
)

data class Live2DModel(
    val id: String,
    val name: String,
    val description: String,
    val modelPath: String,
    val texturePath: String,
    val physicsPath: String = "",
    val motionPath: String = "",
    val version: String,
    val sizeMB: Float = 0f,
    val isActive: Boolean = false,
    val textureQuality: TextureQuality = TextureQuality.HIGH,
    val fps: Int = 30
)

data class AppCategory(
    val packageName: String,
    val category: String,
    val confidence: Float
)

data class DailyCardData(
    val title: String,
    val content: String
)

data class CheckInRecord(
    val date: String,
    val streak: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("date", date)
        put("streak", streak)
    }

    companion object {
        fun fromJson(json: JSONObject): CheckInRecord = CheckInRecord(
            date = json.optString("date", ""),
            streak = json.optInt("streak", 0)
        )
    }
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val category: String,
    val unlockCondition: Int,
    var progress: Int = 0,
    var unlocked: Boolean = false,
    var unlockedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("description", description)
        put("icon", icon)
        put("category", category)
        put("unlockCondition", unlockCondition)
        put("progress", progress)
        put("unlocked", unlocked)
        put("unlockedAt", unlockedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Achievement = Achievement(
            id = json.optString("id", ""),
            title = json.optString("title", ""),
            description = json.optString("description", ""),
            icon = json.optString("icon", "🏆"),
            category = json.optString("category", "general"),
            unlockCondition = json.optInt("unlockCondition", 0),
            progress = json.optInt("progress", 0),
            unlocked = json.optBoolean("unlocked", false),
            unlockedAt = json.optLong("unlockedAt", 0L)
        )
    }
}

data class GrowthNode(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val requiredAffection: Int,
    val unlocked: Boolean = false,
    val children: List<GrowthNode> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("icon", icon)
        put("description", description)
        put("requiredAffection", requiredAffection)
        put("unlocked", unlocked)
    }

    companion object {
        fun fromJson(json: JSONObject): GrowthNode = GrowthNode(
            id = json.optString("id", ""),
            name = json.optString("name", ""),
            icon = json.optString("icon", "🌱"),
            description = json.optString("description", ""),
            requiredAffection = json.optInt("requiredAffection", 0),
            unlocked = json.optBoolean("unlocked", false)
        )
    }
}