package com.aicompanion.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class FavoriteManager(context: Context) {
    private val prefs = context.getSharedPreferences("favorite_messages", Context.MODE_PRIVATE)

    fun addFavorite(message: ChatMessage) {
        val favorites = getAll()
        if (favorites.any { it.id == message.id }) return
        favorites.add(0, message.copy(isFavorited = true))
        saveFavorites(favorites)
    }

    fun removeFavorite(messageId: String) {
        val favorites = getAll()
        favorites.removeAll { it.id == messageId }
        saveFavorites(favorites)
    }

    fun isFavorited(messageId: String): Boolean {
        return getAll().any { it.id == messageId }
    }

    fun getAll(): MutableList<ChatMessage> {
        val json = prefs.getString("messages", "[]") ?: "[]"
        val arr = JSONArray(json)
        val result = mutableListOf<ChatMessage>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(ChatMessage(
                id = obj.optString("id", ""),
                text = obj.optString("text", ""),
                time = obj.optString("time", ""),
                isUser = obj.optBoolean("isUser", false),
                userMood = obj.optString("userMood", ""),
                feedback = obj.optInt("feedback", 0),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                isFavorited = true,
                reactionEmoji = obj.optString("reactionEmoji", "")
            ))
        }
        return result
    }

    fun updateReaction(messageId: String, emoji: String) {
        val favorites = getAll()
        val index = favorites.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            favorites[index] = favorites[index].copy(reactionEmoji = emoji)
        }
        saveFavorites(favorites)
    }

    private fun saveFavorites(favorites: List<ChatMessage>) {
        val arr = JSONArray()
        favorites.forEach { msg ->
            arr.put(JSONObject().apply {
                put("id", msg.id)
                put("text", msg.text)
                put("time", msg.time)
                put("isUser", msg.isUser)
                put("userMood", msg.userMood)
                put("feedback", msg.feedback)
                put("timestamp", msg.timestamp)
                put("isFavorited", true)
                put("reactionEmoji", msg.reactionEmoji)
            })
        }
        prefs.edit().putString("messages", arr.toString()).apply()
    }
}