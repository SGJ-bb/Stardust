package com.aicompanion.memory

import android.content.Context
import com.aicompanion.config.AppConfig
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
    val isGlobal: Boolean = false,
    val importance: Float = 1.0f  // 重要性评分 0.0-1.0，默认1.0
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
        put("importance", importance)
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
            isGlobal = obj.optBoolean("isGlobal", false),
            importance = obj.optDouble("importance", 1.0).toFloat()
        )
    }
}

/**
 * 记忆池配置类
 * 支持根据模型大小动态调整记忆容量
 */
data class MemoryConfig(
    val maxChars: Int = AppConfig.MEMORY_MAX_CHARS_DEFAULT,
    val importanceThreshold: Float = AppConfig.MEMORY_IMPORTANCE_THRESHOLD
) {
    companion object {
        fun forLargeModel() = MemoryConfig(maxChars = AppConfig.MEMORY_MAX_CHARS_LARGE_MODEL)
        fun forSmallModel() = MemoryConfig(maxChars = AppConfig.MEMORY_MAX_CHARS_SMALL_MODEL)
        fun fromUserPreference(prefs: android.content.SharedPreferences): MemoryConfig {
            val modelSize = prefs.getString("model_size", "medium")
            return when (modelSize) {
                "large" -> forLargeModel()
                "small" -> forSmallModel()
                else -> MemoryConfig()
            }
        }
    }
}

class MemoryPool(
    private val context: Context,
    private val personaId: String = "default",
    private val scope: String = "private",
    private val config: MemoryConfig = MemoryConfig()
) {

    companion object {
        private const val TAG = "MemoryPool"
        private const val CONSOLIDATE_INTERVAL = 10
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
        if (entry.content.isBlank()) return
        entries.removeAll { it.id == entry.id }
        entries.add(entry)
        recalcCharCount()
    }

    fun add(entry: MemoryEntry) {
        if (entry.content.isBlank()) return
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

    /**
     * 手动标记记忆重要性
     * @param index 记忆索引
     * @param importance 重要性评分 0.0-1.0
     */
    fun markImportance(index: Int, importance: Float) {
        if (index in entries.indices) {
            entries[index] = entries[index].copy(importance = importance.coerceIn(0f, 1f))
            AppLogger.d(TAG, "Marked memory importance: ${entries[index].content.take(30)}... -> $importance")
        }
    }

    /**
     * 通过ID标记记忆重要性
     * @param id 记忆ID
     * @param importance 重要性评分 0.0-1.0
     */
    fun markImportanceById(id: String, importance: Float) {
        val index = entries.indexOfFirst { it.id == id }
        if (index >= 0) {
            markImportance(index, importance)
        }
    }

    fun incrementTurn() {
        turnsSinceLastConsolidate++
        totalTurns++
    }

    fun needsConsolidate(): Boolean = turnsSinceLastConsolidate >= CONSOLIDATE_INTERVAL

    fun getPoolBlock(): String {
        if (entries.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("[记忆池]")

        // 按scene分组输出，同一场景的记忆聚合展示
        val grouped = entries.groupBy {
            val s = it.scene.ifBlank { it.place.ifBlank { "其他" } }
            s.take(20)
        }

        for ((sceneKey, groupEntries) in grouped) {
            sb.appendLine("◆ $sceneKey")
            for (entry in groupEntries) {
                val structured = entry.toStructuredText()
                if (structured != entry.content && entry.event.isNotBlank()) {
                    sb.appendLine("  · $structured")
                } else {
                    sb.appendLine("  · ${entry.content}")
                }
            }
        }
        return sb.toString().trimEnd()
    }

    fun getDetailBlock(): String {
        if (detailEntries.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("[细节记忆]")
        for (entry in detailEntries) {
            val structured = entry.toStructuredText()
            if (structured != entry.content && entry.event.isNotBlank()) {
                sb.appendLine("  · $structured")
            } else {
                sb.appendLine("  · ${entry.content}")
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

    /**
 * 压缩记忆池。返回被归档的旧内容（用于写入日记作为长期记忆），无归档内容则返回空字符串
 */
suspend fun consolidate(client: ApiClient): String = withContext(Dispatchers.IO) {
        if (entries.isEmpty() && detailEntries.isEmpty()) {
            turnsSinceLastConsolidate = 0
            return@withContext ""
        }

        AppLogger.w(TAG, "consolidate: starting with ${entries.size} entries, ${detailEntries.size} details, $totalCharCount chars")

        val fullPool = buildString {
            appendLine(getPoolBlock())
            if (detailEntries.isNotEmpty()) {
                appendLine()
                appendLine(getDetailBlock())
            }
        }

        val systemPrompt = buildString {
            append("你是记忆整合助手。请将以下记忆重新按【场景】归类整理。\n\n")
            append("【要求】\n")
            append("- 将属于同一场景/主题的记忆合并为一条完整的场景概述\n")
            append("- 合并后的每条记忆应包含该场景的所有关键信息：起因、经过、结果、涉及人物、重要细节\n")
            append("- 删除已过时的信息（如已完成的事件、已过期的计划）\n")
            append("- 保留所有跨场景共享的重要信息（用户喜好、习惯、关系变化等）\n")
            append("- 最终输出应像「故事章节目录」，每个场景一段话，而非流水账\n\n")
            append("【格式要求】\n")
            append("- 只输出JSON数组\n")
            append("- 每条记忆必须包含结构化字段:\n")
            append("  {\"content\":\"场景概述\",\"eventTime\":\"时间\",\"place\":\"地点\",\"people\":\"人物\",\"event\":\"完整事件\",\"scene\":\"场景分类\",\"details\":\"关键细节\",\"relationships\":\"关系变化\"}\n")
            append("- 总字数不超过${config.maxChars}字\n")
            append("- 字段可为空字符串但必须存在\n")
        }

        try {
            val response = client.sendSimplePrompt(systemPrompt, fullPool)
            if (response != null && response.text.isNotBlank()) {
                val newEntries = parseConsolidatedStructuredResult(response.text)
                if (newEntries.isNotEmpty()) {
                    // 收集将被替换的旧内容作为归档（写入日记）
                    val archivedContent = buildString {
                        appendLine("[记忆归档·${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date())}]")
                        for (entry in entries) {
                            val structured = entry.toStructuredText()
                            appendLine(if (structured != entry.content && entry.event.isNotBlank()) "- $structured" else "- ${entry.content}")
                        }
                    }

                    entries.clear()
                    entries.addAll(newEntries)
                    detailEntries.clear()
                    recalcCharCount()
                    saveToStorage()
                    AppLogger.w(TAG, "consolidate: done, ${entries.size} entries, $totalCharCount chars")
                    turnsSinceLastConsolidate = 0
                    return@withContext archivedContent
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "[Memory-Pool] 记忆池整合失败: ${e.javaClass.simpleName}: ${e.message} | entries=${entries.size}")
        }

        if (totalCharCount > config.maxChars) {
            val archivedContent = buildString {
                appendLine("[记忆归档·${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date())}]")
                while (totalCharCount > config.maxChars) {
                    if (entries.size == 1) {
                        // 只有一条记忆且超限：强制截断内容
                        val entry = entries[0]
                        if (entry.content.length > config.maxChars) {
                            val truncatedContent = entry.content.take(config.maxChars)
                            entries[0] = try {
                                entry.copy(content = truncatedContent)
                            } catch (e: Exception) {
                                // 解析失败，保留原始数据避免丢失
                                AppLogger.e(TAG, "截断后解析失败，保留原始数据: ${e.message}")
                                entry  // 不修改，保持原样
                            }
                            recalcCharCount()
                            AppLogger.w(TAG, "单条记忆超限，截断至${config.maxChars}字符")
                        }
                        break
                    }
                    // 智能裁剪：优先删除低重要性记忆
                    val leastImportant = entries.minByOrNull { it.importance }
                    val removed = if (leastImportant != null && leastImportant.importance < config.importanceThreshold) {
                        entries.remove(leastImportant)
                        leastImportant
                    } else {
                        entries.removeAt(0)
                    }
                    appendLine("- ${removed.content}")
                    recalcCharCount()
                }
            }
            saveToStorage()
            turnsSinceLastConsolidate = 0
            return@withContext archivedContent
        }
        turnsSinceLastConsolidate = 0
        return@withContext ""   // 无归档内容
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

    /**
     * 智能裁剪记忆池
     * 优先删除重要性低的记忆，若所有记忆重要性都较高则降级删除最旧的
     * 特殊处理：只有一条记忆且超限时强制截断内容
     */
    private fun trimToLimit() {
        while (totalCharCount > config.maxChars) {
            if (entries.size == 1) {
                // 只有一条记忆且超限：强制截断内容
                val entry = entries[0]
                if (entry.content.length > config.maxChars) {
                    val truncatedContent = entry.content.take(config.maxChars)
                    entries[0] = try {
                        entry.copy(content = truncatedContent)
                    } catch (e: Exception) {
                        // 解析失败，保留原始数据避免丢失
                        AppLogger.e(TAG, "截断后解析失败，保留原始数据: ${e.message}")
                        entry  // 不修改，保持原样
                    }
                    recalcCharCount()
                    AppLogger.w(TAG, "单条记忆超限，截断至${config.maxChars}字符")
                }
                break
            }

            // 找到重要性最低的记忆
            val leastImportant = entries.minByOrNull { it.importance }
            if (leastImportant != null && leastImportant.importance < config.importanceThreshold) {
                entries.remove(leastImportant)
                recalcCharCount()
                AppLogger.d(TAG, "Removed low importance memory: ${leastImportant.content.take(30)}...")
            } else {
                // 降级：删除最旧的
                val removed = entries.removeAt(0)
                recalcCharCount()
                AppLogger.d(TAG, "Removed oldest memory (all important): ${removed.content.take(30)}...")
            }
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
        //AppLogger.d(TAG, "evaluateTurn #$turnNumber: pool=${entries.size} entries")

        val nick = userNickname.ifBlank { "用户" }
        val systemPrompt = buildString {
            append("你是记忆整理助手。你的任务是将对话内容按【场景】和【事件】进行归纳总结。\n\n")
            append("【核心原则】\n")
            append("- 不要逐句记录对话！要将对话内容抽象为「场景→发生了什么」的结构化记忆\n")
            append("- 将同一场景下发生的多轮对话合并为一条完整的场景记忆\n")
            append("- 每条记忆应该像小说的章节摘要，而不是对话记录\n\n")

            append("【输出格式】只输出JSON数组，不要Markdown包裹\n")
            append("每条JSON格式：\n")
            append("{\"action\":\"add\",\"content\":\"场景摘要(一句话概括这个场景发生了什么)\",\"eventTime\":\"时间\",\"place\":\"地点/场景名\",\"people\":\"涉及人物\",\"event\":\"具体事件经过(起因→经过→结果)\",\"scene\":\"场景类型(如:日常闲聊/情感交流/计划讨论/分享经历/游戏娱乐/学习工作等)\",\"details\":\"关键细节(具体数字、名称、承诺、决定等)\",\"relationships\":\"关系变化(如有)\"}\n\n")

            append("【action类型】\n")
            append("- add: 新增场景/事件记忆\n")
            append("- update: 更新已有记忆(需加old_content_fragment字段)\n")
            append("- delete: 删除过时记忆(需加old_content_fragment字段)\n")
            append("- detail: 重要事实细节(单独保存，防止遗忘)\n\n")

            append("【重要规则】\n")
            append("- 场景描述要具体：「在咖啡馆聊了项目进度」而不是「聊了一些事情」\n")
            append("- 事件要有完整脉络：「用户提到最近压力大→AI安慰并建议休息→用户表示会试试」而不是零散的三句话\n")
            append("- 同一话题的多轮对话合并为一条：如果用户和AI连续5轮都在讨论旅行计划，只输出1条记忆概括整个讨论\n")
            append("- 只跳过纯寒暄（如「你好」「嗯」「好的」）\n")
            append("- 用户的具体偏好、承诺、决定、数据必须记入details字段\n")
            append("- 跨场景有价值的信息(喜好、习惯、重要事实)加 \"global\":true\n")
            append("- detail类型的记忆用于保存容易遗忘的具体细节\n")
            append("- 结构化字段可以为空字符串但必须存在\n")
            append("- 提到$nick 时用「$nick」称呼\n")
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
                //AppLogger.d(TAG, "evaluateTurn #$turnNumber: API returned ${response.text.length} chars")
                val result = parseEvaluationResult(response.text, turnNumber)
                //AppLogger.d(TAG, "evaluateTurn #$turnNumber result: ${result.size} new entries")
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
        } catch (e: Exception) { AppLogger.e(TAG, "[Memory-Pool] 记忆保存失败,数据可能丢失: ${e.javaClass.simpleName}: ${e.message}", e) }
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
            AppLogger.e(TAG, "[Memory-Pool] 记忆加载失败,已清空: ${e.javaClass.simpleName}: ${e.message}", e); entries.clear()
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
