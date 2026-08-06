package com.aicompanion.ui.capsule

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.capsule.TimeCapsule
import com.aicompanion.capsule.TimeCapsuleManager
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.ButtonSize
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 时光胶囊页面
 *
 * 列表分两部分：
 * - 已到期（含已开启）：可点击查看内容
 * - 未到期：显示倒计时
 *
 * FAB 弹出对话框输入标题/内容/开启日期（yyyy-MM-dd）
 */
@Composable
fun TimeCapsuleScreen(
    capsules: List<TimeCapsule> = emptyList(),
    onCreateCapsule: (title: String, content: String, openDate: Long) -> Unit = { _, _, _ -> },
    onOpenCapsule: (TimeCapsule) -> Unit = {},
    onDeleteCapsule: (TimeCapsule) -> Unit = {},
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()) }
    val inputFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val now = remember { System.currentTimeMillis() }

    // 分类：已到期（含已开启）+ 未到期
    val sorted = remember(capsules) { capsules.sortedByDescending { it.createdAt } }
    val dueCapsules = remember(sorted, now) {
        sorted.filter { it.isOpened || it.openDate <= now }
    }
    val upcomingCapsules = remember(sorted, now) {
        sorted.filter { !it.isOpened && it.openDate > now }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var openedCapsule by remember { mutableStateOf<TimeCapsule?>(null) }
    var pendingDelete by remember { mutableStateOf<TimeCapsule?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StradustTheme.colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StradustTopBar(title = "时光胶囊", onBackClick = onBackClick)

            if (capsules.isEmpty()) {
                EmptyCapsuleState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (dueCapsules.isNotEmpty()) {
                        item { SectionHeader(title = "已到期", count = dueCapsules.size) }
                        items(dueCapsules, key = { it.id }) { capsule ->
                            CapsuleItemCard(
                                capsule = capsule,
                                dateFormatter = dateFormatter,
                                isDue = true,
                                onClick = { onOpenCapsule(capsule) },
                                onDelete = { pendingDelete = capsule },
                            )
                        }
                    }

                    if (upcomingCapsules.isNotEmpty()) {
                        item {
                            if (dueCapsules.isNotEmpty()) Spacer(Modifier.height(8.dp))
                            SectionHeader(title = "未到期", count = upcomingCapsules.size)
                        }
                        items(upcomingCapsules, key = { it.id }) { capsule ->
                            CapsuleItemCard(
                                capsule = capsule,
                                dateFormatter = dateFormatter,
                                isDue = false,
                                onClick = { onOpenCapsule(capsule) },
                                onDelete = { pendingDelete = capsule },
                            )
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // FAB 创建新胶囊
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = StradustTheme.colors.primary,
            contentColor = StradustTheme.colors.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "创建胶囊")
        }
    }

    // 创建对话框
    if (showCreateDialog) {
        CreateCapsuleDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, content, openDate, fromSelf ->
                // 记录创建前时间，用于定位刚创建的胶囊
                val beforeTime = System.currentTimeMillis()
                onCreateCapsule(title, content, openDate)
                // createCapsule 默认 fromSelf=true，若用户选 false 则需修正
                if (!fromSelf) {
                    val manager = TimeCapsuleManager(context)
                    val capsule = manager.loadCapsules().find {
                        it.createdAt >= beforeTime && it.title == title
                    }
                    capsule?.let { updateCapsuleFromSelf(context, it.id, false) }
                }
                showCreateDialog = false
            },
            inputFormatter = inputFormatter,
        )
    }

    // 开启后的内容查看对话框
    openedCapsule?.let { capsule ->
        CapsuleContentDialog(
            capsule = capsule,
            dateFormatter = dateFormatter,
            onDismiss = { openedCapsule = null },
        )
    }

    // 删除确认
    pendingDelete?.let { capsule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除胶囊") },
            text = {
                Text(
                    text = "确定删除「${capsule.title}」吗？此操作不可撤销。",
                    color = StradustTheme.colors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCapsule(capsule)
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

/** 空状态 */
@Composable
private fun EmptyCapsuleState() {
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
                Icons.Default.Email,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = StradustTheme.colors.textMuted,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "还没有时光胶囊",
            color = StradustTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "点击右下角按钮给未来的自己写一封信 ✉️",
            color = StradustTheme.colors.textMuted,
            fontSize = 14.sp,
        )
    }
}

/** 分组标题 */
@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            color = StradustTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "($count)",
            color = StradustTheme.colors.textMuted,
            fontSize = 12.sp,
        )
    }
}

/** 单个胶囊卡片 */
@Composable
private fun CapsuleItemCard(
    capsule: TimeCapsule,
    dateFormatter: SimpleDateFormat,
    isDue: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    StradustCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (isDue && !capsule.isOpened) onClick else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 状态图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            capsule.isOpened -> StradustTheme.colors.surfaceContainerHigh
                            isDue -> StradustTheme.colors.primary.copy(alpha = 0.15f)
                            else -> StradustTheme.colors.surfaceContainerHigh
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    capsule.isOpened -> Icon(
                        Icons.Default.MarkEmailRead,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = StradustTheme.colors.textSecondary,
                    )
                    isDue -> Icon(
                        Icons.Default.LockOpen,
                        contentDescription = "可开启",
                        modifier = Modifier.size(22.dp),
                        tint = StradustTheme.colors.primary,
                    )
                    else -> Icon(
                        Icons.Default.Lock,
                        contentDescription = "未到期",
                        modifier = Modifier.size(20.dp),
                        tint = StradustTheme.colors.textMuted,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // 内容
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = capsule.title,
                    color = if (capsule.isOpened) StradustTheme.colors.textSecondary
                    else StradustTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                        text = "创建：${dateFormatter.format(Date(capsule.createdAt))}",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "开启：${dateFormatter.format(Date(capsule.openDate))}",
                    color = StradustTheme.colors.textMuted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))
                // 状态徽章
                StatusBadge(capsule = capsule, isDue = isDue)
            }

            // 删除按钮
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = StradustTheme.colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** 状态徽章 */
@Composable
private fun StatusBadge(capsule: TimeCapsule, isDue: Boolean) {
    val (text, color, bg) = when {
        capsule.isOpened -> Triple("已开启", StradustTheme.colors.textSecondary, StradustTheme.colors.surfaceContainerHigh)
        isDue -> Triple("可开启", StradustTheme.colors.primary, StradustTheme.colors.primary.copy(alpha = 0.12f))
        else -> {
            val days = ((capsule.openDate - System.currentTimeMillis()) / 86_400_000L).toInt() + 1
            val label = if (days <= 0) "即将开启" else "还剩 $days 天"
            Triple(label, StradustTheme.colors.tertiary, StradustTheme.colors.tertiary.copy(alpha = 0.12f))
        }
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 创建胶囊对话框 */
@Composable
private fun CreateCapsuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String, openDate: Long, fromSelf: Boolean) -> Unit,
    inputFormatter: SimpleDateFormat,
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var dateStr by remember {
        mutableStateOf(inputFormatter.format(Date(System.currentTimeMillis() + 30L * 86_400_000L)))
    }
    var dateError by remember { mutableStateOf(false) }
    // 默认 true：写给未来的自己
    var fromSelf by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("写给未来的自己", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("标题", color = StradustTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                StradustInput(
                    value = title,
                    onValueChange = { title = it },
                    hint = "给这封信起个名字",
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Text("内容", color = StradustTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                StradustInput(
                    value = content,
                    onValueChange = { content = it },
                    hint = "写下此刻的心情…",
                    maxLines = 5,
                )
                Spacer(Modifier.height(12.dp))
                Text("开启日期", color = StradustTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                StradustInput(
                    value = dateStr,
                    onValueChange = {
                        dateStr = it
                        dateError = parseDateToTimestamp(it) == null
                    },
                    hint = "yyyy-MM-dd",
                    singleLine = true,
                )
                if (dateError) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "日期格式不正确，应为 yyyy-MM-dd",
                        color = StradustTheme.colors.error,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                // fromSelf 开关：「写给未来的自己」(true) / 「来自当前 AI 角色」(false)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (fromSelf) "写给未来的自己" else "来自当前 AI 角色",
                            color = StradustTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "切换胶囊来源",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = fromSelf,
                        onCheckedChange = { fromSelf = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ts = parseDateToTimestamp(dateStr)
                    if (title.isNotBlank() && content.isNotBlank() && ts != null) {
                        onConfirm(title.trim(), content.trim(), ts, fromSelf)
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank() && !dateError && parseDateToTimestamp(dateStr) != null,
            ) {
                Text("封存", color = StradustTheme.colors.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = StradustTheme.colors.textMuted)
            }
        },
    )
}

/**
 * 直接操作 SharedPreferences 修正胶囊的 fromSelf 字段
 * 因 TimeCapsuleManager.createCapsule 不接受 fromSelf 参数且 saveCapsules 私有，
 * 在不改后端的前提下通过读写 prefs 实现
 */
private fun updateCapsuleFromSelf(context: Context, capsuleId: String, fromSelf: Boolean) {
    // 注意：后端 createCapsule 不支持 fromSelf 参数，此处为 workaround，直接操作 SharedPreferences
    try {
        val prefs = context.getSharedPreferences("time_capsules", Context.MODE_PRIVATE)
        val raw = prefs.getString("capsule_list", null) ?: return
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("id") == capsuleId) {
                obj.put("fromSelf", fromSelf)
                break
            }
        }
        prefs.edit().putString("capsule_list", arr.toString()).apply()
    } catch (_: Exception) {
        // 忽略解析与读写异常，防止崩溃
    }
}

/** 胶囊内容查看对话框 */
@Composable
private fun CapsuleContentDialog(
    capsule: TimeCapsule,
    dateFormatter: SimpleDateFormat,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MarkEmailRead,
                    contentDescription = null,
                    tint = StradustTheme.colors.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(capsule.title, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "创建于 ${dateFormatter.format(Date(capsule.createdAt))}",
                    color = StradustTheme.colors.textMuted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = capsule.content,
                    color = StradustTheme.colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            }
        },
        confirmButton = {
            StradustButton(
                text = "好的",
                onClick = onDismiss,
                variant = ButtonVariant.TONAL,
                size = ButtonSize.SMALL,
            )
        },
    )
}

/** 解析 yyyy-MM-dd 为时间戳（当天 00:00） */
private fun parseDateToTimestamp(dateStr: String): Long? {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.isLenient = false
        sdf.parse(dateStr)?.time
    } catch (_: Exception) {
        null
    }
}
