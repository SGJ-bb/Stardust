package com.aicompanion.sticker

import org.json.JSONObject

data class Sticker(
    val id: String,
    val filePath: String,
    val description: String,
    val emotion: String,
    val tags: List<String>,
    val owner: String,
    val createdAt: Long = System.currentTimeMillis(),
    var embedding: FloatArray? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("filePath", filePath)
        put("description", description)
        put("emotion", emotion)
        put("tags", org.json.JSONArray(tags))
        put("owner", owner)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Sticker = Sticker(
            id = json.getString("id"),
            filePath = json.getString("filePath"),
            description = json.optString("description", ""),
            emotion = json.optString("emotion", ""),
            tags = json.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            owner = json.optString("owner", "user"),
            createdAt = json.optLong("createdAt", 0L)
        )
    }
}
