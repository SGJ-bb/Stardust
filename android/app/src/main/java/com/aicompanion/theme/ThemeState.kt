package com.aicompanion.theme

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 全局主题状态 — 响应式主题切换核心
 *
 * 用法：
 * 1. App 启动时调用 ThemeState.init(context) 从 SharedPreferences 恢复
 * 2. 任意 Composable 中读取 ThemeState.currentThemeId / ThemeState.currentDarkMode
 * 3. 设置页调用 ThemeState.setTheme(themeId) 即可全局即时生效
 */
object ThemeState {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_THEME = "theme_scheme"
    private const val KEY_APPEARANCE = "appearance_mode"

    /** 当前主题 ID（响应式） */
    var currentThemeId: ThemeId by mutableStateOf(ThemeId.SAKURA)
        private set

    /** 当前外观模式：null=跟随系统, true=强制暗色, false=强制亮色 */
    var currentDarkMode: Boolean? by mutableStateOf(null)
        private set

    private var prefs: android.content.SharedPreferences? = null

    /** 主题变化监听器列表（用于通知旧系统） */
    private val listeners = mutableListOf<(ThemeId, Boolean?) -> Unit>()

    /** 注册监听器 */
    fun addListener(listener: (ThemeId, Boolean?) -> Unit) {
        listeners.add(listener)
    }

    /** 移除监听器 */
    fun removeListener(listener: (ThemeId, Boolean?) -> Unit) {
        listeners.remove(listener)
    }

    /** App 启动时调用，从 SharedPreferences 恢复主题设置 */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTheme = prefs?.getString(KEY_THEME, "sakura") ?: "sakura"
        val savedAppearance = prefs?.getString(KEY_APPEARANCE, "dark") ?: "dark"
        currentThemeId = ThemeId.fromKey(savedTheme)
        currentDarkMode = when (savedAppearance) {
            "dark" -> true
            "light" -> false
            else -> null
        }
    }

    /** 切换主题（即时生效 + 持久化 + 通知监听器） */
    fun setTheme(themeId: ThemeId) {
        val oldTheme = currentThemeId
        currentThemeId = themeId
        prefs?.edit()?.putString(KEY_THEME, themeId.key)?.apply()
        // 通知所有监听器
        listeners.forEach { it(themeId, currentDarkMode) }
    }

    /** 设置外观模式（即时生效 + 持久化 + 通知监听器） */
    fun setDarkMode(mode: Boolean?) {
        currentDarkMode = mode
        val value = when (mode) {
            true -> "dark"
            false -> "light"
            null -> "system"
        }
        prefs?.edit()?.putString(KEY_APPEARANCE, value)?.apply()
        // 通知所有监听器
        listeners.forEach { it(currentThemeId, mode) }
    }
}
