/** 应用入口: Application生命周期管理, 全局异常捕获和Toast提示 */
package com.aicompanion

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.aicompanion.AppContainer
import com.aicompanion.theme.ThemeManager

class CompanionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.aicompanion.util.AppLogger.init(this)
        com.aicompanion.rag.RagConfig.init(this)  // 初始化RAG配置持久化
        AppContainer.initialize(this)
        com.aicompanion.migration.DataMigrationManager.migrateIfNeeded(this)

        // 初始化外观模式 (DayNight 暗色/亮色/跟随系统)
        ThemeManager.initAppearance(this)

        // 注册全局主题观察器 — 每个 Activity 自动应用配色方案
        ThemeManager.registerGlobalObserver(this)

        // 注册 ThemeState → ThemeManager 同步桥接
        // 当 Compose UI 切换主题时，自动同步到旧系统的 SharedPreferences
        com.aicompanion.theme.ThemeState.addListener { themeId, darkMode ->
            // 将 ThemeState 的变更写回 ThemeManager 使用的 SharedPreferences 格式
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit().putString("theme_scheme", themeId.key).apply()

            val appearanceValue = when (darkMode) {
                true -> "dark"
                false -> "light"
                else -> "system"
            }
            prefs.edit().putString("appearance_mode", appearanceValue).apply()

            // 同时更新 ThemeManager 的外观模式（立即生效）
            try {
                val mode = when (darkMode) {
                    true -> com.aicompanion.theme.AppearanceMode.DARK
                    false -> com.aicompanion.theme.AppearanceMode.LIGHT
                    else -> com.aicompanion.theme.AppearanceMode.SYSTEM
                }
                ThemeManager.applyAppearanceMode(mode)
            } catch (_: Exception) {}
        }

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            com.aicompanion.util.AppLogger.e("CompanionApp", "FATAL on ${thread.name}: ${throwable.javaClass.simpleName}: ${throwable.message}", throwable)
            com.aicompanion.util.AppLogger.flush()
            try {
                Toast.makeText(this, "应用发生错误，请查看日志", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {}
        }
    }
}
