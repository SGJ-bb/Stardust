package com.aicompanion.virtualworld

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WorldConfig(
    val worldLore: String = "",
    val worldBackground: String = "",
    val worldRules: String = "",
    val worldRelations: String = "",
    val worldScene: String = "",
    val worldStyle: String = "",
    val timeRatio: Int = 1,
    val tickIntervalMinutes: Int = 60,
    val isGroupSimulation: Boolean = false,
    val memberPersonaIds: List<String> = listOf(),
    val imageGenEnabled: Boolean = false,
    val uploadedImages: List<String> = listOf()
) {
    fun getFullLore(): String = buildString {
        if (worldBackground.isNotBlank()) append("【世界背景】$worldBackground\n")
        if (worldRules.isNotBlank()) append("【世界规则】$worldRules\n")
        if (worldRelations.isNotBlank()) append("【角色关系】$worldRelations\n")
        if (worldScene.isNotBlank()) append("【初始场景】$worldScene\n")
        if (worldStyle.isNotBlank()) append("【叙事风格】$worldStyle\n")
        if (worldLore.isNotBlank() && worldBackground.isBlank()) append(worldLore)
    }.trim()

    fun toJson(): JSONObject = JSONObject().apply {
        put("worldLore", worldLore)
        put("worldBackground", worldBackground)
        put("worldRules", worldRules)
        put("worldRelations", worldRelations)
        put("worldScene", worldScene)
        put("worldStyle", worldStyle)
        put("timeRatio", timeRatio)
        put("tickIntervalMinutes", tickIntervalMinutes)
        put("isGroupSimulation", isGroupSimulation)
        put("imageGenEnabled", imageGenEnabled)
        val arr = JSONArray()
        memberPersonaIds.forEach { arr.put(it) }
        put("memberPersonaIds", arr)
        val imgArr = JSONArray()
        uploadedImages.forEach { imgArr.put(it) }
        put("uploadedImages", imgArr)
    }

    companion object {
        fun fromJson(obj: JSONObject): WorldConfig {
            val legacyLore = obj.optString("worldLore", "")
            val background = obj.optString("worldBackground", "")
            return WorldConfig(
                worldLore = legacyLore,
                worldBackground = if (background.isBlank() && legacyLore.isNotBlank()) legacyLore else background,
                worldRules = obj.optString("worldRules", ""),
                worldRelations = obj.optString("worldRelations", ""),
                worldScene = obj.optString("worldScene", ""),
                worldStyle = obj.optString("worldStyle", ""),
                timeRatio = obj.optInt("timeRatio", 1),
                tickIntervalMinutes = obj.optInt("tickIntervalMinutes", 60),
                isGroupSimulation = obj.optBoolean("isGroupSimulation", false),
                imageGenEnabled = obj.optBoolean("imageGenEnabled", false),
                memberPersonaIds = run {
                    val arr = obj.optJSONArray("memberPersonaIds") ?: JSONArray()
                    (0 until arr.length()).map { arr.getString(it) }
                },
                uploadedImages = run {
                    val arr = obj.optJSONArray("uploadedImages") ?: JSONArray()
                    (0 until arr.length()).map { arr.getString(it) }
                }
            )
        }
    }
}

data class WorldState(
    val virtualTimeMs: Long = System.currentTimeMillis(),
    val currentLocation: String = "起始之地",
    val currentWeather: String = "晴朗",
    val currentMood: String = "平静",
    val dayCount: Int = 1,
    val hourOfDay: Int = 8
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("virtualTimeMs", virtualTimeMs)
        put("currentLocation", currentLocation)
        put("currentWeather", currentWeather)
        put("currentMood", currentMood)
        put("dayCount", dayCount)
        put("hourOfDay", hourOfDay)
    }

    companion object {
        fun fromJson(obj: JSONObject): WorldState = WorldState(
            virtualTimeMs = obj.optLong("virtualTimeMs", System.currentTimeMillis()),
            currentLocation = obj.optString("currentLocation", "起始之地"),
            currentWeather = obj.optString("currentWeather", "晴朗"),
            currentMood = obj.optString("currentMood", "平静"),
            dayCount = obj.optInt("dayCount", 1),
            hourOfDay = obj.optInt("hourOfDay", 8)
        )
    }
}

data class StoryEvent(
    val id: String = UUID.randomUUID().toString(),
    val virtualDay: Int,
    val virtualHour: Int,
    val content: String,
    val speakerName: String = "旁白",
    val eventType: String = "narrative",
    val imageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("virtualDay", virtualDay)
        put("virtualHour", virtualHour)
        put("content", content)
        put("speakerName", speakerName)
        put("eventType", eventType)
        put("imageUrl", imageUrl)
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJson(obj: JSONObject): StoryEvent = StoryEvent(
            id = obj.optString("id", UUID.randomUUID().toString()),
            virtualDay = obj.optInt("virtualDay", 1),
            virtualHour = obj.optInt("virtualHour", 8),
            content = obj.optString("content", ""),
            speakerName = obj.optString("speakerName", "旁白"),
            eventType = obj.optString("eventType", "narrative"),
            imageUrl = obj.optString("imageUrl", ""),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
        )
    }
}
