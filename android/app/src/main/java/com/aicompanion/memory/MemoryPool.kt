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
    val importance: Int = 2,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceTurn: Int = 0
)

class MemoryPool(private val context: Context) {

    companion object {
        private const val TAG = "MemoryPool"
        private const val SAVE_INTERVAL = 10
        private const val NEW_SESSION_THRESHOLD = 5000
        private const val COMPRESS_THRESHOLD = 10000
    }

    private val entries = mutableListOf<MemoryEntry>()
    private var turnsSinceLastSave = 0
    private var totalCharCount = 0
    private val prefs = context.getSharedPreferences("memory_pool", Context.MODE_PRIVATE)

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
        turnsSinceLastSave++
    }

    fun needsSave(): Boolean = turnsSinceLastSave >= SAVE_INTERVAL

    fun needsNewSession(): Boolean = totalCharCount > NEW_SESSION_THRESHOLD

    fun getPoolBlock(): String {
        if (entries.isEmpty()) return ""

        val grouped = entries.groupBy { it.category }
        val sb = StringBuilder()
        sb.appendLine("[记忆池 - 关于用户的关键信息]")

        val categoryOrder = listOf("喜好", "习惯", "事实", "事件", "计划", "其他")
        for (cat in categoryOrder) {
            val group = grouped[cat] ?: continue
            for (entry in group.sortedByDescending { it.importance }) {
                sb.appendLine("- [${entry.category}/${entry.importance}] ${entry.content}")
            }
        }

        val uncategorized = entries.filter { it.category !in categoryOrder }
        for (entry in uncategorized.sortedByDescending { it.importance }) {
            sb.appendLine("- [${entry.category}/${entry.importance}] ${entry.content}")
        }

        return sb.toString().trimEnd()
    }

    fun getPoolCharCount(): Int = totalCharCount

    suspend fun compress(client: ApiClient, keepChars: Int = 500): String = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext ""

        val fullPool = getPoolBlock()
        val systemPrompt = "压缩以下用户记忆池，保留最重要的信息，输出${keepChars}字以内的精简版本。"
        val userContent = fullPool

        try {
            val response = client.sendSimplePrompt(systemPrompt, userContent)
            if (response != null && response.text.isNotBlank()) {
                response.text.take(keepChars)
            } else {
                fullPool.take(keepChars)
            }
        } catch (_: Exception) {
            fullPool.take(keepChars)
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
        AppLogger.d(TAG, "evaluateTurn #$turnNumber: pool=${entries.size} entries, evaluating...")

        val nick = userNickname.ifBlank { "用户" }
        val systemPrompt = "你是记忆管理助手。分析对话，判断$nick 的哪些信息值得记住。只输出JSON数组，不需要更新时输出[]。提到$nick 时用「$nick」称呼。分类：喜好/习惯/事实/事件/计划/其他。重要性1-5。"

        val userContent = buildString {
            appendLine("[当前记忆池]")
            appendLine(poolBlock)
            appendLine()
            appendLine("[第${turnNumber}轮对话]")
            appendLine("$nick: $userMsg")
            appendLine("AI: $aiMsg")
            appendLine()
            appendLine("输出JSON数组（不要Markdown代码块）：")
            appendLine("[{\"action\":\"add\",\"content\":\"${nick}18岁\",\"category\":\"事实\",\"importance\":5}]")
            appendLine("update: remove+add合并, delete: 删除")
        }

        try {
            val response = client.sendSimplePrompt(systemPrompt, userContent)
            if (response != null && response.text.isNotBlank()) {
                AppLogger.d(TAG, "evaluateTurn #$turnNumber raw: ${response.text.take(200)}")
                val result = parseEvaluationResult(response.text, turnNumber)
                AppLogger.d(TAG, "evaluateTurn #$turnNumber result: ${result.size} new entries")
                result
            } else {
                AppLogger.w(TAG, "evaluateTurn #$turnNumber: LLM returned empty")
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
                                importance = obj.optInt("importance", 2).coerceIn(1, 5),
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
                                    importance = obj.optInt("importance", matched.importance).coerceIn(1, 5),
                                    timestamp = System.currentTimeMillis(),
                                    sourceTurn = turnNumber
                                ))
                            } else {
                                results.add(MemoryEntry(
                                    content = newContent,
                                    category = obj.optString("category", "其他"),
                                    importance = obj.optInt("importance", 2).coerceIn(1, 5),
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
            AppLogger.e(TAG, "parseEvaluationResult error: ${e.message}, raw=$jsonText")
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
                obj.put("importance", entry.importance)
                obj.put("timestamp", entry.timestamp)
                obj.put("sourceTurn", entry.sourceTurn)
                arr.put(obj)
            }
            prefs.edit()
                .putString("entries", arr.toString())
                .putInt("turns_since_save", 0)
                .apply()
            turnsSinceLastSave = 0
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
                    importance = obj.optInt("importance", 2),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    sourceTurn = obj.optInt("sourceTurn", 0)
                ))
            }
            turnsSinceLastSave = prefs.getInt("turns_since_save", 0)
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
        turnsSinceLastSave = 0
        prefs.edit().clear().apply()
    }

    fun getStats(): String {
        val cats = entries.groupBy { it.category }.mapValues { it.value.size }
        return "共${entries.size}条记忆 | ${totalCharCount}字 | ${cats.entries.joinToString { "${it.key}:${it.value}" }}"
    }
}