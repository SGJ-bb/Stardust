package com.aicompanion.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.ChatMessage
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar
import com.aicompanion.ui.animations.clickScale

/**
 * 星尘聊天主界面
 *
 * 布局：
 * - 顶栏：StradustTopBar（标题"星尘" + 副标题"第42天 · ☀️26°C" + 右侧好感度进度条）
 * - 消息列表：LazyColumn（时间分隔线 + 消息气泡 + 打字指示器）
 * - 输入栏：底部固定（更多/图片/表情/输入框/发送或语音）
 * - 功能面板：ModalBottomSheet（更多按钮触发）
 */
@Composable
fun ChatScreen(
    // === 现有参数保持不变 ===
    messages: List<ChatMessage> = emptyList(),
    isTyping: Boolean = false,
    isLoading: Boolean = false,
    onSendMessage: (String) -> Unit = {},
    onStartVoice: () -> Unit = {},
    onStopVoice: () -> Unit = {},
    isRecording: Boolean = false,
    onFeatureClick: (Int) -> Unit = {},
    // === 从 MainActivity 桥接的真实数据 ===
    aiName: String = "星尘",
    aiAvatarPath: String? = null,
    wallpaperPath: String? = null,
    daysTogether: Int = 1,
    affectionInfo: Triple<Int, Int, String> = Triple(0, 100, "Lv.1"),
    weatherInfo: Pair<String, String> = Pair("☀️", "第1天"),
    // === 新增：消息版本号（变化时触发重组） ===
    messageVersion: Int = 0,
    // === 新增：收藏/反馈回调 ===
    onFavoriteToggle: ((ChatMessage) -> Unit)? = null,
    onFeedback: ((ChatMessage, Int) -> Unit)? = null,
    // === 新增：表情/图片/电话/壁纸回调 ===
    onPickSticker: () -> Unit = {},
    onPickImage: () -> Unit = {},
    onPhoneCall: () -> Unit = {},
    onChangeWallpaper: () -> Unit = {},
    // === 新增：引用回复回调 ===
    /** 长按消息触发引用回复 */
    onReplyClick: ((ChatMessage) -> Unit)? = null,
    /** 重新生成AI回复（删除最后一条AI消息并重新请求） */
    onRegenerate: ((ChatMessage) -> Unit)? = null,
    /** 编辑用户消息并重发（编辑消息内容后重新发送） */
    onEditAndResend: ((ChatMessage, String) -> Unit)? = null,
    /** 删除单条消息 */
    onDeleteMessage: ((ChatMessage) -> Unit)? = null,
    // === 新增：通用导航回调（收藏夹/日记搜索等） ===
    onNavigate: (String) -> Unit = {},
    // === Live2D 桥接 ===
    /** Live2D 是否启用 */
    live2dEnabled: Boolean = false,
    /** 获取 Live2D 视图（懒创建） */
    live2dViewProvider: () -> android.view.View? = { null },
    /** 进入聊天页时恢复 Live2D */
    onLive2dResume: () -> Unit = {},
    /** 离开聊天页时暂停 Live2D */
    onLive2dPause: () -> Unit = {},
    // === AI 预测回复 ===
    /** AI 预测的用户可能回复列表 */
    predictions: List<String> = emptyList(),
    /** 点击预测回复时回调（传入预测文本） */
    onPredictionClick: (String) -> Unit = {},
) {
    val colors = StradustTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    var showFeaturePanel by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // 引用回复状态：当前正在回复的消息（null 表示未在回复）
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }

    // 记录已播放入场动画的消息 ID，避免 LazyColumn 滚动回收时重复播放
    val animatedMessageIds = remember { mutableStateOf(emptySet<String>()) }

    // 记录 LazyColumn 上一次的高度（px），用于检测键盘弹出（高度变小时滚动到底部）
    var previousListHeight by remember { mutableStateOf(0) }

    // 从桥接数据解构真实信息
    val (affectionCurrent, affectionMax, affectionLevel) = affectionInfo
    val (weatherIcon, daysLabel) = weatherInfo
    val affectionPercent = if (affectionMax > 0) affectionCurrent.toFloat() / affectionMax else 0f

    // 智能滚动：只有当用户已经在底部附近时才自动滚动，避免打断阅读
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= messages.lastIndex - 1
        }
    }
    // 首次进入或切换角色时直接跳到底部（无动画，避免用户需要手动下滑）
    val firstMessageId = messages.firstOrNull()?.id
    LaunchedEffect(firstMessageId) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }
    // 后续新消息到达时智能滚动（只在用户已在底部时）
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && isAtBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // 壁纸层：有壁纸时全屏覆盖
        if (!wallpaperPath.isNullOrBlank()) {
            AsyncImage(
                model = wallpaperPath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // 半透明遮罩：确保文字在任何壁纸上都清晰可读
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = 0.6f)),
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // ===== 顶部状态栏 =====
            StradustTopBar(
                title = aiName,
                subtitle = "$daysLabel · $weatherIcon",
                actions = {
                    // 收藏夹入口
                    IconButton(onClick = { onNavigate("favorites") }) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "收藏夹",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    // 日记搜索入口
                    IconButton(onClick = { onNavigate("diary_search") }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "日记搜索",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    // 好感度进度条（40dp 宽）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "❤️", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        LinearProgressIndicator(
                            progress = { affectionPercent },
                            modifier = Modifier
                                .width(40.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = colors.primary,
                            trackColor = colors.primary.copy(alpha = 0.2f),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${(affectionPercent * 100).toInt()}%",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
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
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        // 高度变小（键盘弹出导致 adjustResize 缩小窗口），滚动到最后一条消息
                        if (previousListHeight > 0 && size.height < previousListHeight && messages.isNotEmpty()) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(messages.lastIndex)
                            }
                        }
                        previousListHeight = size.height
                    },
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "发送第一条消息开始对话",
                                color = colors.textMuted,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = messages,
                    key = { _, msg -> msg.id },
                    contentType = { _, msg -> if (msg.isUser) "user_msg" else "ai_msg" },
                ) { index, message ->
                    // 仅对新的 AI 消息播放入场动画，已动画过的不再重复
                    val shouldAnimateEntrance = !message.isUser && message.id !in animatedMessageIds.value
                    if (shouldAnimateEntrance) {
                        LaunchedEffect(message.id) {
                            animatedMessageIds.value += message.id
                        }
                    }

                    if (index == 0 || shouldShowTimestamp(index, messages)) {
                        TimeStampLabel(time = message.time)
                    }
                    ChatMessageItem(
                        message = message,
                        modifier = Modifier,
                        animateEntrance = shouldAnimateEntrance,
                        aiName = aiName,
                        aiAvatarPath = aiAvatarPath,
                        onFavoriteToggle = onFavoriteToggle,
                        onFeedback = onFeedback,
                        onReplyClick = { msg ->
                            replyingTo = msg
                            onReplyClick?.invoke(msg)
                        },
                        onRegenerate = onRegenerate,
                        onEditAndResend = onEditAndResend,
                        onDeleteMessage = onDeleteMessage,
                    )
                }
            }

                item {
                    AnimatedVisibility(
                        visible = isTyping,
                        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
                        exit = fadeOut(tween(100)),
                    ) {
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

                item {
                    AnimatedVisibility(
                        visible = isLoading,
                        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
                        exit = fadeOut(tween(100)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = colors.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            // ===== 输入工具栏 =====
            // adjustNothing 模式下，外层 Box 用 imePadding() 处理键盘避让，
            // 输入栏自然贴着键盘上方；底部菜单叠加在窗口底部，被键盘遮挡（不跟随上移）。
            Column(modifier = Modifier.fillMaxWidth()) {
            // 引用回复预览条（显示在输入栏上方）
            if (replyingTo != null) {
                val onJumpToMessage: (String) -> Unit = { messageId ->
                    val index = messages.indexOfFirst { it.id == messageId }
                    if (index >= 0) {
                        coroutineScope.launch { listState.animateScrollToItem(index) }
                    }
                }
                ReplyPreviewBar(
                    quotedMessage = replyingTo!!,
                    aiName = aiName,
                    onDismiss = { replyingTo = null },
                    onJumpToMessage = onJumpToMessage,
                )
            }

            // AI 预测回复条（显示在输入栏上方）
            // 稳定化 onClick lambda，避免每次重组都创建新实例
            if (predictions.isNotEmpty()) {
                val stablePredictionClick = remember {
                    { pred: String ->
                        text = pred
                    }
                }
                PredictionBar(
                    predictions = predictions,
                    onClick = stablePredictionClick,
                )
            }

            QqStyleInputBar(
                text = text,
                onTextChange = { text = it },
                onSend = {
                    if (text.isNotBlank()) {
                        // 发送时携带引用回复信息
                        onSendMessage(text)
                        text = ""
                        // 发送后清除引用状态
                        replyingTo = null
                    }
                },
                onStartVoice = onStartVoice,
                onStopVoice = onStopVoice,
                isRecording = isRecording,
                onPickSticker = onPickSticker,
                onPickImage = onPickImage,
                onPhoneCall = onPhoneCall,
                onChangeWallpaper = onChangeWallpaper,
                onMoreClick = { showFeaturePanel = true },
            )
            }
        }

        // ===== Live2D 模型层（在气泡上方，长按可拖动） =====
        // 默认屏幕中间，长按 100ms 进入拖动模式（Live2DCoordinator.setupTouch 实现）
        if (live2dEnabled) {
            Live2DLayer(
                viewProvider = live2dViewProvider,
                onResume = onLive2dResume,
                onPause = onLive2dPause,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(),
            )
        }

        FeaturePanel(
            show = showFeaturePanel,
            onDismiss = { showFeaturePanel = false },
            onFeatureClick = { index ->
                showFeaturePanel = false
                onFeatureClick(index)
            },
        )
    }
}

/** 时间戳分隔标签（居中胶囊） */
@Composable
private fun TimeStampLabel(time: String, modifier: Modifier = Modifier) {
    val colors = StradustTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerLow)
                .padding(horizontal = 12.dp, vertical = 3.dp),
        ) {
            Text(text = time, color = colors.textMuted, fontSize = 12.sp)
        }
    }
}

/**
 * AI 预测回复条
 *
 * 在输入栏上方横向滚动展示 AI 预测的用户可能回复，点击即填入输入框并发送
 */
@Composable
private fun PredictionBar(
    predictions: List<String>,
    onClick: (String) -> Unit,
) {
    val colors = StradustTheme.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(predictions, key = { it }) { pred ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainerHigh)
                        .clickable { onClick(pred) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = pred,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * QQ 风格底部输入栏
 *
 * 布局：加号按钮 + 输入框 + 表情按钮 + 发送/语音按钮
 * 加号按钮点击后展开面板，包含：图片、电话、壁纸、功能面板入口
 */
@Composable
private fun QqStyleInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    isRecording: Boolean,
    onPickSticker: () -> Unit = {},
    onPickImage: () -> Unit = {},
    onPhoneCall: () -> Unit = {},
    onChangeWallpaper: () -> Unit = {},
    onMoreClick: () -> Unit = {},
) {
    val colors = StradustTheme.colors
    // 传统模式下系统 adjustResize 已处理键盘和导航栏，无需手动 insets padding
    // 加号面板展开状态
    var showPlusPanel by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

        // ===== 加号展开面板 =====
        // 稳定化 PlusExpandPanel 的回调 lambda，避免每次重组都创建新实例
        val currentOnPickImage by rememberUpdatedState(onPickImage)
        val currentOnPhoneCall by rememberUpdatedState(onPhoneCall)
        val currentOnChangeWallpaper by rememberUpdatedState(onChangeWallpaper)
        val currentOnMoreClick by rememberUpdatedState(onMoreClick)

        val stableOnPickImage = remember {
            {
                showPlusPanel = false
                currentOnPickImage()
            }
        }
        val stableOnPhoneCall = remember {
            {
                showPlusPanel = false
                currentOnPhoneCall()
            }
        }
        val stableOnChangeWallpaper = remember {
            {
                showPlusPanel = false
                currentOnChangeWallpaper()
            }
        }
        val stableOnMoreClick = remember {
            {
                showPlusPanel = false
                currentOnMoreClick()
            }
        }

        AnimatedVisibility(visible = showPlusPanel) {
            PlusExpandPanel(
                onPickImage = stableOnPickImage,
                onPhoneCall = stableOnPhoneCall,
                onChangeWallpaper = stableOnChangeWallpaper,
                onMoreClick = stableOnMoreClick,
            )
        }

        // ===== 输入栏主体 =====
        // 传统模式（无 enableEdgeToEdge）下系统已避开导航栏，不能再加 navigationBarsPadding，
        // 否则双重 padding 导致键盘与输入栏之间出现间隙（=导航栏高度）
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.toolbar,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧：加号按钮
                IconButton(
                    onClick = { showPlusPanel = !showPlusPanel },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "更多功能",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                // 输入框
                StradustInput(
                    value = text,
                    onValueChange = onTextChange,
                    hint = "输入消息...",
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    onSend = onSend,
                )

                // 表情按钮
                IconButton(onClick = onPickSticker, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.EmojiEmotions, contentDescription = "表情", modifier = Modifier.size(24.dp))
                }

                // 发送按钮 / 语音按钮
                if (isRecording) {
                    VoiceButton(
                        isRecording = true,
                        onStartRecord = {},
                        onStopRecord = onStopVoice,
                        modifier = Modifier.size(40.dp),
                    )
                } else if (text.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.primary)
                            .clickScale(0.92f, onClick = onSend),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "发送", tint = colors.onPrimary, modifier = Modifier.size(18.dp))
                    }
                } else {
                    VoiceButton(
                        isRecording = false,
                        onStartRecord = onStartVoice,
                        onStopRecord = {},
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
    }
}

/**
 * 加号展开面板（仿 QQ）
 *
 * 4 列网格：图片、电话、壁纸、更多功能
 */
@Composable
private fun PlusExpandPanel(
    onPickImage: () -> Unit,
    onPhoneCall: () -> Unit,
    onChangeWallpaper: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val colors = StradustTheme.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PlusPanelItem(
                icon = Icons.Default.Image,
                label = "图片",
                onClick = onPickImage,
            )
            PlusPanelItem(
                icon = Icons.Default.Phone,
                label = "通话",
                onClick = onPhoneCall,
            )
            PlusPanelItem(
                icon = Icons.Default.Brush,
                label = "壁纸",
                onClick = onChangeWallpaper,
            )
            PlusPanelItem(
                icon = Icons.Default.Widgets,
                label = "更多",
                onClick = onMoreClick,
            )
        }
    }
}

/** 加号面板单项 */
@Composable
private fun PlusPanelItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = StradustTheme.colors

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colors.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 11.sp,
        )
    }
}

/** 判断是否需要显示时间戳（间隔>5分钟） */
private fun shouldShowTimestamp(currentIndex: Int, messages: List<ChatMessage>): Boolean {
    if (currentIndex <= 0) return false
    val current = messages[currentIndex]
    val previous = messages[currentIndex - 1]
    val fiveMinutesMs = 5 * 60 * 1000L
    return (current.timestamp - previous.timestamp) > fiveMinutesMs
}

/**
 * 输入栏上方的引用回复预览条
 *
 * 布局：左侧竖线(primary) + 发送者名 + 消息截断 + 右侧关闭按钮
 * 显示时替代输入栏顶部分隔线，点击关闭按钮取消回复
 */
@Composable
private fun ReplyPreviewBar(
    quotedMessage: ChatMessage,
    onDismiss: () -> Unit,
    aiName: String = "星尘",
    onJumpToMessage: (String) -> Unit = {},
) {
    val colors = StradustTheme.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧引用竖线
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(colors.primary, RoundedCornerShape(1.5.dp)),
            )

            Spacer(Modifier.width(10.dp))

            // 引用内容（可点击跳转到原消息）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onJumpToMessage(quotedMessage.id) },
            ) {
                Text(
                    text = "回复 ${if (quotedMessage.isUser) "我自己" else aiName}",
                    color = colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = quotedMessage.text
                        .takeIf { it.length <= 50 } ?: (quotedMessage.text.take(47) + "..."),
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }

            // 关闭按钮
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消回复",
                    tint = colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    // 底部分隔线
    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
}

/**
 * Live2D 模型显示层
 *
 * 使用 AndroidView 将 Live2DWebView 嵌入 Compose 层级。
 * - 进入聊天页时恢复渲染
 * - 离开聊天页时暂停渲染（不销毁视图，保留状态）
 * - 视图由 AppHost.getLive2DView() 提供（懒创建，单例）
 */
@Composable
private fun Live2DLayer(
    viewProvider: () -> android.view.View?,
    onResume: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnResume by rememberUpdatedState(onResume)
    val currentOnPause by rememberUpdatedState(onPause)
    val currentViewProvider by rememberUpdatedState(viewProvider)

    // 进入聊天页：恢复 Live2D
    androidx.compose.runtime.DisposableEffect(Unit) {
        currentOnResume()
        onDispose {
            currentOnPause()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            // 从 AppHost 获取 Live2DWebView（懒创建）
            val view = currentViewProvider() ?: android.view.View(context)
            // 如果视图已有父级（从上次组合残留），先移除
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            // 禁用裁剪，让 Live2D 模型可以超出边界显示（缩放/拖动时不被截断）
            (view as? android.view.ViewGroup)?.clipChildren = false
            (view as? android.view.ViewGroup)?.clipToPadding = false
            view.clipToOutline = false
            // 视图被添加到父级后，向上遍历父链禁用所有父 ViewGroup 的裁剪
            // 否则 AndroidComposeView 等中间容器会裁剪超出边界的子视图
            view.post {
                var parent = view.parent
                while (parent is android.view.ViewGroup) {
                    parent.clipChildren = false
                    parent.clipToPadding = false
                    parent = parent.parent
                }
            }
            view
        },
        update = { view ->
            // 确保视图可见
            view.visibility = android.view.View.VISIBLE
            // 再次确保裁剪被禁用（视图可能被重新附加）
            (view as? android.view.ViewGroup)?.clipChildren = false
            (view as? android.view.ViewGroup)?.clipToPadding = false
            // 再次确保父链不裁剪
            var parent = view.parent
            while (parent is android.view.ViewGroup) {
                val group = parent as android.view.ViewGroup
                group.clipChildren = false
                group.clipToPadding = false
                parent = group.parent
            }
        },
    )
}
