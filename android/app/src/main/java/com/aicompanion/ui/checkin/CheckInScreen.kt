package com.aicompanion.ui.checkin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustTopBar
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** 日历日期项 */
data class CalendarDay(
    val date: LocalDate?,
    val isCheckedIn: Boolean,
    val isToday: Boolean,
    val isCurrentMonth: Boolean,
)

@Composable
fun CheckInScreen(
    consecutiveDays: Int = 0,
    isCheckedInToday: Boolean = false,
    checkedDates: Set<String> = emptySet(),
    totalCheckIns: Int = 0,
    onCheckIn: () -> Unit = {},
    onBackClick: (() -> Unit)? = null,
) {
    var showConfetti by remember { mutableStateOf(false) }
    val checkInScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // 生成本月日历数据
    val today = LocalDate.now()
    val yearMonth = YearMonth.from(today)
    val calendarDays = remember(yearMonth, checkedDates) { generateCalendarDays(yearMonth, checkedDates) }

    // 外层光环 pulse 动画
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1500)),
        label = "pulseScale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1500)),
        label = "pulseAlpha",
    )

    LaunchedEffect(showConfetti) {
        if (showConfetti) {
            checkInScale.snapTo(0.8f)
            checkInScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StradustTheme.colors.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { StradustTopBar(title = "每日签到", onBackClick = onBackClick) }

            item { Spacer(Modifier.height(16.dp)) }

            // 大型中央签到按钮
            item {
                Box(contentAlignment = Alignment.Center) {
                    if (showConfetti) {
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    StradustTheme.colors.primary.copy(alpha = 0.15f * pulseAlpha),
                                    CircleShape,
                                ),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .scale(checkInScale.value)
                            .size(160.dp)
                            .clip(CircleShape)
                            .then(
                                if (isCheckedInToday) {
                                    Modifier.background(StradustTheme.colors.surfaceContainerLow, CircleShape)
                                } else {
                                    Modifier.background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                StradustTheme.colors.primary,
                                                StradustTheme.colors.tertiary,
                                            ),
                                        ),
                                        CircleShape,
                                    )
                                }
                            )
                            .clickable {
                                if (!isCheckedInToday) {
                                    scope.launch {
                                        checkInScale.snapTo(0.85f)
                                        checkInScale.animateTo(
                                            targetValue = 1.05f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                        )
                                        checkInScale.animateTo(
                                            targetValue = 1f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                        )
                                        onCheckIn()
                                        showConfetti = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isCheckedInToday) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "已签到",
                                    modifier = Modifier.size(36.dp),
                                    tint = StradustTheme.colors.textSecondary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "已签到",
                                    color = StradustTheme.colors.textSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                )
                            } else {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = "签到",
                                    modifier = Modifier.size(36.dp),
                                    tint = StradustTheme.colors.onPrimary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "签到",
                                    color = StradustTheme.colors.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                )
                            }
                        }
                    }
                }
            }

            // 连续签到天数
            item {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "已连续签到 ",
                        color = StradustTheme.colors.textSecondary,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "$consecutiveDays",
                        color = StradustTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                    )
                    Text(
                        text = " 天",
                        color = StradustTheme.colors.textSecondary,
                        fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                // 下一次触发AI动态的剩余天数
                val nextTrigger = 15 - (consecutiveDays % 15)
                val isTriggerDay = consecutiveDays > 0 && consecutiveDays % 15 == 0
                Text(
                    text = if (isTriggerDay) "🎉 今天触发AI发动态！"
                           else "再坚持 $nextTrigger 天触发AI发动态",
                    color = if (isTriggerDay) StradustTheme.colors.tertiary
                           else StradustTheme.colors.textMuted,
                    fontSize = 13.sp,
                    fontWeight = if (isTriggerDay) FontWeight.Medium else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "累计签到 $totalCheckIns 次",
                    color = StradustTheme.colors.textSecondary,
                    fontSize = 13.sp,
                )
            }

            // 月历
            item {
                Spacer(Modifier.height(24.dp))
                StradustCard {
                    Text(
                        text = "${yearMonth.monthValue}月 签到记录",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                            Text(
                                text = day,
                                color = StradustTheme.colors.textMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    val cols = 7
                    calendarDays.chunked(cols).forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            week.forEach { day ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(3.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (day.date != null) {
                                        val bgColor = when {
                                            day.isToday && day.isCheckedIn -> StradustTheme.colors.primary
                                            day.isCheckedIn -> StradustTheme.colors.tertiary.copy(alpha = 0.7f)
                                            day.isToday -> StradustTheme.colors.primary.copy(alpha = 0.15f)
                                            !day.isCurrentMonth -> Color.Transparent
                                            else -> StradustTheme.colors.surfaceContainerLow.copy(alpha = 0.3f)
                                        }
                                        val textColor = when {
                                            day.isCheckedIn || day.isToday -> StradustTheme.colors.onPrimary
                                            !day.isCurrentMonth -> StradustTheme.colors.textDisabled
                                            else -> StradustTheme.colors.textPrimary
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .then(
                                                    if (bgColor != Color.Transparent) {
                                                        Modifier.background(bgColor, CircleShape)
                                                    } else {
                                                        Modifier
                                                    },
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (day.isCheckedIn) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "已签",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = textColor,
                                                )
                                            } else {
                                                Text(
                                                    text = day.date.dayOfMonth.toString(),
                                                    color = textColor,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (day.isToday) FontWeight.Bold
                                                    else FontWeight.Normal,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 连续签到15天触发AI发动态提示
            item {
                Spacer(Modifier.height(16.dp))
                StradustCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = StradustTheme.colors.tertiary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "连续签到15天",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                    Text(
                        text = "每连续签到15天，AI角色会自动发一条动态，分享这段时间的感受和想法。",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** 生成当月日历数据 */
private fun generateCalendarDays(
    yearMonth: YearMonth,
    checkedDates: Set<String> = emptySet()
): List<CalendarDay> {
    val today = LocalDate.now()
    val firstDay = yearMonth.atDay(1)
    val lastDay = yearMonth.atEndOfMonth()
    val startDow = firstDay.dayOfWeek.value % 7
    val days = mutableListOf<CalendarDay>()

    val prevMonth = yearMonth.minusMonths(1)
    for (i in startDow downTo 1) {
        val d = prevMonth.atEndOfMonth().minusDays((i - 1).toLong())
        days.add(CalendarDay(date = d, isCheckedIn = false, isToday = false, isCurrentMonth = false))
    }

    for (day in 1..lastDay.dayOfMonth) {
        val date = yearMonth.atDay(day)
        val dateStr = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        days.add(
            CalendarDay(
                date = date,
                isCheckedIn = dateStr in checkedDates,
                isToday = date == today,
                isCurrentMonth = true,
            ),
        )
    }

    val remaining = 42 - days.size
    for (i in 1..remaining) {
        days.add(
            CalendarDay(
                date = lastDay.plusDays(i.toLong()),
                isCheckedIn = false,
                isToday = false,
                isCurrentMonth = false,
            ),
        )
    }

    return days
}
