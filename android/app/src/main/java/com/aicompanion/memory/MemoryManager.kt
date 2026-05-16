/** 长期记忆管理器: 从对话中自动提取事实, 支持手动添加/删除/检索记忆条目 */
package com.aicompanion.memory

import android.content.Context
import android.content.SharedPreferences
import com.aicompanion.models.DailyCardData
import com.aicompanion.models.MemoryFact
import com.aicompanion.network.ApiClient
import org.json.JSONArray
import org.json.JSONObject

class MemoryManager(private val context: Context, private val personaId: String = "default") {

    private val apiClient = ApiClient("")
    private val localPrefs: SharedPreferences = context.getSharedPreferences("local_memory_$personaId", Context.MODE_PRIVATE)
    private var localMemories: MutableList<MemoryFact> = mutableListOf()

    init {
        loadLocalCache()
    }

    private fun loadLocalCache() {
        try {
            val json = localPrefs.getString("memories_json", null) ?: return
            val arr = JSONArray(json)
            val list = mutableListOf<MemoryFact>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(MemoryFact(
                    id = obj.getString("id"),
                    userId = obj.getString("userId"),
                    fact = obj.getString("fact"),
                    timestamp = obj.optLong("timestamp", 0),
                    category = obj.optString("category", "")
                ))
            }
            localMemories = list.sortedByDescending { it.timestamp }.toMutableList()
        } catch (_: Exception) {}
    }

    private fun saveLocalCache() {
        try {
            val arr = JSONArray()
            for (m in localMemories.take(100)) {
                val obj = JSONObject()
                obj.put("id", m.id)
                obj.put("userId", m.userId)
                obj.put("fact", m.fact)
                obj.put("timestamp", m.timestamp)
                obj.put("category", m.category)
                arr.put(obj)
            }
            localPrefs.edit().putString("memories_json", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getLocalMemories(): List<MemoryFact> = localMemories.toList()

    fun addMemoryFact(content: String, category: String = "") {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return
        val exists = localMemories.any { it.fact.equals(trimmed, ignoreCase = true) }
        if (exists) return
        localMemories.add(0, MemoryFact(
            id = "mem_${System.currentTimeMillis()}_${trimmed.hashCode()}",
            userId = "local",
            fact = trimmed,
            timestamp = System.currentTimeMillis(),
            category = category
        ))
        if (localMemories.size > 200) {
            localMemories = localMemories.take(200).toMutableList()
        }
        saveLocalCache()
    }

    fun getLocalMemoriesByCategory(category: String): List<MemoryFact> {
        if (category.isEmpty()) return localMemories.toList()
        return localMemories.filter { it.category.equals(category, ignoreCase = true) }
    }

    fun searchLocalMemories(query: String): List<MemoryFact> {
        if (query.isEmpty()) return localMemories.toList()
        val lower = query.lowercase()
        return localMemories.filter {
            it.fact.lowercase().contains(lower) || it.category.lowercase().contains(lower)
        }
    }

    fun getTodayMemories(): List<MemoryFact> {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        return localMemories.filter { it.timestamp >= todayStart }
    }

    suspend fun loadMemories(userId: String, limit: Int = 20): List<MemoryFact> {
        return try {
            val cloud = apiClient.getMemories(userId, limit)
            localMemories = (cloud + localMemories)
                .distinctBy { it.id }
                .sortedByDescending { it.timestamp }
                .take(100)
                .toMutableList()
            saveLocalCache()
            cloud
        } catch (_: Exception) {
            localMemories.take(limit)
        }
    }

    suspend fun deleteMemory(userId: String, memoryId: String): Boolean {
        localMemories.removeAll { it.id == memoryId }
        saveLocalCache()
        return !localMemories.any { it.id == memoryId }
    }

    suspend fun deleteAllMemories(userId: String): Boolean {
        val cloudSuccess = try {
            apiClient.deleteAllMemories(userId)
        } catch (_: Exception) { false }
        localMemories.clear()
        saveLocalCache()
        return cloudSuccess
    }

    suspend fun searchMemories(userId: String, query: String): List<MemoryFact> {
        return searchLocalMemories(query)
    }

    suspend fun generateDailyCard(userId: String): DailyCardData {
        return try {
            apiClient.getDailyCard(userId) ?: generateLocalDailyCard()
        } catch (_: Exception) {
            generateLocalDailyCard()
        }
    }

    private fun generateLocalDailyCard(): DailyCardData {
        val todayCount = getTodayMemories().size
        return if (todayCount > 0) {
            DailyCardData("今天的回忆", "今天记录了 $todayCount 条记忆，和主人度过了美好的一天~")
        } else {
            DailyCardData("平凡的一天", "今天还没有特别的记忆呢，多和我说说话吧~")
        }
    }

    fun loadMemoriesBlocking(userId: String, limit: Int = 20): List<MemoryFact> {
        return try {
            kotlinx.coroutines.runBlocking { loadMemories(userId, limit) }
        } catch (_: Exception) {
            localMemories.take(limit)
        }
    }

    fun deleteMemoryBlocking(userId: String, memoryId: String): Boolean {
        return try {
            kotlinx.coroutines.runBlocking { deleteMemory(userId, memoryId) }
        } catch (_: Exception) {
            localMemories.removeAll { it.id == memoryId }
            saveLocalCache()
            localMemories.none { it.id == memoryId }
        }
    }
}