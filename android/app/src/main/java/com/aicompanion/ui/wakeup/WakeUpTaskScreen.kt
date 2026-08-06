package com.aicompanion.ui.wakeup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar
import com.aicompanion.wakeup.WakeUpTask

/**
 * 唤醒任务页面
 *
 * 任务列表，每项显示名称/描述/时间/开关
 * FAB 添加新任务；卡片内可编辑/删除
 * 默认任务（isDefault=true）不可删除，仅可编辑非 id 字段
 */
@Composable
fun WakeUpTaskScreen(
    tasks: List<WakeUpTask> = emptyList(),
    onAddTask: (WakeUpTask) -> Unit = {},
    onUpdateTask: (id: String, updater: (WakeUpTask) -> WakeUpTask) -> Unit = { _, _ -> },
    onDeleteTask: (id: String) -> Unit = {},
    onToggleTask: (id: String, enabled: Boolean) -> Unit = { _, _ -> },
    onBackClick: () -> Unit = {},
) {
    // 默认任务置顶，其余按时间升序
    val sorted = remember(tasks) {
        tasks.sortedWith(compareByDescending<WakeUpTask> { it.isDefault }.thenBy { it.hour * 60 + it.minute })
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<WakeUpTask?>(null) }
    var pendingDelete by remember { mutableStateOf<WakeUpTask?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StradustTheme.colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StradustTopBar(title = "唤醒任务", onBackClick = onBackClick)

            if (sorted.isEmpty()) {
                EmptyTaskState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(sorted, key = { it.id }) { task ->
                        WakeUpTaskItemCard(
                            task = task,
                            onToggle = { enabled -> onToggleTask(task.id, enabled) },
                            onEdit = {
                                editingTask = task
                                showEditDialog = true
                            },
                            onDelete = { pendingDelete = task },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // FAB 添加新任务
        FloatingActionButton(
            onClick = {
                editingTask = null
                showEditDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = StradustTheme.colors.primary,
            contentColor = StradustTheme.colors.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加任务")
        }
    }

    // 编辑/添加对话框
    if (showEditDialog) {
        TaskEditDialog(
            existing = editingTask,
            onDismiss = { showEditDialog = false },
            onConfirm = { task ->
                if (editingTask == null) {
                    onAddTask(task)
                } else {
                    editingTask?.let { editing ->
                        onUpdateTask(editing.id) { old ->
                            old.copy(
                                name = task.name,
                                description = task.description,
                                hour = task.hour,
                                minute = task.minute,
                            )
                        }
                    }
                }
                showEditDialog = false
            },
        )
    }

    // 删除确认
    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除任务") },
            text = {
                Text(
                    text = "确定删除「${task.name}」吗？此操作不可撤销。",
                    color = StradustTheme.colors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTask(task.id)
                    pendingDelete = null
                }) {
                    Text("删除", color = StradustTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = StradustTheme.colors.primary)
                }
            },
        )
    }
}

/** 单个唤醒任务卡片 */
@Composable
private fun WakeUpTaskItemCard(
    task: WakeUpTask,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    StradustCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 时间图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (task.enabled) StradustTheme.colors.primary.copy(alpha = 0.15f)
                        else StradustTheme.colors.surfaceContainerHigh,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (task.enabled) StradustTheme.colors.primary
                    else StradustTheme.colors.textMuted,
                )
            }

            Spacer(Modifier.width(12.dp))

            // 内容
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.name,
                        color = if (task.enabled) StradustTheme.colors.textPrimary
                        else StradustTheme.colors.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (task.isDefault) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    StradustTheme.colors.tertiary.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = StradustTheme.colors.tertiary,
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "默认",
                                    color = StradustTheme.colors.tertiary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
                if (task.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = task.description,
                        color = StradustTheme.colors.textMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = StradustTheme.colors.textMuted,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "%02d:%02d".format(task.hour, task.minute),
                        color = StradustTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // 编辑按钮
            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = StradustTheme.colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }

            // 删除按钮（默认任务不可删除）
            if (!task.isDefault) {
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = StradustTheme.colors.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // 开关
            Switch(
                checked = task.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = StradustTheme.colors.onPrimary,
                    checkedTrackColor = StradustTheme.colors.primary,
                    uncheckedThumbColor = StradustTheme.colors.textMuted,
                    uncheckedTrackColor = StradustTheme.colors.surfaceContainerHigh,
                ),
            )
        }
    }
}

/** 空状态 */
@Composable
private fun EmptyTaskState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(StradustTheme.colors.surfaceContainerLow, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Alarm,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = StradustTheme.colors.textMuted,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "还没有唤醒任务",
            color = StradustTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "点击右下角按钮添加一个闹钟 ⏰",
            color = StradustTheme.colors.textMuted,
            fontSize = 14.sp,
        )
    }
}

/** 任务编辑/添加对话框 */
@Composable
private fun TaskEditDialog(
    existing: WakeUpTask?,
    onDismiss: () -> Unit,
    onConfirm: (WakeUpTask) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var hourStr by remember { mutableStateOf((existing?.hour ?: 9).toString()) }
    var minuteStr by remember { mutableStateOf((existing?.minute ?: 0).toString()) }

    val hour = hourStr.toIntOrNull()
    val minute = minuteStr.toIntOrNull()
    val timeValid = hour != null && hour in 0..23 && minute != null && minute in 0..59
    val canSave = name.isNotBlank() && timeValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (existing == null) Icons.Default.Add else Icons.Default.Edit,
                    contentDescription = null,
                    tint = StradustTheme.colors.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (existing == null) "添加唤醒任务" else "编辑任务", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().imePadding()) {
                Text("名称", color = StradustTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                StradustInput(
                    value = name,
                    onValueChange = { name = it },
                    hint = "例如：健康监督",
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Text("描述", color = StradustTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                StradustInput(
                    value = description,
                    onValueChange = { description = it },
                    hint = "唤醒时显示的话…",
                    maxLines = 3,
                )
                Spacer(Modifier.height(12.dp))
                Text("时间（24 小时制）", color = StradustTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        StradustInput(
                            value = hourStr,
                            onValueChange = { hourStr = it.filter { c -> c.isDigit() }.take(2) },
                            hint = "时 (0-23)",
                            singleLine = true,
                        )
                    }
                    Text(":", color = StradustTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.weight(1f)) {
                        StradustInput(
                            value = minuteStr,
                            onValueChange = { minuteStr = it.filter { c -> c.isDigit() }.take(2) },
                            hint = "分 (0-59)",
                            singleLine = true,
                        )
                    }
                }
                if (!timeValid && (hourStr.isNotEmpty() || minuteStr.isNotEmpty())) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "时间无效：小时 0-23，分钟 0-59",
                        color = StradustTheme.colors.error,
                        fontSize = 11.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canSave) {
                        val task = if (existing == null) {
                            WakeUpTask(
                                name = name.trim(),
                                description = description.trim(),
                                hour = hour!!,
                                minute = minute!!,
                                enabled = true,
                                isDefault = false,
                            )
                        } else {
                            existing.copy(
                                name = name.trim(),
                                description = description.trim(),
                                hour = hour!!,
                                minute = minute!!,
                            )
                        }
                        onConfirm(task)
                    }
                },
                enabled = canSave,
            ) {
                Text("保存", color = StradustTheme.colors.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = StradustTheme.colors.textMuted)
            }
        },
    )
}
