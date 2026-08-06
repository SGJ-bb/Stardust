package com.aicompanion.ui.groupchat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.chat.TypingIndicator
import com.aicompanion.ui.chat.VoiceButton
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar
import com.aicompanion.ui.components.WallpaperBackground
import com.aicompanion.ui.animations.clickScale

/**
 * 群聊消息数据类（Compose 层使用，轻量级）
 *
 * 用于在 Compose UI 中展示群聊消息，
 * 避免直接依赖旧代码中的 Context 相关的 GroupMessage 类
 */
data class GroupMessage(
    val id: String,
    val text: String,
    val senderName: String,
    val isUser: Boolean = false,      // true=当前用户发送, false=其他成员或AI回复
    val isSystem: Boolean = false,     // true=系统消息(入群/退群/系统提示等)
    val time: String = "",
)

/**
 * 群聊成员颜色池（为不同成员分配不同颜色）
 *
 * 这是固定的成员区分色池，不随主题变化，保证多成员可区分性。
 * 主题色不足以区分 6+ 个成员，因此保留固定高区分度色板。
 */
private val MEMBER_COLORS = listOf(
    0xFF9c7cff.toInt(), 0xFF64ffda.toInt(), 0xFF667eea.toInt(),
    0xFFffb347.toInt(), 0xFFe8a0bf.toInt(), 0xFF7fdbda.toInt(),
)

/**
 * 群聊聊天页面（Compose 版本）
 *
 * 替代旧的 GroupChatActivity (AppCompatActivity + RecyclerView)
 * 使用 Jetpack Compose + Material3 实现，遵循项目统一风格
 *
 * 布局结构：
 * - 顶栏：StradustTopBar（群名称 + 成员数 + 返回按钮）
 * - 消息列表：LazyColumn（系统消息 / 用户气泡 / 成员气泡 / 打字指示器）
 * - 输入栏：底部固定（输入框 + 发送按钮 / 语音按钮）
 */
@Composable
fun GroupChatScreen(
    groupId: String = "",
    groupName: String = "未命名群",
    messages: List<GroupMessage> = emptyList(),
    memberNames: List<String> = emptyList(),
    isTyping: Boolean = false,
    onSendMessage: (String) -> Unit = {},
    onStartVoice: () -> Unit = {},
    onStopVoice: () -> Unit = {},
    isRecording: Boolean = false,
    onBackClick: () -> Unit = {},
    /** 群聊设置入口回调（跳转到 GroupChatSettingsScreen） */
    onSettingsClick: () -> Unit = {},
    /** 壁纸图片路径（本地文件路径或URI） */
    wallpaperPath: String? = null,
    /** 当前发言模式：manual/ai_judge/round_robin */
    speakMode: String = "round_robin",
    /** 切换发言模式回调 */
    onSpeakModeChange: (String) -> Unit = {},
    /** 成员 personaId 列表（与 memberNames 一一对应，用于手动选择） */
    memberPersonaIds: List<String> = emptyList(),
    /** 手动模式下已选中的成员 personaId 集合 */
    manualSelectedIds: Set<String> = emptySet(),
    /** 手动选择变更回调 */
    onManualSelectionChange: (Set<String>) -> Unit = {},
) {
    val colors = StradustTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    // 群聊信息弹窗
    var showGroupInfo by remember { mutableStateOf(false) }
    // 发言模式选择弹窗
    var showSpeakModeDialog by remember { mutableStateOf(false) }
    // 为每个成员分配固定颜色（按 senderName 缓存）
    var colorIndex by remember { mutableIntStateOf(0) }
    val memberColorMap = remember(memberNames) {
        mutableMapOf<String, Color>().apply {
            memberNames.forEach { name ->
                if (!containsKey(name)) {
                    put(name, Color(MEMBER_COLORS[colorIndex % MEMBER_COLORS.size]))
                    colorIndex++
                }
            }
        }
    }

    // 自动滚动到最新消息
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    WallpaperBackground(wallpaperPath = wallpaperPath) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ===== 顶部栏：返回按钮 + 群名 + 成员数 badge =====
        StradustTopBar(
            title = groupName,
            subtitle = "${memberNames.size} 名成员",
            onBackClick = onBackClick,
            actions = {
                // 发言模式切换入口
                IconButton(onClick = { showSpeakModeDialog = true }) {
                    val modeIcon = when (speakMode) {
                        "manual" -> Icons.Default.TouchApp
                        "ai_judge" -> Icons.Default.Psychology
                        else -> Icons.Default.Repeat
                    }
                    val modeLabel = when (speakMode) {
                        "manual" -> "手动"
                        "ai_judge" -> "AI判定"
                        else -> "轮流"
                    }
                    Icon(
                        modeIcon,
                        contentDescription = "发言模式：$modeLabel",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                // 群聊设置入口
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "群聊设置",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                // 更多操作按钮：打开群聊信息弹窗
                IconButton(onClick = { showGroupInfo = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "群聊信息",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )

        HorizontalDivider(
            color = colors.outlineVariant.copy(alpha = 0.3f),
            thickness = 0.5.dp,
        )

        // ===== 消息列表 =====
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty() && !isTyping) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "暂无消息，发送第一条消息开始对话",
                                color = colors.textMuted,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            items(
                items = messages,
                key = { it.id },
                contentType = { msg ->
                    when {
                        msg.isSystem -> "system"
                        msg.isUser -> "user"
                        else -> "member"
                    }
                },
            ) { msg ->
                when {
                    msg.isSystem -> SystemMessageItem(msg)
                    msg.isUser -> GroupUserBubble(msg)
                    else -> GroupMemberBubble(msg, senderColor = memberColorMap[msg.senderName] ?: Color(MEMBER_COLORS[0]))
                }
            }

            item {
                AnimatedVisibility(visible = isTyping) {
                    Row(
                        modifier = Modifier.padding(start = 48.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TypingIndicator()
                        Text(
                            text = "正在输入...",
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }

        // ===== 手动模式：成员选择条 =====
        if (speakMode == "manual" && memberPersonaIds.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surfaceContainerLow,
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(memberPersonaIds.size) { idx ->
                        val pid = memberPersonaIds[idx]
                        val name = memberNames.getOrNull(idx) ?: "?"
                        val selected = pid in manualSelectedIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val newSet = if (selected) {
                                    manualSelectedIds - pid
                                } else {
                                    manualSelectedIds + pid
                                }
                                onManualSelectionChange(newSet)
                            },
                            label = { Text(name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = colors.onPrimary,
                            ),
                        )
                    }
                }
            }
        }

        // ===== 底部输入栏 =====
        GroupChatInputBar(
            text = text,
            onTextChange = { text = it },
            onSend = {
                if (text.isNotBlank()) {
                    // 手动模式下，如果没选任何成员且消息中没有 @提及，提示用户选择
                    if (speakMode == "manual" && manualSelectedIds.isEmpty() && !text.contains("@")) {
                        // 仍然发送，但后端会处理（无选中成员时所有成员都不说话，只有 @提及会回复）
                    }
                    onSendMessage(text)
                    text = ""
                }
            },
            onStartVoice = onStartVoice,
            onStopVoice = onStopVoice,
            isRecording = isRecording,
        )
    }
    } // WallpaperBackground

    // ===== 发言模式选择弹窗 =====
    if (showSpeakModeDialog) {
        AlertDialog(
            onDismissRequest = { showSpeakModeDialog = false },
            title = { Text("发言模式") },
            text = {
                Column {
                    listOf(
                        Triple("manual", "手动选择", "用户在聊天界面手动选择谁发言"),
                        Triple("ai_judge", "AI判定", "AI根据上下文判断每个成员是否说话"),
                        Triple("round_robin", "轮流发言", "所有成员依次全部发言"),
                    ).forEach { (value, label, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (speakMode == value) colors.primary.copy(alpha = 0.1f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    onSpeakModeChange(value)
                                    showSpeakModeDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = when (value) {
                                "manual" -> Icons.Default.TouchApp
                                "ai_judge" -> Icons.Default.Psychology
                                else -> Icons.Default.Repeat
                            }
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (speakMode == value) colors.primary else colors.textSecondary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = label,
                                    color = if (speakMode == value) colors.primary else colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = desc,
                                    color = colors.textMuted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "提示：任何模式下，消息中 @角色名 都会让对应角色必须回复",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeakModeDialog = false }) { Text("关闭") }
            },
        )
    }

    // ===== 群聊信息弹窗 =====
    if (showGroupInfo) {
        AlertDialog(
            onDismissRequest = { showGroupInfo = false },
            title = { Text("群聊信息") },
            text = {
                Column {
                    Text(
                        text = "群名称：$groupName",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "群成员（${memberNames.size}）",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (memberNames.isEmpty()) {
                        Text(
                            text = "暂无成员",
                            color = colors.textMuted,
                            fontSize = 12.sp,
                        )
                    } else {
                        memberNames.forEach { name ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background((memberColorMap[name] ?: Color(MEMBER_COLORS[0])).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = name.firstOrNull()?.toString() ?: "?",
                                        color = memberColorMap[name] ?: Color(MEMBER_COLORS[0]),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = name,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGroupInfo = false }) { Text("关闭") }
            },
        )
    }
}

// ==================== 子组件 ====================

/**
 * 系统消息项（居中显示，灰色胶囊样式）
 * 例如："xxx 加入了群聊"、"xxx 退出了群聊"
 */
@Composable
private fun SystemMessageItem(msg: GroupMessage) {
    val colors = StradustTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerLow)
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            Text(
                text = msg.text.ifEmpty { "系统消息" },
                color = colors.textMuted,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * 用户消息气泡（右侧对齐，渐变背景）
 * 复用 ChatMessageItem 的渐变气泡样式
 * 圆角 [topStart=16, topEnd=4, bottomEnd=16, bottomStart=16]
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupUserBubble(msg: GroupMessage) {
    val colors = StradustTheme.colors
    val clipboardManager = LocalClipboardManager.current
    var showCopiedHint by remember { mutableStateOf(false) }
    // 复制提示 2 秒后自动消失，避免"已复制"赖死
    LaunchedEffect(showCopiedHint) {
        if (showCopiedHint) {
            kotlinx.coroutines.delay(2000)
            showCopiedHint = false
        }
    }
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 4.dp,
        bottomEnd = 16.dp,
        bottomStart = 16.dp,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .combinedClickable(
                        // 设计说明：单击无操作，仅长按生效（长按触发复制）
                        onClick = {},
                        onLongClick = {
                            clipboardManager.setText(AnnotatedString(msg.text))
                            showCopiedHint = true
                        },
                    )
                    .clip(bubbleShape)
                    .drawBehind {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(colors.userBubbleStart, colors.userBubbleEnd),
                            ),
                            cornerRadius = CornerRadius(16f, 16f),
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = msg.text,
                    color = colors.onPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
            Text(
                text = if (showCopiedHint) "已复制" else msg.time,
                color = colors.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp),
            )
        }
    }
}

/**
 * 群成员/AI 消息气泡（左侧对齐，带头像和发送者名称）
 * 圆角 [topStart=4, topEnd=16, bottomEnd=16, bottomStart=16]
 * 不同成员使用不同的头像颜色
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupMemberBubble(
    msg: GroupMessage,
    senderColor: Color = StradustTheme.colors.primary,
) {
    val colors = StradustTheme.colors
    val clipboardManager = LocalClipboardManager.current
    val bubbleShape = RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 16.dp,
        bottomEnd = 16.dp,
        bottomStart = 16.dp,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 成员头像（32dp 圆形，使用成员专属颜色）
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(senderColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = msg.senderName.firstOrNull()?.toString() ?: "?",
                color = senderColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Column {
            // 发送者名称标签
            Text(
                text = msg.senderName,
                color = senderColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )

            Box(
                modifier = Modifier
                    .combinedClickable(
                        // 设计说明：单击无操作，仅长按生效（长按触发复制）
                        onClick = {},
                        onLongClick = {
                            clipboardManager.setText(AnnotatedString(msg.text))
                        },
                    )
                    .clip(bubbleShape)
                    .background(colors.aiBubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = msg.text,
                    color = colors.aiBubbleText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }

            // 时间戳
            Text(
                text = msg.time,
                color = colors.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp),
            )
        }
    }
}

/**
 * 群聊底部输入工具栏
 *
 * 样式复用 ChatInputBar 布局结构：
 * - 更多按钮 + 表情 + 图片 + 输入框(StradustInput) + 发送/VoiceButton
 */
@Composable
private fun GroupChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    isRecording: Boolean,
) {
    val colors = StradustTheme.colors

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.toolbar,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .imePadding()
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 输入框
                StradustInput(
                    value = text,
                    onValueChange = onTextChange,
                    hint = "输入消息...",
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )

                // 发送按钮 / 语音按钮
                if (isRecording) {
                    VoiceButton(
                        isRecording = true,
                        // 录制中无需 start 回调，留空（VoiceButton 双回调设计）
                        onStartRecord = {},
                        onStopRecord = onStopVoice,
                        modifier = Modifier.size(40.dp),
                    )
                } else if (text.isNotBlank()) {
                    // 发送按钮：圆形 primary 底 + Send icon(white)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.primary)
                            .clickScale(0.92f, onClick = onSend),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = colors.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    VoiceButton(
                        isRecording = false,
                        onStartRecord = onStartVoice,
                        // 未录制时无需 stop 回调，留空（VoiceButton 双回调设计）
                        onStopRecord = {},
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
    }
}
