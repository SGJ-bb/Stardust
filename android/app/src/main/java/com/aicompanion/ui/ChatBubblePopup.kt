package com.aicompanion.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

// ============================================================
// QQ 风格消息弹窗 — Compose 实现
// 原理：StateFlow 由 MainActivity 触发，Compose 层观察并渲染 Popup
// 优势：不依赖传统 View 树，避免被 ComposeView 遮挡，生命周期自动跟随 Activity
// ============================================================

/** 弹窗数据 */
data class ChatBubbleData(
    val senderName: String,
    val message: String,
    val avatarPath: String?,
    val timestamp: Long = System.currentTimeMillis(),
)

/** 全局控制器：MainActivity 调用 show()，Compose 层观察 state */
object ChatBubbleController {
    private const val TAG = "ChatBubbleController"
    private const val AUTO_DISMISS_MS = 4000L
    private const val MAX_TEXT_LENGTH = 120

    private val _state = MutableStateFlow<ChatBubbleData?>(null)
    val state: StateFlow<ChatBubbleData?> = _state.asStateFlow()

    fun show(senderName: String, message: String, avatarPath: String? = null) {
        AppLogger.i(TAG, "show: sender=$senderName, msgLen=${message.length}")
        _state.value = ChatBubbleData(
            senderName = senderName,
            message = if (message.length > MAX_TEXT_LENGTH) message.take(MAX_TEXT_LENGTH) + "…" else message,
            avatarPath = avatarPath?.takeIf { it.isNotBlank() },
        )
    }

    fun dismiss() {
        _state.value = null
    }
}

/**
 * 向后兼容包装类。
 * 保留原 API：chatBubblePopup?.show(...) / cleanup()
 * 内部委托给 ChatBubbleController 的 StateFlow，由 Compose 层 ChatBubbleOverlay 观察渲染。
 */
class ChatBubblePopup(context: Context) {
    fun show(senderName: String, message: String, avatarPath: String? = null) {
        ChatBubbleController.show(senderName, message, avatarPath)
    }

    fun dismiss() = ChatBubbleController.dismiss()
    fun cleanup() = ChatBubbleController.dismiss()
}

/**
 * Compose 弹窗层 — 放置在 setContent 顶层，覆盖在所有页面之上。
 * 观察ChatBubbleController.state，非空时显示 Popup，4 秒后自动消失。
 */
@Composable
fun ChatBubbleOverlay() {
    val state by ChatBubbleController.state.collectAsState()
    val bubble = state ?: return

    // 4 秒后自动消失（timestamp 变化时重新计时，支持连续弹出新消息）
    LaunchedEffect(bubble.timestamp) {
        delay(4000)
        ChatBubbleController.dismiss()
    }

    val context = LocalContext.current
    val density = LocalDensity.current

    Popup(
        alignment = Alignment.BottomStart,
        offset = with(density) {
            IntOffset(
                16.dp.roundToPx(),
                -100.dp.roundToPx(),
            )
        },
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        onDismissRequest = { /* 不响应系统关闭请求，由自动计时控制 */ },
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(end = 60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xE6222244))
                .clickable { ChatBubbleController.dismiss() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1e1e44)),
                contentAlignment = Alignment.Center,
            ) {
                val avatarPath = bubble.avatarPath
                if (!avatarPath.isNullOrBlank() && File(avatarPath).exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(avatarPath))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.widthIn(max = 220.dp)) {
                Text(
                    text = bubble.senderName,
                    color = Color(0xFFc4b5fd),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = bubble.message,
                    color = Color(0xFFe0e0f0),
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}
