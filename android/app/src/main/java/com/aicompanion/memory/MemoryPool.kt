package com.aicompanion.memory

import android.content.Context
import com.aicompanion.network.ApiClient
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MemoryEntry(
    val id: String = UUID.randomUUID().toString().take(8),
    val content: String,
    val category: String = "其他",
    val timestamp: Long = System.currentTimeMillis(),
    val sourceTurn: Int = 0
)

class MemoryPool(private val context: Context, private val personaId: String = "default") {

    companion object {
        private const val TAG = "MemoryPool"
        private const val CONSOLIDATE_INTERVAL = 10
        private const val MAX_CHARS = 1000
        private const val COMPRESS_KEEP_CHARS = 800
    }

    private val entries = mutableListOf<MemoryEntry>()
    private var turnsSinceLastConsolidate = 0
    private var totalTurns = 0
    private var totalCharCount = 0
    private val prefs = context.getSharedPreferences("memory_pool_$personaId", Context.MODE_PRIVATE)

    val isEmpty: Boolean get() = entries.isEmpty()
    val size: Int get() = entries.size

    init {
        loadFromStorage()
    }

    fun getAll(): List<MemoryEntry> = entries.toList()

    fun addOrUpdate(entry: MemoryEntry) {
        entries.removeAll { it.id == entry.id }
        entries.add(entry)
        recalcCharCount()
    }

    fun add(entry: MemoryEntry) {
        entries.add(entry)
        recalcCharCount()
    }

    fun delete(id: String) {
        entries.removeAll { it.id == id }
        recalcCharCount()
    }

    fun deleteByIndex(index: Int): Boolean {
        if (index < 0 || index >= entries.size) return false
        entries.removeAt(index)
        recalcCharCount()
        return true
    }

    fun incrementTurn() {
        turnsSinceLastConsolidate++
        totalTurns++
    }

    fun needsConsolidate(): Boolean = turnsSinceLastConsolidate >= CONSOLIDATE_INTERVAL

    fun getPoolBlock(): String {
        if (entries.isEmpty()) return ""

        val grouped = entries.groupBy { it.category }
        val sb = StringBuilder()
        sb.appendLine("[记忆池 - 场景、剧情与关键信息]")

        val categoryOrder = listOf("场景", "剧情", "喜好", "习惯", "事实", "事件", "计划", "继承", "其他")
        for (cat in categoryOrder) {
            val group = grouped[cat] ?: continue
            for (entry in group) {
                sb.appendLine("- [${entry.category}] ${entry.content}")
            }
        }

        val uncategorized = entries.filter { it.category !in categoryOrder }
        for (entry in uncategorized) {
            sb.appendLine("- [${entry.category}] ${entry.content}")
        }

        return sb.toString().trimEnd()
    }

    fun getPoolCharCount(): Int = totalCharCount

    suspend fun consolidate(client: ApiClient): Boolean = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) {
            turnsSinceLastConsolidate = 0
            return@withContext false
        }

        AppLogger.d(TAG, "consolidate: starting with ${entries.size} entries, $totalCharCount chars")

        val fullPool = getPoolBlock()
        val systemPrompt = buildString {
            append("整理以下记忆池，保留所有重要信息（场景、剧情、角色关系、用户喜好等），合并重复项，删除过时信息。")
            append("\n输出格式：每行一条记忆，格式为 - [分类] 内容")
            append("\n分类可选：场景/剧情/喜好/习惯/事实/事件/计划/其他")
            append("\n总字数不超过${MAX_CHARS}字。只输出记忆条目，不要其他内容。")
        }

        try {
            val response = client.sendSimplePrompt(systemPrompt, fullPool)
            if (response != null && response.text.isNotBlank()) {
                val newEntries = parseConsolidatedResult(response.text)
                if (newEntries.isNotEmpty()) {
                    entries.clear()
                    entries.addAll(newEntries)
                    recalcCharCount()
                    saveToStorage()
                    AppLogger.d(TAG, "consolidate: done, ${entries.size} entries, $totalCharCount chars")
                    turnsSinceLastConsolidate = 0
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
        turnsSinceLastConsolidate = 0
        return@withContext false
    }

    private fun parseConsolidatedResult(text: String): List<MemoryEntry> {
        val results = mutableListOf<MemoryEntry>()
        val lines = text.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("[记忆池")) continue

            val content: String
            val category: String

            val bracketMatch = Regex("^-\\s*\\[(.+?)\\]\\s*(.+)").find(trimmed)
            if (bracketMatch != null) {
                category = bracketMatch.groupValues[1].trim()
                content = bracketMatch.groupValues[2].trim()
            } else {
                val cleanLine = trimmed.removePrefix("-").removePrefix("•").trim()
                if (cleanLine.isBlank()) continue
                category = "其他"
                content = cleanLine
            }

            if (content.isNotBlank()) {
                results.add(MemoryEntry(
                    content = content,
                    category = category,
                    sourceTurn = totalTurns
                ))
            }
        }
        return results
    }

    private fun trimToLimit() {
        while (totalCharCount > MAX_CHARS && entries.size > 1) {
            entries.removeAt(entries.lastIndex)
            recalcCharCount()
        }
    }

    suspend fun evaluateTurn(
        client: ApiClient,
        userMsg: String,
        aiMsg: String,
        turnNumber: Int,
        userNickname: String = "用户"
    ): List<MemoryEntry> = withContext(Dispatchers.IO) {
        if (userMsg.isBlank() || aiMsg.isBlank()) return@withContext emptyList()

        val poolBlock = if (entries.isEmpty()) "（空）" else getPoolBlock()
        AppLogger.d(TAG, "evaluateTurn #$turnNumber: pool=${entries.size} entries")

        val nick = userNickname.ifBlank { "用户" }
        val systemPrompt = buildString {
            append("你是记忆管理助手。分析对话，提取值得记住的信息（场景、剧情、角色关系、用户喜好等）。")
            append("\n只输出JSON数组，不需要更新时输出[]。")
            append("\n分类：场景/剧情/喜好/习惯/事实/事件/计划/其他")
            append("\n提到$nick 时用「$nick」称呼。")
        }

        val userContent = buildString {
            appendLine("[当前记忆池]")
            appendLine(poolBlock)
            appendLine()
            appendLine("[第${turnNumber}轮对话]")
            appendLine("$nick: $userMsg")
            appendLine("AI: $aiMsg")
            appendLine()
            appendLine("输出JSON数组（不要Markdown代码块）：")
            appendLine("[{\"action\":\"add\",\"content\":\"...\",\"category\":\"场景\"}]")
            appendLine("update: remove+add合并, delete: 删除")
        }

        try {
            val response = client.sendSimplePrompt(systemPrompt, userContent)
            if (response != null && response.text.isNotBlank()) {
                val result = parseEvaluationResult(response.text, turnNumber)
                AppLogger.d(TAG, "evaluateTurn #$turnNumber result: ${result.size} new entries")
                result
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "evaluateTurn #$turnNumber failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseEvaluationResult(jsonText: String, turnNumber: Int): List<MemoryEntry> {
        val results = mutableListOf<MemoryEntry>()
        try {
            var cleaned = jsonText.trim()
            cleaned = cleaned.replace(Regex("```(?:json)?\\s*"), "").replace("```", "").trim()

            val bracketStart = cleaned.indexOf('[')
            val bracketEnd = cleaned.lastIndexOf(']')
            if (bracketStart >= 0 && bracketEnd > bracketStart) {
                cleaned = cleaned.substring(bracketStart, bracketEnd + 1)
            }

            if (cleaned == "[]") return emptyList()

            val arr = try { JSONArray(cleaned) } catch (_: Exception) { return emptyList() }
            if (arr.length() == 0) return emptyList()

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val action = obj.optString("action", "")

                when (action) {
                    "add" -> {
                        val content = obj.optString("content", "").trim()
                        if (content.isNotBlank()) {
                            results.add(MemoryEntry(
                                content = content,
                                category = obj.optString("category", "其他"),
                                sourceTurn = turnNumber
                            ))
                        }
                    }
                    "update" -> {
                        val oldFragment = obj.optString("old_content_fragment", "").trim()
                        val newContent = obj.optString("content", "").trim()
                        if (newContent.isNotBlank()) {
                            val matched = if (oldFragment.isNotBlank()) {
                                entries.find { it.content.contains(oldFragment, ignoreCase = true) }
                            } else null

                            if (matched != null) {
                                entries.removeAll { it.id == matched.id }
                                results.add(matched.copy(
                                    content = newContent,
                                    category = obj.optString("category", matched.category),
                                    timestamp = System.currentTimeMillis(),
                                    sourceTurn = turnNumber
                                ))
                            } else {
                                results.add(MemoryEntry(
                                    content = newContent,
                                    category = obj.optString("category", "其他"),
                                    sourceTurn = turnNumber
                                ))
                            }
                        }
                    }
                    "delete" -> {
                        val oldFragment = obj.optString("old_content_fragment", "").trim()
                        if (oldFragment.isNotBlank()) {
                            entries.removeAll { it.content.contains(oldFragment, ignoreCase = true) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "parseEvaluationResult error: ${e.message}")
        }
        return results
    }

    fun saveToStorage() {
        try {
            val arr = JSONArray()
            for (entry in entries) {
                val obj = JSONObject()
                obj.put("id", entry.id)
                obj.put("content", entry.content)
                obj.put("category", entry.category)
                obj.put("timestamp", entry.timestamp)
                obj.put("sourceTurn", entry.sourceTurn)
                arr.put(obj)
            }
            prefs.edit()
                .putString("entries", arr.toString())
                .putInt("turns_since_consolidate", turnsSinceLastConsolidate)
                .putInt("total_turns", totalTurns)
                .apply()
        } catch (_: Exception) {}
    }

    private fun loadFromStorage() {
        try {
            val json = prefs.getString("entries", null) ?: return
            val arr = JSONArray(json)
            entries.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                entries.add(MemoryEntry(
                    id = obj.optString("id", UUID.randomUUID().toString().take(8)),
                    content = obj.getString("content"),
                    category = obj.optString("category", "其他"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    sourceTurn = obj.optInt("sourceTurn", 0)
                ))
            }
            turnsSinceLastConsolidate = prefs.getInt("turns_since_consolidate", 0)
            totalTurns = prefs.getInt("total_turns", 0)
            recalcCharCount()
        } catch (_: Exception) {
            entries.clear()
        }
    }

    private fun recalcCharCount() {
        totalCharCount = entries.sumOf { it.content.length }
    }

    fun clear() {
        entries.clear()
        totalCharCount = 0
        turnsSinceLastConsolidate = 0
        totalTurns = 0
        prefs.edit().clear().apply()
    }

    fun getStats(): String {
        val cats = entries.groupBy { it.category }.mapValues { it.value.size }
        return "共${entries.size}条记忆 | ${totalCharCount}字 | ${cats.entries.joinToString { "${it.key}:${it.value}" }}"
    }
}
