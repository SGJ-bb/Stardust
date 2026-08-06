package com.aicompanion.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.calendar.CalendarEvent
import com.aicompanion.calendar.CalendarEventManager
import com.aicompanion.calendar.CalendarManager
import com.aicompanion.persona.PersonaManager
import com.aicompanion.settings.SettingsManager
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.ButtonSize
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustTopBar
import com.aicompanion.ui.components.WallpaperBackground
import java.util.Calendar

/** 星期标题行（周日为首列） */
private val WEEKDAY_LABELS = listOf("日", "一", "二", "三", "四", "五", "六")

/**
 * 日历页面 —— 月历网格视图
 *
 * 功能：
 * - 顶部栏 + 月份切换（< 2024年6月 >）
 * - 星期标题行（日 一 二 三 四 五 六）
 * - 7 列日期网格：当天高亮 / 事件圆点指示 / 点击选中
 * - 底部选中日期事件列表（节假日 / 生日 / 节日节气）
 *
 * 数据源：CalendarManager（object 单例）
 * - 节假日：getHolidayForDate(year, month, day) 支持任意日期
 * - 生日：通过 userBirthday 的月/日匹配判断
 * - 节日/节气：getTodayEvents() 仅返回"今天"的事件，无法查询任意日期
 *   （故非今日的节日/节气事件不在网格中标记，仅节假日与生日会标记）
 *
 * @param onBackClick 返回回调
 */
@Composable
fun CalendarScreen(
    onBackClick: () -> Unit = {},
    /** 壁纸图片路径（本地文件路径或URI） */
    wallpaperPath: String? = null,
) {
    val context = LocalContext.current
    val colors = StradustTheme.colors

    // ===== 日历事件管理器（按角色隔离） =====
    var refreshTick by remember { mutableIntStateOf(0) }
    val eventManager = remember(refreshTick) {
        val pm = PersonaManager(context)
        pm.load()
        val pid = pm.getActivePersona()?.id ?: "default"
        CalendarEventManager(context, pid)
    }

    // ===== 添加事件对话框状态 =====
    var showAddEventDialog by remember { mutableStateOf(false) }
    var newEventTitle by remember { mutableStateOf("") }
    var newEventDesc by remember { mutableStateOf("") }
    var newEventTime by remember { mutableStateOf("") }

    // ===== 今日日期（固定，仅用于高亮与"回到今天"） =====
    val todayCal = remember { Calendar.getInstance() }
    val todayYear = todayCal.get(Calendar.YEAR)
    val todayMonth = todayCal.get(Calendar.MONTH) + 1 // 1-12
    val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

    // ===== 用户生日（解析为 年/月/日 三元组，失败为 null） =====
    val userBirthday = remember { runCatching { SettingsManager(context).userBirthday }.getOrDefault("") }
    val birthdayParts = remember(userBirthday) {
        if (userBirthday.isBlank()) return@remember null
        val parts = userBirthday.split("-")
        if (parts.size < 3) return@remember null
        val y = parts[0].toIntOrNull()
        val m = parts[1].toIntOrNull()
        val d = parts[2].toIntOrNull()
        if (m == null || d == null) null else Triple(y, m, d)
    }

    // ===== 当前展示的月份 =====
    var displayYear by remember { mutableIntStateOf(todayYear) }
    var displayMonth by remember { mutableIntStateOf(todayMonth) } // 1-12

    // ===== 选中的日期（默认今天） =====
    var selectedYear by remember { mutableIntStateOf(todayYear) }
    var selectedMonth by remember { mutableIntStateOf(todayMonth) }
    var selectedDay by remember { mutableIntStateOf(todayDay) }

    // ===== 网格计算：当月 1 号是星期几（周日=0）+ 当月天数 =====
    val firstDayOfWeek = remember(displayYear, displayMonth) {
        val cal = Calendar.getInstance()
        cal.set(displayYear, displayMonth - 1, 1)
        cal.get(Calendar.DAY_OF_WEEK) - 1 // Calendar.SUNDAY=1 → 0
    }
    val daysInMonth = remember(displayYear, displayMonth) {
        val cal = Calendar.getInstance()
        cal.set(displayYear, displayMonth - 1, 1)
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    // ===== 当月节假日映射（day -> HolidayDay），用于网格标记 =====
    val monthHolidays = remember(displayYear, displayMonth, daysInMonth) {
        val map = mutableMapOf<Int, CalendarManager.HolidayDay>()
        for (d in 1..daysInMonth) {
            CalendarManager.getHolidayForDate(displayYear, displayMonth, d)?.let { map[d] = it }
        }
        map
    }

    // ===== 选中日期的详情数据 =====
    val selectedDateHoliday = remember(selectedYear, selectedMonth, selectedDay) {
        CalendarManager.getHolidayForDate(selectedYear, selectedMonth, selectedDay)
    }
    val isSelectedToday = selectedYear == todayYear && selectedMonth == todayMonth && selectedDay == todayDay
    // 注意：getTodayEvents() 仅返回今天的事件；选中非今日时该列表为空
    val selectedDateEvents = remember(selectedYear, selectedMonth, selectedDay, isSelectedToday) {
        if (isSelectedToday) CalendarManager.getTodayEvents() else emptyList()
    }
    val selectedDateIsBirthday = remember(selectedYear, selectedMonth, selectedDay, birthdayParts) {
        birthdayParts?.second == selectedMonth && birthdayParts?.third == selectedDay
    }
    // ===== 选中日期的用户/AI 事件 =====
    val selectedDateUserEvents = remember(selectedYear, selectedMonth, selectedDay, refreshTick) {
        eventManager.getEventsByDate(selectedYear, selectedMonth, selectedDay)
    }
    val selectedBirthdayGreeting = remember(selectedDateIsBirthday, birthdayParts, selectedYear) {
        if (!selectedDateIsBirthday || birthdayParts == null) return@remember null
        val birthYear = birthdayParts.first
        if (birthYear != null) {
            val age = selectedYear - birthYear
            "生日快乐！今天是${age}岁的生日，愿你新的一岁一切顺利~"
        } else {
            "生日快乐！今天是你特别的日子~"
        }
    }

    // ===== 当月用户事件日期集合（用于网格标记） =====
    val monthEventDays = remember(displayYear, displayMonth, refreshTick) {
        eventManager.getEventDaysInMonth(displayYear, displayMonth)
    }

    // ===== 判断网格中某天是否有事件（用于圆点指示） =====
    val todayHasEvents = remember(isSelectedToday) { CalendarManager.hasEventsToday() }
    fun dayHasEvents(day: Int): Boolean {
        if (monthHolidays.containsKey(day)) return true
        if (birthdayParts?.second == displayMonth && birthdayParts?.third == day) return true
        if (displayYear == todayYear && displayMonth == todayMonth && day == todayDay) {
            if (todayHasEvents) return true
        }
        if (day in monthEventDays) return true
        return false
    }

    // ===== 月份切换 =====
    fun prevMonth() {
        if (displayMonth == 1) { displayYear -= 1; displayMonth = 12 }
        else { displayMonth -= 1 }
    }
    fun nextMonth() {
        if (displayMonth == 12) { displayYear += 1; displayMonth = 1 }
        else { displayMonth += 1 }
    }
    fun goToday() {
        displayYear = todayYear
        displayMonth = todayMonth
        selectedYear = todayYear
        selectedMonth = todayMonth
        selectedDay = todayDay
    }

    WallpaperBackground(wallpaperPath = wallpaperPath) {
        Column(modifier = Modifier.fillMaxSize()) {
            StradustTopBar(title = "日历", onBackClick = onBackClick)
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            // ===== 月历卡片 =====
            item {
                StradustCard {
                    // ----- 月份切换栏：< 2024年6月 > -----
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(onClick = { prevMonth() }) {
                            Icon(
                                Icons.Default.KeyboardArrowLeft,
                                contentDescription = "上个月",
                                tint = colors.textPrimary,
                            )
                        }
                        Text(
                            text = "${displayYear}年${displayMonth}月",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { nextMonth() }) {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "下个月",
                                tint = colors.textPrimary,
                            )
                        }
                    }

                    // "回到今天"按钮（仅当不在当月时显示）
                    if (displayYear != todayYear || displayMonth != todayMonth) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            StradustButton(
                                text = "回到今天",
                                onClick = { goToday() },
                                variant = ButtonVariant.OUTLINED,
                                size = ButtonSize.SMALL,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    Spacer(Modifier.height(8.dp))

                    // ----- 星期标题行 -----
                    Row(modifier = Modifier.fillMaxWidth()) {
                        WEEKDAY_LABELS.forEach { label ->
                            Text(
                                text = label,
                                color = colors.textMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))

                    // ----- 日期网格（6行 × 7列，Row + Column 手动布局） -----
                    for (week in 0 until 6) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                        ) {
                            for (col in 0 until 7) {
                                val cellIndex = week * 7 + col
                                val day = cellIndex - firstDayOfWeek + 1
                                if (day in 1..daysInMonth) {
                                    val isToday = displayYear == todayYear &&
                                        displayMonth == todayMonth && day == todayDay
                                    val isSelected = selectedYear == displayYear &&
                                        selectedMonth == displayMonth && selectedDay == day
                                    val holiday = monthHolidays[day]
                                    val isBirthday = birthdayParts?.second == displayMonth &&
                                        birthdayParts?.third == day
                                    val hasEvents = dayHasEvents(day)
                                    DayCell(
                                        day = day,
                                        isToday = isToday,
                                        isSelected = isSelected,
                                        holiday = holiday,
                                        isBirthday = isBirthday,
                                        hasEvents = hasEvents,
                                        onClick = {
                                            selectedYear = displayYear
                                            selectedMonth = displayMonth
                                            selectedDay = day
                                        },
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                    )
                                } else {
                                    // 非当月日期的空白格
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight())
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ----- 图例 -----
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // 假（红色）
                        LegendDot(color = colors.error, label = "假")
                        // 班（其他色）
                        LegendDot(color = colors.tertiary, label = "班")
                        // 生日
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎂", fontSize = 10.sp)
                            Spacer(Modifier.width(3.dp))
                            Text("生日", color = colors.textSecondary, fontSize = 10.sp)
                        }
                        // 事件圆点
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary, CircleShape),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text("事件", color = colors.textSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }

            // ===== 选中日期事件列表 =====
            item {
                StradustCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${selectedMonth}月${selectedDay}日",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelectedToday) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        colors.primary.copy(alpha = 0.18f),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "今天",
                                    color = colors.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        // 添加事件按钮
                        IconButton(onClick = {
                            newEventTitle = ""
                            newEventDesc = ""
                            newEventTime = ""
                            showAddEventDialog = true
                        }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "添加事件",
                                tint = colors.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // 生日标记
                    if (selectedDateIsBirthday) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    colors.tertiary.copy(alpha = 0.12f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🎂", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "生日",
                                    color = colors.tertiary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                )
                                selectedBirthdayGreeting?.let {
                                    Text(
                                        text = it,
                                        color = colors.textSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // 节假日标记（红色=假，其他色=班）
                    selectedDateHoliday?.let { hd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (hd.isWorkday) colors.tertiary.copy(alpha = 0.12f)
                                    else colors.error.copy(alpha = 0.12f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (hd.isWorkday) "🛠️" else "🎉",
                                fontSize = 16.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hd.isWorkday) "调休上班" else "节假日",
                                    color = if (hd.isWorkday) colors.tertiary else colors.error,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    text = hd.label,
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // 事件列表（节日/节气，仅选中今天时可用）
                    if (selectedDateEvents.isNotEmpty()) {
                        selectedDateEvents.forEach { ev ->
                            Text(
                                text = ev,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }

                    // 用户/AI 添加的事件
                    if (selectedDateUserEvents.isNotEmpty()) {
                        if (selectedDateEvents.isNotEmpty()) Spacer(Modifier.height(8.dp))
                        selectedDateUserEvents.forEach { evt ->
                            UserEventItem(
                                event = evt,
                                onDelete = {
                                    eventManager.deleteEvent(evt.id)
                                    refreshTick++
                                },
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    if (selectedDateEvents.isEmpty() && selectedDateUserEvents.isEmpty() &&
                        selectedDateHoliday == null && !selectedDateIsBirthday) {
                        Text(
                            text = "该日期无特别事件",
                            color = colors.textMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
        }
    }

    // ===== 添加事件对话框 =====
    if (showAddEventDialog) {
        val dateStr = String.format("%04d-%02d-%02d", selectedYear, selectedMonth, selectedDay)
        AlertDialog(
            onDismissRequest = { showAddEventDialog = false },
            title = { Text("添加事件") },
            text = {
                Column {
                    Text(
                        text = "日期：$dateStr",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newEventTitle,
                        onValueChange = { newEventTitle = it },
                        label = { Text("事件标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newEventTime,
                        onValueChange = { newEventTime = it },
                        label = { Text("时间（可选，格式 HH:mm）") },
                        placeholder = { Text("如 14:00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newEventDesc,
                        onValueChange = { newEventDesc = it },
                        label = { Text("描述（可选）") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newEventTitle.isNotBlank()) {
                        eventManager.addEvent(
                            title = newEventTitle.trim(),
                            date = dateStr,
                            time = newEventTime.trim(),
                            description = newEventDesc.trim(),
                            category = "general",
                            createdBy = "user",
                        )
                        refreshTick++
                    }
                    showAddEventDialog = false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddEventDialog = false }) { Text("取消") }
            },
        )
    }
}

/** 用户/AI 事件列表项 */
@Composable
private fun UserEventItem(
    event: CalendarEvent,
    onDelete: () -> Unit,
) {
    val colors = StradustTheme.colors
    val isAiCreated = event.createdBy == "ai"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isAiCreated) colors.tertiary.copy(alpha = 0.10f)
                else colors.primary.copy(alpha = 0.10f),
                RoundedCornerShape(8.dp),
            )
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 来源图标
        Icon(
            if (isAiCreated) Icons.Default.SmartToy else Icons.Default.CalendarToday,
            contentDescription = if (isAiCreated) "AI添加" else "用户添加",
            tint = if (isAiCreated) colors.tertiary else colors.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                if (event.time.isNotBlank()) {
                    Text(
                        text = event.time,
                        color = colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
            }
            if (event.description.isNotBlank()) {
                Text(
                    text = event.description,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
            // 来源标签
            Text(
                text = if (isAiCreated) "AI添加" else "用户添加",
                color = if (isAiCreated) colors.tertiary else colors.primary,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = colors.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * 日期格组件（网格单元）
 *
 * 视觉规则：
 * - 今天：primary 色圆形背景 + onPrimary 文字
 * - 选中（非今天）：primaryContainer 半透明圆形背景
 * - 节假日假：红色文字
 * - 节假日班：tertiary 色文字
 * - 生日：数字下方显示 🎂
 * - 有事件：数字下方显示对应颜色小圆点
 *
 * @param day 日期数字
 * @param isToday 是否为今天
 * @param isSelected 是否被选中
 * @param holiday 节假日信息（可为空）
 * @param isBirthday 是否为生日
 * @param hasEvents 是否有事件（用于圆点指示）
 * @param onClick 点击回调
 * @param modifier 由父级传入（通常含 weight + fillMaxHeight）
 */
@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    holiday: CalendarManager.HolidayDay?,
    isBirthday: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StradustTheme.colors

    // 文字颜色：今天用 onPrimary；节假日假用 error；节假日班用 tertiary；其余用 textPrimary
    val dayColor = when {
        isToday -> colors.onPrimary
        holiday != null && !holiday.isWorkday -> colors.error
        holiday != null && holiday.isWorkday -> colors.tertiary
        else -> colors.textPrimary
    }

    // 指示点颜色：节假日假用 error；节假日班用 tertiary；其余用 primary
    val dotColor = when {
        holiday != null && !holiday.isWorkday -> colors.error
        holiday != null && holiday.isWorkday -> colors.tertiary
        else -> colors.primary
    }

    Box(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 日期数字圆形容器
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isToday -> colors.primary
                            isSelected -> colors.primaryContainer.copy(alpha = 0.6f)
                            else -> Color.Transparent
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.toString(),
                    color = dayColor,
                    fontSize = 14.sp,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(2.dp))
            // 指示标记行：生日显示 🎂，否则有事件显示圆点
            if (isBirthday) {
                Text("🎂", fontSize = 9.sp)
            } else if (hasEvents) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(dotColor, CircleShape),
                )
            }
        }
    }
}

/** 图例小圆点 + 文字 */
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(3.dp))
        Text(label, color = color, fontSize = 10.sp)
    }
}
