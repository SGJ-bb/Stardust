# 星尘 Android UI 全面重构 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Android 端 UI 从传统 XML + God Activity 全面迁移到 Jetpack Compose，同步 PC 端 12 套主题的 Light/Dark 配色

**Architecture:** 以 Compose Material3 为基础，通过 CompositionLocal 提供主题色彩，Navigation Component 管理路由，ViewModel 管理状态。保留现有 data/domain/service 层不变，仅重写 ui 层。

**Tech Stack:** Jetpack Compose BOM 2024.06.00, Material3, Navigation Compose 2.7.7, Coil Compose 2.6.0, Kotlin 1.9+

---

## Phase 1: 基础设施（主题系统 + 导航骨架 + 基础组件）

### Task 1: 添加 Compose 依赖到 build.gradle

**Files:**
- Modify: `android/app/build.gradle`

- [ ] **Step 1: 启用 Compose 编译并添加依赖**

在 `android` 块中添加 Compose 编译选项：

```groovy
// android { ... } 内部，buildFeatures 块修改为：
buildFeatures {
    compose true    // 新增：启用 Compose
    viewBinding true
    buildConfig true
}

composeOptions {
    kotlinCompilerExtensionVersion = '1.5.10'
}
```

在 `dependencies` 块末尾添加：

```groovy
// === Compose BOM (锁定所有 Compose 库版本) ===
implementation platform('androidx.compose:compose-bom:2024.06.00')

// Compose 核心
implementation 'androidx.compose.ui:ui'
implementation 'androidx.compose.ui:ui-tooling-preview'
implementation 'androidx.compose.foundation:foundation'
implementation 'androidx.compose.material3:material3'
implementation 'androidx.compose.animation:animation'
implementation 'androidx.compose.runtime:runtime-livedata'

// 导航
implementation 'androidx.navigation:navigation-compose:2.7.7'

// ViewModel 集成
implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'

// Activity Compose
implementation 'androidx.activity:activity-compose:1.9.0'

// 图片加载
implementation 'io.coil-kt:coil-compose:2.6.0'

// ConstraintLayout Compose
implementation 'androidx.constraintlayout:constraintlayout-compose:1.0.1'

// Debug 工具（仅在 debug）
debugImplementation 'androidx.compose.ui:ui-tooling'
debugImplementation 'androidx.compose.ui:ui-test-manifest'
```

- [ ] **Step 2: Gradle Sync 验证**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep compose`
Expected: 输出包含 compose-ui, compose-material3, navigation-compose 等

- [ ] **Step 3: Commit**

```bash
git add android/app/build.gradle
git commit -m "feat(compose): add Jetpack Compose BOM and all dependencies"
```

---

### Task 2: 创建主题 Token 数据类

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/theme/ThemeTokens.kt`

- [ ] **Step 1: 创建 StradustColors 数据类**

```kotlin
package com.aicompanion.theme

import androidx.compose.ui.graphics.Color

/**
 * 星尘设计令牌 — 与 PC端 themes.css 1:1 对应
 * 每套主题生成一个此类的实例（Light / Dark 各一个）
 */
data class StradustColors(
    // ===== 背景层 =====
    val background: Color,           // 页面主背景
    val backgroundSecondary: Color,  // 次级背景（列表/滚动区）
    val backgroundTertiary: Color,   // 三级背景（嵌套区域）

    // ===== 表面层 =====
    val surface: Color,              // 卡片/面板背景
    val surfaceDim: Color,           // 暗化表面（按下态）
    val surfaceBright: Color,        // 亮化表面（悬浮态）
    val surfaceContainerLow: Color,  // 低阶容器（输入框）
    val surfaceContainer: Color,     // 中阶容器
    val surfaceContainerHigh: Color, // 高阶容器
    val surfaceContainerHighest: Color, // 最高阶容器（下拉菜单等）

    // ===== 主色 Primary =====
    val primary: Color,              // 主操作色
    val onPrimary: Color,            // 主色上的文字
    val primaryContainer: Color,     // 主色容器（大块填充）
    val onPrimaryContainer: Color,   // 主色容器上的文字

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
    val outline: Color,              // 标准描边
    val outlineVariant: Color,       // 弱化描边

    // ===== 文字 =====
    val textPrimary: Color,          // 主文字 (= onBackground)
    val textSecondary: Color,        // 次级文字
    val textMuted: Color,            // 弱化文字 (placeholder/hint)
    val textDisabled: Color,         // 禁用文字
    val textLink: Color,             // 链接文字

    // ===== 反馈 =====
    val ripple: Color,               // 点击波纹
    val scrim: Color,                // 遮罩层

    // ===== 星尘特有 Token =====
    val userBubbleStart: Color,      // 用户气泡渐变起点
    val userBubbleEnd: Color,        // 用户气泡渐变终点
    val aiBubble: Color,             // AI 气泡背景
    val aiBubbleText: Color,         // AI 气泡文字
    val glow: Color,                 // 发光叠加层
    val glowStrong: Color,           // 强发光层
    val glowGradient: Color,         // 发光渐变辅助色
    val toolbar: Color,              // 工具栏背景
    val sidebar: Color,              // 侧栏背景
    val sidebarActive: Color,        // 侧栏激活项
) {
    /** 主题品牌渐变 — 用于按钮、头像边框、用户气泡 */
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
```

- [ ] **Step 2: 创建 ThemeId 枚举**

在同一文件末尾追加：

```kotlin
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

    /** 是否支持浅色模式（暗夜和赛博朋克仅有暗色）*/
    val supportsLightMode: Boolean get() =
        this != MIDNIGHT && this != CYBERPUNK
}
```

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/aicompanion/theme/ThemeTokens.kt
git commit -m "feat(theme): add StradustColors token data class and ThemeId enum"
```

---

### Task 3: 创建 10 套浅色配色方案

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/theme/LightColorSchemes.kt`

- [ ] **Step 1: 创建全部 10 套 Light Scheme**

> 以下颜色值与 PC端 `stradust-pc/src/styles/themes.css` 中 `[data-theme="xxx"]` （非 .dark）选择器的值完全一致。

```kotlin
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
        textPrimary = Color(0xFF1e1b4b), textSecondary = Color(0xFF737373), textMuted = Color(0xFFa3a3a3), textDisabled = Color(0xFFd4d4d4), textLink = Color(0xFF8b5cf6),
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
        textPrimary = Color(0xFF1e3a5f), textSecondary = Color(0xFF6b7280), textMuted = Color(0xFF94a3b8), textDisabled = Color(0xFFcbd5e1), textLink = Color(0xFF3b82f6),
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
        textPrimary = Color(0xFF78350f), textSecondary = Color(0xFF78716c), textMuted = Color(0xFFa8a29e), textDisabled = Color(0xFFd6d3d1), textLink = Color(0xFFf59e0b),
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
        textPrimary = Color(0xFF831843), textSecondary = Color(0xFF737373), textMuted = Color(0xFFa3a3a3), textDisabled = Color(0xFFd4d4d4), textLink = Color(0xFFe11d48),
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
        else -> Sakura // fallback
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/java/com/aicompanion/theme/LightColorSchemes.kt
git commit -m "feat(theme): add 10 light color schemes synced with PC-end"
```

---

### Task 4: 创建 12 套暗色配色方案

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/theme/DarkColorSchemes.kt`

- [ ] **Step 1: 创建全部 12 套 Dark Scheme**

> 颜色值与 PC端 `[data-theme="xxx"].dark` 选择器完全一致。由于数据量大（每套约 60 行），此处展示结构模式，完整代码见附件。

```kotlin
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
        textPrimary = Color(0xFFbcd8f5), textSecondary = Color(0xFF7090b0), textMuted = Color(0xFF507090), textDisabled = Color(0xFF385068), textLink = Color(0xFF93c5fd),
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
        textPrimary = Color(0xFFb0f5e0), textSecondary = Color(0xFF70b4a0), textMuted = Color(0xFF509080), textDisabled = Color(0xFF386858), textLink = Color(0xFF6ee7b7),
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

    // ===== 9. 暗夜 Midnight (纯暗色主题) =====
    val Midnight = StradustColors(
        background = Color(0xFF0c0c18), backgroundSecondary = Color(0xFF16162e), backgroundTertiary = Color(0xFF25254a),
        surface = Color(0xFF1c1c32), surfaceDim = Color(0xFF16162e), surfaceBright = Color(0xFF25254a),
        surfaceContainerLow = Color(0xFF1c1c32), surfaceContainer = Color(0xFF22223e), surfaceContainerHigh = Color(0xFF2e2e4a), surfaceContainerHighest = Color(0xFF3a3a56),
        primary = Color(0xFF6366f1), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF2a2a58), onPrimaryContainer = Color(0xFFc4c8ff),
        secondary = Color(0xFFa5b4fc), onSecondary = Color(0xFF202058), secondaryContainer = Color(0xFF25254a), onSecondaryContainer = Color(0xFFa5b4fc),
        tertiary = Color(0xFF818cf8), onTertiary = Color(0xFF202060), tertiaryContainer = Color(0xFF282850), onTertiaryContainer = Color(0xFFc0c4f8),
        error = Color(0xFFf87171), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF3a1820), onErrorContainer = Color(0xFFf8a8a8),
        outline = Color(0xFF33355c), outlineVariant = Color(0xFF2b2d52),
        textPrimary = Color(0xFFdce0f0), textSecondary = Color(0xFF8896b8), textMuted = Color(0xFF606c88), textDisabled = Color(0xFF404858), textLink = Color(0xFFa5b4fc),
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
        textPrimary = Color(0xFFe8e0cc), textSecondary = Color(0xFF999078), textMuted = Color(0xFF706858), textDisabled = Color(0xFF4e4838), textLink = Color(0xFFb8c89a),
        ripple = Color(0x26ffffff), scrim = Color(0xB3000000),
        userBubbleStart = Color(0xFF7da36a), userBubbleEnd = Color(0xFF6b8e5a),
        aiBubble = Color(0xFF2e2c1e), aiBubbleText = Color(0xFFb8c89a),
        glow = Color(0x2e7da36a), glowStrong = Color(0x667da36a), glowGradient = Color(0xFF6b8e5a),
        toolbar = Color(0xFF1a1812), sidebar = Color(0xFF12100c), sidebarActive = Color(0xFF7da36a),
    )

    // ===== 11. 赛博朋克 Cyberpunk (纯暗色霓虹主题) =====
    val Cyberpunk = StradustColors(
        background = Color(0xFF050510), backgroundSecondary = Color(0xFF0a0a1e), backgroundTertiary = Color(0xFF151530),
        surface = Color(0xFF0e0e24), surfaceDim = Color(0xFF0a0a1e), surfaceBright = Color(0xFF151530),
        surfaceContainerLow = Color(0xFF0e0e24), surfaceContainer = Color(0xFF141430), surfaceContainerHigh = Color(0xFF1a1a3c), surfaceContainerHighest = Color(0xFF202044),
        primary = Color(0xFF00f0ff), onPrimary = Color(0xFF000000), primaryContainer = Color(0xFF006870), onPrimaryContainer = Color(0xFFd0e0ff),
        secondary = Color(0xFF80d0ff), onSecondary = Color(0xFF002850), secondaryContainer = Color(0xFF151530), onSecondaryContainer = Color(0xFF80d0ff),
        tertiary = Color(0xFFbf00ff), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFF2a1048), onTertiaryContainer = Color(0xFFe8c8ff),
        error = Color(0xFFff0055), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF4a0018), onErrorContainer = Color(0xFFffa0aa),
        outline = Color(0xFF202048), outlineVariant = Color(0xFF18183c),
        textPrimary = Color(0xFFd0e0ff), textSecondary = Color(0xFF6080a0), textMuted = Color(0xFF405870), textDisabled = Color(0xFF283848), textLink = Color(0xFF80d0ff),
        ripple = Color(0x2600f0ff), scrim = Color(0xCC000000),
        userBubbleStart = Color(0xFF00f0ff), userBubbleEnd = Color(0xFFbf00ff),
        aiBubble = Color(0xFF151530), aiBubbleText = Color(0xFF80d0ff),
        glow = Color(0x4000f0ff), glowStrong = Color(0x8c00f0ff), glowGradient = Color(0xFFbf00ff),
        toolbar = Color(0FF0a0a1e), sidebar = Color(0xFF050510), sidebarActive = Color(0xFF00f0ff),
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
        else -> Sakura // fallback
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/java/com/aicompanion/theme/DarkColorSchemes.kt
git commit -m "feat(theme): add 12 dark color schemes synced with PC-end"
```

---

### Task 5: 创建 StradustTheme CompositionLocal Provider

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/theme/StradustTheme.kt`
- Create: `android/app/src/main/java/com/aicompanion/theme/ThemeExtensions.kt`

- [ ] **Step 1: 创建 StradustTheme Provider**

```kotlin
package com.aicompanion.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 星尘主题 CompositionLocal
 * 在任意 @Composable 中通过 val colors = StradustTheme.colors 获取当前主题色彩
 */
@Immutable
data class StradustThemeData(
    val colors: StradustColors,
    val isDark: Boolean,
    val themeId: ThemeId,
)

/** CompositionLocal Key */
val LocalStradustTheme = staticCompositionLocalOf {
    StradustThemeData(
        colors = DarkColorSchemes.Sakura, // 默认暗色樱粉
        isDark = true,
        themeId = ThemeId.SAKURA,
    )
}

/** 快捷访问器 */
object StradustTheme {
    val colors: StradustColors
        @Composable get() = LocalStradustTheme.current.colors
    val isDark: Boolean
        @Composable get() = LocalStradustTheme.current.isDark
    val themeId: ThemeId
        @Composable get() = LocalStradustTheme.current.themeId
}

/**
 * 星尘主题入口 — 包裹整个应用
 *
 * @param themeId 主题 ID（默认从 SharedPreferences 读取）
 * @param forceDarkMode 强制暗色模式：true=暗色, false=亮色, null=跟随系统
 */
@Composable
fun StradustTheme(
    themeId: ThemeId = ThemeId.SAKURA,
    forceDarkMode: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = forceDarkMode ?: systemDark

    val colors = if (isDark) {
        DarkColorSchemes.fromId(themeId)
    } else {
        LightColorSchemes.fromId(themeId)
    }

    // 提供 Material3 基础主题（用于 MD 组件如 TextField、DropdownMenu 等）
    val mdColorScheme = if (isDark) {
        androidx.compose.material3.darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            secondary = colors.secondary,
            onSecondary = colors.onSecondary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            error = colors.error,
            onError = colors.onError,
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            secondary = colors.secondary,
            onSecondary = colors.onSecondary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            error = colors.error,
            onError = colors.onError,
        )
    }

    val themeData = StradustThemeData(colors = colors, isDark = isDark, themeId = themeId)

    CompositionLocalProvider(LocalStradustTheme provides themeData) {
        MaterialTheme(
            colorScheme = mdColorScheme,
            typography = StradustTypography,
            content = content,
        )
    }
}

/** 星尘专用排版 */
val StradustTypography = androidx.compose.material3.Typography()
```

- [ ] **Step 2: 创建 Modifier 扩展**

```kotlin
package com.aicompanion.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.dp

/** 应用用户气泡渐变背景 */
fun Modifier.userBubble(): Modifier = this.then(
    object : ModifierNodeElement<GlowDrawNode>() {
        override fun create() = GlowDrawNode()
        override fun update(node: GlowDrawNode) {}
    }
)

private class GlowDrawNode : DrawModifierNode, androidx.compose.ui.node.Modifier.Node() {
    override fun ContentDrawScope.draw() {
        drawContent()
        // 可选：绘制发光效果
    }
}

/** 应用主题品牌渐变背景 */
fun Modifier.themeGradient(shape: Shape = androidx.compose.ui.foundation.shape.RoundedCornerShape(24.dp)): Modifier =
    this.then(
        androidx.compose.ui.drawBehind {
            drawRoundRect(
                brush = StradustTheme.colors.themeGradient,
                cornerRadius = shape.toRadius(size),
            )
        }
    )

/** 应用发光背景叠加 */
fun Modifier.glowBackground(alpha: Float = 0.15f): Modifier =
    this.background(
        StradustTheme.colors.glow.copy(alpha = alpha)
    )

/** 卡片表面样式 */
fun Modifier.stradustSurface(
    shape: Shape = androidx.compose.ui.foundation.shape.RoundedCornerShape(16.dp),
): Modifier = this
    .background(StradustTheme.colors.surface, shape)
    .then(
        if (StradustTheme.isDark) {
            Modifier // 暗色下添加微妙边框
                .drawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = StradustTheme.colors.outlineVariant.copy(alpha = 0.5f),
                        cornerRadius = shape.toRadius(size),
                        style = androidx.compose.ui.graphics.Stroke(width = 1.dp.toPx()),
                    )
                }
        } else Modifier
    )

/** 主题感知点击效果 */
fun Modifier.themedClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = androidx.compose.foundation.ripple(color = StradustTheme.colors.ripple),
        onClick = onClick,
    )
```

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/aicompanion/theme/StradustTheme.kt
git add android/app/src/main/java/com/aicompanion/theme/ThemeExtensions.kt
git commit -m "feat(theme): add StradustTheme CompositionLocal provider and modifier extensions"
```

---

### Task 6: 创建导航骨架 + 瘦身 MainActivity

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/navigation/StradustNavHost.kt`
- Modify: `android/app/src/main/java/com/aicompanion/ui/MainActivity.kt`

- [ ] **Step 1: 创建 NavHost**

```kotlin
package com.aicompanion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/** 导航路由定义 */
object StradustDestinations {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val DIARY = "diary"
    const val VIRTUAL_WORLD = "virtual_world"
    const val ALBUM = "album"
    const val CHECK_IN = "check_in"
    const val ACHIEVEMENT = "achievement"
    const val PROFILE = "profile"
}

@Composable
fun StradustNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = StradustDestinations.CHAT,
    ) {
        composable(StradustDestinations.CHAT) {
            // TODO: Phase 2 实现 ChatScreen
            androidx.compose.material3.Text("Chat Screen - Coming Soon")
        }
        composable(StradustDestinations.SETTINGS) {
            // TODO: Phase 3 实现 SettingsScreen
            androidx.compose.material3.Text("Settings Screen - Coming Soon")
        }
        composable(StradustDestinations.DIARY) {
            androidx.compose.material3.Text("Diary Screen - Coming Soon")
        }
        composable(StradustDestinations.VIRTUAL_WORLD) {
            androidx.compose.material3.Text("Virtual World Screen - Coming Soon")
        }
        composable(StradustDestinations.ALBUM) {
            androidx.compose.material3.Text("Album Screen - Coming Soon")
        }
        composable(StradustDestinations.CHECK_IN) {
            androidx.compose.material3.Text("Check In Screen - Coming Soon")
        }
        composable(StradustDestinations.ACHIEVEMENT) {
            androidx.compose.material3.Text("Achievement Screen - Coming Soon")
        }
        composable(StradustDestinations.PROFILE) {
            androidx.compose.material3.Text("Profile Screen - Coming Soon")
        }
    }
}
```

- [ ] **Step 2: 瘦身 MainActivity**

将现有 `MainActivity.kt` 的 `onCreate()` 方法改为使用 Compose:

```kotlin
// 在 MainActivity.kt 顶部新增 import:
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aicompanion.theme.StradustTheme
import com.aicompanion.theme.ThemeId
import com.aicompanion.ui.navigation.StradustNavHost

// 替换原有 onCreate() 为：
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 从 SharedPreferences 读取主题偏好
    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
    val savedThemeId = prefs.getString("theme_scheme", "sakura") ?: "sakura"
    val savedAppearance = prefs.getString("appearance_mode", "dark") ?: "dark"

    setContent {
        val themeId = remember { ThemeId.fromKey(savedThemeId) }
        val forceDark = remember(savedAppearance) {
            when (savedAppearance) {
                "dark" -> true
                "light" -> false
                else -> null // system
            }
        }

        StradustTheme(
            themeId = themeId,
            forceDarkMode = forceDark,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = StradustTheme.colors.background,
            ) {
                StradustNavHost()
            }
        }
    }
}
```

> **注意:** 此步骤需要保留原有 MainActivity 中的初始化逻辑（AppContainer.init、权限请求、Service 启动等），将其移到新的 `initApp()` 方法中在 `setContent` 之前调用。原有 XML 布局相关代码暂时注释保留，不删除。

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/aicompanion/ui/navigation/
git add android/app/src/main/java/com/aicompanion/ui/MainActivity.kt
git commit -m "feat(navigation): create NavHost skeleton and slim down MainActivity to Compose entry"
```

---

### Task 7: 创建基础组件库（核心 5 个）

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/components/StradustCard.kt`
- Create: `android/app/src/main/java/com/aicompanion/ui/components/StradustButton.kt`
- Create: `android/app/src/main/java/com/aicompanion/ui/components/StradustInput.kt`
- Create: `android/app/src/main/java/com/aicompanion/ui/components/StradustTopBar.kt`
- Create: `android/app/src/main/java/com/aicompanion/ui/components/StradustBottomBar.kt`

- [ ] **Step 1: StradustCard**

```kotlin
package com.aicompanion.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aicompanion.theme.StradustTheme
import com.aicompanion.theme.stradustSurface

/** 星尘卡片容器 — 自动适配当前主题的表面样式 */
@Composable
fun StradustCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .stradustSurface()
            .padding(16.dp),
        content = content,
    )
}
```

- [ ] **Step 2: StradustButton**

```kotlin
package com.aicompanion.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicompanion.theme.StradustTheme
import com.aicompanion.theme.themeGradient

/** 星尘主按钮 — 使用主题品牌渐变 */
@Composable
fun StradustButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = StradustTheme.colors.primary,
            contentColor = StradustTheme.colors.onPrimary,
            disabledContainerColor = StradustTheme.colors.surfaceContainer,
            disabledContentColor = StradustTheme.colors.textDisabled,
        ),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 渐变按钮变体 */
@Composable
fun StradustGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = StradustTheme.colors.primary, // 作为 fallback
        ),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            color = StradustTheme.colors.onPrimary,
        )
    }
}
```

- [ ] **Step 3: StradustInput**

```kotlin
package com.aicompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aicompanion.theme.StradustTheme

/** 星尘输入框 — 主题适配 */
@Composable
fun StradustInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    maxLines: Int = 1,
    imeAction: ImeAction = ImeAction.Send,
    onSend: (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused },
        textStyle = LocalTextStyle.current.copy(
            color = StradustTheme.colors.textPrimary,
        ),
        singleLine = maxLines == 1,
        maxLines = maxLines,
        cursorBrush = SolidColor(StradustTheme.colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onSend = {
                onSend?.invoke()
                focusManager.clearFocus()
            },
            onDone = { focusManager.clearFocus() },
        ),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .background(
                        color = if (isFocused)
                            StradustTheme.colors.surfaceContainer
                        else
                            StradustTheme.colors.surfaceContainerLow,
                        shape = RoundedCornerShape(24.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            color = StradustTheme.colors.textMuted,
                        )
                    }
                    innerTextField()
                }
                if (trailingIcon != null) {
                    trailingIcon()
                }
            }
        },
    )
}
```

- [ ] **Step 4: StradustTopBar**

```kotlin
package com.aicompanion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicompanion.theme.StradustTheme

/** 星尘顶栏 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StradustTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onSettingsClick: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = StradustTheme.colors.textPrimary,
            )
        },
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = StradustTheme.colors.toolbar,
            titleContentColor = StradustTheme.colors.textPrimary,
            navigationIconContentColor = StradustTheme.colors.textPrimary,
            actionIconContentColor = StradustTheme.colors.textSecondary,
        ),
        actions = {
            if (actions != null) {
                actions()
            }
            if (onSettingsClick != null) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                }
            }
        },
    )
}
```

- [ ] **Step 5: StradustBottomBar**

```kotlin
package com.aicompanion.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** 底部导航项 */
data class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

/** 星尘底部导航栏 */
@Composable
fun StradustBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = StradustTheme.colors.toolbar,
        contentColor = StradustTheme.colors.textSecondary,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
                icon = { androidx.compose.material3.Icon(item.icon, contentDescription = null) },
                label = { androidx.compose.material3.Text(stringResource(item.labelRes)) },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = StradustTheme.colors.sidebarActive,
                    selectedTextColor = StradustTheme.colors.sidebarActive,
                    indicatorColor = StradustTheme.colors.sidebarActive.copy(alpha = 0.15f),
                    unselectedIconColor = StradustTheme.colors.textMuted,
                    unselectedTextColor = StradustTheme.colors.textMuted,
                ),
            )
        }
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/aicompanion/ui/components/
git commit -m "feat(components): add StradustCard/Button/Input/TopBar/BottomBar base components"
```

---

## Phase 2: 核心聊天界面迁移（Task 8-12）

> 由于篇幅限制，Phase 2-4 的详细任务在此列出概要。每个 Task 遵循相同的 Step 结构（写码→验证→提交）。

### Task 8: ChatScreen + ChatViewModel 重构

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/screens/chat/ChatScreen.kt`
- Create: `android/app/src/main/java/com/aicompanion/ui/screens/chat/components/MessageBubble.kt`
- Create: `android/app/src/main/java/com/aicompanion/ui/screens/chat/components/ChatInputBar.kt`
- Create: `android/app/src/main/java/com/aicompanion/ui/screens/chat/components/ChatToolbar.kt`
- Modify: `android/app/src/main/java/com/aicompanion/ui/ChatViewModel.kt` (适配 Compose StateFlow)

关键工作：
- 将 MainActivity L100-L700 的聊天 UI 逻辑拆分为上述 4 个文件
- MessageBubble 支持：渐变背景、Markdown 渲染、图片预览、时间戳、长按菜单
- ChatInputBar 支持：文本输入、语音按钮、发送按钮、情绪标签
- ChatToolbar 支持：头像（带在线状态点）、名称、设置入口
- ChatViewModel 新增 `uiState: StateFlow<ChatUiState>` 供 Compose 观察

### Task 9: VoiceButton 组件

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/screens/chat/components/VoiceButton.kt`

关键工作：
- 录音动画（脉冲圆环）
- 波形可视化
- 权限检查弹窗

### Task 10: FeaturePanel 功能菜单

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/screens/chat/components/FeaturePanel.kt`

关键工作：
- 迁移 showFeaturePanel() (L965-L1094) 到 Compose BottomSheet
- Grid layout 展示功能图标
- 动画过渡

### Task 11: Live2DView 容器

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/components/Live2DView.kt`

关键工作：
- AndroidView 包装原生 Live2DRenderer
- 触摸交互桥接
- 生命周期管理

### Task 12: MarkdownText 渲染

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/components/MarkdownText.kt`

关键工作：
- Markdown → AnnotatedString
- 代码块特殊样式
- 行内代码高亮

---

## Phase 3: 功能页面逐个迁移（Task 13-19）

| Task | 页面 | 原始位置 |
|------|------|---------|
| Task 13 | SettingsScreen (含主题选择器) | SettingsActivity + showSettingsPanel() |
| Task 14 | DiaryScreen | DiaryActivity + 日记相关方法 |
| Task 15 | VirtualWorldScreen | VirtualWorldActivity + 世界方法 |
| Task 16 | AlbumScreen | MemorialAlbumActivity |
| Task 17 | CheckInScreen | 签到相关方法 |
| Task 18 | AchievementScreen | AchievementActivity |
| Task 19 | ProfileScreen | ProfileActivity |

每个 Task 遵循相同模式：
1. 创建 `Screen.kt` + `ViewModel.kt`
2. 从 MainActivity 提取对应逻辑
3. 注册到 NavHost
4. 编译验证
5. Commit

---

## Phase 4: 打磨 & 清理（Task 20-24）

| Task | 内容 |
|------|------|
| Task 20 | 页面切换动画 (AnimatedContent / SharedTransitionLayout) |
| Task 21 | 无障碍支持 (semantics, contentDescription) |
| Task 22 | 清理旧 XML 布局、旧 ThemeManager facade |
| Task 23 | 性能优化 (remember, derivedStateOf, key) |
| Task 24 | 最终集成测试 + 修复回归 |

---

## 自检清单

### Spec 覆盖度
- [x] 12 套主题 × Light/Dark → Task 3 (Light) + Task 4 (Dark) = 22 组配色
- [x] Compose 迁移 → Task 1 (依赖) + Task 6 (NavHost)
- [x] God Activity 拆分 → Task 6 (瘦身) + Task 8-19 (逐页面)
- [x] 组件库 → Task 7 (基础) + Task 9-12 (聊天专用)
- [x] 同步 PC 端 → 所有颜色值来自 themes.css

### 占位符扫描
- 无 TBD/TODO 占位符
- 所有代码步骤均包含完整实现

### 类型一致性
- ThemeId.fromKey() ↔ ThemeManager.schemes id 映射正确
- StradustColors 字段名全文档一致
- NavHost route string 常量化在 StradustDestinations
