package com.aicompanion.memory

import android.content.Context
import com.aicompanion.network.ApiClient
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 虚拟世界专用记忆池
 *
 * 与私聊 MemoryPool 完全独立存储，专门管理虚拟世界推演产生的事件记忆。
 * 特点：
 * - 独立 SharedPreferences 存储 (key: vw_memory_pool_{worldId})
 * - 自动按场景/时间段压缩（每10条事件触发一次）
 * - 上限 800 字，超限自动压缩为场景摘要
 * 提供 getVwBlock() 输出格式化的记忆块，供 ContextManager 注入 LLM prompt
 */
class VirtualWorldMemoryPool(
    private val context: Context,
    private val worldId: String = ""
) {

    companion object {
        private const val TAG = "VWMemoryPool"
        private const val MAX_CHARS = 800       // VW记忆上限800字
        private const val CONSOLIDATE_INTERVAL = 10  // 每10条事件压缩一次
    }

    private val entries = mutableListOf<VwMemoryEntry>()
    private var eventCountSinceConsolidate = 0
    private var totalCharCount = 0
    private val lock = Any()

    private val prefsKey = "vw_memory_pool_${if (worldId.isBlank()) "global" else worldId}"
    private val prefs = context.getSharedPreferences(prefsKey, Context.MODE_PRIVATE)

    val isEmpty: Boolean get() = entries.isEmpty()
    val size: Int get() = entries.size

    init { loadFromStorage() }

    data class VwMemoryEntry(
        val id: String = UUID.randomUUID().toString().take(8),
        val day: Int,
        val hour: Int,
        val location: String,
        val weather: String,
        val mood: String,
        val summary: String,         // 事件摘要（1-2句话）
        val fullContent: String,     // 完整内容（原始推演文本）
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun toDisplayText(): String {
            return "[第${day}天${String.format("%02d", hour)}时·$location] $summary"
        }

        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("day", day)
            put("hour", hour)
            put("location", location)
            put("weather", weather)
            put("mood", mood)
            put("summary", summary)
            put("fullContent", fullContent)
            put("timestamp", timestamp)
        }

        companion object {
            fun fromJson(obj: JSONObject): VwMemoryEntry = VwMemoryEntry(
                id = obj.optString("id", UUID.randomUUID().toString().take(8)),
                day = obj.optInt("day", 1),
                hour = obj.optInt("hour", 8),
                location = obj.optString("location", ""),
                weather = obj.optString("weather", ""),
                mood = obj.optString("mood", ""),
                summary = obj.optString("summary", ""),
                fullContent = obj.optString("fullContent", ""),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }

    /** 添加一条推演事件 */
    @Synchronized
    fun addEvent(day: Int, hour: Int, location: String, weather: String, mood: String, content: String) {
        val entry = VwMemoryEntry(
            day = day,
            hour = hour,
            location = location,
            weather = weather,
            mood = mood,
            summary = content.take(150),
            fullContent = content
        )
        entries.add(entry)
        totalCharCount += content.length
        eventCountSinceConsolidate++

        while (totalCharCount > MAX_CHARS && entries.size > 1) {
            val removed = entries.removeAt(0)
            totalCharCount -= removed.fullContent.length
        }

        saveToStorage()
    }

    /** 需要压缩？ */
    fun needsConsolidate(): Boolean = eventCountSinceConsolidate >= CONSOLIDATE_INTERVAL || totalCharCount > MAX_CHARS

    /**
     * 压缩：调用LLM将多条事件归纳为场景级摘要
     * 返回被移除的旧内容（用于写入日记）
     */
    suspend fun consolidate(client: ApiClient): String = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (entries.isEmpty()) {
                eventCountSinceConsolidate = 0
                return@synchronized ""
            }

            val overflowContent = buildString {
                appendLine("[虚拟世界·长期记忆归档]")
                for (entry in entries) {
                    appendLine(entry.toDisplayText())
                }
            }

            // Build current event summary for LLM compression
            val currentEvents = entries.joinToString("\n") {
                "[第${it.day}天${it.hour}时·${it.location}] ${it.summary}"
            }

            val systemPrompt = """You are a virtual world memory organizer. Compress these events into concise scene summaries.

Rules:
- Merge consecutive events at the same location into one scene summary
- Keep key info: time, location, what happened, state changes (weather/mood)
- Remove trivial details
- Output format per line: [Day HH·Location] One-sentence summary
- Total output under ${MAX_CHARS} characters
- Output only text list, one line each"""

            try {
                val response = client.sendSimplePrompt(systemPrompt, currentEvents)
                if (response != null && response.text.isNotBlank()) {
                    val newEntries = parseConsolidatedResult(response.text)
                    if (newEntries.isNotEmpty()) {
                        entries.clear()
                        entries.addAll(newEntries)
                        totalCharCount = entries.sumOf { it.fullContent.length }
                        eventCountSinceConsolidate = 0
                        saveToStorage()
                        AppLogger.i(TAG, "VW memory consolidated: ${entries.size} entries, ${totalCharCount} chars")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "[Memory-VW] 虚拟世界记忆池整合失败: ${e.javaClass.simpleName}: ${e.message}")

                // Fallback: merge by location, keep latest 3 per location
                if (entries.size > 10) {
                    val merged = entries.groupBy { it.location.ifBlank { "Unknown" } }.mapValues { (_, group) ->
                        group.takeLast(3)
                    }.values.flatten().sortedBy { it.timestamp }

                    if (merged.isNotEmpty()) {
                        entries.clear()
                        entries.addAll(merged)
                        totalCharCount = entries.sumOf { it.fullContent.length }
                        saveToStorage()
                        AppLogger.i(TAG, "VW fallback consolidation: ${entries.size} entries")
                    }
                }
            }

            if (totalCharCount > MAX_CHARS) trimToLimit()
            eventCountSinceConsolidate = 0
            overflowContent
        }
    }

    private fun parseConsolidatedResult(text: String): List<VwMemoryEntry> {
        val results = mutableListOf<VwMemoryEntry>()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("[") && !it.startsWith("虚拟世界") }
        for (line in lines) {
            val cleanLine = line.removePrefix("-").removePrefix("•").removePrefix("·").removePrefix("*").trim()
            if (cleanLine.isBlank()) continue

            // 尝试解析 [第X天HH时·地点] 格式
            val pattern = Regex("\\[第(\\d+)天(\\d+)时·(.+?)\\](.+)")
            val match = pattern.find(cleanLine)
            if (match != null) {
                results.add(VwMemoryEntry(
                    day = match.groupValues[1].toIntOrNull() ?: 1,
                    hour = match.groupValues[2].toIntOrNull() ?: 12,
                    location = match.groupValues[3].trim(),
                    weather = "",
                    mood = "",
                    summary = match.groupValues[4].trim(),
                    fullContent = cleanLine
                ))
            } else {
                results.add(VwMemoryEntry(
                    day = 1, hour = 12, location = "", weather = "", mood = "",
                    summary = cleanLine, fullContent = cleanLine
                ))
            }
        }
        return if (results.isEmpty()) {
            // 解析失败时保留原始条目的摘要版本
            entries.takeLast(5).map { VwMemoryEntry(
                day = it.day, hour = it.hour, location = it.location,
                weather = it.weather, mood = it.mood,
                summary = it.summary, fullContent = it.summary
            )}
        } else results
    }

    private fun trimToLimit() {
        while (totalCharCount > MAX_CHARS && entries.size > 1) {
            val removed = entries.removeAt(0)
            totalCharCount -= removed.fullContent.length
        }
    }

    /** 获取格式化的记忆块，用于注入 LLM prompt */
    fun getVwBlock(): String {
        if (entries.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("[虚拟世界记忆]")

        // 按地点分组
        val grouped = entries.groupBy { it.location.ifBlank { "未知地点" } }
        for ((loc, locEntries) in grouped) {
            sb.appendLine("◆ $loc")
            for (entry in locEntries) {
                sb.appendLine("  · [第${entry.day}天${String.format("%02d", entry.hour)}时] ${entry.summary}")
                if (entry.weather.isNotBlank() || entry.mood.isNotBlank()) {
                    val extra = listOfNotNull(
                        if (entry.weather.isNotBlank()) "天气:${entry.weather}" else null,
                        if (entry.mood.isNotBlank()) "氛围:${entry.mood}" else null
                    ).joinToString(" ")
                    if (extra.isNotEmpty()) sb.appendLine("    ($extra)")
                }
            }
        }
        return sb.toString().trimEnd()
    }

    /** 获取所有记忆条目（用于详情展示） */
    fun getAllEntries(): List<VwMemoryEntry> = entries.toList()

    /** 获取最近的N条事件摘要（用于虚拟世界推演上下文） */
    fun getRecentSummaries(count: Int = 5): List<String> {
        return entries.takeLast(count).map { it.toDisplayText() }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        totalCharCount = 0
        eventCountSinceConsolidate = 0
        prefs.edit().clear().apply()
    }

    @Synchronized
    private fun saveToStorage() {
        try {
            val arr = JSONArray()
            for (entry in entries) arr.put(entry.toJson())
            prefs.edit()
                .putString("entries", arr.toString())
                .putInt("event_count_since_consolidate", eventCountSinceConsolidate)
                .apply()
        } catch (e: Exception) {
            AppLogger.e(TAG, "[Memory-VW] 虚拟世界记忆保存失败: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Synchronized
    private fun loadFromStorage() {
        try {
            val json = prefs.getString("entries", null) ?: return
            val arr = JSONArray(json)
            entries.clear()
            for (i in 0 until arr.length()) {
                entries.add(VwMemoryEntry.fromJson(arr.getJSONObject(i)))
            }
            eventCountSinceConsolidate = prefs.getInt("event_count_since_consolidate", 0)
            totalCharCount = entries.sumOf { it.fullContent.length }
        } catch (e: Exception) {
            AppLogger.e(TAG, "[Memory-VW] 虚拟世界记忆加载失败: ${e.javaClass.simpleName}: ${e.message}")
            entries.clear()
        }
    }

    fun getStats(): String = "共${entries.size}条VW记忆 | ${totalCharCount}字"
}
