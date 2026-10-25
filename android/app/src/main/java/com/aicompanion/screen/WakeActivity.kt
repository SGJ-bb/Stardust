/**
 * 只用于"点亮屏幕"的透明 Activity。
 *
 * 声明了 `android:turnScreenOn="true"`：被启动时系统会把屏幕点亮。
 * 启动完立即 finish，不留界面。作为 [ScreenWaker] 的兜底手段
 * （后台启动 Activity 在 Android 10+ 可能被拦，所以不能只靠它）。
 */
package com.aicompanion.screen

import android.app.Activity
import android.os.Bundle

class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不设布局，直接结束；屏幕会在 Activity 可见的瞬间被点亮
        finish()
    }
}
