package com.aicompanion.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.action.AIActionManager
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.ButtonSize
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar
import java.util.Calendar

/**
 * 日程管理页面
 *
 * 展示按日期排序的日程列表，支持通过对话框添加新日程。
 *
 * @param onBackClick 返回回调
 */
@Composable
fun ScheduleScreen(
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val actionManager = remember { AIActionManager(context) }
    var refreshTick by remember { mutableStateOf(0) }
    val schedules = remember(refreshTick) {
        actionManager.getAllSchedules().sortedWith(
            compareBy({ it.year }, { it.month }, { it.dayOfMonth }, { it.hour }, { it.minute })
        )
    }
    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StradustTheme.colors.background),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { StradustTopBar(title = "日程管理", onBackClick = onBackClick) }

            item { Spacer(Modifier.height(16.dp)) }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    StradustButton(
                        text = "添加日程",
                        onClick = { showAddDialog = true },
                        variant = ButtonVariant.FILLED,
                        size = ButtonSize.MEDIUM,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            if (schedules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = StradustTheme.colors.textMuted,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "还没有日程，点击上方按钮添加",
                                color = StradustTheme.colors.textMuted,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            } else {
                items(
                    schedules,
                    key = { "${it.year}-${it.month}-${it.dayOfMonth}-${it.hour}-${it.minute}-${it.description}" },
                ) { schedule ->
                    ScheduleItemCard(
                        schedule = schedule,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }

        if (showAddDialog) {
            AddScheduleDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { info ->
                    actionManager.addSchedule(info)
                    refreshTick++
                    showAddDialog = false
                },
            )
        }
    }
}

/** 单条日程卡片 */
@Composable
private fun ScheduleItemCard(
    schedule: AIActionManager.ScheduleInfo,
    modifier: Modifier = Modifier,
) {
    StradustCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(StradustTheme.colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = StradustTheme.colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${schedule.year}/${schedule.month + 1}/${schedule.dayOfMonth}  " +
                        "${"%02d".format(schedule.hour)}:${"%02d".format(schedule.minute)}",
                    color = StradustTheme.colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = schedule.description,
                    color = StradustTheme.colors.textPrimary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/** 添加日程对话框 */
@Composable
private fun AddScheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (AIActionManager.ScheduleInfo) -> Unit,
) {
    val calendar = Calendar.getInstance()
    var year by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) }
    var day by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    var hour by remember { mutableIntStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(calendar.get(Calendar.MINUTE)) }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加日程") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("日期", color = StradustTheme.colors.textSecondary, fontSize = 13.sp)
                    NumberStepper(
                        label = "年",
                        displayText = "$year",
                        value = year,
                        min = 2024,
                        max = 2100,
                        onValueChange = { year = it },
                    )
                    NumberStepper(
                        label = "月",
                        displayText = "${month + 1}",
                        value = month + 1,
                        min = 1,
                        max = 12,
                        onValueChange = { month = it - 1 },
                    )
                    NumberStepper(
                        label = "日",
                        displayText = "$day",
                        value = day,
                        min = 1,
                        max = 31,
                        onValueChange = { day = it },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("时间", color = StradustTheme.colors.textSecondary, fontSize = 13.sp)
                    NumberStepper(
                        label = "时",
                        displayText = "%02d".format(hour),
                        value = hour,
                        min = 0,
                        max = 23,
                        onValueChange = { hour = it },
                    )
                    NumberStepper(
                        label = "分",
                        displayText = "%02d".format(minute),
                        value = minute,
                        min = 0,
                        max = 59,
                        onValueChange = { minute = it },
                    )
                }
                Spacer(Modifier.height(12.dp))
                StradustInput(
                    value = description,
                    onValueChange = { description = it },
                    hint = "日程描述",
                    singleLine = true,
                    imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        AIActionManager.ScheduleInfo(
                            hour = hour,
                            minute = minute,
                            dayOfMonth = day,
                            month = month,
                            year = year,
                            description = description,
                        )
                    )
                },
                enabled = description.isNotBlank(),
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 数字步进器（上/下箭头调节） */
@Composable
private fun NumberStepper(
    label: String,
    displayText: String,
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = StradustTheme.colors.textMuted,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(StradustTheme.colors.surfaceContainerLow)
                    .clickable {
                        val next = value - 1
                        onValueChange(if (next < min) max else next)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "减少",
                    tint = StradustTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = displayText,
                color = StradustTheme.colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(36.dp),
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(StradustTheme.colors.surfaceContainerLow)
                    .clickable {
                        val next = value + 1
                        onValueChange(if (next > max) min else next)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "增加",
                    tint = StradustTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
