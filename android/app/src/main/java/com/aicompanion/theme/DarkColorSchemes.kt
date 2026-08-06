package com.aicompanion.theme

import androidx.compose.ui.graphics.Color

/** 12 套暗色配色方案（含暗夜和赛博朋克）*/
object DarkColorSchemes {

    // ===== 1. 樱粉 Dark =====
    val Sakura = StradustColors(
        background = Color(0xFF14080f), backgroundSecondary = Color(0xFF1e1020), backgroundTertiary = Color(0xFF361830),
        surface = Color(0xFF261424), surfaceDim = Color(0xFF1e1020), surfaceBright = Color(0xFF361830),
        surfaceContainerLow = Color(0xFF261424), surfaceContainer = Color(0xFF2e1830), surfaceContainerHigh = Color(0xFF381e38), surfaceContainerHighest = Color(0xFF422440),
        primary = Color(0xFFec4899), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF6b2048), onPrimaryContainer = Color(0xFFffd0ea),
        secondary = Color(0xFFfbcfe8), onSecondary = Color(0xFF4a1535), secondaryContainer = Color(0xFF361830), onSecondaryContainer = Color(0xFFfbcfe8),
        tertiary = Color(0xFFf5b8d8), onTertiary = Color(0xFF4a1035), tertiaryContainer = Color(0xFF4a1840), onTertiaryContainer = Color(0xFFf5cce4),
        error = Color(0xFFf87171), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF3a1018), onErrorContainer = Color(0xFFf8a8a8),
        outline = Color(0xFF462840), outlineVariant = Color(0xFF3e2040),
        textPrimary = Color(0xFFfad0ea), textSecondary = Color(0xFFc4a0b0), textMuted = Color(0xFF8e7080), textDisabled = Color(0xFF5e4050), textLink = Color(0xFFf5a8d0),
        ripple = Color(0x26ffffff), scrim = Color(0xB3000000),
        userBubbleStart = Color(0xFFec4899), userBubbleEnd = Color(0xFFdb2777),
        aiBubble = Color(0xFF361830), aiBubbleText = Color(0xFFfbcfe8),
        glow = Color(0x33ec4899), glowStrong = Color(0x73ec4899), glowGradient = Color(0xFFdb2777),
        toolbar = Color(0xFF1a0e18), sidebar = Color(0xFF14080f), sidebarActive = Color(0xFFec4899),
    )

    // ===== 2. 桃粉 Dark =====
    val Peach = StradustColors(
        background = Color(0xFF141008), backgroundSecondary = Color(0xFF20180e), backgroundTertiary = Color(0xFF382818),
        surface = Color(0xFF281e14), surfaceDim = Color(0xFF20180e), surfaceBright = Color(0xFF382818),
        surfaceContainerLow = Color(0xFF281e14), surfaceContainer = Color(0xFF302618), surfaceContainerHigh = Color(0xFF3a301c), surfaceContainerHighest = Color(0xFF443824),
        primary = Color(0xFFf97316), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF6a3400), onPrimaryContainer = Color(0xFFfed8b0),
        secondary = Color(0xFFfed7aa), onSecondary = Color(0xFF4a2800), secondaryContainer = Color(0xFF382818), onSecondaryContainer = Color(0xFFfed7aa),
        tertiary = Color(0xFFfecf99), onTertiary = Color(0xFF4a2800), tertiaryContainer = Color(0xFF422e18), onTertiaryContainer = Color(0xFFfed7aa),
        error = Color(0xFFf87171), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF3a1810), onErrorContainer = Color(0xFFf8a8a8),
        outline = Color(0xFF483828), outlineVariant = Color(0xFF403020),
        textPrimary = Color(0xFFfed8b0), textSecondary = Color(0xFFc4a890), textMuted = Color(0xFF8e7868), textDisabled = Color(0xFF5e4838), textLink = Color(0xFFfdba74),
        ripple = Color(0x26ffffff), scrim = Color(0xB3000000),
        userBubbleStart = Color(0xFFf97316), userBubbleEnd = Color(0xFFea580c),
        aiBubble = Color(0xFF382818), aiBubbleText = Color(0xFFfed7aa),
        glow = Color(0x33f97316), glowStrong = Color(0x73f97316), glowGradient = Color(0xFFea580c),
        toolbar = Color(0xFF1a150e), sidebar = Color(0xFF141008), sidebarActive = Color(0xFFf97316),
    )

    // ===== 3. 紫罗兰 Dark =====
    val Violet = StradustColors(
        background = Color(0xFF0d0818), backgroundSecondary = Color(0xFF18102e), backgroundTertiary = Color(0xFF2a184a),
        surface = Color(0xFF20143a), surfaceDim = Color(0xFF18102e), surfaceBright = Color(0xFF2a184a),
        surfaceContainerLow = Color(0xFF20143a), surfaceContainer = Color(0xFF281842), surfaceContainerHigh = Color(0xFF32204c), surfaceContainerHighest = Color(0xFF3c2856),
        primary = Color(0xFF8b5cf6), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF4a2080), onPrimaryContainer = Color(0xFFddd0f5),
        secondary = Color(0xFFd8b4fe), onSecondary = Color(0xFF381070), secondaryContainer = Color(0xFF2a184a), onSecondaryContainer = Color(0xFFd8b4fe),
        tertiary = Color(0xFFf0d0ff), onTertiary = Color(0xFF381070), tertiaryContainer = Color(0xFF34204e), onTertiaryContainer = Color(0xFFecd4ff),
        error = Color(0xFFf87171), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF3a1018), onErrorContainer = Color(0xFFf8a8a8),
        outline = Color(0xFF3a285c), outlineVariant = Color(0xFF322050),
        textPrimary = Color(0xFFddd0f5), textSecondary = Color(0xFFa090c4), textMuted = Color(0xFF786898), textDisabled = Color(0xFF504468), textLink = Color(0xFFc4b5fd),
        ripple = Color(0x26ffffff), scrim = Color(0xB3000000),
        userBubbleStart = Color(0xFF8b5cf6), userBubbleEnd = Color(0xFF7c3aed),
        aiBubble = Color(0xFF2a184a), aiBubbleText = Color(0xFFd8b4fe),
        glow = Color(0x338b5cf6), glowStrong = Color(0x738b5cf6), glowGradient = Color(0xFF7c3aed),
        toolbar = Color(0xFF161028), sidebar = Color(0xFF0d0818), sidebarActive = Color(0xFF8b5cf6),
    )

    // ===== 4. 海蓝 Dark =====
    val Ocean = StradustColors(
        background = Color(0xFF08101a), backgroundSecondary = Color(0xFF101a2e), backgroundTertiary = Color(0xFF182a4a),
        surface = Color(0xFF14203a), surfaceDim = Color(0xFF101a2e), surfaceBright = Color(0xFF182a4a),
        surfaceContainerLow = Color(0xFF14203a), surfaceContainer = Color(0xFF1c2442), surfaceContainerHigh = Color(0xFF242e4a), surfaceContainerHighest = Color(0xFF2e3852),
        primary = Color(0xFF3b82f6), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF1a3a68), onPrimaryContainer = Color(0xFFbcd8f5),
        secondary = Color(0xFF93c5fd), onSecondary = Color(0xFF0a2852), secondaryContainer = Color(0xFF182a4a), onSecondaryContainer = Color(0xFF93c5fd),
        tertiary = Color(0xFFa78bfa), onTertiary = Color(0xFF182858), tertiaryContainer = Color(0xFF1e2848), onTertiaryContainer = Color(0xFFc4b5fd),
        error = Color(0xFFf87171), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF3a1820), onErrorContainer = Color(0xFFf8a8a8),
        outline = Color(0xFF283a5c), outlineVariant = Color(0xFF203250),
        textPrimary = Color(0xFFe8f4fc), // [修复] 从#bcd8f5改为更亮的近白色，减少蓝色调影响
        textSecondary = Color(0xFF98c0e0), // [修复] 从#7090b0提亮，确保在深蓝surface上可读
        textMuted = Color(0xFF7098b8), // [修复] 从#507090显著提亮，解决文字模糊问题
        textDisabled = Color(0xFF486888), textLink = Color(0xFF93c5fd),
        ripple = Color(0x26ffffff), scrim = Color(0xB3000000),
        userBubbleStart = Color(0xFF3b82f6), userBubbleEnd = Color(0xFF2563eb),
        aiBubble = Color(0xFF182a4a), aiBubbleText = Color(0xFF93c5fd),
        glow = Color(0x333b82f6), glowStrong = Color(0x733b82f6), glowGradient = Color(0xFF2563eb),
        toolbar = Color(0xFF0e1624), sidebar = Color(0xFF08101a), sidebarActive = Color(0xFF3b82f6),
    )

    // ===== 5. 翡翠 Dark =====
    val Emerald = StradustColors(
        background = Color(0xFF081410), backgroundSecondary = Color(0xFF102a1e), backgroundTertiary = Color(0xFF183a2d),
        surface = Color(0xFF143024), surfaceDim = Color(0xFF102a1e), surfaceBright = Color(0xFF183a2d),
        surfaceContainerLow = Color(0xFF143024), surfaceContainer = Color(0xFF1c382c), surfaceContainerHigh = Color(0xFF244234), surfaceContainerHighest = Color(0xFF2e4c3c),
        primary = Color(0xFF10b981), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF0a4a32), onPrimaryContainer = Color(0xFFb0f5e0),
        secondary = Color(0xFF6ee7b7), onSecondary = Color(0xFF023824), secondaryContainer = Color(0xFF183a2d), onSecondaryContainer = Color(0xFF6ee7b7),
        tertiary = Color(0xFF5eead4), onTertiary = Color(0xFF02382a), tertiaryContainer = Color(0xFF143830), onTertiaryContainer = Color(0xFF84eedc),
        error = Color(0xFFf87171), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF3a1810), onErrorContainer = Color(0xFFf8a8a8),
        outline = Color(0xFF284d3d), outlineVariant = Color(0xFF204535),
        textPrimary = Color(0xFFe0f8f0), // [修复] 从#b0f5e0改为更亮的近白色，减少绿色调影响
        textSecondary = Color(0xFF90d0b8), // [修复] 从#70b4a0提亮，提高在深绿surface上的可读性
        textMuted = Color(0xFF68a890), // [修复] 从#509080显著提亮，解决文字与背景融合问题
        textDisabled = Color(0xFF487868), textLink = Color(0xFF6ee7b7),
        ripple = Color(0x26ffffff), scrim = Color(0xB3000000),
        userBubbleStart = Color(0xFF10b981), userBubbleEnd = Color(0xFF059669),
        aiBubble = Color(0xFF183a2d), aiBubbleText = Color(0xFF6ee7b7),
        glow = Color(0x3310b981), glowStrong = Color(0x7310b981), glowGradient = Color(0xFF059669),
        toolbar = Color(0xFF0c1a14), sidebar = Color(0xFF081410), sidebarActive = Color(0xFF10b981),
    )

    // ===== 6. 日落 Dark =====
    val Sunset = StradustColors(
        background = Color(0xFF141008), backgroundSecondary = Color(0xFF201c10), backgroundTertiary = Color(0xFF383018),
        surface = Color(0xFF282214), surfaceDim = Color(0xFF201c10), surfaceBright = Color(0xFF383018),
        surfaceContainerLow = Color(0xFF282214), surfaceContainer = Color(0xFF302a18), surfaceContainerHigh = Color(0xFF3a3220), surfaceContainerHighest = Color(0xFF443a28),
        primary = Color(0xFFf59e0b), onPrimary = Color(0xFF141008), primaryContainer = Color(0xFF5a4800), onPrimaryContainer = Color(0xFFf5e8c0),
        secondary = Color(0xFFfde68a), onSecondary = Color(0xFF4a3c00), secondaryContainer = Color(0xFF383018), onSecondaryContainer = Color(0xFFfde68a),
        tertiary = Color(0xFFfcdba9), onTertiary = Color(0xFF4a3c00), tertiaryContainer = Color(0xFF3a2e14), onTertiaryContainer = Color(0xFFfed7aa),
        error = Color(0xFFf87171), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF3a1810), onErrorContainer = Color(0xFFf8a8a8),
        outline = Color(0xFF484028), outlineVariant = Color(0xFF403820),
        textPrimary = Color(0xFFf5e8c0), textSecondary = Color(0xFFb4a480), textMuted = Color(0xFF807860), textDisabled = Color(0xFF585040), textLink = Color(0xFFfde68a),
        ripple = Color(0x26ffffff), scrim = Color(0xB3000000),
        userBubbleStart = Color(0xFFf59e0b), userBubbleEnd = Color(0xFFd97706),
        aiBubble = Color(0xFF383018), aiBubbleText = Color(0xFFfde68a),
        glow = Color(0x33f59e0b), glowStrong = Color(0x73f59e0b), glowGradient = Color(0xFFd97706),
        toolbar = Color(0xFF1a180e), sidebar = Color(0xFF141008), sidebarActive = Color(0xFFf59e0b),
    )

    // ===== 7. 玫瑰金 Dark =====
    val RoseGold = StradustColors(
        background = Color(0xFF140810), backgroundSecondary = Color(0xFF201018), backgroundTertiary = Color(0xFF381830),
        surface = Color(0xFF281420), surfaceDim = Color(0xFF201018), surfaceBright = Color(0xFF381830),
        surfaceContainerLow = Color(0xFF281420), surfaceContainer = Color(0xFF301828), surfaceContainerHigh = Color(0xFF3a2030), surfaceContainerHighest = Color(0xFF442838),
        primary = Color(0xFFe11d48), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF600020), onPrimaryContainer = Color(0xFFf5d0e8),
        secondary = Color(0xFFfbcfe8), onSecondary = Color(0xFF4a1028), secondaryContainer = Color(0xFF381830), onSecondaryContainer = Color(0xFFfbcfe8),
        tertiary = Color(0xFFfbe1ec), onTertiary = Color(0xFF4a1028), tertiaryContainer = Color(0xFF3a1838), onTertiaryContainer = Color(0xFFfbd0e0),
        error = Color(0xFFf87171), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF3a1018), onErrorContainer = Color(0xFFf8a8a8),
        outline = Color(0xFF482840), outlineVariant = Color(0xFF402038),
        textPrimary = Color(0xFFf5d0e8), textSecondary = Color(0xFFb4a0b0), textMuted = Color(0xFF807080), textDisabled = Color(0xFF584858), textLink = Color(0xFFfbcfe8),
        ripple = Color(0x26ffffff), scrim = Color(0xB3000000),
        userBubbleStart = Color(0xFFe11d48), userBubbleEnd = Color(0xFFbe123c),
        aiBubble = Color(0xFF381830), aiBubbleText = Color(0xFFfbcfe8),
        glow = Color(0x33e11d48), glowStrong = Color(0x73e11d48), glowGradient = Color(0xFFbe123c),
        toolbar = Color(0xFF1a0e16), sidebar = Color(0xFF140810), sidebarActive = Color(0xFFe11d48),
    )

    // ===== 8. 薄荷 Dark =====
    val Mint = StradustColors(
        background = Color(0xFF081412), backgroundSecondary = Color(0xFF102618), backgroundTertiary = Color(0xFF183830),
        surface = Color(0xFF142824), surfaceDim = Color(0xFF102618), surfaceBright = Color(0xFF183830),
        surfaceContainerLow = Color(0xFF142824), surfaceContainer = Color(0xFF1c302c), surfaceContainerHigh = Color(0xFF243834), surfaceContainerHighest = Color(0xFF2e403c),
        primary = Color(0xFF14b8a6), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF0a4a3a), onPrimaryContainer = Color(0xFFb0f5ee),
        secondary = Color(0xFF5eead4), onSecondary = Color(0xFF02382a), secondaryContainer = Color(0xFF183830), onSecondaryContainer = Color(0xFF5eead4),
        tertiary = Color(0xFF84eedc), onTertiary = Color(0xFF02382a), tertiaryContainer = Color(0xFF143030), onTertiaryContainer = Color(0xFFa8f0e4),
        error = Color(0xFFf87171), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF3a1810), onErrorContainer = Color(0xFFf8a8a8),
        outline = Color(0xFF284840), outlineVariant = Color(0xFF204038),
        textPrimary = Color(0xFFb0f5ee), textSecondary = Color(0xFF70aeaa), textMuted = Color(0xFF508e8a), textDisabled = Color(0xFF386a66), textLink = Color(0xFF5eead4),
        ripple = Color(0x26ffffff), scrim = Color(0xB3000000),
        userBubbleStart = Color(0xFF14b8a6), userBubbleEnd = Color(0xFF0d9488),
        aiBubble = Color(0xFF183830), aiBubbleText = Color(0xFF5eead4),
        glow = Color(0x3314b8a6), glowStrong = Color(0x7314b8a6), glowGradient = Color(0xFF0d9488),
        toolbar = Color(0xFF0c1a16), sidebar = Color(0xFF081412), sidebarActive = Color(0xFF14b8a6),
    )

    // ===== 9. 暗夜 Midnight (纯暗色) =====
    val Midnight = StradustColors(
        background = Color(0xFF0c0c18), backgroundSecondary = Color(0xFF16162e), backgroundTertiary = Color(0xFF25254a),
        surface = Color(0xFF1c1c32), surfaceDim = Color(0xFF16162e), surfaceBright = Color(0xFF25254a),
        surfaceContainerLow = Color(0xFF1c1c32), surfaceContainer = Color(0xFF22223e), surfaceContainerHigh = Color(0xFF2e2e4a), surfaceContainerHighest = Color(0xFF3a3a56),
        primary = Color(0xFF6366f1), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF2a2a58), onPrimaryContainer = Color(0xFFc4c8ff),
        secondary = Color(0xFFa5b4fc), onSecondary = Color(0xFF202058), secondaryContainer = Color(0xFF25254a), onSecondaryContainer = Color(0xFFa5b4fc),
        tertiary = Color(0xFF818cf8), onTertiary = Color(0xFF202060), tertiaryContainer = Color(0xFF282850), onTertiaryContainer = Color(0xFFc0c4f8),
        error = Color(0xFFf87171), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF3a1820), onErrorContainer = Color(0xFFf8a8a8),
        outline = Color(0xFF33355c), outlineVariant = Color(0xFF2b2d52),
        textPrimary = Color(0xFFf0f0ff), // [修复] 从#dce0f0改为近白色，提高在深色背景上的对比度至7:1以上
        textSecondary = Color(0xFFb0b8d0), // [修复] 从#8896b8大幅提亮，确保在surface上可读性≥4.5:1
        textMuted = Color(0xFF8890a8), // [修复] 从#606c88显著提亮，解决"看不清"问题
        textDisabled = Color(0xFF586078), textLink = Color(0xFFa5b4fc),
        ripple = Color(0x26ffffff), scrim = Color(0xB3000000),
        userBubbleStart = Color(0xFF6366f1), userBubbleEnd = Color(0xFF818cf8),
        aiBubble = Color(0xFF25254a), aiBubbleText = Color(0xFFa5b4fc),
        glow = Color(0x2e6366f1), glowStrong = Color(0x6b6366f1), glowGradient = Color(0xFF818cf8),
        toolbar = Color(0xFF16162e), sidebar = Color(0xFF0c0c18), sidebarActive = Color(0xFF6366f1),
    )

    // ===== 10. 茶香 Dark =====
    val Tea = StradustColors(
        background = Color(0xFF12100c), backgroundSecondary = Color(0xFF1c1a14), backgroundTertiary = Color(0xFF2e2c1e),
        surface = Color(0xFF222016), surfaceDim = Color(0xFF1c1a14), surfaceBright = Color(0xFF2e2c1e),
        surfaceContainerLow = Color(0xFF222016), surfaceContainer = Color(0xFF2a281c), surfaceContainerHigh = Color(0xFF343024), surfaceContainerHighest = Color(0xFF3e382c),
        primary = Color(0xFF7da36a), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF304828), onPrimaryContainer = Color(0xFFe8e0cc),
        secondary = Color(0xFFb8c89a), onSecondary = Color(0xFF203018), secondaryContainer = Color(0xFF2e2c1e), onSecondaryContainer = Color(0xFFb8c89a),
        tertiary = Color(0xFFc8dda8), onTertiary = Color(0xFF203018), tertiaryContainer = Color(0xFF2e2e1e), onTertiaryContainer = Color(0xFFd8e8b8),
        error = Color(0xFFf87171), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF3a1810), onErrorContainer = Color(0xFFf8a8a8),
        outline = Color(0xFF3e3c28), outlineVariant = Color(0xFF363424),
        textPrimary = Color(0xFFf8f0e0), // [修复] 从#e8e0cc改为更亮的暖白色，提高在深棕背景上的对比度
        textSecondary = Color(0xFFc8b898), // [修复] 从#999078提亮，确保可读性
        textMuted = Color(0xFFa09878), // [修复] 从#706858显著提亮，解决文字模糊问题
        textDisabled = Color(0xFF787058), textLink = Color(0xFFb8c89a),
        ripple = Color(0x26ffffff), scrim = Color(0xB3000000),
        userBubbleStart = Color(0xFF7da36a), userBubbleEnd = Color(0xFF6b8e5a),
        aiBubble = Color(0xFF2e2c1e), aiBubbleText = Color(0xFFb8c89a),
        glow = Color(0x2e7da36a), glowStrong = Color(0x667da36a), glowGradient = Color(0xFF6b8e5a),
        toolbar = Color(0xFF1a1812), sidebar = Color(0xFF12100c), sidebarActive = Color(0xFF7da36a),
    )

    // ===== 11. 赛博朋克 Cyberpunk (纯暗色霓虹) =====
    val Cyberpunk = StradustColors(
        background = Color(0xFF050510), backgroundSecondary = Color(0xFF0a0a1e), backgroundTertiary = Color(0xFF151530),
        surface = Color(0xFF0e0e24), surfaceDim = Color(0xFF0a0a1e), surfaceBright = Color(0xFF151530),
        surfaceContainerLow = Color(0xFF0e0e24), surfaceContainer = Color(0xFF141430), surfaceContainerHigh = Color(0xFF1a1a3c), surfaceContainerHighest = Color(0xFF202044),
        primary = Color(0xFF00f0ff), onPrimary = Color(0xFF000000), primaryContainer = Color(0xFF006870), onPrimaryContainer = Color(0xFFd0e0ff),
        secondary = Color(0xFF80d0ff), onSecondary = Color(0xFF002850), secondaryContainer = Color(0xFF151530), onSecondaryContainer = Color(0xFF80d0ff),
        tertiary = Color(0xFFbf00ff), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFF2a1048), onTertiaryContainer = Color(0xFFe8c8ff),
        error = Color(0xFFff0055), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF4a0018), onErrorContainer = Color(0xFFffa0aa),
        outline = Color(0xFF202048), outlineVariant = Color(0xFF18183c),
        textPrimary = Color(0xFFf0f8ff), // [修复] 从#d0e0ff改为更亮的青白色，确保在极暗背景上清晰可见
        textSecondary = Color(0xFF90b8d8), // [修复] 从#6080a0大幅提亮42%，解决霓虹背景下文字模糊问题
        textMuted = Color(0xFF6890b0), // [修复] 从#405870显著提亮，确保次要信息可读
        textDisabled = Color(0xFF486888), textLink = Color(0xFF80d0ff),
        ripple = Color(0x2600f0ff), scrim = Color(0xCC000000),
        userBubbleStart = Color(0xFF00f0ff), userBubbleEnd = Color(0xFFbf00ff),
        aiBubble = Color(0xFF151530), aiBubbleText = Color(0xFF80d0ff),
        glow = Color(0x4000f0ff), glowStrong = Color(0x8c00f0ff), glowGradient = Color(0xFFbf00ff),
        toolbar = Color(0xFF0a0a1e), sidebar = Color(0xFF050510), sidebarActive = Color(0xFF00f0ff),
    )

    // ===== 12. 华夏风韵 Dark =====
    val Chinese = StradustColors(
        background = Color(0xFF100c08), backgroundSecondary = Color(0xFF1a1410), backgroundTertiary = Color(0xFF2e2418),
        surface = Color(0xFF241c14), surfaceDim = Color(0xFF1a1410), surfaceBright = Color(0xFF2e2418),
        surfaceContainerLow = Color(0xFF241c14), surfaceContainer = Color(0xFF2c241c), surfaceContainerHigh = Color(0xFF362c24), surfaceContainerHighest = Color(0xFF40342c),
        primary = Color(0xFFe0454a), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF5a1820), onPrimaryContainer = Color(0xFFf0e4d0),
        secondary = Color(0xFFd4a05a), onSecondary = Color(0xFF381010), secondaryContainer = Color(0xFF2e2418), onSecondaryContainer = Color(0xFFd4a05a),
        tertiary = Color(0xFFe08030), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFF3a1818), onTertiaryContainer = Color(0xFFf0a090),
        error = Color(0xFFf87171), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF3a1810), onErrorContainer = Color(0xFFf8a8a8),
        outline = Color(0xFF3e3428), outlineVariant = Color(0xFF362c20),
        textPrimary = Color(0xFFf0e4d0), textSecondary = Color(0xFFa08070), textMuted = Color(0xFF786050), textDisabled = Color(0xFF504038), textLink = Color(0xFFd4a05a),
        ripple = Color(0x26ffffff), scrim = Color(0xB3000000),
        userBubbleStart = Color(0xFFe0454a), userBubbleEnd = Color(0xFFc53d43),
        aiBubble = Color(0xFF2e2418), aiBubbleText = Color(0xFFd4a05a),
        glow = Color(0x2ee0454a), glowStrong = Color(0x6be0454a), glowGradient = Color(0xFFc53d43),
        toolbar = Color(0xFF1a1410), sidebar = Color(0xFF100c08), sidebarActive = Color(0xFFe0454a),
    )

    /** 根据 ThemeId 获取对应的 Dark Scheme */
    fun fromId(id: ThemeId): StradustColors = when (id) {
        ThemeId.SAKURA -> Sakura
        ThemeId.PEACH -> Peach
        ThemeId.VIOLET -> Violet
        ThemeId.OCEAN -> Ocean
        ThemeId.EMERALD -> Emerald
        ThemeId.SUNSET -> Sunset
        ThemeId.ROSEGOLD -> RoseGold
        ThemeId.MINT -> Mint
        ThemeId.MIDNIGHT -> Midnight
        ThemeId.TEA -> Tea
        ThemeId.CYBERPUNK -> Cyberpunk
        ThemeId.CHINESE -> Chinese
        else -> Sakura
    }
}
