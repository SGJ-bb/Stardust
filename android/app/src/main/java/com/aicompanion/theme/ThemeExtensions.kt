package com.aicompanion.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 主题渐变背景 Modifier
 * 使用当前主题的 themeGradient 画刷绘制圆角矩形
 */
@Composable
fun Modifier.themeGradient(
    shape: Shape = RoundedCornerShape(24.dp),
): Modifier {
    val gradientBrush = StradustTheme.colors.themeGradient
    return this.then(
        Modifier.drawBehind {
            drawRoundRect(
                brush = gradientBrush,
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
            )
        }
    )
}

/**
 * 发光背景 Modifier（用于卡片悬浮效果）
 */
@Composable
fun Modifier.glowBackground(alpha: Float = 0.15f): Modifier =
    this.background(StradustTheme.colors.glow.copy(alpha = alpha))

/**
 * Stradust 风格 Surface Modifier
 * 暗色模式下自动添加细微边框线
 */
@Composable
fun Modifier.stradustSurface(
    shape: Shape = RoundedCornerShape(16.dp),
): Modifier {
    val surfaceColor = StradustTheme.colors.surface
    val isDark = StradustTheme.isDark
    val borderColor = StradustTheme.colors.outlineVariant.copy(alpha = 0.5f)
    return this
        .background(surfaceColor, shape)
        .then(
            if (isDark) {
                Modifier.drawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = borderColor,
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            } else Modifier
        )
}

/**
 * 主题化点击效果 Modifier
 * 使用默认 ripple 效果（主题色由 Material3 自动处理）
 */
@Composable
fun Modifier.themedClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
