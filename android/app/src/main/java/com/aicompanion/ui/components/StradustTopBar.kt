package com.aicompanion.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row as RowLayout
import com.aicompanion.theme.StradustTheme

/**
 * 星尘顶部栏组件
 * 支持渐变背景、副标题、返回按钮涟漪效果
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StradustTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    useGradient: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    val colors = StradustTheme.colors
    val containerColor = if (useGradient) Color.Transparent else colors.toolbar

    TopAppBar(
        title = {
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, color = if (useGradient) colors.onPrimary else colors.textPrimary, fontSize = 18.sp)
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = if (useGradient) colors.onPrimary.copy(alpha = 0.8f) else colors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        modifier = modifier.then(
            if (useGradient) {
                Modifier.drawBehind {
                    drawRect(brush = colors.themeGradient)
                }
            } else {
                Modifier
            }
        ),
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick, interactionSource = remember { MutableInteractionSource() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = if (useGradient) colors.onPrimary else colors.textPrimary)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            titleContentColor = if (useGradient) colors.onPrimary else colors.textPrimary,
            navigationIconContentColor = if (useGradient) colors.onPrimary else colors.textPrimary,
        ),
        actions = {
            if (actions != null) actions()
            if (onSettingsClick != null) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, "设置", tint = if (useGradient) colors.onPrimary else colors.textSecondary)
                }
            }
        },
    )
}
