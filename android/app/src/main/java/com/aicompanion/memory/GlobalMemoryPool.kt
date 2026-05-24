package com.aicompanion.memory

import android.content.Context
import com.aicompanion.network.ApiClient
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class GlobalMemoryPool(
    private val context: Context,
    private val personaId: String
) {
    companion object {
        private const val TAG = "GlobalMemoryPool"
        private const val MAX_ENTRIES = 50
        private const val MAX_CHARS = 2000
        private const val CONSOLIDATE_INTERVAL = 20
        private const val PREFS_KEY = "global_memory_pool"
    }

    private val entries = mutableListOf<MemoryEntry>()
    private var totalCharCount = 0
    private var writeCount = 0
    private val prefs = context.getSharedPreferences("${PREFS_KEY}_$personaId", Context.MODE_PRIVATE)

    init {
        loadFromStorage()
    }

    val size: Int get() = entries.size

    fun addFromScene(scene: String, newEntries: List<MemoryEntry>) {
        for (entry in newEntries) {
            if (!entry.isGlobal) continue
            val existingIdx = entries.indexOfFirst {
                it.content.contains(entry.content.take(20), ignoreCase = true) ||
                entry.content.contains(it.content.take(20), ignoreCase = true)
            }
            if (existingIdx >= 0) {
                entries[existingIdx] = entry.copy(isGlobal = true)
            } else {
                entries.add(entry.copy(isGlobal = true))
            }
        }
        trimToLimit()
        recalcCharCount()
        writeCount += newEntries.size
        saveToStorage()
    }

    fun getGlobalBlock(): String {
        if (entries.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("[跨场景共享记忆]")
        for (entry in entries) {
            val structured = entry.toStructuredText()
            if (structured != entry.content && entry.event.isNotBlank()) {
                sb.appendLine("- $structured")
            } else {
                sb.appendLine("- ${entry.content}")
            }
        }
        return sb.toString().trimEnd()
    }

    fun needsConsolidate(): Boolean = writeCount >= CONSOLIDATE_INTERVAL

    suspend fun consolidate(client: ApiClient): Boolean = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) {
            writeCount = 0
            return@withContext false
        }

        val fullPool = getGlobalBlock()

        val systemPrompt = buildString {
            append("你是记忆总结助手。请将以下跨场景共享记忆重新整理总结。\n")
            append("要求：\n")
            append("- 简短精炼，保留所有重要细节\n")
            append("- 合并重复内容，删除过时信息\n")
            append("- 只保留跨场景有价值的信息（用户喜好、事实、关系、重要决定等）\n")
            append("- 删除场景特定的事件描述（如'在群里讨论了xxx'）\n")
            append("- 总字数不超过${MAX_CHARS}字\n")
            append("- 每条记忆一行，以 - 开头\n")
            append("- 只输出纯文本，不要JSON\n")
        }

        try {
            val response = client.sendSimplePrompt(systemPrompt, fullPool)
            if (response != null && response.text.isNotBlank()) {
                val newEntries = parseConsolidatedResult(response.text)
                if (newEntries.isNotEmpty()) {
                    entries.clear()
                    entries.addAll(newEntries.map { it.copy(isGlobal = true) })
                    recalcCharCount()
                    saveToStorage()
                    writeCount = 0
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "consolidate failed: ${e.message}")
        }

        if (totalCharCount > MAX_CHARS) {
            trimToLimit()
            saveToStorage()
        }
        writeCount = 0
        return@withContext false
    }

    private fun parseConsolidatedResult(text: String): List<MemoryEntry> {
        val results = mutableListOf<MemoryEntry>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("[跨场景")) continue
            val cleanLine = trimmed
                .removePrefix("-").removePrefix("•")
                .removePrefix("·").removePrefix("*")
                .trim()
            if (cleanLine.isBlank()) continue
            results.add(MemoryEntry(
                content = cleanLine,
                category = "全局",
                isGlobal = true
            ))
        }
        return results
    }

    private fun trimToLimit() {
        while ((totalCharCount > MAX_CHARS || entries.size > MAX_ENTRIES) && entries.size > 1) {
            entries.removeAt(0)
            recalcCharCount()
        }
    }

    fun saveToStorage() {
        try {
            val arr = JSONArray()
            for (entry in entries) {
                arr.put(entry.toJson())
            }
            prefs.edit()
                .putString("entries", arr.toString())
                .putInt("write_count", writeCount)
                .apply()
        } catch (e: Exception) {
            AppLogger.e(TAG, "saveToStorage error: ${e.message}")
        }
    }

    private fun loadFromStorage() {
        try {
            val json = prefs.getString("entries", null) ?: return
            val arr = JSONArray(json)
            entries.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                entries.add(MemoryEntry.fromJson(obj).copy(isGlobal = true))
            }
            writeCount = prefs.getInt("write_count", 0)
            recalcCharCount()
        } catch (e: Exception) {
            AppLogger.e(TAG, "loadFromStorage error: ${e.message}")
            entries.clear()
        }
    }

    private fun recalcCharCount() {
        totalCharCount = entries.sumOf { it.content.length }
    }

    fun clear() {
        entries.clear()
        totalCharCount = 0
        writeCount = 0
        prefs.edit().clear().apply()
    }

    fun getStats(): String = "全局${entries.size}条 | ${totalCharCount}字"
}
