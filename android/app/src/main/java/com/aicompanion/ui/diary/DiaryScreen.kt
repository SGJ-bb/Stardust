package com.aicompanion.ui.diary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustTopBar
import com.aicompanion.ui.components.WallpaperBackground
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 日记条目数据模型 */
data class DiaryEntry(
    val id: Int,
    val date: LocalDate,
    val mood: String,
    val moodEmoji: String,
    val content: String,
    val aiReplySummary: String,
)

@Composable
fun DiaryScreen(
    /** 真实日记数据列表（从 DiaryManager 获取） */
    diaryEntries: List<DiaryEntry> = emptyList(),
    /** 回调：写新日记（content 为用户输入内容） */
    onAddDiary: (String) -> Unit = {},
    /** 回调：点击日记卡片（查看/编辑）—— 现已内联为对话框，保留以便外部扩展 */
    onDiaryClick: (DiaryEntry) -> Unit = {},
    /** 回调：更新已有日记（id 为日记唯一标识，content 为新内容） */
    onUpdateDiary: (Long, String) -> Unit = { _, _ -> },
    /** 回调：删除日记条目 */
    onDeleteDiary: (DiaryEntry) -> Unit = {},
    /** 回调：返回上一页 */
    onBackClick: (() -> Unit)? = null,
    /** 回调：点击搜索按钮 */
    onSearchClick: () -> Unit = {},
    /** 壁纸图片路径（本地文件路径或URI） */
    wallpaperPath: String? = null,
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    // 日记编辑对话框状态：showEditDialog 控制显隐；editingDiaryId=null 表示新建
    var showEditDialog by remember { mutableStateOf(false) }
    var editingDiaryContent by remember { mutableStateOf("") }
    var editingDiaryId by remember { mutableStateOf<Long?>(null) }

    // 生成最近7天的日期列表（最旧在前，最新在后）
    val dates = remember {
        (0..6).map { LocalDate.now().minusDays(it.toLong()) }.reversed()
    }
    val dateFormatter = DateTimeFormatter.ofPattern("M/d")
    val dayFormatter = DateTimeFormatter.ofPattern("EEE", java.util.Locale.CHINESE)

    WallpaperBackground(wallpaperPath = wallpaperPath) {
        Column {
            StradustTopBar(
                title = "日记",
                onBackClick = onBackClick,
                actions = {
                    // 搜索日记入口
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "搜索日记",
                            tint = StradustTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )

            // 顶部日历选择器：横向滚动 7 天
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                dates.forEach { date ->
                    val isSelected = date == selectedDate
                    val isToday = date == LocalDate.now()
                    // 日期胶囊 56dp 宽，选中=primary 底+白字，今天=小圆点指示
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) StradustTheme.colors.primary
                                else StradustTheme.colors.surfaceContainerLow.copy(alpha = 0.5f),
                                RoundedCornerShape(16.dp),
                            )
                            .clickable { selectedDate = date }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = dayFormatter.format(date),
                            color = if (isSelected) StradustTheme.colors.onPrimary
                            else StradustTheme.colors.textMuted,
                            fontSize = 11.sp,
                        )
                        Text(
                            text = dateFormatter.format(date),
                            color = if (isSelected) StradustTheme.colors.onPrimary
                            else StradustTheme.colors.textPrimary,
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                        )
                        // 今天小圆点指示器
                        if (isToday && !isSelected) {
                            Spacer(Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(StradustTheme.colors.primary, CircleShape),
                            )
                        } else {
                            Spacer(Modifier.height(7.dp))
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (diaryEntries.isEmpty()) {
                    // 空状态：圆形 surfaceContainerLow 底 + EditNote icon(48dp muted)
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
                                Icons.Default.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = StradustTheme.colors.textMuted,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "还没有日记哦",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "点击右下角按钮写下第一篇日记吧 ✍️",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(diaryEntries, key = { it.id }) { entry ->
                            DiaryItemCard(
                                entry = entry,
                                onClick = {
                                    // 内联打开编辑对话框：填充已有内容与 id
                                    editingDiaryId = entry.id.toLong()
                                    editingDiaryContent = entry.content
                                    showEditDialog = true
                                    onDiaryClick(entry)
                                },
                                onDelete = { onDeleteDiary(entry) },
                            )
                        }
                        item {
                            Spacer(Modifier.height(80.dp))
                        }
                    }
                }
            }
        }

        // FAB：primary 底 + Add icon，spring scale 入场动画
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                initialScale = 0.8f,
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            FloatingActionButton(
                onClick = {
                    // 新建日记：清空编辑状态并打开对话框
                    editingDiaryId = null
                    editingDiaryContent = ""
                    showEditDialog = true
                },
                containerColor = StradustTheme.colors.primary,
                contentColor = StradustTheme.colors.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "写日记")
            }
        }
    }

    // 日记编辑对话框：新建 / 编辑共用
    if (showEditDialog) {
        DiaryEditDialog(
            initialContent = editingDiaryContent,
            isEdit = editingDiaryId != null,
            onDismiss = { showEditDialog = false },
            onSave = { content ->
                val id = editingDiaryId
                if (id != null) {
                    // 编辑已有日记
                    onUpdateDiary(id, content)
                } else {
                    // 新建日记
                    onAddDiary(content)
                }
                showEditDialog = false
            },
        )
    }
}

/** 日记卡片：日期行 + 正文 + AI 回复区 + 删除入口 */
@Composable
private fun DiaryItemCard(
    entry: DiaryEntry,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    StradustCard(
        modifier = Modifier
            .scale(if (isPressed) 0.97f else 1f)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            ) { onClick() },
    ) {
        // 日期 + 心情标签行 + 删除按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Default.CalendarToday,
                contentDescription = null,
                tint = StradustTheme.colors.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = entry.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                color = StradustTheme.colors.textSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(12.dp))
            // 心情 Badge：primaryContainer 底 + primary 字 12sp
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        StradustTheme.colors.primaryContainer.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${entry.moodEmoji} ${entry.mood}",
                    color = StradustTheme.colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.weight(1f))
            // 删除按钮：点击弹出确认对话框
            androidx.compose.material3.IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除日记",
                    tint = StradustTheme.colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // 正文：14sp textPrimary, maxLines 3, lineHeight 22sp
        Text(
            text = entry.content,
            color = StradustTheme.colors.textPrimary,
            fontSize = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(10.dp))

        // AI 回复区：glow*0.08 背景 + AutoAwesome icon(14dp tertiary) + 摘要(12sp secondary)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(
                    StradustTheme.colors.glow.copy(alpha = 0.08f),
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = StradustTheme.colors.tertiary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = entry.aiReplySummary,
                color = StradustTheme.colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    // 删除确认对话框
    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { androidx.compose.material3.Text("删除日记") },
            text = {
                androidx.compose.material3.Text(
                    text = "确定删除 ${entry.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))} 的日记吗？此操作不可撤销。",
                    color = StradustTheme.colors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    androidx.compose.material3.Text(
                        text = "删除",
                        color = StradustTheme.colors.error,
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    androidx.compose.material3.Text("取消")
                }
            },
        )
    }
}

/**
 * 日记编辑对话框：新建 / 编辑共用
 * - 多行文本输入框（maxLines=10）
 * - 心情选择（emoji 按钮）
 * - 保存 / 取消按钮
 */
@Composable
private fun DiaryEditDialog(
    initialContent: String,
    isEdit: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var content by remember { mutableStateOf(initialContent) }
    // 心情 emoji 列表（与 DiaryManager 中 moodEmoji 保持一致）
    val moodOptions = listOf("😊", "🥰", "😢", "🤩", "😌", "🌙")
    var selectedMood by remember { mutableStateOf(moodOptions.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) "编辑日记" else "写日记",
                fontWeight = FontWeight.SemiBold,
                color = StradustTheme.colors.textPrimary,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 心情选择行
                Text(
                    text = "心情",
                    fontSize = 13.sp,
                    color = StradustTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    moodOptions.forEach { emoji ->
                        val isSelected = emoji == selectedMood
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) StradustTheme.colors.primaryContainer
                                    else StradustTheme.colors.surfaceContainerLow.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { selectedMood = emoji },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = emoji, fontSize = 18.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 多行文本输入框
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    placeholder = {
                        Text(
                            text = "写下今天的故事…",
                            color = StradustTheme.colors.textMuted,
                        )
                    },
                    maxLines = 10,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions.Default,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StradustTheme.colors.primary,
                        unfocusedBorderColor = StradustTheme.colors.surfaceContainerLow,
                        focusedContainerColor = StradustTheme.colors.surface,
                        unfocusedContainerColor = StradustTheme.colors.surface,
                        cursorColor = StradustTheme.colors.primary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(content) },
                enabled = content.isNotBlank(),
            ) {
                Text(
                    text = "保存",
                    color = if (content.isNotBlank()) StradustTheme.colors.primary
                    else StradustTheme.colors.textMuted,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
                    color = StradustTheme.colors.textSecondary,
                )
            }
        },
    )
}
