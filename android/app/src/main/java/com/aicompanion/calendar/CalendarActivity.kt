package com.aicompanion.calendar

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aicompanion.R
import com.aicompanion.settings.SettingsManager
import com.aicompanion.theme.ThemeManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarActivity : AppCompatActivity() {

    private lateinit var sm: SettingsManager
    private lateinit var gridLayout: GridLayout
    private lateinit var tvMonthTitle: TextView
    private lateinit var eventsContainer: LinearLayout
    private var currentYear: Int = 0
    private var currentMonth: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sm = SettingsManager(this)

        val scheme = ThemeManager.getCurrentScheme(this)
        val bgColor = parseColor(scheme.backgroundDark, "#0a0a1a")
        val cardColor = parseColor(scheme.cardColor, "#1a1a30")
        val primaryColor = parseColor(scheme.primaryColor, "#667eea")
        val textColor = parseColor(scheme.textColor, "#ececf4")
        val textSecColor = parseColor(scheme.textSecondaryColor, "#99aabb")

        window.statusBarColor = bgColor

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }

        val toolbar = com.google.android.material.appbar.MaterialToolbar(this).apply {
            title = "日历"
            setNavigationIcon(android.R.drawable.ic_menu_revert)
            setNavigationOnClickListener { finish() }
            setTitleTextColor(textColor)
            setBackgroundColor(bgColor)
        }
        root.addView(toolbar)

        val monthNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = dp(16); setPadding(pad, dp(8), pad, dp(8))
        }

        val btnPrev = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(primaryColor)
            val sz = dp(36)
            layoutParams = LinearLayout.LayoutParams(sz, sz)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener { changeMonth(-1) }
        }
        monthNav.addView(btnPrev)

        tvMonthTitle = TextView(this).apply {
            textSize = 18f
            setTextColor(textColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        monthNav.addView(tvMonthTitle)

        val btnNext = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_next)
            setColorFilter(primaryColor)
            val sz = dp(36)
            layoutParams = LinearLayout.LayoutParams(sz, sz)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener { changeMonth(1) }
        }
        monthNav.addView(btnNext)
        root.addView(monthNav)

        val weekRow = GridLayout(this).apply {
            columnCount = 7
            rowCount = 1
            val pad = dp(8); setPadding(pad, dp(4), pad, dp(4))
        }
        val weekLabels = listOf("日", "一", "二", "三", "四", "五", "六")
        for (label in weekLabels) {
            weekRow.addView(TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(textSecColor)
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            })
        }
        root.addView(weekRow)

        gridLayout = GridLayout(this).apply {
            columnCount = 7
            val pad = dp(8); setPadding(pad, 0, pad, dp(8))
        }
        root.addView(gridLayout)

        val divider = View(this).apply {
            setBackgroundColor(parseColor(scheme.surfaceColor, "#1a1a38"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(dp(16), 0, dp(16), 0)
            }
        }
        root.addView(divider)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        eventsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16); setPadding(pad, dp(12), pad, dp(16))
        }
        scrollView.addView(eventsContainer)
        root.addView(scrollView)

        setContentView(root)

        val cal = Calendar.getInstance()
        currentYear = cal.get(Calendar.YEAR)
        currentMonth = cal.get(Calendar.MONTH)
        renderCalendar()
    }

    private fun changeMonth(delta: Int) {
        currentMonth += delta
        if (currentMonth > 11) { currentMonth = 0; currentYear++ }
        if (currentMonth < 0) { currentMonth = 11; currentYear-- }
        renderCalendar()
    }

    private fun renderCalendar() {
        val scheme = ThemeManager.getCurrentScheme(this)
        val primaryColor = parseColor(scheme.primaryColor, "#667eea")
        val textColor = parseColor(scheme.textColor, "#ececf4")
        val textSecColor = parseColor(scheme.textSecondaryColor, "#99aabb")
        val cardColor = parseColor(scheme.cardColor, "#1a1a30")
        val accentPink = 0xFFff6b9d.toInt()

        tvMonthTitle.text = "${currentYear}年${currentMonth + 1}月"

        gridLayout.removeAllViews()

        val cal = Calendar.getInstance().apply { set(currentYear, currentMonth, 1) }
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance()
        val isCurrentMonth = today.get(Calendar.YEAR) == currentYear && today.get(Calendar.MONTH) == currentMonth
        val todayDay = today.get(Calendar.DAY_OF_MONTH)

        val festivalDays = getFestivalDaysForMonth()
        val periodDays = getPeriodDaysForMonth()

        for (i in 0 until firstDayOfWeek) {
            gridLayout.addView(TextView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(40)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            })
        }

        for (day in 1..daysInMonth) {
            val isToday = isCurrentMonth && day == todayDay
            val festival = festivalDays[day]
            val isPeriod = periodDays.contains(day)
            val holiday = CalendarManager.getHolidayForDate(currentYear, currentMonth + 1, day)
            val isHoliday = holiday != null && !holiday.isWorkday
            val isWorkday = holiday?.isWorkday == true

            val dayCell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(44)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            }

            val dayNum = TextView(this).apply {
                text = day.toString()
                textSize = 14f
                gravity = Gravity.CENTER
                setTypeface(null, if (isToday) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                setTextColor(when {
                    isToday -> 0xFFFFFFFF.toInt()
                    isHoliday -> 0xFF64ffda.toInt()
                    isWorkday -> 0xFFff9f43.toInt()
                    isPeriod -> accentPink
                    else -> textColor
                })
                if (isToday) {
                    val sz = dp(28)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(primaryColor)
                    }
                    layoutParams = LinearLayout.LayoutParams(sz, sz).apply { gravity = Gravity.CENTER }
                    gravity = Gravity.CENTER
                }
            }
            dayCell.addView(dayNum)

            if (festival != null || isPeriod || isHoliday || isWorkday) {
                val dot = View(this).apply {
                    val sz = dp(4)
                    layoutParams = LinearLayout.LayoutParams(sz, sz).apply { topMargin = dp(1) }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(when {
                            isHoliday -> 0xFF64ffda.toInt()
                            isWorkday -> 0xFFff9f43.toInt()
                            isPeriod -> accentPink
                            festival != null -> 0xFFc4b5fd.toInt()
                            else -> primaryColor
                        })
                    }
                }
                dayCell.addView(dot)
            }

            dayCell.setOnClickListener {
                showDayDetail(day, festival, isPeriod, isHoliday, isWorkday, holiday?.label)
            }

            gridLayout.addView(dayCell)
        }

        renderEvents()
    }

    private fun renderEvents() {
        val scheme = ThemeManager.getCurrentScheme(this)
        val primaryColor = parseColor(scheme.primaryColor, "#667eea")
        val textColor = parseColor(scheme.textColor, "#ececf4")
        val textSecColor = parseColor(scheme.textSecondaryColor, "#99aabb")
        val cardColor = parseColor(scheme.cardColor, "#1a1a30")

        eventsContainer.removeAllViews()

        val events = CalendarManager.getTodayEvents()
        val birthdayGreeting = CalendarManager.getBirthdayGreeting(sm.userBirthday)

        if (events.isEmpty() && birthdayGreeting == null && sm.userGender != "female") {
            eventsContainer.addView(TextView(this).apply {
                text = "今天没有特别的日历事件~"
                textSize = 14f; setTextColor(textSecColor)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(16)
                }
            })
            return
        }

        eventsContainer.addView(TextView(this).apply {
            val sdf = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINESE)
            text = "📅 ${sdf.format(Date())}"
            textSize = 15f; setTextColor(primaryColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        })

        for (event in events) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(cardColor)
                val pad = dp(12); setPadding(pad, dp(10), pad, dp(10))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(cardColor)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), parseColor(scheme.surfaceColor, "#2a2a4a"))
                }
            }

            val isFestival = event.contains("节日")
            val isSolarTerm = event.contains("节气")
            val emoji = when {
                isFestival && event.contains("春节") -> "🧨"
                isFestival && event.contains("中秋") -> "🌕"
                isFestival && event.contains("端午") -> "🐲"
                isFestival && event.contains("七夕") -> "💕"
                isFestival && event.contains("元宵") -> "🏮"
                isFestival && event.contains("除夕") -> "🎆"
                isFestival && event.contains("国庆") -> "🇨🇳"
                isFestival && event.contains("劳动") -> "💪"
                isFestival && event.contains("儿童") -> "🎈"
                isFestival && event.contains("情人") -> "💝"
                isFestival && event.contains("圣诞") -> "🎄"
                isFestival && event.contains("元旦") -> "🎊"
                isSolarTerm && event.contains("立春") -> "🌱"
                isSolarTerm && event.contains("夏至") -> "☀️"
                isSolarTerm && event.contains("冬至") -> "❄️"
                isSolarTerm -> "🌿"
                isFestival -> "🎉"
                else -> "📌"
            }

            card.addView(TextView(this).apply {
                text = emoji; textSize = 22f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(10)
                }
            })

            val textContent = event.replace(Regex("【[^】]+】"), "").trim()
            card.addView(TextView(this).apply {
                text = textContent
                textSize = 13f; setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            eventsContainer.addView(card)
        }

        if (birthdayGreeting != null) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = dp(12); setPadding(pad, dp(10), pad, dp(10))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF2a1a2a.toInt())
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), 0xFF4a2a4a.toInt())
                }
            }
            card.addView(TextView(this).apply {
                text = "🎂"; textSize = 22f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(10)
                }
            })
            card.addView(TextView(this).apply {
                text = birthdayGreeting
                textSize = 13f; setTextColor(0xFFff9f43.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            eventsContainer.addView(card)
        }

        if (sm.userGender == "female") {
            renderPeriodCard()
        }
    }

    private fun renderPeriodCard() {
        val scheme = ThemeManager.getCurrentScheme(this)
        val cardColor = parseColor(scheme.cardColor, "#1a1a30")
        val textColor = parseColor(scheme.textColor, "#ececf4")

        val daysSince = CalendarManager.getDaysSinceLastPeriod(this)
        val lastDate = CalendarManager.getLastPeriodDate(this)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(12); setPadding(pad, dp(10), pad, dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1a1a2a.toInt())
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0xFF3a2a4a.toInt())
            }
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = "🌸 生理期记录"; textSize = 14f
            setTextColor(0xFFff6b9d.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val btnRecord = TextView(this).apply {
            text = "记录"; textSize = 12f
            setTextColor(0xFF667eea.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF2a2a4a.toInt())
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { showPeriodRecordDialog() }
        }
        titleRow.addView(btnRecord)
        card.addView(titleRow)

        if (lastDate.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = if (daysSince >= 0) "上次：$lastDate（${daysSince}天前）" else "上次：$lastDate"
                textSize = 12f; setTextColor(0xFF99aabb.toInt())
                setPadding(0, dp(4), 0, 0)
            })

            val cycleLen = CalendarManager.getCycleLength(this)
            val nextDate = CalendarManager.getNextPeriodDate(this)
            if (nextDate != null) {
                card.addView(TextView(this).apply {
                    text = "预计下次：$nextDate（周期${cycleLen}天）"
                    textSize = 12f; setTextColor(0xFF99aabb.toInt())
                })
            }

            val statusText = when {
                CalendarManager.isOnPeriod(this) -> "🔴 生理期中 — 注意保暖休息~"
                CalendarManager.isNearPeriod(this) -> "🟡 即将到来（${cycleLen - daysSince}天后）"
                else -> "🟢 当前安全期"
            }
            val statusColor = when {
                CalendarManager.isOnPeriod(this) -> 0xFFff6b9d.toInt()
                CalendarManager.isNearPeriod(this) -> 0xFFffaa44.toInt()
                else -> 0xFF64ffda.toInt()
            }
            card.addView(TextView(this).apply {
                text = statusText; textSize = 13f; setTextColor(statusColor)
                setPadding(0, dp(4), 0, 0)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
        } else {
            card.addView(TextView(this).apply {
                text = "尚未记录，点击「记录」开始追踪"
                textSize = 12f; setTextColor(0xFF667788.toInt())
                setPadding(0, dp(4), 0, 0)
            })
        }

        eventsContainer.addView(card)
    }

    private fun showPeriodRecordDialog() {
        val calMgr = CalendarManager
        val act = this
        val cycleLen = calMgr.getCycleLength(act)
        val periodLen = calMgr.getPeriodLength(act)

        val contentView = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20); setPadding(pad, pad, pad, pad)
        }

        contentView.addView(TextView(act).apply {
            text = "选择本次生理期开始日期："
            textSize = 14f; setTextColor(0xFFd0d0e0.toInt())
        })

        val tvDate = TextView(act).apply {
            text = calMgr.getLastPeriodDate(act).ifBlank {
                val cal = Calendar.getInstance()
                String.format("%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            }
            textSize = 15f; setTextColor(0xFF667eea.toInt())
            setPadding(0, dp(8), 0, dp(8))
        }
        tvDate.setOnClickListener {
            val cal = Calendar.getInstance()
            val currentText = tvDate.text?.toString() ?: ""
            if (currentText.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val parts = currentText.split("-")
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
            android.app.DatePickerDialog(
                act,
                R.style.ThemeOverlay_Companion_DatePicker,
                { _, year, month, day -> tvDate.text = String.format("%04d-%02d-%02d", year, month + 1, day) },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        contentView.addView(tvDate)

        contentView.addView(TextView(act).apply {
            text = "周期天数（默认28）："; textSize = 14f; setTextColor(0xFFd0d0e0.toInt())
            setPadding(0, dp(12), 0, 0)
        })
        val cycleInput = com.google.android.material.textfield.TextInputEditText(act).apply {
            hint = "28"; setText(cycleLen.toString()); textSize = 14f; setTextColor(0xFFd0d0e0.toInt())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        contentView.addView(cycleInput)

        contentView.addView(TextView(act).apply {
            text = "持续天数（默认5）："; textSize = 14f; setTextColor(0xFFd0d0e0.toInt())
            setPadding(0, dp(12), 0, 0)
        })
        val periodInput = com.google.android.material.textfield.TextInputEditText(act).apply {
            hint = "5"; setText(periodLen.toString()); textSize = 14f; setTextColor(0xFFd0d0e0.toInt())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        contentView.addView(periodInput)

        val dialog = android.app.AlertDialog.Builder(act)
            .setTitle("记录生理期")
            .setView(contentView)
            .setPositiveButton("保存") { _, _ ->
                val dateStr = tvDate.text?.toString()?.trim() ?: ""
                if (dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    calMgr.saveLastPeriodDate(act, dateStr)
                }
                cycleInput.text?.toString()?.toIntOrNull()?.let { if (it in 20..45) calMgr.saveCycleLength(act, it) }
                periodInput.text?.toString()?.toIntOrNull()?.let { if (it in 2..10) calMgr.savePeriodLength(act, it) }
                android.widget.Toast.makeText(act, "已保存", android.widget.Toast.LENGTH_SHORT).show()
                renderCalendar()
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_settings_card)
    }

    private fun showDayDetail(day: Int, festival: String?, isPeriod: Boolean, isHoliday: Boolean, isWorkday: Boolean, holidayLabel: String?) {
        val scheme = ThemeManager.getCurrentScheme(this)
        val textColor = parseColor(scheme.textColor, "#ececf4")

        val contentView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16); setPadding(pad, pad, pad, pad)
        }

        contentView.addView(TextView(this).apply {
            text = "${currentYear}年${currentMonth + 1}月${day}日"
            textSize = 16f; setTextColor(textColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        if (isHoliday && holidayLabel != null) {
            contentView.addView(TextView(this).apply {
                text = "\n🏖️ $holidayLabel"
                textSize = 14f; setTextColor(0xFF64ffda.toInt())
            })
        }

        if (isWorkday) {
            contentView.addView(TextView(this).apply {
                text = "\n💼 调休上班日"
                textSize = 14f; setTextColor(0xFFff9f43.toInt())
            })
        }

        if (festival != null) {
            contentView.addView(TextView(this).apply {
                text = "\n🎉 $festival"
                textSize = 14f; setTextColor(0xFFc4b5fd.toInt())
            })
        }

        if (isPeriod) {
            contentView.addView(TextView(this).apply {
                text = "\n🌸 生理期"
                textSize = 14f; setTextColor(0xFFff6b9d.toInt())
            })
        }

        if (!isHoliday && !isWorkday && festival == null && !isPeriod) {
            contentView.addView(TextView(this).apply {
                text = "\n这一天没有特别的日历事件"
                textSize = 14f; setTextColor(0xFF667788.toInt())
            })
        }

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(contentView)
            .setPositiveButton("关闭", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_settings_card)
    }

    private fun getFestivalDaysForMonth(): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        val calMgr = CalendarManager

        for (f in com.aicompanion.calendar.CalendarManager.getTodayEvents()) {
            // We need a different approach - get events for the current month
        }

        // Use the internal data from CalendarManager
        val solarFestivalsField = com.aicompanion.calendar.CalendarManager::class.java.getDeclaredField("solarFestivals")
        solarFestivalsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val solarFestivals = solarFestivalsField.get(com.aicompanion.calendar.CalendarManager) as List<*>

        for (f in solarFestivals) {
            val fest = f as com.aicompanion.calendar.FestivalEvent
            if (!fest.lunar && fest.month == currentMonth + 1) {
                result[fest.day] = fest.name
            }
        }

        try {
            val lunarDatesField = com.aicompanion.calendar.CalendarManager::class.java.getDeclaredField("lunarDates")
            lunarDatesField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val lunarDates = lunarDatesField.get(com.aicompanion.calendar.CalendarManager) as Map<Int, *>
            val yearDates = lunarDates[currentYear]
            if (yearDates != null) {
                val lunarFestivalsField = com.aicompanion.calendar.CalendarManager::class.java.getDeclaredField("lunarFestivalNames")
                lunarFestivalsField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val lunarFestivals = lunarFestivalsField.get(com.aicompanion.calendar.CalendarManager) as List<*>
                for (lf in lunarFestivals) {
                    val fest = lf as com.aicompanion.calendar.FestivalEvent
                    @Suppress("UNCHECKED_CAST")
                    val datePair = (yearDates as Map<String, Pair<Int, Int>>)[fest.name]
                    if (datePair != null && datePair.first == currentMonth + 1) {
                        result[datePair.second] = fest.name
                    }
                }
            }
        } catch (_: Exception) {}

        return result
    }

    private fun getPeriodDaysForMonth(): Set<Int> {
        if (sm.userGender != "female") return emptySet()
        val lastDate = CalendarManager.getLastPeriodDate(this)
        if (lastDate.isBlank()) return emptySet()
        val periodLen = CalendarManager.getPeriodLength(this)
        val cycleLen = CalendarManager.getCycleLength(this)

        val result = mutableSetOf<Int>()
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val lastCal = Calendar.getInstance()
            val parts = lastDate.split("-")
            lastCal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())

            for (cycleOffset in -3..3) {
                val cycleCal = lastCal.clone() as Calendar
                cycleCal.add(Calendar.DAY_OF_MONTH, cycleOffset * cycleLen)

                for (dayOffset in 0 until periodLen) {
                    val dayCal = cycleCal.clone() as Calendar
                    dayCal.add(Calendar.DAY_OF_MONTH, dayOffset)
                    if (dayCal.get(Calendar.YEAR) == currentYear && dayCal.get(Calendar.MONTH) == currentMonth) {
                        result.add(dayCal.get(Calendar.DAY_OF_MONTH))
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun parseColor(colorStr: String, fallback: String): Int {
        return try { android.graphics.Color.parseColor(colorStr) } catch (_: Exception) {
            try { android.graphics.Color.parseColor(fallback) } catch (_: Exception) { 0xFF1a1a2e.toInt() }
        }
    }
}
