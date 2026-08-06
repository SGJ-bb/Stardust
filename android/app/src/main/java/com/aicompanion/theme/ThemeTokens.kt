package com.aicompanion.theme

import androidx.compose.ui.graphics.Color

/**
 * 星尘设计令牌 — 与 PC端 themes.css 1:1 对应
 * 每套主题生成一个此类的实例（Light / Dark 各一个）
 */
data class StradustColors(
    // ===== 背景层 =====
    val background: Color,
    val backgroundSecondary: Color,
    val backgroundTertiary: Color,

    // ===== 表面层 =====
    val surface: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,

    // ===== 主色 Primary =====
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,

    // ===== 次色 Secondary =====
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,

    // ===== 强调色 Tertiary/Accent =====
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,

    // ===== 错误色 =====
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,

    // ===== 轮廓/边框 =====
    val outline: Color,
    val outlineVariant: Color,

    // ===== 文字 =====
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val textLink: Color,

    // ===== 反馈 =====
    val ripple: Color,
    val scrim: Color,

    // ===== 星尘特有 Token =====
    val userBubbleStart: Color,
    val userBubbleEnd: Color,
    val aiBubble: Color,
    val aiBubbleText: Color,
    val glow: Color,
    val glowStrong: Color,
    val glowGradient: Color,
    val toolbar: Color,
    val sidebar: Color,
    val sidebarActive: Color,
) {
    /** 主题品牌渐变 */
    val themeGradient: androidx.compose.ui.graphics.Brush get() =
        androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(primary, tertiary),
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset.Infinite
        )

    /** 用户气泡渐变 */
    val userBubbleBrush: androidx.compose.ui.graphics.Brush get() =
        androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(userBubbleStart, userBubbleEnd)
        )
}

/** 12 套主题 ID */
enum class ThemeId(val key: String, val displayName: String) {
    SAKURA("sakura", "樱粉"),
    PEACH("peach", "桃粉"),
    VIOLET("violet", "紫罗兰"),
    OCEAN("ocean", "海蓝"),
    EMERALD("emerald", "翡翠"),
    SUNSET("sunset", "日落"),
    ROSEGOLD("rosegold", "玫瑰金"),
    MINT("mint", "薄荷"),
    MIDNIGHT("midnight", "暗夜"),
    TEA("tea", "茶香"),
    CYBERPUNK("cyberpunk", "赛博朋克"),
    CHINESE("chinese", "华夏风韵");

    companion object {
        fun fromKey(key: String): ThemeId =
            entries.find { it.key == key } ?: SAKURA
    }

    val supportsLightMode: Boolean get() =
        this != MIDNIGHT && this != CYBERPUNK
}
