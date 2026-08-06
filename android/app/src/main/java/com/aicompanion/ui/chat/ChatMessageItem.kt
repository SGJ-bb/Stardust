package com.aicompanion.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.ChatMessage
import com.aicompanion.voice.TtsManager
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.foundation.clickable

/**
 * 全局语音播放状态控制器。
 * 由 MainActivity 初始化（设置 TtsManager 引用），Compose 层观察 playingPath 状态。
 * 支持：自动播放（triggerTtsAndPlay）+ 手动点击播放/暂停。
 */
object VoicePlaybackController {
    private const val TAG = "VoicePlaybackCtrl"

    private var ttsManager: TtsManager? = null
    private val _playingPath = MutableStateFlow<String?>(null)
    val playingPath: StateFlow<String?> = _playingPath.asStateFlow()

    /** MainActivity 在 TtsManager 初始化后调用 */
    fun init(tm: TtsManager) {
        ttsManager = tm
    }

    /** 标记当前正在播放的音频路径（由 triggerTtsAndPlay 自动播放时调用） */
    fun setPlaying(path: String?) {
        _playingPath.value = path
    }

    /** 判断指定音频是否正在播放 */
    fun isPlaying(audioPath: String?, audioUrl: String?): Boolean {
        val current = _playingPath.value ?: return false
        return current == audioPath || current == audioUrl
    }

    /** 用户点击语音气泡：切换播放/暂停 */
    fun togglePlay(audioPath: String?, audioUrl: String?) {
        val tm = ttsManager ?: return
        val playKey = audioPath ?: audioUrl ?: return

        if (_playingPath.value == playKey) {
            // 正在播放，停止
            tm.stopPlayback()
            _playingPath.value = null
        } else {
            // 开始播放
            _playingPath.value = playKey
            tm.playAudio(audioPath, audioUrl) {
                _playingPath.value = null
            }
        }
    }

    /** 停止所有播放 */
    fun stop() {
        ttsManager?.stopPlayback()
        _playingPath.value = null
    }
}

/**
 * 星尘聊天消息项组件
 *
 * 支持：
 * - 用户/AI 双气泡布局（右/左对齐）
 * - 用户气泡：渐变背景 + 白色文字 + 圆角[16,4,16,16]
 * - AI气泡：aiBubble 底色 + aiBubbleText 文字 + 头像 + 圆角[4,16,16,16]
 * - 图片消息（Coil AsyncImage，圆角 12dp，最大 200dp 宽）
 * - 部分消息（isPartial）末尾闪烁光标
 * - 长按复制到剪贴板
 * - AI消息反馈行（点赞/踩/收藏）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    aiName: String = "星尘",
    aiAvatarPath: String? = null,
    onFavoriteToggle: ((ChatMessage) -> Unit)? = null,
    onFeedback: ((ChatMessage, Int) -> Unit)? = null,
    /** 长按消息触发引用回复 */
    onReplyClick: ((ChatMessage) -> Unit)? = null,
    /** 重新生成AI回复 */
    onRegenerate: ((ChatMessage) -> Unit)? = null,
    /** 编辑用户消息并重发 */
    onEditAndResend: ((ChatMessage, String) -> Unit)? = null,
    /** 删除单条消息 */
    onDeleteMessage: ((ChatMessage) -> Unit)? = null,
    /** 是否播放入场动画（仅新出现的 AI 消息为 true，避免滚动时重复播放） */
    animateEntrance: Boolean = false,
) {
    // 入场动画：淡入 + 从下方滑入，仅对新消息播放一次
    // 使用 graphicsLayer + Animatable 而非 AnimatedVisibility，
    // 避免在 LazyColumn 滚动回收时重复触发动画导致卡顿
    val entranceAlpha = remember { Animatable(if (animateEntrance) 0f else 1f) }
    val slideFraction = remember { Animatable(if (animateEntrance) 1f else 0f) }
    val density = LocalDensity.current
    val maxSlidePx = with(density) { 20.dp.toPx() }

    LaunchedEffect(Unit) {
        if (animateEntrance) {
            launch { entranceAlpha.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing)) }
            launch { slideFraction.animateTo(0f, tween(durationMillis = 400, easing = LinearOutSlowInEasing)) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entranceAlpha.value
                translationY = maxSlidePx * slideFraction.value
            },
    ) {
        if (message.isUser) {
            UserBubble(
                message = message,
                aiName = aiName,
                onReplyClick = onReplyClick,
                onEditAndResend = onEditAndResend,
                onDeleteMessage = onDeleteMessage,
            )
        } else {
            AiBubble(
                message = message,
                aiName = aiName,
                aiAvatarPath = aiAvatarPath,
                onFavoriteToggle = onFavoriteToggle,
                onFeedback = onFeedback,
                onReplyClick = onReplyClick,
                onRegenerate = onRegenerate,
                onDeleteMessage = onDeleteMessage,
            )
        }
    }
}

/**
 * 用户消息气泡（右侧，渐变背景）
 * 圆角 [topStart=16, topEnd=4, bottomEnd=16, bottomStart=16]
 * 长按显示菜单：引用、编辑重发、复制、删除
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(
    message: ChatMessage,
    aiName: String = "星尘",
    onReplyClick: ((ChatMessage) -> Unit)? = null,
    onEditAndResend: ((ChatMessage, String) -> Unit)? = null,
    onDeleteMessage: ((ChatMessage) -> Unit)? = null,
) {
    val colors = StradustTheme.colors
    val bubbleGradient = remember(colors) {
        Brush.horizontalGradient(colors = listOf(colors.userBubbleStart, colors.userBubbleEnd))
    }
    val clipboardManager = LocalClipboardManager.current
    var showCopiedHint by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(message.text) }
    
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
                    .animateContentSize()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true },
                    )
                    .clip(bubbleShape)
                    .drawBehind {
                        drawRoundRect(
                            brush = bubbleGradient,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Column {
                    if (message.replyTo != null) {
                        QuotePreviewBar(quotedMessage = message.replyTo!!, isUserBubble = true, aiName = aiName)
                        Spacer(Modifier.height(6.dp))
                    }
                    MessageContent(message = message, isUser = true)
                }
                
                // 长按菜单
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("引用回复") },
                        leadingIcon = { Icon(Icons.Default.Reply, null) },
                        onClick = {
                            showMenu = false
                            onReplyClick?.invoke(message)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("编辑重发") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = {
                            showMenu = false
                            editText = message.text
                            showEditDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("复制") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = {
                            showMenu = false
                            clipboardManager.setText(AnnotatedString(message.text))
                            showCopiedHint = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = colors.error) },
                        onClick = {
                            showMenu = false
                            onDeleteMessage?.invoke(message)
                        },
                    )
                }
            }
            
            // 编辑重发对话框
            if (showEditDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showEditDialog = false },
                    title = { Text("编辑消息") },
                    text = {
                        androidx.compose.material3.TextField(
                            value = editText,
                            onValueChange = { editText = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showEditDialog = false
                                if (editText.isNotBlank() && editText != message.text) {
                                    onEditAndResend?.invoke(message, editText)
                                }
                            },
                        ) { Text("重发") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showEditDialog = false },
                        ) { Text("取消") }
                    },
                )
            }
            
            Text(
                text = if (showCopiedHint) "已复制" else message.time,
                color = colors.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp),
            )
        }
    }
}

/**
 * AI消息气泡（左侧，带头像）
 * 圆角 [topStart=4, topEnd=16, bottomEnd=16, bottomStart=16]
 * 长按显示菜单：引用、重新生成、复制、删除
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiBubble(
    message: ChatMessage,
    aiName: String = "星尘",
    aiAvatarPath: String? = null,
    onFavoriteToggle: ((ChatMessage) -> Unit)? = null,
    onFeedback: ((ChatMessage, Int) -> Unit)? = null,
    onReplyClick: ((ChatMessage) -> Unit)? = null,
    onRegenerate: ((ChatMessage) -> Unit)? = null,
    onDeleteMessage: ((ChatMessage) -> Unit)? = null,
) {
    val colors = StradustTheme.colors
    val clipboardManager = LocalClipboardManager.current
    var showCopiedHint by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    LaunchedEffect(showCopiedHint) {
        if (showCopiedHint) {
            kotlinx.coroutines.delay(2000)
            showCopiedHint = false
        }
    }
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
        // AI头像（32dp 圆形：有图片用 AsyncImage，否则 primaryContainer 底 + 首字母）
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (!aiAvatarPath.isNullOrBlank()) {
                AsyncImage(
                    model = aiAvatarPath,
                    contentDescription = "AI头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = aiName.firstOrNull()?.toString() ?: "星",
                    color = colors.onPrimaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Column {
            Box(
                modifier = Modifier
                    .animateContentSize()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true },
                    )
                    .clip(bubbleShape)
                    .background(colors.aiBubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column {
                    if (message.replyTo != null) {
                        QuotePreviewBar(quotedMessage = message.replyTo!!, isUserBubble = false, aiName = aiName)
                        Spacer(Modifier.height(6.dp))
                    }
                    MessageContent(message = message, isUser = false)
                    // 语音播放气泡（仅当 AI 消息有音频时显示）
                    val hasAudio = !message.audioPath.isNullOrBlank() || !message.audioUrl.isNullOrBlank()
                    if (hasAudio) {
                        Spacer(Modifier.height(6.dp))
                        VoiceBubble(message = message)
                    }
                }
                
                // 长按菜单
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("引用回复") },
                        leadingIcon = { Icon(Icons.Default.Reply, null) },
                        onClick = {
                            showMenu = false
                            onReplyClick?.invoke(message)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("重新生成") },
                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                        onClick = {
                            showMenu = false
                            onRegenerate?.invoke(message)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("复制") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = {
                            showMenu = false
                            clipboardManager.setText(AnnotatedString(message.text))
                            showCopiedHint = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = colors.error) },
                        onClick = {
                            showMenu = false
                            onDeleteMessage?.invoke(message)
                        },
                    )
                }
            }

            // 反馈行：时间 + 收藏 + 点赞/踩
            Row(
                modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (showCopiedHint) "已复制" else message.time,
                    color = colors.textMuted,
                    fontSize = 12.sp,
                )

                Spacer(modifier = Modifier.width(12.dp))

                FavoriteButton(
                    isFavorited = message.isFavorited,
                    onClick = { onFavoriteToggle?.invoke(message) },
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 点赞/踩反馈
                FeedbackIcons(
                    feedback = message.feedback,
                    onThumbUp = { onFeedback?.invoke(message, 1) },
                    onThumbDown = { onFeedback?.invoke(message, -1) },
                )
            }
        }
    }
}

/**
 * 消息内容区域：文本 / 图片 / 贴纸
 */
@Composable
private fun MessageContent(message: ChatMessage, isUser: Boolean) {
    when {
        // 贴纸
        message.stickerPath != null -> {
            AsyncImage(
                model = message.stickerPath,
                contentDescription = "贴纸",
                modifier = Modifier.size(120.dp),
                contentScale = ContentScale.Fit,
            )
        }
        // AI生成图片
        message.generatedImagePath != null -> {
            AsyncImage(
                model = message.generatedImagePath,
                contentDescription = "生成图片",
                modifier = Modifier
                    .width(200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        // 图片列表
        !message.imageUrls.isNullOrEmpty() -> {
            ImageListColumn(imageUrls = message.imageUrls)
        }
        // 文本消息（默认）
        else -> {
            TextWithCursor(message = message, isUser = isUser)
        }
    }
}

/**
 * 带闪烁光标的文本（用于部分消息 isPartial）
 * 光标 "|" 独立闪烁，文本保持稳定
 */
@Composable
private fun TextWithCursor(message: ChatMessage, isUser: Boolean) {
    val colors = StradustTheme.colors
    val textColor = if (isUser) colors.onPrimary else colors.aiBubbleText

    if (message.isPartial && message.text.isNotEmpty()) {
        val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
        val cursorAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "cursor_alpha",
        )

        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = message.text,
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Text(
                text = "|",
                color = textColor.copy(alpha = cursorAlpha),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    } else {
        Text(
            text = message.text,
            color = textColor,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

/**
 * 图片列表展示（最大 200dp 宽，圆角 12dp）
 */
@Composable
private fun ImageListColumn(imageUrls: List<String>) {
    val colors = StradustTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        imageUrls.take(3).forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = "聊天图片",
                modifier = Modifier
                    .width(200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        if (imageUrls.size > 3) {
            Text(
                text = "+${imageUrls.size - 3} 张图片",
                color = colors.textSecondary,
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * 收藏按钮（isFavorited 时显示 Favorite icon，tertiary 色）
 */
@Composable
private fun FavoriteButton(
    isFavorited: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            imageVector = if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (isFavorited) "取消收藏" else "收藏",
            tint = if (isFavorited) StradustTheme.colors.tertiary else StradustTheme.colors.textMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * 点赞/踩反馈图标
 * feedback==1 时 ThumbUp 用 tertiary 色，feedback==-1 时 ThumbDown 用 error 色
 */
@Composable
private fun FeedbackIcons(
    feedback: Int,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
) {
    val colors = StradustTheme.colors
    Row {
        IconButton(onClick = onThumbUp, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.ThumbUp,
                contentDescription = "点赞",
                tint = if (feedback == 1) colors.tertiary else colors.textMuted,
                modifier = Modifier.size(15.dp),
            )
        }
        IconButton(onClick = onThumbDown, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.ThumbDown,
                contentDescription = "踩",
                tint = if (feedback == -1) colors.error else colors.textMuted,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * 引用消息预览条（显示在气泡内部顶部）
 */
@Composable
private fun QuotePreviewBar(
    quotedMessage: ChatMessage,
    isUserBubble: Boolean,
    aiName: String = "星尘",
) {
    val colors = StradustTheme.colors
    val barColor = if (isUserBubble) colors.onPrimary.copy(alpha = 0.6f) else colors.primary.copy(alpha = 0.6f)
    val textColor = if (isUserBubble) colors.onPrimary.copy(alpha = 0.7f) else colors.primary
    val subColor = if (isUserBubble) colors.onPrimary.copy(alpha = 0.5f) else colors.textMuted

    Row(
        modifier = Modifier.width(220.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧引用竖线
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(40.dp)
                .background(barColor, RoundedCornerShape(1.5.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val senderName = if (quotedMessage.isUser) "我" else aiName
            val previewText = quotedMessage.text.let { if (it.length <= 60) it else it.take(57) + "..." }
            Text(text = senderName, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = previewText, color = subColor, fontSize = 11.sp, maxLines = 2, lineHeight = 15.sp)
        }
    }
}

/**
 * 语音消息气泡 — 类似 QQ 语音消息的播放控件。
 * 当 AI 消息有 audioPath/audioUrl 时显示，点击播放/暂停，播放时显示波形动画。
 */
@Composable
private fun VoiceBubble(message: ChatMessage) {
    val colors = StradustTheme.colors
    val playingPath by VoicePlaybackController.playingPath.collectAsState()
    val isPlaying = playingPath == message.audioPath || playingPath == message.audioUrl

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.primaryContainer.copy(alpha = 0.35f))
            .clickable { VoicePlaybackController.togglePlay(message.audioPath, message.audioUrl) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放语音",
            tint = colors.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        if (isPlaying) {
            // 播放中：显示波形动画
            VoiceWaveAnimation(barColor = colors.primary)
        } else {
            // 未播放：显示语音图标 + "语音消息"
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "语音消息",
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * 语音波形动画 — 3 条高度变化的竖条，模拟 QQ 语音播放效果。
 */
@Composable
private fun VoiceWaveAnimation(barColor: androidx.compose.ui.graphics.Color) {
    val transition = rememberInfiniteTransition(label = "voice_wave")
    val waves = listOf(0, 1, 2).map { index ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 350, delayMillis = index * 80),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "wave_$index",
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        waves.forEach { wave ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((10 * wave.value + 4).dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor),
            )
        }
    }
}
