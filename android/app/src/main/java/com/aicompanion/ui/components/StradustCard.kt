package com.aicompanion.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aicompanion.theme.StradustTheme

/**
 * 星尘卡片组件
 *
 * 特性：
 * - 圆角 + 阴影分层（亮色模式靠阴影区分层次）
 * - 暗色模式自动添加 0.5dp 细微边框（outlineVariant 30% 透明度）
 * - 可选点击回调（整个卡片可点击）
 * - 可自定义圆角和阴影高度
 */
@Composable
fun StradustCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
    cornerRadius: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = StradustTheme.colors
    val isDark = StradustTheme.isDark
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current

    Surface(
        modifier = modifier
            .shadow(elevation, RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
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
        shape = RoundedCornerShape(cornerRadius),
        color = colors.surface,
        shadowElevation = elevation,
        border = if (isDark) {
            BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.3f))
        } else null,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}
