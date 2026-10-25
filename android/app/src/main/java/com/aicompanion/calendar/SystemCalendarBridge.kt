package com.aicompanion.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.TextUtils
import com.aicompanion.util.AppLogger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 手机「系统日历」的读写桥。
 *
 * 和 App 内置的 CalendarEventManager（只有自己能看见的日程）不同，
 * 这里操作的是日历 App 里那份真日历：用户在系统日历里加的东西 AI 能读到，
 * AI 加的东西用户在系统日历里也能看到。
 *
 * 权限上有个便利：我们有 shell(2000) 通道，`pm grant` 可以直接把运行时权限授给自己，
 * 所以不需要弹授权框 —— 用户零操作。
 */
object SystemCalendarBridge {

    private const val TAG = "SystemCalendar"

    private fun hasRead(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

    private fun hasWrite(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

    /**
     * 确保权限。优先用 shell 通道自授权（零弹窗），失败则如实返回 false，
     * 由上层告诉用户"需要在系统设置里给日历权限"。
     */
    fun ensurePermission(ctx: Context, needWrite: Boolean): Boolean {
        if (hasRead(ctx) && (!needWrite || hasWrite(ctx))) return true
        // 没有 shell 通道时不要空等（拉起守护进程要 8 秒而且注定失败）：
        // 直接判定为需要用户授权，由 UI 侧弹系统授权框。
        if (!com.aicompanion.adb.AdbSelf.isReady()) {
            AppLogger.i(TAG, "无 shell 通道，跳过自授权（需用户在系统授权框里允许日历权限）")
            return false
        }
        val pkg = ctx.packageName
        val cmds = buildList {
            if (!hasRead(ctx)) add("pm grant $pkg android.permission.READ_CALENDAR")
            if (needWrite && !hasWrite(ctx)) add("pm grant $pkg android.permission.WRITE_CALENDAR")
        }
        val sh = com.aicompanion.adb.AdbSelf
        for (c in cmds) {
            val r = sh.execRaw(ctx, c, 8000)
            AppLogger.i(TAG, "自动授权 [$c] exit=${r.exitCode} ${r.stderr.take(80)}")
        }
        return hasRead(ctx) && (!needWrite || hasWrite(ctx))
    }

    /** 可写日历 id（优先主日历，其次第一个可写日历） */
    private fun writableCalendarId(ctx: Context): Long? {
        val proj = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE,
        )
        var fallback: Long? = null
        runCatching {
            ctx.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, proj, null, null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val access = if (c.isNull(1)) 0 else c.getInt(1)
                    val primary = if (c.isNull(2)) 0 else c.getInt(2)
                    if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                    if (primary == 1) return id
                    if (fallback == null) fallback = id
                }
            }
        }
        return fallback
    }

    data class Event(
        val title: String,
        val beginMs: Long,
        val endMs: Long,
        val allDay: Boolean,
        val location: String,
        val description: String,
        val calendarName: String,
    ) {
        fun render(): String {
            val fmt = if (allDay) SimpleDateFormat("MM-dd", Locale.CHINA)
            else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
            val range = if (allDay) "${fmt.format(beginMs)} 全天"
            else "${fmt.format(beginMs)}–" + SimpleDateFormat("HH:mm", Locale.CHINA).format(endMs)
            val parts = buildList {
                add(range)
                add(title)
                if (location.isNotBlank()) add("@$location")
                if (calendarName.isNotBlank()) add("[$calendarName]")
            }
            return parts.joinToString(" · ")
        }
    }

    /** 查一段时间内的事件，按开始时间排序 */
    fun agenda(ctx: Context, startMs: Long, endMs: Long): List<Event> {
        if (!ensurePermission(ctx, needWrite = false)) {
            throw IllegalStateException("没有日历读取权限（自动授权也失败了）")
        }
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMs.toString())
            .appendPath(endMs.toString())
            .build()
        val proj = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )
        val out = mutableListOf<Event>()
        ctx.contentResolver.query(uri, proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                out.add(
                    Event(
                        title = c.getString(0) ?: "(无标题)",
                        beginMs = c.getLong(1),
                        endMs = c.getLong(2),
                        allDay = c.getInt(3) == 1,
                        location = c.getString(4) ?: "",
                        description = c.getString(5) ?: "",
                        calendarName = c.getString(6) ?: "",
                    )
                )
            }
        }
        return out
    }

    /** 写入系统日历，返回新事件 id */
    fun addEvent(
        ctx: Context,
        title: String,
        beginMs: Long,
        endMs: Long,
        description: String = "",
        location: String = "",
        allDay: Boolean = false,
    ): Long {
        if (!ensurePermission(ctx, needWrite = true)) {
            throw IllegalStateException("没有日历写入权限（自动授权也失败了）")
        }
        val calId = writableCalendarId(ctx) ?: throw IllegalStateException("手机上没有可写的日历账户")
        val tz = TimeZone.getDefault().id
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DTSTART, beginMs)
            put(CalendarContract.Events.DTEND, endMs)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, tz)
        }
        val uri = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: throw IllegalStateException("写入日历失败（系统拒绝）")
        return ContentUris.parseId(uri)
    }

    /** 按标题+开始时间删事件（用于"我改主意了"） */
    fun deleteByTitleAndTime(ctx: Context, title: String, beginMs: Long): Int {
        if (!ensurePermission(ctx, needWrite = true)) return 0
        val sel = "${CalendarContract.Events.TITLE} = ? AND ${CalendarContract.Events.DTSTART} = ?"
        return ctx.contentResolver.delete(
            CalendarContract.Events.CONTENT_URI, sel, arrayOf(title, beginMs.toString())
        )
    }

    // ---------- 时间解析 ----------

    /** 解析 yyyy-MM-dd（缺省今天） */
    fun dayStart(date: String): Long {
        val cal = Calendar.getInstance()
        if (!TextUtils.isEmpty(date)) {
            val p = date.trim().split("-", "/", ".")
            if (p.size >= 3) {
                runCatching {
                    cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 0, 0, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                }
            }
        } else {
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /** yyyy-MM-dd + HH:mm → 时间戳 */
    fun at(date: String, time: String): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dayStart(date)
        val m = Regex("(\\d{1,2})[:：](\\d{2})").find(time)
        if (m != null) {
            cal.set(Calendar.HOUR_OF_DAY, m.groupValues[1].toInt())
            cal.set(Calendar.MINUTE, m.groupValues[2].toInt())
        }
        return cal.timeInMillis
    }
}
