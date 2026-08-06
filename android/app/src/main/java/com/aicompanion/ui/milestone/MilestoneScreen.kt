package com.aicompanion.ui.milestone

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import com.aicompanion.milestone.Milestone
import com.aicompanion.milestone.MilestoneManager
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 里程碑分类预设（key 与后端 category 字段对应） */
private val MILESTONE_CATEGORIES = linkedMapOf(
    "general" to "一般",
    "memory" to "纪念",
    "achievement" to "成就",
    "study" to "学习",
    "relation" to "关系",
)

/**
 * 里程碑与纪念日页面
 *
 * 顶部今日周年提醒（如有） + 全部里程碑列表（按时间倒序）
 * FAB 弹出对话框输入标题/描述/分类
 */
@Composable
fun MilestoneScreen(
    milestones: List<Milestone> = emptyList(),
    anniversaryMessages: List<String> = emptyList(),
    onAddMilestone: (id: String, title: String, description: String, category: String) -> Unit = { _, _, _, _ -> },
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    // MilestoneManager：用于检查每个里程碑是否已达成（hasMilestone）
    val milestoneManager = remember { MilestoneManager(context) }
    val dateFormatter = remember { SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()) }
    val sorted = remember(milestones) { milestones.sortedByDescending { it.timestamp } }
    // 统计已达成数量
    val achievedCount = remember(sorted, milestoneManager) {
        sorted.count { runCatching { milestoneManager.hasMilestone(it.id) }.getOrDefault(false) }
    }

    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StradustTheme.colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StradustTopBar(title = "里程碑与纪念日", onBackClick = onBackClick)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 今日周年提醒区域
                if (anniversaryMessages.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        AnniversaryBanner(messages = anniversaryMessages)
                    }
                }

                // 全部里程碑标题
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = null,
                            tint = StradustTheme.colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "全部里程碑",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "(${sorted.size})",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.width(6.dp))
                        // 已达成统计：已达成/总数
                        Text(
                            text = "· 已达成 $achievedCount/${sorted.size}",
                            color = StradustTheme.colors.tertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                if (sorted.isEmpty()) {
                    item { EmptyMilestoneState() }
                } else {
                    items(sorted, key = { it.id }, contentType = { it.category }) { milestone ->
                        // 调用 milestoneManager.hasMilestone(id) 检查是否已达成
                        val isAchieved = remember(milestone.id) {
                            runCatching { milestoneManager.hasMilestone(milestone.id) }.getOrDefault(false)
                        }
                        MilestoneItemCard(
                            milestone = milestone,
                            dateFormatter = dateFormatter,
                            isAchieved = isAchieved,
                        )
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // FAB 添加新里程碑
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = StradustTheme.colors.primary,
            contentColor = StradustTheme.colors.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加里程碑")
        }
    }

    if (showAddDialog) {
        AddMilestoneDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, description, category ->
                val id = "ms_${System.currentTimeMillis()}"
                onAddMilestone(id, title, description, category)
                showAddDialog = false
            },
        )
    }
}

/** 今日周年提醒横幅 */
@Composable
private fun AnniversaryBanner(messages: List<String>) {
    StradustCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(StradustTheme.colors.tertiary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Cake,
                    contentDescription = null,
                    tint = StradustTheme.colors.tertiary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "今日纪念",
                    color = StradustTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(4.dp))
                messages.take(2).forEach { msg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = StradustTheme.colors.tertiary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = msg,
                            color = StradustTheme.colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
                if (messages.size > 2) {
                    Text(
                        text = "还有 ${messages.size - 2} 条…",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/** 单个里程碑卡片 */
@Composable
private fun MilestoneItemCard(
    milestone: Milestone,
    dateFormatter: SimpleDateFormat,
    isAchieved: Boolean,
) {
    StradustCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 分类图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isAchieved) StradustTheme.colors.tertiary.copy(alpha = 0.18f)
                        else StradustTheme.colors.primary.copy(alpha = 0.12f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isAchieved) Icons.Default.CheckCircle else categoryIcon(milestone.category),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    // 已达成用绿色（tertiary）勾标识，未达成用灰色 primary
                    tint = if (isAchieved) StradustTheme.colors.tertiary else StradustTheme.colors.primary,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = milestone.title,
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // 达成状态标识：已达成用绿色勾，未达成用灰色
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isAchieved) StradustTheme.colors.tertiary.copy(alpha = 0.18f)
                                else StradustTheme.colors.surfaceContainerHigh.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isAchieved) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = if (isAchieved) StradustTheme.colors.tertiary else StradustTheme.colors.textMuted,
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = if (isAchieved) "已达成" else "未达成",
                                color = if (isAchieved) StradustTheme.colors.tertiary else StradustTheme.colors.textMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                if (milestone.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = milestone.description,
                        color = StradustTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = StradustTheme.colors.textMuted,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = dateFormatter.format(Date(milestone.timestamp)),
                        color = StradustTheme.colors.textMuted,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    CategoryTag(category = milestone.category)
                }
            }
        }
    }
}

/** 分类标签 */
@Composable
private fun CategoryTag(category: String) {
    val label = MILESTONE_CATEGORIES[category] ?: category
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                StradustTheme.colors.primaryContainer.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = StradustTheme.colors.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 空状态 */
@Composable
private fun EmptyMilestoneState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(StradustTheme.colors.surfaceContainerLow, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = StradustTheme.colors.textMuted,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "还没有里程碑",
            color = StradustTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "记录每一个值得纪念的时刻 🎯",
            color = StradustTheme.colors.textMuted,
            fontSize = 13.sp,
        )
    }
}

/** 添加里程碑对话框 */
@Composable
private fun AddMilestoneDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, category: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("general") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加里程碑", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("标题", color = StradustTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                StradustInput(
                    value = title,
                    onValueChange = { title = it },
                    hint = "例如：第一次对话",
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Text("描述", color = StradustTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                StradustInput(
                    value = description,
                    onValueChange = { description = it },
                    hint = "记录这段经历…",
                    maxLines = 3,
                )
                Spacer(Modifier.height(12.dp))
                Text("分类", color = StradustTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MILESTONE_CATEGORIES.entries.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { selectedCategory = key },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StradustTheme.colors.primary.copy(alpha = 0.15f),
                                selectedLabelColor = StradustTheme.colors.primary,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title.trim(), description.trim(), selectedCategory)
                    }
                },
                enabled = title.isNotBlank(),
            ) {
                Text("记录", color = StradustTheme.colors.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = StradustTheme.colors.textMuted)
            }
        },
    )
}

/** 根据 category 返回对应图标 */
private fun categoryIcon(category: String) = when (category) {
    "achievement" -> Icons.Default.Star
    "study" -> Icons.Default.School
    "relation" -> Icons.Default.Favorite
    "memory" -> Icons.Default.Cake
    else -> Icons.Default.Flag
}
