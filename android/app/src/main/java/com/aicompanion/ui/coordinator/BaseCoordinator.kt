package com.aicompanion.ui.coordinator

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * Coordinator 基类：封装一组相关业务逻辑，通过生命周期回调与 Activity 解耦。
 * 子类在 onCreate 中初始化资源，在 onDestroy 中释放资源。
 */
abstract class BaseCoordinator(
    protected val context: Context,
    protected val lifecycleOwner: LifecycleOwner
) {
    protected val handler = Handler(Looper.getMainLooper())
    protected val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle

    /** Activity onCreate 时调用 */
    open fun onCreate() {}

    /** Activity onResume 时调用 */
    open fun onResume() {}

    /** Activity onPause 时调用 */
    open fun onPause() {}

    /** Activity onDestroy 时调用，必须释放所有资源 */
    open fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
    }

    protected fun isAtLeast(state: Lifecycle.State): Boolean {
        return lifecycle.currentState.isAtLeast(state)
    }

    protected fun runIfAlive(block: () -> Unit) {
        if (isAtLeast(Lifecycle.State.CREATED)) {
            handler.post(block)
        }
    }
}
