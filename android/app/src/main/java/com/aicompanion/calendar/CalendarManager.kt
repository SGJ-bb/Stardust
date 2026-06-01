package com.aicompanion.calendar

import android.content.Context
import java.util.Calendar

data class FestivalEvent(
    val name: String,
    val month: Int,
    val day: Int,
    val type: String,
    val greeting: String,
    val lunar: Boolean = false
)

object CalendarManager {

    private val solarFestivals = listOf(
        FestivalEvent("元旦", 1, 1, "公历节日", "新年快乐！新的一年，新的开始~"),
        FestivalEvent("情人节", 2, 14, "公历节日", "情人节快乐！今天有没有想对谁说点什么呀~"),
        FestivalEvent("妇女节", 3, 8, "公历节日", "妇女节快乐！向所有了不起的女性致敬~"),
        FestivalEvent("植树节", 3, 12, "公历节日", "植树节到了，一起为地球添点绿吧~"),
        FestivalEvent("愚人节", 4, 1, "公历节日", "愚人节快乐！今天可要小心别被骗了哦~"),
        FestivalEvent("劳动节", 5, 1, "公历节日", "劳动节快乐！辛苦了，今天好好休息吧~"),
        FestivalEvent("青年节", 5, 4, "公历节日", "青年节快乐！青春就是最好的礼物~"),
        FestivalEvent("儿童节", 6, 1, "公历节日", "儿童节快乐！愿你永远保持一颗童心~"),
        FestivalEvent("建党节", 7, 1, "公历节日", "七一建党节，铭记历史~"),
        FestivalEvent("建军节", 8, 1, "公历节日", "八一建军节，向最可爱的人致敬~"),
        FestivalEvent("教师节", 9, 10, "公历节日", "教师节快乐！感恩每一位辛勤付出的老师~"),
        FestivalEvent("国庆节", 10, 1, "公历节日", "国庆节快乐！祝祖国繁荣昌盛~"),
        FestivalEvent("万圣节", 10, 31, "公历节日", "万圣节快乐！不给糖就捣蛋~"),
        FestivalEvent("光棍节", 11, 11, "公历节日", "双十一快乐！是买买买还是享受单身呢~"),
        FestivalEvent("圣诞节", 12, 25, "公历节日", "圣诞快乐！铃儿响叮当~")
    )

    private val lunarFestivalNames = listOf(
        FestivalEvent("春节", 0, 0, "农历节日", "新年快乐！恭喜发财，万事如意~", lunar = true),
        FestivalEvent("元宵节", 0, 0, "农历节日", "元宵节快乐！吃汤圆，团团圆圆~", lunar = true),
        FestivalEvent("龙抬头", 0, 0, "农历节日", "龙抬头好日子，鸿运当头~", lunar = true),
        FestivalEvent("端午节", 0, 0, "农历节日", "端午安康！吃粽子了吗~", lunar = true),
        FestivalEvent("七夕节", 0, 0, "农历节日", "七夕快乐！愿有情人终成眷属~", lunar = true),
        FestivalEvent("中秋节", 0, 0, "农历节日", "中秋快乐！月圆人团圆~", lunar = true),
        FestivalEvent("重阳节", 0, 0, "农历节日", "重阳节快乐！登高望远，敬老爱老~", lunar = true),
        FestivalEvent("腊八节", 0, 0, "农历节日", "腊八节快乐！喝了腊八粥就是年~", lunar = true),
        FestivalEvent("除夕", 0, 0, "农历节日", "除夕快乐！辞旧迎新，阖家团圆~", lunar = true)
    )

    private val lunarDates: Map<Int, Map<String, Pair<Int, Int>>> = mapOf(
        2025 to mapOf(
            "春节" to Pair(1, 29), "元宵节" to Pair(2, 12), "龙抬头" to Pair(2, 28),
            "端午节" to Pair(5, 31), "七夕节" to Pair(8, 29), "中秋节" to Pair(10, 6),
            "重阳节" to Pair(10, 29), "腊八节" to Pair(12, 28), "除夕" to Pair(1, 28)
        ),
        2026 to mapOf(
            "春节" to Pair(2, 17), "元宵节" to Pair(3, 3), "龙抬头" to Pair(3, 19),
            "端午节" to Pair(6, 19), "七夕节" to Pair(8, 27), "中秋节" to Pair(10, 4),
            "重阳节" to Pair(10, 27), "腊八节" to Pair(12, 17), "除夕" to Pair(2, 16)
        ),
        2027 to mapOf(
            "春节" to Pair(2, 6), "元宵节" to Pair(2, 20), "龙抬头" to Pair(3, 8),
            "端午节" to Pair(6, 9), "七夕节" to Pair(8, 16), "中秋节" to Pair(9, 23),
            "重阳节" to Pair(10, 16), "腊八节" to Pair(12, 7), "除夕" to Pair(2, 5)
        )
    )

    data class HolidayDay(val month: Int, val day: Int, val label: String, val isWorkday: Boolean = false)

    private val holidays: Map<Int, List<HolidayDay>> = mapOf(
        2025 to listOf(
            HolidayDay(1, 1, "元旦假期"), HolidayDay(1, 28, "春节假期"), HolidayDay(1, 29, "春节假期"),
            HolidayDay(1, 30, "春节假期"), HolidayDay(1, 31, "春节假期"), HolidayDay(2, 1, "春节假期"),
            HolidayDay(2, 2, "春节假期"), HolidayDay(2, 3, "春节假期"), HolidayDay(2, 4, "春节假期"),
            HolidayDay(1, 26, "调休上班", true), HolidayDay(2, 8, "调休上班", true),
            HolidayDay(4, 4, "清明假期"), HolidayDay(4, 5, "清明假期"), HolidayDay(4, 6, "清明假期"),
            HolidayDay(5, 1, "劳动节假期"), HolidayDay(5, 2, "劳动节假期"), HolidayDay(5, 3, "劳动节假期"),
            HolidayDay(5, 4, "劳动节假期"), HolidayDay(5, 5, "劳动节假期"),
            HolidayDay(4, 27, "调休上班", true),
            HolidayDay(5, 31, "端午假期"), HolidayDay(6, 1, "端午假期"), HolidayDay(6, 2, "端午假期"),
            HolidayDay(10, 1, "国庆假期"), HolidayDay(10, 2, "国庆假期"), HolidayDay(10, 3, "国庆假期"),
            HolidayDay(10, 4, "国庆假期"), HolidayDay(10, 5, "国庆假期"), HolidayDay(10, 6, "中秋假期"),
            HolidayDay(10, 7, "国庆假期"), HolidayDay(10, 8, "国庆假期"),
            HolidayDay(9, 28, "调休上班", true), HolidayDay(10, 11, "调休上班", true)
        ),
        2026 to listOf(
            HolidayDay(1, 1, "元旦假期"), HolidayDay(1, 2, "元旦假期"), HolidayDay(1, 3, "元旦假期"),
            HolidayDay(2, 16, "除夕"), HolidayDay(2, 17, "春节假期"), HolidayDay(2, 18, "春节假期"),
            HolidayDay(2, 19, "春节假期"), HolidayDay(2, 20, "春节假期"), HolidayDay(2, 21, "春节假期"),
            HolidayDay(2, 22, "春节假期"), HolidayDay(2, 23, "春节假期"),
            HolidayDay(6, 19, "端午假期"), HolidayDay(6, 20, "端午假期"), HolidayDay(6, 21, "端午假期"),
            HolidayDay(10, 1, "国庆假期"), HolidayDay(10, 2, "国庆假期"), HolidayDay(10, 3, "国庆假期"),
            HolidayDay(10, 4, "中秋假期"), HolidayDay(10, 5, "国庆假期"), HolidayDay(10, 6, "国庆假期"),
            HolidayDay(10, 7, "国庆假期"), HolidayDay(10, 8, "国庆假期")
        )
    )

    fun getHolidayForDate(year: Int, month: Int, day: Int): HolidayDay? {
        return holidays[year]?.find { it.month == month && it.day == day }
    }

    fun isHoliday(year: Int, month: Int, day: Int): Boolean {
        val h = getHolidayForDate(year, month, day)
        return h != null && !h.isWorkday
    }

    fun isWorkday(year: Int, month: Int, day: Int): Boolean {
        val h = getHolidayForDate(year, month, day)
        return h?.isWorkday == true
    }

    private val solarTermOffsets = listOf(
        "小寒" to intArrayOf(5, 20), "大寒" to intArrayOf(20, 20),
        "立春" to intArrayOf(3, 4), "雨水" to intArrayOf(18, 19),
        "惊蛰" to intArrayOf(5, 6), "春分" to intArrayOf(20, 21),
        "清明" to intArrayOf(4, 5), "谷雨" to intArrayOf(20, 20),
        "立夏" to intArrayOf(5, 6), "小满" to intArrayOf(21, 21),
        "芒种" to intArrayOf(5, 6), "夏至" to intArrayOf(21, 21),
        "小暑" to intArrayOf(7, 7), "大暑" to intArrayOf(22, 23),
        "立秋" to intArrayOf(7, 8), "处暑" to intArrayOf(23, 23),
        "白露" to intArrayOf(7, 8), "秋分" to intArrayOf(23, 23),
        "寒露" to intArrayOf(8, 8), "霜降" to intArrayOf(23, 24),
        "立冬" to intArrayOf(7, 8), "小雪" to intArrayOf(22, 22),
        "大雪" to intArrayOf(7, 7), "冬至" to intArrayOf(21, 22)
    )

    private val solarTermGreetings = mapOf(
        "小寒" to "小寒到了，天冷记得加衣~", "大寒" to "大寒是一年中最冷的时候，注意保暖~",
        "立春" to "立春了！春天要来了，万物复苏~", "雨水" to "雨水节气，春雨贵如油~",
        "惊蛰" to "惊蛰到了，春雷响万物长~", "春分" to "春分昼夜平分，春意正浓~",
        "清明" to "清明时节雨纷纷~", "谷雨" to "谷雨到了，春播好时节~",
        "立夏" to "立夏了！夏天正式开始~", "小满" to "小满节气，麦粒渐满~",
        "芒种" to "芒种忙忙种，农事正当时~", "夏至" to "夏至日最长，盛夏来了~",
        "小暑" to "小暑天气热，注意防暑~", "大暑" to "大暑是一年中最热的时候~",
        "立秋" to "立秋了！秋天要来了~", "处暑" to "处暑暑渐消，秋凉将至~",
        "白露" to "白露秋分夜，一夜凉一夜~", "秋分" to "秋分昼夜平分，秋意渐浓~",
        "寒露" to "寒露时节，秋深露重~", "霜降" to "霜降到了，天气渐冷~",
        "立冬" to "立冬了！冬天正式开始~", "小雪" to "小雪节气，初雪将至~",
        "大雪" to "大雪纷飞，银装素裹~", "冬至" to "冬至大如年，记得吃饺子/汤圆~"
    )

    fun getTodayEvents(): List<String> {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val year = cal.get(Calendar.YEAR)
        val results = mutableListOf<String>()

        for (f in solarFestivals) {
            if (f.month == month && f.day == day) {
                results.add("【${f.type}】${f.name}：${f.greeting}")
            }
        }

        val dates = lunarDates[year]
        if (dates != null) {
            for (f in lunarFestivalNames) {
                val datePair = dates[f.name]
                if (datePair != null && datePair.first == month && datePair.second == day) {
                    results.add("【${f.type}】${f.name}：${f.greeting}")
                }
            }
        }

        for (i in solarTermOffsets.indices step 2) {
            val name1 = solarTermOffsets[i].first
            val d1 = solarTermOffsets[i].second[if (year % 4 == 0) 1 else 0]
            val m1 = i / 2 + 1
            if (m1 == month && d1 == day) {
                results.add("【节气】$name1：${solarTermGreetings[name1]}")
            }
            if (i + 1 < solarTermOffsets.size) {
                val name2 = solarTermOffsets[i + 1].first
                val d2 = solarTermOffsets[i + 1].second[if (year % 4 == 0) 1 else 0]
                val m2 = i / 2 + 1
                if (m2 == month && d2 == day) {
                    results.add("【节气】$name2：${solarTermGreetings[name2]}")
                }
            }
        }

        return results
    }

    fun isBirthdayToday(birthday: String): Boolean {
        if (birthday.isBlank()) return false
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val parts = birthday.split("-")
        if (parts.size < 3) return false
        return parts[1].toIntOrNull() == month && parts[2].toIntOrNull() == day
    }

    fun getBirthdayGreeting(birthday: String): String? {
        if (!isBirthdayToday(birthday)) return null
        val birthYear = birthday.split("-")[0].toIntOrNull() ?: return "生日快乐！今天是你特别的日子~"
        val age = Calendar.getInstance().get(Calendar.YEAR) - birthYear
        return "生日快乐！今天是你${age}岁的生日，愿你新的一岁一切顺利~"
    }

    fun getCalendarContextBlock(birthday: String, context: Context? = null): String {
        val events = getTodayEvents()
        val sb = StringBuilder()
        if (events.isNotEmpty()) {
            sb.appendLine("\n【今日日历】")
            events.forEach { sb.appendLine(it) }
        }
        getBirthdayGreeting(birthday)?.let { sb.appendLine("【生日】$it") }
        if (context != null) {
            val periodInfo = getPeriodContext(context)
            if (periodInfo.isNotBlank()) sb.appendLine(periodInfo)
        }
        return sb.toString().trimEnd('\n')
    }

    fun hasEventsToday(): Boolean = getTodayEvents().isNotEmpty()

    private const val PERIOD_PREFS = "period_tracker"
    private const val KEY_LAST_PERIOD_DATE = "last_period_date"
    private const val KEY_CYCLE_LENGTH = "cycle_length"
    private const val KEY_PERIOD_LENGTH = "period_length"

    fun saveLastPeriodDate(context: Context, date: String) {
        context.getSharedPreferences(PERIOD_PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_PERIOD_DATE, date).apply()
    }

    fun getLastPeriodDate(context: Context): String {
        return context.getSharedPreferences(PERIOD_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_PERIOD_DATE, "") ?: ""
    }

    fun saveCycleLength(context: Context, length: Int) {
        context.getSharedPreferences(PERIOD_PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_CYCLE_LENGTH, length).apply()
    }

    fun getCycleLength(context: Context): Int {
        return context.getSharedPreferences(PERIOD_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_CYCLE_LENGTH, 28)
    }

    fun savePeriodLength(context: Context, length: Int) {
        context.getSharedPreferences(PERIOD_PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PERIOD_LENGTH, length).apply()
    }

    fun getPeriodLength(context: Context): Int {
        return context.getSharedPreferences(PERIOD_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PERIOD_LENGTH, 5)
    }

    fun getDaysSinceLastPeriod(context: Context): Int {
        val lastDate = getLastPeriodDate(context)
        if (lastDate.isBlank()) return -1
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val last = sdf.parse(lastDate) ?: return -1
            val diff = System.currentTimeMillis() - last.time
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } catch (_: Exception) { -1 }
    }

    fun getNextPeriodDate(context: Context): String? {
        val lastDate = getLastPeriodDate(context)
        if (lastDate.isBlank()) return null
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val last = sdf.parse(lastDate) ?: return null
            val cycleLen = getCycleLength(context).toLong()
            val next = java.util.Date(last.time + cycleLen * 24 * 60 * 60 * 1000)
            sdf.format(next)
        } catch (_: Exception) { null }
    }

    fun isOnPeriod(context: Context): Boolean {
        val daysSince = getDaysSinceLastPeriod(context)
        if (daysSince < 0) return false
        val periodLen = getPeriodLength(context)
        return daysSince in 0 until periodLen
    }

    fun isNearPeriod(context: Context): Boolean {
        val daysSince = getDaysSinceLastPeriod(context)
        if (daysSince < 0) return false
        val cycleLen = getCycleLength(context)
        return daysSince >= cycleLen - 3
    }

    private fun getPeriodContext(context: Context): String {
        val sm = com.aicompanion.settings.SettingsManager(context)
        if (sm.userGender != "female") return ""
        val daysSince = getDaysSinceLastPeriod(context)
        if (daysSince < 0) return ""
        return when {
            isOnPeriod(context) -> "【生理期】用户正在生理期中（第${daysSince + 1}天），请多关心体贴，避免建议剧烈运动或冷饮。"
            isNearPeriod(context) -> "【生理期】用户生理期即将到来（预计${getCycleLength(context) - daysSince}天后），可以温柔提醒做好准备。"
            else -> ""
        }
    }
}
