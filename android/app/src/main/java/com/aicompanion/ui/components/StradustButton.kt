package com.aicompanion.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * - 三种尺寸：SMALL(36dp) / MEDIUM(44dp) / LARGE(52dp)
 * - 胶囊形圆角（24dp）
 * - 按压缩放反馈（0.96x，弹簧动画）
 * - 禁用态半透明（alpha 0.5f）
 * - 可选前置图标 / 加载态
 *
 * 触控尺寸：视觉高度按尺寸走，但通过 [minimumInteractiveComponentSize]
 * 保证点击热区不小于 48dp —— 小按钮看着小，实际好点中。
 */
@Composable
fun StradustButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.FILLED,
    size: ButtonSize = ButtonSize.MEDIUM,
    leadingIcon: ImageVector? = null,
    loading: Boolean = false,
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

    // 尺寸参数：三档必须真正有区分（原实现 SMALL/MEDIUM 都是 40dp，小按钮并不小）
    val heightDp = when (size) {
        ButtonSize.SMALL -> 36.dp
        ButtonSize.MEDIUM -> 44.dp
        ButtonSize.LARGE -> 52.dp
    }
    val fontSizeSp = when (size) {
        ButtonSize.SMALL -> 13.sp
        ButtonSize.MEDIUM -> 14.sp
        ButtonSize.LARGE -> 15.sp
    }
    val paddingH = when (size) {
        ButtonSize.SMALL -> 14.dp
        ButtonSize.MEDIUM -> 22.dp
        ButtonSize.LARGE -> 30.dp
    }
    val paddingV = when (size) {
        ButtonSize.SMALL -> 6.dp
        ButtonSize.MEDIUM -> 10.dp
        ButtonSize.LARGE -> 14.dp
    }
    val iconSize = when (size) {
        ButtonSize.SMALL -> 16.dp
        ButtonSize.MEDIUM -> 18.dp
        ButtonSize.LARGE -> 20.dp
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
        enabled = enabled && !loading,
        modifier = modifier
            .minimumInteractiveComponentSize()
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
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize),
                color = contentColor,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
        } else if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = contentColor,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSizeSp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
