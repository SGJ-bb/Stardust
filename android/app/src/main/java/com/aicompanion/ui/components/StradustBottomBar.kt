package com.aicompanion.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.theme.StradustTheme

/** 底部导航项数据 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** 不显示底部导航栏的路由集合（全屏沉浸式页面） */
private val BOTTOM_NAV_EXCLUDED_ROUTES = setOf(
    "phone_call",
    "group_chat/",
    "chat",
)

/**
 * 星尘底部导航栏
 *
 * 特性：
 * - 选中图标 spring 放大动画 (1.0 → 1.15)
 * - 主题色驱动（StradustTheme.colors）
 * - 圆角容器 + 安全区内边距
 * - 支持标签显隐切换
 */
@Composable
fun StradustBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val colors = StradustTheme.colors

    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        containerColor = colors.surface,
        contentColor = colors.textSecondary,
        tonalElevation = 8.dp,
    ) {
        items.forEach { item ->
            val isSelected = currentRoute == item.route

            val iconScale by animateFloatAsState(
                targetValue = if (isSelected) 1.15f else 1.0f,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 400f,
                ),
                label = "bottomNavIconScale_${item.route}",
            )

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.scale(iconScale),
                    )
                },
                label = if (showLabel) {
                    { Text(text = item.label, fontSize = 11.sp) }
                } else null,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.primary,
                    selectedTextColor = colors.primary,
                    indicatorColor = colors.primary.copy(alpha = 0.12f),
                    unselectedIconColor = colors.textMuted,
                    unselectedTextColor = colors.textMuted,
                ),
            )
        }
    }
}

/** 判断当前路由是否应显示底部导航栏 */
fun shouldShowBottomNav(route: String?): Boolean {
    if (route == null) return false
    return BOTTOM_NAV_EXCLUDED_ROUTES.none { excluded -> route.startsWith(excluded) }
}
