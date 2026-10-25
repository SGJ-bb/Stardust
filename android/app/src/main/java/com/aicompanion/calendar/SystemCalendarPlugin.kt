package com.aicompanion.calendar

import android.content.Context
import com.aicompanion.calendar.SystemCalendarBridge as Cal
import com.aicompanion.models.ToolDefinition
import com.aicompanion.plugin.ToolPlugin
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 系统日历工具。
 *
 * 跟内置的 `add_calendar_event`（只写 App 自己的日历）互补：
 * 这个读写的是手机日历 App 里那份真数据 —— 用户在系统日历里排的会议，AI 能看到。
 *
 * 权限靠 shell 通道 `pm grant` 自授权，不需要用户点授权框。
 */
class SystemCalendarPlugin(private val context: Context) : ToolPlugin {

    override val name = "calendar"
    override val description = "读写手机系统日历：查日程、加日程、删日程"

    override fun getDefinition() = ToolDefinition(
        name = "calendar",
        description = """读写手机「系统日历」（就是手机自带日历 App 里那份）。
【何时调用】
- 用户问"我今天/明天/这周有什么安排""最近有什么事" → action=agenda
- 用户说"帮我记到日历上""明天下午3点开会" → action=add
- 用户说"把那个会议删掉" → action=delete
【注意】
- 只查一天时 date 传 yyyy-MM-dd；查多天用 days
- 不要编造日程：查不到就如实说"这段时间没有安排"""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("agenda", "add", "delete"),
                    "description" to "agenda=查看日程，add=新增，delete=删除"
                ),
                "date" to mapOf("type" to "string", "description" to "起始日期 yyyy-MM-dd，留空=今天"),
                "days" to mapOf("type" to "integer", "description" to "查看范围的天数，默认 1"),
                "title" to mapOf("type" to "string", "description" to "日程标题（add/delete 必填）"),
                "time" to mapOf("type" to "string", "description" to "开始时间 HH:mm，全天事件不填"),
                "duration_min" to mapOf("type" to "integer", "description" to "时长（分钟），默认 60"),
                "all_day" to mapOf("type" to "boolean", "description" to "是否全天事件"),
                "location" to mapOf("type" to "string", "description" to "地点（可选）"),
                "description" to mapOf("type" to "string", "description" to "备注（可选）")
            ),
            "required" to listOf("action")
        )
    )

    override suspend fun execute(arguments: String): String = withContext(Dispatchers.IO) {
        val a = JSONObject(arguments)
        val action = a.optString("action", "agenda")
        try {
            when (action) {
                "agenda" -> agenda(a)
                "add" -> add(a)
                "delete" -> delete(a)
                else -> "错误：action 只能是 agenda/add/delete"
            }
        } catch (e: SecurityException) {
            "没有日历权限：请在系统弹出的授权框里允许「日历」权限（或到 设置→应用→星尘→权限 里打开），然后再让我试一次。"
        } catch (e: Exception) {
            AppLogger.e("CalendarTool", "$action 失败: ${e.message}")
            if (e.message?.contains("权限") == true) {
                "日历操作没成功：${e.message}。请允许「日历」权限后再试。"
            } else {
                "日历操作失败：${e.message}"
            }
        }
    }

    private fun agenda(a: JSONObject): String {
        val date = a.optString("date", "")
        val days = a.optInt("days", 1).coerceIn(1, 60)
        val start = Cal.dayStart(date)
        val end = start + days * 24L * 3600_000L
        val events = Cal.agenda(context, start, end)
        val rangeText = "${SimpleDateFormat("M月d日", Locale.CHINA).format(Date(start))}" +
            if (days > 1) " 起 $days 天内" else ""

        if (events.isEmpty()) return "$rangeText 没有安排。"

        val sb = StringBuilder("$rangeText 共 ${events.size} 项：\n")
        events.take(30).forEach { sb.appendLine("· ${it.render()}") }
        if (events.size > 30) sb.appendLine("（只列了前 30 项）")
        return sb.toString().trimEnd()
    }

    private fun add(a: JSONObject): String {
        val title = a.optString("title", "").trim()
        if (title.isBlank()) return "错误：请提供日程标题"
        val allDay = a.optBoolean("all_day", false)
        val date = a.optString("date", "")
        val time = a.optString("time", "")
        val begin = if (allDay || time.isBlank()) Cal.dayStart(date) else Cal.at(date, time)
        val durationMin = a.optInt("duration_min", 60).coerceIn(5, 24 * 60)
        val end = if (allDay || time.isBlank()) begin + 24L * 3600_000L else begin + durationMin * 60_000L

        val id = Cal.addEvent(
            ctx = context,
            title = title,
            beginMs = begin,
            endMs = end,
            description = a.optString("description", ""),
            location = a.optString("location", ""),
            allDay = allDay || time.isBlank(),
        )
        val fmt = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
        val whenText = if (allDay || time.isBlank()) {
            SimpleDateFormat("M月d日", Locale.CHINA).format(Date(begin)) + " 全天"
        } else {
            "${fmt.format(Date(begin))}–${SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(end))}"
        }
        AppLogger.i("CalendarTool", "写入系统日历 id=$id $title $whenText")
        return "已写进手机日历：「$title」$whenText（事件 id $id）"
    }

    private fun delete(a: JSONObject): String {
        val title = a.optString("title", "").trim()
        if (title.isBlank()) return "错误：请提供要删除的日程标题"
        val date = a.optString("date", "")
        val time = a.optString("time", "")
        val begin = if (time.isBlank()) Cal.dayStart(date) else Cal.at(date, time)
        val n = Cal.deleteByTitleAndTime(context, title, begin)
        return if (n > 0) "已删除 $n 条「$title」" else "没找到匹配的日程（标题和开始时间都要对上）"
    }
}
