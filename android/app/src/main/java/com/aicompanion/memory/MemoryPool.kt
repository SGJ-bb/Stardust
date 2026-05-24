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
    val category: String = "总结",
    val timestamp: Long = System.currentTimeMillis(),
    val sourceTurn: Int = 0,
    val eventTime: String = "",
    val place: String = "",
    val people: String = "",
    val event: String = "",
    val scene: String = "",
    val details: String = "",
    val relationships: String = "",
    val isGlobal: Boolean = false
) {
    fun toStructuredText(): String {
        val parts = mutableListOf<String>()
        if (eventTime.isNotBlank()) parts.add("时间:$eventTime")
        if (place.isNotBlank()) parts.add("地点:$place")
        if (people.isNotBlank()) parts.add("人物:$people")
        if (event.isNotBlank()) parts.add("事件:$event")
        if (scene.isNotBlank()) parts.add("场景:$scene")
        if (details.isNotBlank()) parts.add("细节:$details")
        if (relationships.isNotBlank()) parts.add("关系:$relationships")
        return if (parts.isNotEmpty()) parts.joinToString(" | ") else content
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("content", content)
        put("category", category)
        put("timestamp", timestamp)
        put("sourceTurn", sourceTurn)
        put("eventTime", eventTime)
        put("place", place)
        put("people", people)
        put("event", event)
        put("scene", scene)
        put("details", details)
        put("relationships", relationships)
        put("isGlobal", isGlobal)
    }

    companion object {
        fun fromJson(obj: JSONObject): MemoryEntry = MemoryEntry(
            id = obj.optString("id", UUID.randomUUID().toString().take(8)),
            content = obj.optString("content", ""),
            category = obj.optString("category", "总结"),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            sourceTurn = obj.optInt("sourceTurn", 0),
            eventTime = obj.optString("eventTime", ""),
            place = obj.optString("place", ""),
            people = obj.optString("people", ""),
            event = obj.optString("event", ""),
            scene = obj.optString("scene", ""),
            details = obj.optString("details", ""),
            relationships = obj.optString("relationships", ""),
            isGlobal = obj.optBoolean("isGlobal", false)
        )
    }
}

class MemoryPool(
    private val context: Context,
    private val personaId: String = "default",
    private val scope: String = "private"
) {

    companion object {
        private const val TAG = "MemoryPool"
        private const val CONSOLIDATE_INTERVAL = 10
        private const val MAX_CHARS = 3000
    }

    private val entries = mutableListOf<MemoryEntry>()
    private val detailEntries = mutableListOf<MemoryEntry>()
    private var turnsSinceLastConsolidate = 0
    private var totalTurns = 0
    private var totalCharCount = 0
    private val prefsKey = if (scope == "private") "memory_pool_$personaId" else "memory_pool_${personaId}_${scope}"
    private val prefs = context.getSharedPreferences(prefsKey, Context.MODE_PRIVATE)

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
        val sb = StringBuilder()
        sb.appendLine("[记忆池 - 剧情与关键信息]")
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

    fun getDetailBlock(): String {
        if (detailEntries.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("[细节记忆 - 事实与状态]")
        for (entry in detailEntries) {
            val structured = entry.toStructuredText()
            if (structured != entry.content && entry.event.isNotBlank()) {
                sb.appendLine("- $structured")
            } else {
                sb.appendLine("- ${entry.content}")
            }
        }
        return sb.toString().trimEnd()
    }

    fun addDetailEntry(entry: MemoryEntry) {
        val existingIdx = detailEntries.indexOfFirst {
            it.content.contains(entry.content.take(20), ignoreCase = true) ||
            entry.content.contains(it.content.take(20), ignoreCase = true)
        }
        if (existingIdx >= 0) {
            detailEntries[existingIdx] = entry
        } else {
            detailEntries.add(entry)
        }
        recalcCharCount()
        saveToStorage()
    }

    fun deleteDetailEntry(id: String) {
        detailEntries.removeAll { it.id == id }
        recalcCharCount()
        saveToStorage()
    }

    fun getAllDetails(): List<MemoryEntry> = detailEntries.toList()

    fun getPoolCharCount(): Int = totalCharCount

    suspend fun consolidate(client: ApiClient): Boolean = withContext(Dispatchers.IO) {
        if (entries.isEmpty() && detailEntries.isEmpty()) {
            turnsSinceLastConsolidate = 0
            return@withContext false
        }

        AppLogger.d(TAG, "consolidate: starting with ${entries.size} entries, ${detailEntries.size} details, $totalCharCount chars")

        val fullPool = buildString {
            appendLine(getPoolBlock())
            if (detailEntries.isNotEmpty()) {
                appendLine()
                appendLine(getDetailBlock())
            }
        }

        val systemPrompt = buildString {
            append("你是一个记忆总结助手。请将以下记忆池内容重新整理总结。\n")
            append("要求：\n")
            append("- 简短精炼，但必须保留所有重要细节和具体内容\n")
            append("- 保留：具体事件、事实信息、用户观点与态度、场景描述、角色关系、用户喜好、对话中的关键细节\n")
            append("- 场景描述要具体，事件经过要完整\n")
            append("- 合并重复内容，删除过时信息\n")
            append("- 将细节记忆中仍然重要的事实合并到总结记忆中\n")
            append("- 不要丢失任何具体的事实、数据、观点或描述\n")
            append("- 每条记忆必须包含结构化字段，按以下JSON格式输出：\n")
            append("  {\"content\":\"总结内容\",\"eventTime\":\"时间\",\"place\":\"地点\",\"people\":\"人物\",\"event\":\"事件\",\"scene\":\"场景\",\"details\":\"细节\",\"relationships\":\"关系变化\"}\n")
            append("- 字段可以为空字符串，但必须存在\n")
            append("- 总字数不超过${MAX_CHARS}字\n")
            append("- 只输出JSON数组，不要其他内容\n")
        }

        try {
            val response = client.sendSimplePrompt(systemPrompt, fullPool)
            if (response != null && response.text.isNotBlank()) {
                val newEntries = parseConsolidatedStructuredResult(response.text)
                if (newEntries.isNotEmpty()) {
                    entries.clear()
                    entries.addAll(newEntries)
                    detailEntries.clear()
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

    private fun parseConsolidatedStructuredResult(text: String): List<MemoryEntry> {
        val results = mutableListOf<MemoryEntry>()
        try {
            var cleaned = text.trim()
                .replace(Regex("```(?:json)?\\s*"), "").replace("```", "").trim()

            val bracketStart = cleaned.indexOf('[')
            val bracketEnd = cleaned.lastIndexOf(']')
            if (bracketStart >= 0 && bracketEnd > bracketStart) {
                cleaned = cleaned.substring(bracketStart, bracketEnd + 1)
            }

            val arr = try { JSONArray(cleaned) } catch (e: Exception) {
                AppLogger.w(TAG, "记忆整合JSON数组解析失败，尝试文本解析: ${e.message}"); return parseConsolidatedResult(text)
            }

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    val content = obj.optString("content", "").trim()
                    if (content.isNotBlank()) {
                        results.add(MemoryEntry(
                            content = content,
                            category = "总结",
                            sourceTurn = totalTurns,
                            eventTime = obj.optString("eventTime", ""),
                            place = obj.optString("place", ""),
                            people = obj.optString("people", ""),
                            event = obj.optString("event", ""),
                            scene = obj.optString("scene", ""),
                            details = obj.optString("details", ""),
                            relationships = obj.optString("relationships", "")
                        ))
                    }
                } else {
                    val line = arr.optString(i, "").trim()
                    if (line.isNotBlank()) {
                        val cleanLine = line
                            .removePrefix("-").removePrefix("•")
                            .removePrefix("·").removePrefix("*").trim()
                        if (cleanLine.isNotBlank()) {
                            results.add(MemoryEntry(content = cleanLine, category = "总结", sourceTurn = totalTurns))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "parseConsolidatedStructuredResult error: ${e.message}")
            return parseConsolidatedResult(text)
        }

        return if (results.isEmpty()) parseConsolidatedResult(text) else results
    }

    private fun parseConsolidatedResult(text: String): List<MemoryEntry> {
        val results = mutableListOf<MemoryEntry>()
        val lines = text.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("[记忆池")) continue

            val cleanLine = trimmed
                .removePrefix("-").removePrefix("•")
                .removePrefix("·")
                .removePrefix("*")
                .trim()
                .removePrefix("[").let { if (it.contains("]")) it.substringAfter("]") else it }
                .trim()

            if (cleanLine.isBlank()) continue

            results.add(MemoryEntry(
                content = cleanLine,
                category = "总结",
                sourceTurn = totalTurns
            ))
        }
        return results
    }

    private fun trimToLimit() {
        while (totalCharCount > MAX_CHARS && entries.size > 1) {
            entries.removeAt(0)
            recalcCharCount()
        }
    }

    suspend fun evaluateTurn(
        client: ApiClient,
        turnsText: String,
        turnNumber: Int,
        userNickname: String = "用户"
    ): List<MemoryEntry> = withContext(Dispatchers.IO) {
        if (turnsText.isBlank()) return@withContext emptyList()

        val poolBlock = if (entries.isEmpty()) "（空）" else getPoolBlock()
        AppLogger.d(TAG, "evaluateTurn #$turnNumber: pool=${entries.size} entries")

        val nick = userNickname.ifBlank { "用户" }
        val systemPrompt = buildString {
            append("你是记忆总结助手。分析对话，提取值得记住的信息。\n")
            append("要求：\n")
            append("- 每条记忆必须包含结构化字段\n")
            append("- 必须记录对话中提到的所有具体内容：事实、数据、观点、描述、事件、决定、承诺等\n")
            append("- 不要过滤掉对话的具体内容，用户说的具体话、具体描述、具体信息都要记\n")
            append("- 只跳过纯寒暄（如「你好」「嗯」「好的」这类无实质内容的回应）\n")
            append("- 关注：对话中涉及的具体事件、事实信息、用户观点与态度、场景描述、角色关系、用户喜好\n")
            append("- 场景描述要具体（如：在森林里遇到受伤的小鹿而不是在某个地方遇到了什么）\n")
            append("- 事件经过要完整（起因、经过、结果）\n")
            append("- 用户表达的观点、感受、偏好都要记录，即使看起来不重要\n")
            append("- 提到$nick 时用「$nick」称呼\n")
            append("- 只输出JSON数组，不需要更新时输出[]\n")
            append("- 不要用Markdown代码块包裹\n")
            append("\n每条记忆的JSON格式：\n")
            append("{\"action\":\"add\",\"content\":\"简短总结\",\"eventTime\":\"事件发生的时间\",\"place\":\"地点\",\"people\":\"涉及的人物\",\"event\":\"发生了什么事\",\"scene\":\"场景描述\",\"details\":\"重要细节\",\"relationships\":\"关系变化\"}\n")
            append("action可选: add(新增), update(更新旧记忆), delete(删除过时记忆)\n")
            append("update需要额外字段: old_content_fragment(旧记忆片段)\n")
            append("delete需要额外字段: old_content_fragment(要删除的记忆片段)\n")
            append("结构化字段可以为空字符串，但必须存在\n")
            append("\n额外规则：对话中任何具体的、事实性的内容（如具体描述、数据、物品、位置、状态、颜色、数量、时间点、承诺、决定等容易遗忘的细节），请额外输出一条action为\"detail\"的记忆：\n")
            append("{\"action\":\"detail\",\"content\":\"具体细节描述\",\"details\":\"细节内容\",\"place\":\"位置\",\"event\":\"相关事件\"}\n")
            append("detail类型的记忆会单独保存，确保不会遗忘。宁可多记也不要遗漏。\n")
            append("\n重要：对于跨场景有价值的信息（如用户的喜好、习惯、事实信息、重要决定、关系变化等），请在记忆的JSON中添加 \"global\":true 字段。场景特定的事件（如'在群里讨论了游戏'）不需要标记global。示例：\n")
            append("{\"action\":\"add\",\"content\":\"用户喜欢吃苹果\",\"category\":\"喜好\",\"global\":true,...}\n")
            append("{\"action\":\"add\",\"content\":\"群里聊了3小时游戏\",\"category\":\"事件\",\"global\":false,...}\n")
        }

        val userContent = buildString {
            appendLine("[当前记忆池]")
            appendLine(poolBlock)
            appendLine()
            appendLine("[近期对话]")
            appendLine(turnsText)
            appendLine()
            appendLine("输出JSON数组：")
        }

        try {
            val response = client.sendSimplePrompt(systemPrompt, userContent)
            if (response != null && response.text.isNotBlank()) {
                AppLogger.d(TAG, "evaluateTurn #$turnNumber: API returned ${response.text.length} chars")
                val result = parseEvaluationResult(response.text, turnNumber)
                AppLogger.d(TAG, "evaluateTurn #$turnNumber result: ${result.size} new entries")
                result
            } else {
                val reason = if (response == null) "response is null" else "response text is blank"
                AppLogger.w(TAG, "evaluateTurn #$turnNumber: API call returned no usable result ($reason)")
                emptyList()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "evaluateTurn #$turnNumber failed: ${e.javaClass.simpleName}: ${e.message}")
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

            val arr = try { JSONArray(cleaned) } catch (e: Exception) { AppLogger.e(TAG, "记忆评估结果解析失败: ${e.message}", e); return emptyList() }
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
                                category = "总结",
                                sourceTurn = turnNumber,
                                eventTime = obj.optString("eventTime", ""),
                                place = obj.optString("place", ""),
                                people = obj.optString("people", ""),
                                event = obj.optString("event", ""),
                                scene = obj.optString("scene", ""),
                                details = obj.optString("details", ""),
                                relationships = obj.optString("relationships", ""),
                                isGlobal = obj.optBoolean("global", false)
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
                                    category = "总结",
                                    timestamp = System.currentTimeMillis(),
                                    sourceTurn = turnNumber,
                                    eventTime = obj.optString("eventTime", matched.eventTime),
                                    place = obj.optString("place", matched.place),
                                    people = obj.optString("people", matched.people),
                                    event = obj.optString("event", matched.event),
                                    scene = obj.optString("scene", matched.scene),
                                    details = obj.optString("details", matched.details),
                                    relationships = obj.optString("relationships", matched.relationships),
                                    isGlobal = obj.optBoolean("global", matched.isGlobal)
                                ))
                            } else {
                                results.add(MemoryEntry(
                                    content = newContent,
                                    category = "总结",
                                    sourceTurn = turnNumber,
                                    eventTime = obj.optString("eventTime", ""),
                                    place = obj.optString("place", ""),
                                    people = obj.optString("people", ""),
                                    event = obj.optString("event", ""),
                                    scene = obj.optString("scene", ""),
                                    details = obj.optString("details", ""),
                                    relationships = obj.optString("relationships", ""),
                                    isGlobal = obj.optBoolean("global", false)
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
                    "detail" -> {
                        val content = obj.optString("content", "").trim()
                        if (content.isNotBlank()) {
                            results.add(MemoryEntry(
                                content = content,
                                category = "细节",
                                sourceTurn = turnNumber,
                                eventTime = obj.optString("eventTime", ""),
                                place = obj.optString("place", ""),
                                people = obj.optString("people", ""),
                                event = obj.optString("event", ""),
                                scene = obj.optString("scene", ""),
                                details = obj.optString("details", ""),
                                relationships = obj.optString("relationships", ""),
                                isGlobal = obj.optBoolean("global", false)
                            ))
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
                arr.put(entry.toJson())
            }
            val detailArr = JSONArray()
            for (entry in detailEntries) {
                detailArr.put(entry.toJson())
            }
            prefs.edit()
                .putString("entries", arr.toString())
                .putString("detail_entries", detailArr.toString())
                .putInt("turns_since_consolidate", turnsSinceLastConsolidate)
                .putInt("total_turns", totalTurns)
                .apply()
        } catch (e: Exception) { AppLogger.e(TAG, "记忆保存失败！数据可能丢失: ${e.message}", e) }
    }

    fun loadFromStorage() {
        try {
            val json = prefs.getString("entries", null) ?: return
            val arr = JSONArray(json)
            entries.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                entries.add(MemoryEntry.fromJson(obj))
            }
            turnsSinceLastConsolidate = prefs.getInt("turns_since_consolidate", 0)
            totalTurns = prefs.getInt("total_turns", 0)
            val detailJson = prefs.getString("detail_entries", null)
            if (detailJson != null) {
                val detailArr = JSONArray(detailJson)
                detailEntries.clear()
                for (i in 0 until detailArr.length()) {
                    val obj = detailArr.getJSONObject(i)
                    detailEntries.add(MemoryEntry.fromJson(obj))
                }
            }
            recalcCharCount()
        } catch (e: Exception) {
            AppLogger.e(TAG, "记忆加载失败，已清空: ${e.message}", e); entries.clear()
        }
    }

    private fun recalcCharCount() {
        totalCharCount = entries.sumOf { it.content.length } + detailEntries.sumOf { it.content.length }
    }

    fun clear() {
        entries.clear()
        detailEntries.clear()
        totalCharCount = 0
        turnsSinceLastConsolidate = 0
        totalTurns = 0
        prefs.edit().clear().apply()
    }

    fun getStats(): String {
        return "共${entries.size}条记忆 | ${totalCharCount}字"
    }
}
