package com.aicompanion.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.theme.StradustTheme

/** 按钮视觉变体 */
enum class ButtonVariant { FILLED, OUTLINED, TONAL }

/** 按钮尺寸 */
enum class ButtonSize { SMALL, MEDIUM, LARGE }

/**
 * 星尘按钮组件
 *
 * 特性：
 * - 三种变体：FILLED（实心）/ OUTLINED（描边）/ TONAL（色调）
 * - 三种尺寸：SMALL / MEDIUM / LARGE
 * - 胶囊形圆角（24dp）
 * - 按压缩放反馈（0.96x，弹簧动画）
 * - 禁用态半透明（alpha 0.5f）
 */
@Composable
fun StradustButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.FILLED,
    size: ButtonSize = ButtonSize.MEDIUM,
) {
    val colors = StradustTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 按压缩放动画
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "button_press_scale",
    )

    // 尺寸参数
    val (heightDp, fontSizeSp, paddingH, paddingV) = when (size) {
        ButtonSize.SMALL -> Quad(40.dp, 13.sp, 16.dp, 6.dp)
        ButtonSize.MEDIUM -> Quad(40.dp, 14.sp, 24.dp, 10.dp)
        ButtonSize.LARGE -> Quad(48.dp, 15.sp, 32.dp, 14.dp)
    }

    // 变体颜色
    val (containerColor, contentColor, borderStroke) = when (variant) {
        ButtonVariant.FILLED -> Triple(
            colors.primary,
            colors.onPrimary,
            null,
        )
        ButtonVariant.OUTLINED -> Triple(
            Color.Transparent,
            colors.primary,
            BorderStroke(1.5.dp, colors.primary),
        )
        ButtonVariant.TONAL -> Triple(
            colors.primaryContainer,
            colors.onPrimaryContainer,
            null,
        )
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(heightDp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .then(if (!enabled) Modifier.graphicsLayer { alpha = 0.5f } else Modifier),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = paddingH, vertical = paddingV),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
        border = borderStroke,
        interactionSource = interactionSource,
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold, fontSize = fontSizeSp)
    }
}

/** 四元组辅助（避免引入 Pair 嵌套） */
private data class Quad(
    val a: androidx.compose.ui.unit.Dp,
    val b: androidx.compose.ui.unit.TextUnit,
    val c: androidx.compose.ui.unit.Dp,
    val d: androidx.compose.ui.unit.Dp,
)
