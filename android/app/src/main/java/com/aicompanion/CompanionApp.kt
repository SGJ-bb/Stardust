/** 应用入口: Application生命周期管理, 全局异常捕获和Toast提示 */
package com.aicompanion

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.aicompanion.AppContainer

class CompanionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.initialize(this)
        com.aicompanion.migration.DataMigrationManager.migrateIfNeeded(this)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CompanionApp", "FATAL on ${thread.name}: ${throwable.javaClass.simpleName}: ${throwable.message}", throwable)
            try {
                Toast.makeText(this, "⚠ 错误: ${throwable.javaClass.simpleName}: ${throwable.message}", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {}
        }
    }
}
