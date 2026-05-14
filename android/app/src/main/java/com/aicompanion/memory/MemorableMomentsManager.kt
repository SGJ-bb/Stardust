/** 难忘时刻评分器: 定期评估对话时刻的情感强度, 标记特别有意义的对话瞬间 */
package com.aicompanion.memory

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ScoredMemory(
    val id: String,
    val content: String,
    val score: Int,
    val category: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("content", content)
        put("score", score)
        put("category", category)
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJson(json: JSONObject): ScoredMemory = ScoredMemory(
            id = json.optString("id", ""),
            content = json.optString("content", ""),
            score = json.optInt("score", 0),
            category = json.optString("category", "general"),
            timestamp = json.optLong("timestamp", System.currentTimeMillis())
        )
    }
}

class MemorableMomentsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("memorable_moments", Context.MODE_PRIVATE)
    private val moments: MutableList<ScoredMemory> = mutableListOf()

    init {
        load()
    }

    private fun load() {
        try {
            val json = prefs.getString("moments", "[]") ?: "[]"
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                moments.add(ScoredMemory.fromJson(arr.getJSONObject(i)))
            }
            moments.sortByDescending { it.score }
        } catch (_: Exception) {}
    }

    private fun save() {
        val arr = JSONArray()
        moments.take(50).forEach { arr.put(it.toJson()) }
        prefs.edit().putString("moments", arr.toString()).apply()
    }

    fun getAll(): List<ScoredMemory> = moments.toList()

    fun getByScore(minScore: Int): List<ScoredMemory> = moments.filter { it.score >= minScore }

    fun addMoment(content: String, score: Int, category: String) {
        if (score >= 8) {
            val id = "mm_${System.currentTimeMillis()}"
            moments.add(0, ScoredMemory(id, content, score, category))
            moments.sortByDescending { it.score }
            if (moments.size > 50) {
                moments.removeAt(moments.size - 1)
            }
            save()
        }
    }

    fun addMoments(scoredList: List<Triple<String, Int, String>>) {
        var changed = false
        scoredList.forEach { (content, score, category) ->
            if (score >= 8) {
                val id = "mm_${System.currentTimeMillis()}_${moments.size}"
                moments.add(0, ScoredMemory(id, content, score, category))
                changed = true
            }
        }
        if (changed) {
            moments.sortByDescending { it.score }
            if (moments.size > 50) {
                val toRemove = moments.size - 50
                moments.subList(moments.size - toRemove, moments.size).clear()
            }
            save()
        }
    }

    fun deleteMoment(id: String) {
        moments.removeAll { it.id == id }
        save()
    }

    val count: Int get() = moments.size
}