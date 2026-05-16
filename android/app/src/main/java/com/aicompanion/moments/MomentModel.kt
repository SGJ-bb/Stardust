package com.aicompanion.moments

import org.json.JSONArray
import org.json.JSONObject

data class Moment(
    val id: String,
    val author: String,
    val content: String,
    val imagePath: String? = null,
    val stickerPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val comments: MutableList<Comment> = mutableListOf()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("author", author)
        put("content", content)
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
                    comments.add(Comment.fromJson(arr.getJSONObject(i)))
                }
            }
            return Moment(
                id = json.getString("id"),
                author = json.getString("author"),
                content = json.getString("content"),
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
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("author", author)
        put("content", content)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Comment = Comment(
            id = json.getString("id"),
            author = json.getString("author"),
            content = json.getString("content"),
            createdAt = json.optLong("createdAt", 0L)
        )
    }
}
