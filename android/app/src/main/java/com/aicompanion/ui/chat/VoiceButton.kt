package com.aicompanion.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.aicompanion.theme.StradustTheme

/**
 * 星尘语音录制按钮
 *
 * 待机状态：48dp 圆形 surfaceContainer 底 + Mic icon(primary)，按压缩放 0.95
 * 录音状态：外圈 56dp 脉冲扩散 + 内圈 48dp error 底 + Mic icon(white) + "录音中..."文字
 */
@Composable
fun VoiceButton(
    isRecording: Boolean,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isRecording) {
        RecordingVoiceButton(modifier = modifier, onStopRecord = onStopRecord)
    } else {
        IdleVoiceButton(modifier = modifier, onStartRecord = onStartRecord)
    }
}

/** 待机状态的语音按钮 */
@Composable
private fun IdleVoiceButton(
    modifier: Modifier = Modifier,
    onStartRecord: () -> Unit,
) {
    val colors = StradustTheme.colors
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "voice_press_scale",
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(CircleShape)
            .background(colors.surfaceContainer)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onStartRecord() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Mic, "语音输入", tint = colors.primary, modifier = Modifier.size(22.dp))
    }
}

/** 录音中的语音按钮（带外圈脉冲动画） */
@Composable
private fun RecordingVoiceButton(
    modifier: Modifier = Modifier,
    onStopRecord: () -> Unit,
) {
    val colors = StradustTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")

    // 外圈脉冲：scale 1.0→1.3，alpha 0.6→0.0
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_progress",
    )
    val pulseScale = 1f + pulseProgress * 0.3f
    val pulseAlpha = 0.6f * (1f - pulseProgress)

    Column(
        modifier = modifier.clickable(role = Role.Button, onClick = onStopRecord),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 脉冲外圈
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale; alpha = pulseAlpha }
                    .clip(CircleShape)
                    .border(width = 2.dp, color = colors.error, shape = CircleShape),
            )

            // 内圈红色实心
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.error),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Mic, "录音中", tint = colors.onError, modifier = Modifier.size(24.dp))
            }
        }

        Text(
            text = "录音中...",
            color = colors.error,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
