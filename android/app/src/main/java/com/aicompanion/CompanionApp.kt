package com.aicompanion

import android.app.Application
import android.util.Log
import android.widget.Toast

class CompanionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CompanionApp", "FATAL on ${thread.name}: ${throwable.javaClass.simpleName}: ${throwable.message}", throwable)
            try {
                Toast.makeText(this, "⚠ 错误: ${throwable.javaClass.simpleName}: ${throwable.message}", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {}
        }
    }
}
