/** 主题管理器: DayNight 暗色/亮色/跟随系统 + 自定义配色方案 + 全局自动生效 */
package com.aicompanion.theme

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/**
 * 外观模式枚举
 */
enum class AppearanceMode(val key: String, val label: String) {
    DARK("dark", "Dark"),
    LIGHT("light", "Light"),
    SYSTEM("system", "Follow System");
    
    companion object {
        fun fromKey(key: String): AppearanceMode =
            entries.find { it.key == key } ?: DARK
    }
}

data class ColorScheme(
    val id: String,
    val name: String,
    val primaryColor: String,
    val primaryGradient: String,
    val primaryColorDark: String,
    val accentColor: String,
    val surfaceColor: String,
    val surfaceColorDark: String,
    val bubbleUserColor: String,
    val bubbleGradient: String,
    val bubbleAIColor: String,
    val textColor: String,
    val textSecondaryColor: String,
    val statusDotColor: String,
    val backgroundDark: String,
    val cardColor: String,
    val toolbarColor: String
)

object ThemeManager {

    // ===== 配色方案 (保持不变) =====
    val schemes = listOf(
        ColorScheme(
            id = "sakura_grad", name = "Sakura Pink",
            primaryColor = "#ff6b9d", primaryGradient = "linear-gradient(135deg, #ff6b9d 0%, #ffa8c5 50%, #ffebf0 100%)",
            primaryColorDark = "#4a2a3a", accentColor = "#ffb3c6",
            surfaceColor = "#CC3a1629", surfaceColorDark = "#BB4a2a3a",
            bubbleUserColor = "#ff6b9d", bubbleGradient = "linear-gradient(135deg, #ff6b9d 0%, #ff85b8 100%)",
            bubbleAIColor = "#BB4a2a3a", textColor = "#ffffff", textSecondaryColor = "#8899cc",
            statusDotColor = "#E91E63",
            backgroundDark = "#2a1525", cardColor = "#3a2233", toolbarColor = "#3a2233"
        ),
        ColorScheme(
            id = "peach_grad", name = "Peach Blossom",
            primaryColor = "#ff9a76", primaryGradient = "linear-gradient(135deg, #ff9a76 0%, #fecfef 50%, #ffd4e8 100%)",
            primaryColorDark = "#4a2a2a", accentColor = "#ffd4e8",
            surfaceColor = "#CC2a1a1a", surfaceColorDark = "#BB3a2a2a",
            bubbleUserColor = "#ff9a76", bubbleGradient = "linear-gradient(135deg, #ff9a76 0%, #ff6b85 100%)",
            bubbleAIColor = "#BB3a2a2a", textColor = "#ffffff", textSecondaryColor = "#cc9988",
            statusDotColor = "#FF5722",
            backgroundDark = "#2a1a15", cardColor = "#3a2a22", toolbarColor = "#3a2a22"
        ),
        ColorScheme(
            id = "lavender_grad", name = "Lavender Dream",
            primaryColor = "#a78bfa", primaryGradient = "linear-gradient(135deg, #a78bfa 0%, #c4b5fd 50%, #ede9fe 100%)",
            primaryColorDark = "#2a1a3a", accentColor = "#d8b4fe",
            surfaceColor = "#CC1a0a2a", surfaceColorDark = "#BB2a1a3a",
            bubbleUserColor = "#a78bfa", bubbleGradient = "linear-gradient(135deg, #a78bfa 0%, #8b5cf6 100%)",
            bubbleAIColor = "#BB2a1a3a", textColor = "#ffffff", textSecondaryColor = "#9988cc",
            statusDotColor = "#9C27B0",
            backgroundDark = "#1a0a2a", cardColor = "#2a1a3a", toolbarColor = "#2a1a3a"
        ),
        ColorScheme(
            id = "blue_grad", name = "Ocean Blue",
            primaryColor = "#60a5fa", primaryGradient = "linear-gradient(135deg, #60a5fa 0%, #93c5fd 50%, #dbeafe 100%)",
            primaryColorDark = "#1a2a3a", accentColor = "#93c5fd",
            surfaceColor = "#CC0a1a2a", surfaceColorDark = "#BB1a2a3a",
            bubbleUserColor = "#60a5fa", bubbleGradient = "linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%)",
            bubbleAIColor = "#BB1a2a3a", textColor = "#ffffff", textSecondaryColor = "#8899cc",
            statusDotColor = "#2196F3",
            backgroundDark = "#0a1a2a", cardColor = "#1a2a3a", toolbarColor = "#1a2a3a"
        ),
        ColorScheme(
            id = "emerald_grad", name = "Emerald Green",
            primaryColor = "#34d399", primaryGradient = "linear-gradient(135deg, #34d399 0%, #6ee7b7 50%, #d1fae5 100%)",
            primaryColorDark = "#1a3a2a", accentColor = "#6ee7b7",
            surfaceColor = "#CC0a2a1a", surfaceColorDark = "#BB1a3a2a",
            bubbleUserColor = "#34d399", bubbleGradient = "linear-gradient(135deg, #34d399 0%, #10b981 100%)",
            bubbleAIColor = "#BB1a3a2a", textColor = "#ffffff", textSecondaryColor = "#88aa99",
            statusDotColor = "#4CAF50",
            backgroundDark = "#0a1a0a", cardColor = "#1a2a1a", toolbarColor = "#1a2a1a"
        ),
        ColorScheme(
            id = "sunset_grad", name = "Sunset Gradient",
            primaryColor = "#fbbf24", primaryGradient = "linear-gradient(135deg, #fbbf24 0%, #f97316 50%, #ef4444 100%)",
            primaryColorDark = "#3a2a1a", accentColor = "#fcd34d",
            surfaceColor = "#CC2a1a0a", surfaceColorDark = "#BB3a2a1a",
            bubbleUserColor = "#fbbf24", bubbleGradient = "linear-gradient(135deg, #fbbf24 0%, #f97316 100%)",
            bubbleAIColor = "#BB3a2a1a", textColor = "#ffffff", textSecondaryColor = "#cca888",
            statusDotColor = "#FF9800",
            backgroundDark = "#2a1a0a", cardColor = "#3a2a1a", toolbarColor = "#3a2a1a"
        ),
        ColorScheme(
            id = "rose_gold", name = "Rose Gold",
            primaryColor = "#e8b4b8", primaryGradient = "linear-gradient(135deg, #e8b4b8 0%, #d4a5a5 30%, #c99797 100%)",
            primaryColorDark = "#2a1a1a", accentColor = "#f0d0d0",
            surfaceColor = "#CC1a0a0a", surfaceColorDark = "#BB2a1a1a",
            bubbleUserColor = "#e8b4b8", bubbleGradient = "linear-gradient(135deg, #e8b4b8 0%, #d4a5a5 100%)",
            bubbleAIColor = "#BB2a1a1a", textColor = "#ffffff", textSecondaryColor = "#cc9999",
            statusDotColor = "#E91E63",
            backgroundDark = "#2a1515", cardColor = "#3a2222", toolbarColor = "#3a2222"
        ),
        ColorScheme(
            id = "mint_grad", name = "Mint Fresh",
            primaryColor = "#67e8f9", primaryGradient = "linear-gradient(135deg, #67e8f9 0%, #22d3ee 50%, #06b6d4 100%)",
            primaryColorDark = "#0a2a2a", accentColor = "#a5f3fc",
            surfaceColor = "#CC0a1a1a", surfaceColorDark = "#BB0a2a2a",
            bubbleUserColor = "#67e8f9", bubbleGradient = "linear-gradient(135deg, #67e8f9 0%, #06b6d4 100%)",
            bubbleAIColor = "#BB0a2a2a", textColor = "#ffffff", textSecondaryColor = "#88aabb",
            statusDotColor = "#00BCD4",
            backgroundDark = "#0a1a1a", cardColor = "#1a2a2a", toolbarColor = "#1a2a2a"
        ),
        ColorScheme(
            id = "midnight", name = "Midnight Aura",
            primaryColor = "#6366f1", primaryGradient = "linear-gradient(135deg, #6366f1 0%, #818cf8 50%, #a5b4fc 100%)",
            primaryColorDark = "#1a1a2e", accentColor = "#818cf8",
            surfaceColor = "#CC0a0a1a", surfaceColorDark = "#BB111122",
            bubbleUserColor = "#6366f1", bubbleGradient = "linear-gradient(135deg, #6366f1 0%, #4f46e5 100%)",
            bubbleAIColor = "#BB111122", textColor = "#e0e0e0", textSecondaryColor = "#7788aa",
            statusDotColor = "#3F51B5",
            backgroundDark = "#0a0a1a", cardColor = "#1a1a30", toolbarColor = "#1a1a30"
        )
    )

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_APPEARANCE = "appearance_mode"   // dark / light / system
    private const val KEY_THEME_SCHEME = "theme_scheme"

    // ===== 外观模式 (DayNight) =====

    /** 获取当前外观模式 */
    fun getAppearanceMode(context: Context): AppearanceMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppearanceMode.fromKey(prefs.getString(KEY_APPEARANCE, "dark") ?: "dark")
    }

    /** 设置外观模式并立即应用 */
    fun setAppearanceMode(context: Context, mode: AppearanceMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_APPEARANCE, mode.key)
        }
        applyAppearanceMode(mode)
    }

    /** 应用外观模式到全局 (AppCompatDelegate) */
    fun applyAppearanceMode(mode: AppearanceMode) {
        when (mode) {
            AppearanceMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            AppearanceMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            AppearanceMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    /** 初始化外观模式（在 Application.onCreate 中调用） */
    fun initAppearance(context: Context) {
        val mode = getAppearanceMode(context)
        applyAppearanceMode(mode)
    }

    // ===== 配色方案 (原有功能保留) =====

    fun getCurrentScheme(context: Context): ColorScheme {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentId = prefs.getString(KEY_THEME_SCHEME, "sakura_grad") ?: "sakura_grad"
            schemes.find { it.id == currentId } ?: schemes[0]
        } catch (e: Exception) {
            schemes[0]
        }
    }

    fun setScheme(context: Context, schemeId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_THEME_SCHEME, schemeId)
        }
    }

    /**
     * 应用配色方案到单个 Activity
     * 用于覆盖 DayNight 主题无法控制的区域（状态栏、导航栏、背景）
     */
    fun applyTheme(activity: Activity) {
        val scheme = getCurrentScheme(activity)
        try {
            activity.window.statusBarColor = safeColor(scheme.toolbarColor)
        } catch (_: Exception) {}
        try {
            activity.window.navigationBarColor = safeColor(scheme.toolbarColor)
        } catch (_: Exception) {}
        try {
            activity.window.decorView.setBackgroundColor(safeColor(scheme.backgroundDark))
        } catch (_: Exception) {}
    }

    /** 兼容旧接口：对指定 View 批量应用配色 */
    fun applyThemeToViews(activity: Activity, vararg viewIds: Int) {
        val scheme = getCurrentScheme(activity)
        for (id in viewIds) {
            val view = activity.findViewById<View>(id) ?: continue
            when (view) {
                is TextView -> {
                    if (view.textSize >= 18f) {
                        view.setTextColor(safeColor(scheme.textColor))
                    } else {
                        view.setTextColor(safeColor(scheme.textSecondaryColor))
                    }
                }
                is ViewGroup -> view.setBackgroundColor(safeColor(scheme.surfaceColor))
            }
        }
    }

    // ===== 颜色工具方法 =====

    fun getSchemeColors(scheme: ColorScheme): Map<String, Int> {
        return mapOf(
            "primary" to safeColor(scheme.primaryColor),
            "accent" to safeColor(scheme.accentColor),
            "surface" to safeColor(scheme.surfaceColor),
            "card" to safeColor(scheme.cardColor),
            "text" to safeColor(scheme.textColor),
            "textSecondary" to safeColor(scheme.textSecondaryColor),
            "toolbar" to safeColor(scheme.toolbarColor),
            "primaryDark" to safeColor(scheme.primaryColorDark),
            "bubbleUser" to safeColor(scheme.bubbleUserColor),
            "bubbleAI" to safeColor(scheme.bubbleAIColor)
        )
    }

    private fun createRoundedDrawable(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun safeColor(colorStr: String?, default: Int = 0xFF667eea.toInt()): Int {
        if (colorStr.isNullOrEmpty()) return default
        return try {
            Color.parseColor(colorStr)
        } catch (_: Exception) {
            default
        }
    }

    // ===== 全局生命周期监听 (自动在每个 Activity 应用主题) =====

    /**
     * 在 Application.onCreate() 中注册，实现全局主题自动生效
     * 无需每个 Activity 手动调用 applyTheme()
     */
    fun registerGlobalObserver(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                // 每个 Activity 恢复时自动应用配色方案
                if (!isThemeActivity(activity)) {
                    applyTheme(activity)
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** 排除不需要自动应用配色的 Activity（如 Splash、引导页等） */
    private fun isThemeActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return name.contains("Splash") ||
               name.contains("Onboarding") ||
               name.contains("Tutorial") ||
               name.contains("Launch")
    }
}
