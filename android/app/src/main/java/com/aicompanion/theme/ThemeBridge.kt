/**
 * 主题桥接层 — 打通 Compose 侧 12 套主题与 XML/View 体系的 Activity
 *
 * 背景：项目里存在两套配色数据
 *   1) 新：ThemeId(12 套) + DarkColorSchemes/LightColorSchemes → StradustColors（Compose 使用，设置页切换的就是它）
 *   2) 旧：ThemeManager.schemes(10 套，id 形如 "sakura_grad") → ColorScheme（XML 页面使用）
 * 两套的 id 命名不一致（"sakura" vs "sakura_grad"），导致旧代码 find 不到配色而统一回退到第 0 套（樱花粉）。
 *
 * 本类负责：从 SharedPreferences 读取用户真正选择的主题，解析出 StradustColors，
 * 供非 Composable 环境（XML Activity / View / Drawable）使用。
 */
package com.aicompanion.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object ThemeBridge {

    private const val PREFS = "app_prefs"
    private const val KEY_THEME = "theme_scheme"
    private const val KEY_APPEARANCE = "appearance_mode"

    /** 当前主题（直接读持久化值，不依赖 ThemeState.init 是否已执行） */
    fun currentThemeId(context: Context): ThemeId {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, ThemeId.SAKURA.key)
        return ThemeId.fromKey(key ?: ThemeId.SAKURA.key)
    }

    /** 当前是否使用暗色。MIDNIGHT / CYBERPUNK 只提供暗色，强制返回 true */
    fun isDark(context: Context): Boolean {
        val themeId = currentThemeId(context)
        if (!themeId.supportsLightMode) return true
        val mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_APPEARANCE, "dark") ?: "dark"
        return when (mode) {
            "dark" -> true
            "light" -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
    }

    /** 解析当前主题的颜色令牌（带缓存：inflate 期间会被高频调用，避免反复读 SharedPreferences） */
    fun resolveColors(context: Context): StradustColors {
        val themeId = currentThemeId(context)
        val dark = isDark(context) || !themeId.supportsLightMode
        val key = themeId.key + "|" + dark
        val hit = cachedColors
        if (hit != null && key == cachedKey) return hit
        val resolved = if (dark) DarkColorSchemes.fromId(themeId) else LightColorSchemes.fromId(themeId)
        cachedKey = key
        cachedColors = resolved
        return resolved
    }

    private var cachedKey: String? = null
    private var cachedColors: StradustColors? = null

    fun resolveColors(context: Context, themeId: ThemeId): StradustColors =
        if (isDark(context) || !themeId.supportsLightMode) {
            DarkColorSchemes.fromId(themeId)
        } else {
            LightColorSchemes.fromId(themeId)
        }

    /** 应用主题到 Activity：状态栏 / 导航栏 / 窗口底色 */
    fun applyToActivity(activity: Activity) {
        val colors = try {
            resolveColors(activity)
        } catch (_: Exception) {
            return
        }
        try {
            activity.window.statusBarColor = colors.toolbar.toArgb()
        } catch (_: Exception) {}
        try {
            activity.window.navigationBarColor = colors.toolbar.toArgb()
        } catch (_: Exception) {}
        try {
            activity.window.decorView.setBackgroundColor(colors.background.toArgb())
        } catch (_: Exception) {}
    }

    /** 把当前主题转换成旧 ThemeManager.ColorScheme，供仍未迁移的 XML 页面复用 */
    fun toLegacyScheme(context: Context): ColorScheme {
        val themeId = currentThemeId(context)
        return toLegacyScheme(context, themeId)
    }

    fun toLegacyScheme(context: Context, themeId: ThemeId): ColorScheme {
        val c = resolveColors(context, themeId)
        return ColorScheme(
            id = themeId.key,
            name = themeId.displayName,
            primaryColor = c.primary.toHex(),
            primaryGradient = "linear-gradient(135deg, ${c.primary.toHex()} 0%, ${c.tertiary.toHex()} 100%)",
            primaryColorDark = c.background.toHex(),
            accentColor = c.tertiary.toHex(),
            surfaceColor = c.surface.toHex(),
            surfaceColorDark = c.surfaceContainer.toHex(),
            bubbleUserColor = c.userBubbleStart.toHex(),
            bubbleGradient = "linear-gradient(135deg, ${c.userBubbleStart.toHex()} 0%, ${c.userBubbleEnd.toHex()} 100%)",
            bubbleAIColor = c.aiBubble.toHex(),
            textColor = c.textPrimary.toHex(),
            textSecondaryColor = c.textSecondary.toHex(),
            statusDotColor = c.primary.toHex(),
            backgroundDark = c.background.toHex(),
            cardColor = c.surfaceContainer.toHex(),
            toolbarColor = c.toolbar.toHex()
        )
    }

    /**
     * 新 ThemeId.key → 旧场景表 key。
     * ThemeEntranceView 的场景表仍以旧 id 为键，且只覆盖 9 套；
     * 茶香/赛博朋克/华夏风韵没有专属场景，退到视觉上最接近的一套。
     */
    fun legacySceneKey(themeKey: String): String? = when (themeKey) {
        "sakura" -> "sakura_grad"
        "peach" -> "peach_grad"
        "violet" -> "lavender_grad"
        "ocean" -> "blue_grad"
        "emerald" -> "emerald_grad"
        "sunset" -> "sunset_grad"
        "rosegold" -> "rose_gold"
        "mint" -> "mint_grad"
        "midnight" -> "midnight"
        "tea" -> "sunset_grad"
        "cyberpunk" -> "midnight"
        "chinese" -> "sakura_grad"
        else -> null
    }

    private fun Color.toHex(): String =
        "#" + Integer.toHexString(this.toArgb()).padStart(8, '0')
}
