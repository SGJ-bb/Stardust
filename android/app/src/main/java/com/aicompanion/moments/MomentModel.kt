package com.aicompanion.moments

import org.json.JSONArray
import org.json.JSONObject

data class Moment(
    val id: String,
    val author: String,
    val content: String,
    val authorPersonaId: String? = null,
    val imagePath: String? = null,
    val stickerPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val comments: MutableList<Comment> = mutableListOf()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("author", author)
        put("content", content)
        put("authorPersonaId", authorPersonaId ?: "")
        put("imagePath", imagePath ?: "")
        put("stickerPath", stickerPath ?: "")
        put("createdAt", createdAt)
        put("comments", JSONArray().apply {
            comments.forEach { put(it.toJson()) }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): Moment {
            val comments = mutableListOf<Comment>()
            val arr = json.optJSONArray("comments")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    try {
                        comments.add(Comment.fromJson(arr.getJSONObject(i)))
                    } catch (_: Exception) {}
                }
            }
            return Moment(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                author = json.optString("author", "user"),
                content = json.optString("content", ""),
                authorPersonaId = json.optString("authorPersonaId", "").ifBlank { null },
                imagePath = json.optString("imagePath", "").ifBlank { null },
                stickerPath = json.optString("stickerPath", "").ifBlank { null },
                createdAt = json.optLong("createdAt", 0L),
                comments = comments
            )
        }
    }
}

data class Comment(
    val id: String,
    val author: String,
    val content: String,
    val authorPersonaId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("author", author)
        put("content", content)
        put("authorPersonaId", authorPersonaId ?: "")
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Comment = Comment(
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            author = json.optString("author", "user"),
            content = json.optString("content", ""),
            authorPersonaId = json.optString("authorPersonaId", "").ifBlank { null },
            createdAt = json.optLong("createdAt", 0L)
        )
    }
}
