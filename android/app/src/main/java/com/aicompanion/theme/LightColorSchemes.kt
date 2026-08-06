package com.aicompanion.theme

import androidx.compose.ui.graphics.Color

/** 10 套浅色配色方案（暗夜和赛博朋克无浅色模式）*/
object LightColorSchemes {

    // ===== 1. 樱粉 Sakura =====
    val Sakura = StradustColors(
        background = Color(0xFFfef7f7), backgroundSecondary = Color(0xFFfdf2f2), backgroundTertiary = Color(0xFFfce7f3),
        surface = Color(0xFFFFFFFF), surfaceDim = Color(0xFFe8dee6), surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFfce7f3), surfaceContainer = Color(0xFFF8EEF4), surfaceContainerHigh = Color(0xFFF2E2EC), surfaceContainerHighest = Color(0xFFECD8E4),
        primary = Color(0xFFec4899), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFffd6e9), onPrimaryContainer = Color(0xFF450026),
        secondary = Color(0xFFce3d72), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFffd9e4), onSecondaryContainer = Color(0xFF500023),
        tertiary = Color(0xFFb14e87), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFffd0ea), onTertiaryContainer = Color(0xFF3c0027),
        error = Color(0xFFef4444), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFfee2e2), onErrorContainer = Color(0xFF7f1d1d),
        outline = Color(0xFFf3e8ff), outlineVariant = Color(0xFFe8dde8),
        textPrimary = Color(0xFF1a1a2e), textSecondary = Color(0xFF6b7280), textMuted = Color(0xFF9ca3af), textDisabled = Color(0xFFd1d5db), textLink = Color(0xFFec4899),
        ripple = Color(0x1aec4899), scrim = Color(0x80000000),
        userBubbleStart = Color(0xFFec4899), userBubbleEnd = Color(0xFFf472b6),
        aiBubble = Color(0xFFfce7f3), aiBubbleText = Color(0xFF9d174d),
        glow = Color(0x26ec4899), glowStrong = Color(0x59ec4899), glowGradient = Color(0xFFf472b6),
        toolbar = Color(0xFFFFFFFF), sidebar = Color(0xFFfef7f7), sidebarActive = Color(0xFFec4899),
    )

    // ===== 2. 桃粉 Peach =====
    val Peach = StradustColors(
        background = Color(0xFFfff7ed), backgroundSecondary = Color(0xFFffedd5), backgroundTertiary = Color(0xFFffedd5),
        surface = Color(0xFFFFFFFF), surfaceDim = Color(0xFFe8ddd0), surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFffedd5), surfaceContainer = Color(0xFFF8F0E4), surfaceContainerHigh = Color(0xFFF2E8D8), surfaceContainerHighest = Color(0xFFece0cc),
        primary = Color(0xFFf97316), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFffe8cd), onPrimaryContainer = Color(0xFF331a00),
        secondary = Color(0xFFc25b08), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFfed7aa), onSecondaryContainer = Color(0xFF4a1e00),
        tertiary = Color(0xFF9e4a10), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFfecf99), onTertiaryContainer = Color(0xFF351400),
        error = Color(0xFFef4444), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFfee2e2), onErrorContainer = Color(0xFF7f1d1d),
        outline = Color(0xFFfed7aa), outlineVariant = Color(0xFFf5e6d3),
        textPrimary = Color(0xFF1c1917), textSecondary = Color(0xFF78716c), textMuted = Color(0xFFa8a29e), textDisabled = Color(0xFFd6d3d1), textLink = Color(0xFFf97316),
        ripple = Color(0x1af97316), scrim = Color(0x80000000),
        userBubbleStart = Color(0xFFf97316), userBubbleEnd = Color(0xFFfb923c),
        aiBubble = Color(0xFFffedd5), aiBubbleText = Color(0xFF9a3412),
        glow = Color(0x26f97316), glowStrong = Color(0x59f97316), glowGradient = Color(0xFFfb923c),
        toolbar = Color(0xFFFFFFFF), sidebar = Color(0xFFfff7ed), sidebarActive = Color(0xFFf97316),
    )

    // ===== 3. 紫罗兰 Violet =====
    val Violet = StradustColors(
        background = Color(0xFFfaf5ff), backgroundSecondary = Color(0xFFf3e8ff), backgroundTertiary = Color(0xFFf3e8ff),
        surface = Color(0xFFFFFFFF), surfaceDim = Color(0xFFe8def4), surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFf3e8ff), surfaceContainer = Color(0xFFF4EDFA), surfaceContainerHigh = Color(0xFFEBE2F4), surfaceContainerHighest = Color(0xFFe2d6ee),
        primary = Color(0xFF8b5cf6), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFede9fe), onPrimaryContainer = Color(0xFF21005d),
        secondary = Color(0xFF6b21a8), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFe9d5ff), onSecondaryContainer = Color(0xFF27005a),
        tertiary = Color(0xFF7e1dc0), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFf3d8ff), onTertiaryContainer = Color(0xFF29005a),
        error = Color(0xFFef4444), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFfee2e2), onErrorContainer = Color(0xFF7f1d1d),
        outline = Color(0xFFe9d5ff), outlineVariant = Color(0xFFe2d5f0),
        textPrimary = Color(0xFF1a1a2e), // [修复] 从#1e1b4b改为深蓝黑，统一高对比度标准
        textSecondary = Color(0xFF57534e), // [修复] 从#737373改为更深灰，提高可读性
        textMuted = Color(0xFF78716c), // [修复] 从#a3a3a3改为更深的灰褐色
        textDisabled = Color(0xFFd4d4d4), textLink = Color(0xFF8b5cf6),
        ripple = Color(0x1a8b5cf6), scrim = Color(0x80000000),
        userBubbleStart = Color(0xFF8b5cf6), userBubbleEnd = Color(0xFFa78bfa),
        aiBubble = Color(0xFFf3e8ff), aiBubbleText = Color(0xFF5b21b6),
        glow = Color(0x268b5cf6), glowStrong = Color(0x598b5cf6), glowGradient = Color(0xFFa78bfa),
        toolbar = Color(0xFFFFFFFF), sidebar = Color(0xFFfaf5ff), sidebarActive = Color(0xFF8b5cf6),
    )

    // ===== 4. 海蓝 Ocean =====
    val Ocean = StradustColors(
        background = Color(0xFFeff6ff), backgroundSecondary = Color(0xFFdbeafe), backgroundTertiary = Color(0xFFdbeafe),
        surface = Color(0xFFFFFFFF), surfaceDim = Color(0xFFdce4ee), surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFdbeafe), surfaceContainer = Color(0xFFEFF4FC), surfaceContainerHigh = Color(0xFFE4ECF8), surfaceContainerHighest = Color(0xFFd9e3f2),
        primary = Color(0xFF3b82f6), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFdbeafe), onPrimaryContainer = Color(0xFF072260),
        secondary = Color(0xFF1e40af), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFbfdbfe), onSecondaryContainer = Color(0xFF001d4c),
        tertiary = Color(0xFF7c3aed), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFe0e7ff), onTertiaryContainer = Color(0xFF27007e),
        error = Color(0xFFef4444), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFfee2e2), onErrorContainer = Color(0xFF7f1d1d),
        outline = Color(0xFFbfdbfe), outlineVariant = Color(0xFFc7d7eb),
        textPrimary = Color(0xFF0f172a), // [修复] 从#1e3a5f改为更深的海军蓝，提高对比度至7:1以上
        textSecondary = Color(0xFF475569), // [修复] 从#6b7280改为更深的蓝灰色，确保可读性
        textMuted = Color(0xFF64748b), // [修复] 从#94a3b8改为更深的石板灰
        textDisabled = Color(0xFFcbd5e1), textLink = Color(0xFF3b82f6),
        ripple = Color(0x1a3b82f6), scrim = Color(0x80000000),
        userBubbleStart = Color(0xFF3b82f6), userBubbleEnd = Color(0xFF60a5fa),
        aiBubble = Color(0xFFdbeafe), aiBubbleText = Color(0xFF1e40af),
        glow = Color(0x263b82f6), glowStrong = Color(0x593b82f6), glowGradient = Color(0xFF60a5fa),
        toolbar = Color(0xFFFFFFFF), sidebar = Color(0xFFeff6ff), sidebarActive = Color(0xFF3b82f6),
    )

    // ===== 5. 翡翠 Emerald =====
    val Emerald = StradustColors(
        background = Color(0xFFecfdf5), backgroundSecondary = Color(0xFFd1fae5), backgroundTertiary = Color(0xFFd1fae5),
        surface = Color(0xFFFFFFFF), surfaceDim = Color(0xFFd1e8dd), surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFd1fae5), surfaceContainer = Color(0xFFE2F0E8), surfaceContainerHigh = Color(0xFFD6E8DC), surfaceContainerHighest = Color(0xFFcae0d2),
        primary = Color(0xFF10b981), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFa7f3d0), onPrimaryContainer = Color(0xFF002114),
        secondary = Color(0xFF065f46), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFa7f3d0), onSecondaryContainer = Color(0xFF002117),
        tertiary = Color(0xFF047857), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFF99f6e4), onTertiaryContainer = Color(0xFF002018),
        error = Color(0xFFef4444), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFfee2e2), onErrorContainer = Color(0xFF7f1d1d),
        outline = Color(0xFFa7f3d0), outlineVariant = Color(0xFFb2dfce),
        textPrimary = Color(0xFF064e3b), textSecondary = Color(0xFF6b7280), textMuted = Color(0xFF86ac98), textDisabled = Color(0xFFbbd9ca), textLink = Color(0xFF10b981),
        ripple = Color(0x1a10b981), scrim = Color(0x80000000),
        userBubbleStart = Color(0xFF10b981), userBubbleEnd = Color(0xFF34d399),
        aiBubble = Color(0xFFd1fae5), aiBubbleText = Color(0xFF065f46),
        glow = Color(0x2610b981), glowStrong = Color(0x5910b981), glowGradient = Color(0xFF34d399),
        toolbar = Color(0xFFFFFFFF), sidebar = Color(0xFFecfdf5), sidebarActive = Color(0xFF10b981),
    )

    // ===== 6. 日落 Sunset =====
    val Sunset = StradustColors(
        background = Color(0xFFfffbeb), backgroundSecondary = Color(0xFFfef3c7), backgroundTertiary = Color(0xFFfef3c7),
        surface = Color(0xFFFFFFFF), surfaceDim = Color(0xFFe8dfd0), surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFfef3c7), surfaceContainer = Color(0xFFF8F0DC), surfaceContainerHigh = Color(0xFFF2EAD0), surfaceContainerHighest = Color(0xFFebe4c4),
        primary = Color(0xFFf59e0b), onPrimary = Color(0xFF78350f), primaryContainer = Color(0xFFfde68a), onPrimaryContainer = Color(0xFF452a00),
        secondary = Color(0xFF92400e), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFfef3c7), onSecondaryContainer = Color(0xFF2b1600),
        tertiary = Color(0xFFb45309), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFfcdba9), onTertiaryContainer = Color(0xFF3c1800),
        error = Color(0xFFef4444), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFfee2e2), onErrorContainer = Color(0xFF7f1d1d),
        outline = Color(0xFFfde68a), outlineVariant = Color(0xFFf2e4c4),
        textPrimary = Color(0xFF1c1917), // [修复] 从#78350f改为深棕黑，提高与浅黄背景的对比度至7:1以上
        textSecondary = Color(0xFF57534e), // [修复] 从#78716c改为更深灰，确保在白色surface上可读
        textMuted = Color(0xFF78716c), // [修复] 从#a8a29e改为更深的暖灰
        textDisabled = Color(0xFFd6d3d1), textLink = Color(0xFFf59e0b),
        ripple = Color(0x1af59e0b), scrim = Color(0x80000000),
        userBubbleStart = Color(0xFFf59e0b), userBubbleEnd = Color(0xFFfbbf24),
        aiBubble = Color(0xFFfef3c7), aiBubbleText = Color(0xFF92400e),
        glow = Color(0x26f59e0b), glowStrong = Color(0x59f59e0b), glowGradient = Color(0xFFfbbf24),
        toolbar = Color(0xFFFFFFFF), sidebar = Color(0xFFfffbeb), sidebarActive = Color(0xFFf59e0b),
    )

    // ===== 7. 玫瑰金 RoseGold =====
    val RoseGold = StradustColors(
        background = Color(0xFFfdf2f8), backgroundSecondary = Color(0xFFfce7f3), backgroundTertiary = Color(0xFFfce7f3),
        surface = Color(0xFFFFFFFF), surfaceDim = Color(0xFFe8ded8), surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFfce7f3), surfaceContainer = Color(0xFFF8EEEA), surfaceContainerHigh = Color(0xFFF2E6E0), surfaceContainerHighest = Color(0xFFebded6),
        primary = Color(0xFFe11d48), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFfbcfe8), onPrimaryContainer = Color(0xFF4a0017),
        secondary = Color(0xFF9d174d), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFfce7f3), onSecondaryContainer = Color(0xFF38001b),
        tertiary = Color(0xFFbe185d), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFfbe1ec), onTertiaryContainer = Color(0xFF420032),
        error = Color(0xFFef4444), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFfee2e2), onErrorContainer = Color(0xFF7f1d1d),
        outline = Color(0xFFfbcfe8), outlineVariant = Color(0xFFf0dce4),
        textPrimary = Color(0xFF1a1a2e), // [修复] 从#831843改为深蓝黑，提高与浅粉背景的对比度至7:1以上
        textSecondary = Color(0xFF57534e), // [修复] 从#737373改为更深灰，提高可读性
        textMuted = Color(0xFF78716c), // [修复] 从#a3a3a3改为更深的灰褐色
        textDisabled = Color(0xFFd4d4d4), textLink = Color(0xFFe11d48),
        ripple = Color(0x1ae11d48), scrim = Color(0x80000000),
        userBubbleStart = Color(0xFFe11d48), userBubbleEnd = Color(0xFFfb7185),
        aiBubble = Color(0xFFfce7f3), aiBubbleText = Color(0xFF9d174d),
        glow = Color(0x26e11d48), glowStrong = Color(0x59e11d48), glowGradient = Color(0xFFfb7185),
        toolbar = Color(0xFFFFFFFF), sidebar = Color(0xFFfdf2f8), sidebarActive = Color(0xFFe11d48),
    )

    // ===== 8. 薄荷 Mint =====
    val Mint = StradustColors(
        background = Color(0xFFf0fdfa), backgroundSecondary = Color(0xFFccfbf1), backgroundTertiary = Color(0xFFccfbf1),
        surface = Color(0xFFFFFFFF), surfaceDim = Color(0xFFd1e8e0), surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFccfbf1), surfaceContainer = Color(0xFFE2F2EA), surfaceContainerHigh = Color(0xFFD6EAE0), surfaceContainerHighest = Color(0xFFcadfd6),
        primary = Color(0xFF14b8a6), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF99f6e4), onPrimaryContainer = Color(0xFF002822),
        secondary = Color(0xFF115e59), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFccfbf1), onSecondaryContainer = Color(0xFF001f1e),
        tertiary = Color(0xFF0d9488), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFF84eedc), onTertiaryContainer = Color(0xFF00201c),
        error = Color(0xFFef4444), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFfee2e2), onErrorContainer = Color(0xFF7f1d1d),
        outline = Color(0xFF99f6e4), outlineVariant = Color(0xFFa5ebe0),
        textPrimary = Color(0xFF134e4a), textSecondary = Color(0xFF6b7280), textMuted = Color(0xFF88a8a0), textDisabled = Color(0xFFb8d4cc), textLink = Color(0xFF14b8a6),
        ripple = Color(0x1a14b8a6), scrim = Color(0x80000000),
        userBubbleStart = Color(0xFF14b8a6), userBubbleEnd = Color(0xFF2dd4bf),
        aiBubble = Color(0xFFccfbf1), aiBubbleText = Color(0xFF115e59),
        glow = Color(0x2614b8a6), glowStrong = Color(0x5914b8a6), glowGradient = Color(0xFF2dd4bf),
        toolbar = Color(0xFFFFFFFF), sidebar = Color(0xFFf0fdfa), sidebarActive = Color(0xFF14b8a6),
    )

    // ===== 9. 茶香 Tea =====
    val Tea = StradustColors(
        background = Color(0xFFfaf8f3), backgroundSecondary = Color(0xFFf2ede0), backgroundTertiary = Color(0xFFe8e0cc),
        surface = Color(0xFFFFFFFF), surfaceDim = Color(0xFFe2dad0), surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFe8e0cc), surfaceContainer = Color(0xFFF2EBE0), surfaceContainerHigh = Color(0xFFeae2d4), surfaceContainerHighest = Color(0xFFe2d8c8),
        primary = Color(0xFF6b8e5a), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFc8ddb8), onPrimaryContainer = Color(0xFF1a2e13),
        secondary = Color(0xFF4a5a3a), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFe8e0cc), onSecondaryContainer = Color(0xFF0f1f08),
        tertiary = Color(0xFF5a7048), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFd4e4be), onTertiaryContainer = Color(0xFF17230f),
        error = Color(0xFFdc2626), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFfee2e2), onErrorContainer = Color(0xFF7f1d1d),
        outline = Color(0xFFd4cbb0), outlineVariant = Color(0xFFdec8a8),
        textPrimary = Color(0xFF2d2a1e), textSecondary = Color(0xFF6b6655), textMuted = Color(0xFF908a78), textDisabled = Color(0xFFb8b4a0), textLink = Color(0xFF6b8e5a),
        ripple = Color(0x1a6b8e5a), scrim = Color(0x80000000),
        userBubbleStart = Color(0xFF6b8e5a), userBubbleEnd = Color(0xFF8ba86a),
        aiBubble = Color(0xFFe8e0cc), aiBubbleText = Color(0xFF4a5a3a),
        glow = Color(0x1f6b8e5a), glowStrong = Color(0x4d6b8e5a), glowGradient = Color(0xFF8ba86a),
        toolbar = Color(0xFFFFFFFF), sidebar = Color(0xFFfaf8f3), sidebarActive = Color(0xFF6b8e5a),
    )

    // ===== 10. 华夏风韵 Chinese =====
    val Chinese = StradustColors(
        background = Color(0xFFfaf6f0), backgroundSecondary = Color(0xFFf2ebe0), backgroundTertiary = Color(0xFFf0e4d8),
        surface = Color(0xFFFFFFFF), surfaceDim = Color(0xFFe4dad0), surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFf0e4d8), surfaceContainer = Color(0xFFF4EBE0), surfaceContainerHigh = Color(0xFFede2d4), surfaceContainerHighest = Color(0xFFe6d8c8),
        primary = Color(0xFFc53d43), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFfadcd4), onPrimaryContainer = Color(0xFF3d0808),
        secondary = Color(0xFF8b4513), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFf0e4d8), onSecondaryContainer = Color(0xFF2b1400),
        tertiary = Color(0xFFa35420), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFfddcc8), onTertiaryContainer = Color(0xFF381900),
        error = Color(0xFFdc2626), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFfee2e2), onErrorContainer = Color(0xFF7f1d1d),
        outline = Color(0xFFd4c4ac), outlineVariant = Color(0xFFdeb89c),
        textPrimary = Color(0xFF2a1a10), textSecondary = Color(0xFF7a6050), textMuted = Color(0xFFa08070), textDisabled = Color(0xFFc8a888), textLink = Color(0xFFc53d43),
        ripple = Color(0x1ac53d43), scrim = Color(0x80000000),
        userBubbleStart = Color(0xFFc53d43), userBubbleEnd = Color(0xFFd4765a),
        aiBubble = Color(0xFFf0e4d8), aiBubbleText = Color(0xFF8b4513),
        glow = Color(0x1fc53d43), glowStrong = Color(0x4cc53d43), glowGradient = Color(0xFFd4765a),
        toolbar = Color(0xFFFFFFFF), sidebar = Color(0xFFfaf6f0), sidebarActive = Color(0xFFc53d43),
    )

    /** 根据 ThemeId 获取对应的 Light Scheme */
    fun fromId(id: ThemeId): StradustColors = when (id) {
        ThemeId.SAKURA -> Sakura
        ThemeId.PEACH -> Peach
        ThemeId.VIOLET -> Violet
        ThemeId.OCEAN -> Ocean
        ThemeId.EMERALD -> Emerald
        ThemeId.SUNSET -> Sunset
        ThemeId.ROSEGOLD -> RoseGold
        ThemeId.MINT -> Mint
        ThemeId.TEA -> Tea
        ThemeId.CHINESE -> Chinese
        else -> Sakura
    }
}
