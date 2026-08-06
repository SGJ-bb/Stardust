package com.aicompanion.ui.chat

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.aicompanion.theme.StradustTheme

/**
 * 打字指示器 - 三个错开延迟的跳动圆点
 *
 * 实现原理：通过不同的 initialValue 偏移量模拟 200ms 的相位延迟。
 * 当 phase 为负时，offset = 0（圆点停在底部）；当 phase 为正时，圆点向上跳。
 *
 * @param modifier 修饰符
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val colors = StradustTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    // 圆点1：0~600ms 跳动（标准相位）
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot1_phase",
    )

    // 圆点2：偏移 200ms（初始值 -0.33f，相当于延迟 1/3 周期）
    val phase2 by infiniteTransition.animateFloat(
        initialValue = -0.33f, targetValue = 0.67f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot2_phase",
    )

    // 圆点3：偏移 400ms（初始值 -0.67f，相当于延迟 2/3 周期）
    val phase3 by infiniteTransition.animateFloat(
        initialValue = -0.67f, targetValue = 0.33f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot3_phase",
    )

    val dotColor = colors.primary.copy(alpha = 0.7f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypingDot(offsetFraction = phase1, color = dotColor)
        TypingDot(offsetFraction = phase2, color = dotColor)
        TypingDot(offsetFraction = phase3, color = dotColor)
    }
}

/**
 * 单个跳动圆点
 *
 * @param offsetFraction 相位偏移分数（[-0.67, 1]），负值时圆点不动，正值时向上跳
 * @param color 圆点颜色
 */
@Composable
private fun TypingDot(
    offsetFraction: Float,
    color: androidx.compose.ui.graphics.Color,
) {
    // 负值时圆点在底部不动，正值时向上跳 6dp
    val offsetDp = offsetFraction.coerceAtLeast(0f) * 6f
    Box(
        modifier = Modifier
            .size(8.dp)
            .graphicsLayer { translationY = -offsetDp * density }
            .clip(CircleShape)
            .background(color),
    )
}
