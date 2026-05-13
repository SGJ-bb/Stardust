package com.aicompanion.diary

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class DiaryEntry(
    val version: Int = DiaryManager.CURRENT_VERSION,
    val date: String,
    val title: String,
    val content: String,
    val mood: String = "normal",
    val moodEmoji: String = "😊",
    val affectionLevel: Int = 0,
    val messageCount: Int = 0,
    val keyMemories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val pluginMeta: JSONObject? = null,
    val customFields: JSONObject? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val appVersion: String = DiaryManager.APP_VERSION
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        put("app_version", appVersion)
        put("date", date)
        put("title", title)
        put("content", content)
        put("mood", mood)
        put("mood_emoji", moodEmoji)
        put("affection_level", affectionLevel)
        put("message_count", messageCount)
        put("created_at", createdAt)
        put("updated_at", updatedAt)

        val memArray = JSONArray()
        keyMemories.forEach { memArray.put(it) }
        put("key_memories", memArray)

        val tagsArray = JSONArray()
        tags.forEach { tagsArray.put(it) }
        put("tags", tagsArray)

        if (pluginMeta != null) put("plugin_meta", pluginMeta)
        if (customFields != null) put("custom_fields", customFields)
    }

    companion object {
        fun fromJson(json: JSONObject): DiaryEntry {
            val mems = mutableListOf<String>()
            val memsArr = json.optJSONArray("key_memories")
            if (memsArr != null) {
                for (i in 0 until memsArr.length()) mems.add(memsArr.optString(i, ""))
            }

            val tagsList = mutableListOf<String>()
            val tagsArr = json.optJSONArray("tags")
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) tagsList.add(tagsArr.optString(i, ""))
            }

            return DiaryEntry(
                version = json.optInt("version", 1),
                appVersion = json.optString("app_version", DiaryManager.APP_VERSION),
                date = json.optString("date", ""),
                title = json.optString("title", ""),
                content = json.optString("content", ""),
                mood = json.optString("mood", "normal"),
                moodEmoji = json.optString("mood_emoji", "😊"),
                affectionLevel = json.optInt("affection_level", 0),
                messageCount = json.optInt("message_count", 0),
                keyMemories = mems,
                tags = tagsList,
                pluginMeta = json.optJSONObject("plugin_meta"),
                customFields = json.optJSONObject("custom_fields"),
                createdAt = json.optLong("created_at", System.currentTimeMillis()),
                updatedAt = json.optLong("updated_at", System.currentTimeMillis())
            )
        }
    }
}