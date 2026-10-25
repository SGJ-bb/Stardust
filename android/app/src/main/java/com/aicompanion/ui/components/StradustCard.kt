package com.aicompanion.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aicompanion.theme.StradustTheme

/** 卡片视觉变体 */
enum class CardVariant {
    /** 默认：主题表面色 + 极细描边（暗色更明显），层次最稳 */
    AUTO,

    /** 描边：透明底 + 边框，用于"分组容器"，不与内容抢视觉 */
    OUTLINED,

    /** 填充：次级容器色 + 无边框，用于卡片内嵌套的小块 */
    FILLED,
}

/**
 * 星尘卡片组件
 *
 * 特性：
 * - 圆角 + 可选阴影分层
 * - 默认带 0.5dp 极细描边（亮色 45% / 暗色 30% 透明度），让卡片从背景里"浮"出来
 * - 三种变体：AUTO / OUTLINED / FILLED
 * - 可自定义内边距（原先写死 16dp，导致列表型内容偏挤/偏松都无法调整）
 * - 可选点击回调（整个卡片可点击，带涟漪）
 *
 * 修复：原先同时使用 `Modifier.shadow()` 与 `Surface(shadowElevation)`，
 * 阴影被画两遍（视觉偏重且多一层图层）。现只保留 Surface 的阴影。
 */
@Composable
fun StradustCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
    cornerRadius: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    variant: CardVariant = CardVariant.AUTO,
    containerColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = StradustTheme.colors
    val isDark = StradustTheme.isDark
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current

    val shape = RoundedCornerShape(cornerRadius)
    val bg = when {
        containerColor != Color.Unspecified -> containerColor
        variant == CardVariant.OUTLINED -> Color.Transparent
        variant == CardVariant.FILLED -> colors.surfaceContainerLow
        else -> colors.surface
    }
    val border = when (variant) {
        // 分组容器：边框更清楚一点，充当"区域边界"
        CardVariant.OUTLINED -> BorderStroke(1.dp, colors.outlineVariant)
        // 嵌套块：靠背景色区分，不要边框（否则和父卡片边框叠成"框中框"）
        CardVariant.FILLED -> null
        CardVariant.AUTO -> BorderStroke(
            0.5.dp,
            colors.outlineVariant.copy(alpha = if (isDark) 0.3f else 0.45f),
        )
    }

    Surface(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = indication,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else Modifier
            ),
        shape = shape,
        color = bg,
        shadowElevation = elevation,
        border = border,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}
