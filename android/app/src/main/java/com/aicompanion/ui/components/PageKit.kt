package com.aicompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.theme.StradustTheme

/**
 * 页面内区块标题。
 *
 * 全 App 原先每页各自写 `Text(..., fontWeight = Bold, fontSize = 15.sp)`，
 * 字号 13~17sp、上下留白 4~20dp 全不统一，看起来就"碎"。统一到这里之后
 * 各页的节奏才一致。
 */
@Composable
fun StradustSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val colors = StradustTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        if (actionText != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = actionText,
                color = colors.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .clickable(enabled = onAction != null) { onAction?.invoke() }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 统一空状态。
 *
 * 原先 60 处各自手写"暂无 xx"，emoji、字号、留白、有没有引导按钮全不一样。
 * 空状态是新用户最先看到的画面，最值得统一：一个柔和底色的图标圈 + 一句说明
 * + 可选的一个动作按钮，比干巴巴一行字更能告诉用户"接下来该做什么"。
 */
@Composable
fun StradustEmptyState(
    emoji: String,
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val colors = StradustTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 16.dp else 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 空状态经常压在壁纸/插画上（日记、相册等页），实测浅色文字会糊成一片。
        // 给内容套一层半透明底色，任何背景下都能读清，也不影响纯色页观感。
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    colors.background.copy(alpha = 0.68f),
                    RoundedCornerShape(24.dp),
                )
                .padding(horizontal = 28.dp, vertical = if (compact) 16.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 44.dp else 56.dp)
                    .background(colors.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = if (compact) 20.sp else 26.sp)
            }
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = if (compact) 13.sp else 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (!hint.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hint,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                StradustButton(
                    text = actionText,
                    onClick = onAction,
                    size = ButtonSize.SMALL,
                    variant = ButtonVariant.TONAL,
                )
            }
        }
    }
}

/**
 * 通用列表行。
 *
 * 设置页之所以显得挤：几十行内容、行高 44~72dp 混排、无分组、无分隔。
 * 统一行高（最小 56dp）+ 可选副标题 + 右侧尾部区 + 可选分隔线，
 * 再配合 [StradustSectionHeader] 分组，密度问题就变成可控的排版参数。
 */
@Composable
fun StradustListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingEmoji: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = StradustTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingEmoji != null || leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(colors.surfaceContainerHigh, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.textSecondary,
                    )
                } else {
                    Text(text = leadingEmoji.orEmpty(), fontSize = 16.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.textMuted,
            )
        }
    }
}

/** 行间分隔线（左缩进，视觉上跟着文字走，不会把左侧图标切段） */
@Composable
fun StradustRowDivider(startIndent: androidx.compose.ui.unit.Dp = 20.dp) {
    val colors = StradustTheme.colors
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent),
        thickness = 0.5.dp,
        color = colors.outlineVariant.copy(alpha = 0.5f),
    )
}

/**
 * 页面内容容器：统一左右边距与垂直节奏。
 *
 * 各页面原先 padding 16/18/20 混用、区块间距 8~24 混用，
 * 统一成 16dp 外边距 + 调用方自控区块间距。
 */
@Composable
fun StradustPageContent(
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        content = content,
    )
}
