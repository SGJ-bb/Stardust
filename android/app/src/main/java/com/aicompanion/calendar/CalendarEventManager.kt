package com.aicompanion.calendar

import android.content.Context
import android.content.SharedPreferences
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户/AI 添加的日历事件
 *
 * @param id 唯一 ID
 * @param title 事件标题
 * @param description 事件描述（可选）
 * @param date 日期，格式 yyyy-MM-dd
 * @param time 时间，格式 HH:mm（可选，为空表示全天事件）
 * @param category 分类：general/anniversary/reminder/birthday/meeting 等
 * @param createdBy 创建者：user / ai
 * @param createdAt 创建时间戳
 * @param color 颜色标记（可选，用于 UI 显示）
 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    val date: String,
    val time: String,
    val category: String,
    val createdBy: String,
    val createdAt: Long,
    val color: String = "primary",
)

/**
 * 日历事件管理器 — 管理用户和 AI 添加的自定义事件
 *
 * 按 personaId 隔离，存储到 `calendar_events_$personaId` SP
 */
class CalendarEventManager(context: Context, private val personaId: String = "default") {

    companion object {
        private const val TAG = "CalendarEventManager"
        private const val KEY_EVENTS = "event_list"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("calendar_events_$personaId", Context.MODE_PRIVATE)

    @Synchronized
    fun addEvent(
        title: String,
        date: String,
        time: String = "",
        description: String = "",
        category: String = "general",
        createdBy: String = "user",
        color: String = "primary",
    ): CalendarEvent {
        val event = CalendarEvent(
            id = "evt_${System.currentTimeMillis()}_${(1..9999).random()}",
            title = title,
            description = description,
            date = date,
            time = time,
            category = category,
            createdBy = createdBy,
            createdAt = System.currentTimeMillis(),
            color = color,
        )
        val events = getAllEvents().toMutableList()
        events.add(event)
        saveEvents(events)
        AppLogger.i(TAG, "Event added: $title on $date by $createdBy")
        return event
    }

    @Synchronized
    fun deleteEvent(eventId: String) {
        val events = getAllEvents().filter { it.id != eventId }
        saveEvents(events)
    }

    fun getAllEvents(): List<CalendarEvent> {
        val json = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val obj = arr.getJSONObject(i)
                    CalendarEvent(
                        id = obj.optString("id", ""),
                        title = obj.optString("title", ""),
                        description = obj.optString("description", ""),
                        date = obj.optString("date", ""),
                        time = obj.optString("time", ""),
                        category = obj.optString("category", "general"),
                        createdBy = obj.optString("createdBy", "user"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        color = obj.optString("color", "primary"),
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    /** 获取指定日期的事件 */
    fun getEventsByDate(year: Int, month: Int, day: Int): List<CalendarEvent> {
        val dateStr = String.format("%04d-%02d-%02d", year, month, day)
        return getAllEvents().filter { it.date == dateStr }
    }

    /** 获取指定月份所有事件的日期集合（用于网格标记） */
    fun getEventDaysInMonth(year: Int, month: Int): Set<Int> {
        val monthStr = String.format("%04d-%02d", year, month)
        return getAllEvents()
            .filter { it.date.startsWith(monthStr) }
            .mapNotNull { it.date.substringAfterLast("-").toIntOrNull() }
            .toSet()
    }

    private fun saveEvents(events: List<CalendarEvent>) {
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("title", e.title)
                put("description", e.description)
                put("date", e.date)
                put("time", e.time)
                put("category", e.category)
                put("createdBy", e.createdBy)
                put("createdAt", e.createdAt)
                put("color", e.color)
            })
        }
        prefs.edit().putString(KEY_EVENTS, arr.toString()).apply()
    }
}
