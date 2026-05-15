package com.aicompanion.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class NicknameEntry(
    val nickname: String,
    val source: String,
    val timestamp: Long = System.currentTimeMillis()
)

class NicknameManager(context: Context) {
    private val prefs = context.getSharedPreferences("nickname_data", Context.MODE_PRIVATE)

    fun getManualNickname(): String = prefs.getString("manual_nickname", "") ?: ""

    fun setManualNickname(nickname: String) {
        prefs.edit().putString("manual_nickname", nickname).apply()
        if (nickname.isNotBlank()) {
            addDiscovered(nickname, "manual")
        }
    }

    fun isManualSet(): Boolean = getManualNickname().isNotBlank()

    fun getActiveNicknames(): List<String> {
        val manual = getManualNickname()
        if (manual.isNotBlank()) return listOf(manual)
        val all = getAllDiscovered()
        return all.map { it.nickname }.distinct().take(5)
    }

    fun addDiscovered(nickname: String, source: String = "llm") {
        if (nickname.isBlank()) return
        val all = getAllDiscovered()
        all.add(0, NicknameEntry(nickname, source))
        saveAll(all)
    }

    fun addDiscoveredBatch(nicknames: List<String>, source: String = "llm") {
        if (nicknames.isEmpty()) return
        val all = getAllDiscovered()
        val existing = all.map { it.nickname }.toSet()
        nicknames.filter { it.isNotBlank() && it !in existing }.forEach { n ->
            all.add(0, NicknameEntry(n, source))
        }
        saveAll(all)
    }

    fun getAllDiscovered(): MutableList<NicknameEntry> {
        val json = prefs.getString("discovered_nicknames", "[]") ?: "[]"
        val arr = JSONArray(json)
        val result = mutableListOf<NicknameEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(NicknameEntry(
                nickname = obj.optString("nickname", ""),
                source = obj.optString("source", "llm"),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            ))
        }
        return result
    }

    fun removeDiscovered(nickname: String) {
        val all = getAllDiscovered()
        all.removeAll { it.nickname == nickname }
        saveAll(all)
    }

    fun clearAll() {
        prefs.edit().remove("discovered_nicknames").apply()
    }

    private fun saveAll(entries: List<NicknameEntry>) {
        val arr = JSONArray()
        entries.take(20).forEach { entry ->
            arr.put(JSONObject().apply {
                put("nickname", entry.nickname)
                put("source", entry.source)
                put("timestamp", entry.timestamp)
            })
        }
        prefs.edit().putString("discovered_nicknames", arr.toString()).apply()
    }
}