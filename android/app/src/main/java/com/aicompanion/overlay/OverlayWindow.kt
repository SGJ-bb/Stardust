package com.aicompanion.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.aicompanion.live2d.Live2DWebView
import com.aicompanion.ui.MainActivity

class OverlayWindow(context: Context) {

    private val context: Context = context.applicationContext

    private var windowManager: WindowManager? = null
    private var overlayRoot: FrameLayout? = null
    private var live2dView: Live2DWebView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    companion object {
        private const val TAG = "OverlayWindow"
        private var instance: OverlayWindow? = null

        fun notifySizeChanged() {
            instance?.updateSize()
        }
    }

    init {
        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            instance = this
        } catch (e: Exception) {
            Log.e(TAG, "Init error: ${e.message}", e)
        }
    }

    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    fun show() {
        if (!hasOverlayPermission()) {
            Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show()
            return
        }

        if (overlayRoot != null || windowManager == null) return
        try {
            val wm = windowManager ?: return
            val density = context.resources.displayMetrics.density
            val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            val sizePercent = prefs.getInt("overlay_size_percent", 100)
            val containerSize = ((120 * sizePercent) / 100 * density).toInt()

            live2dView = Live2DWebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(0x00000000)

                val modelPath = getModelPath()
                if (modelPath.startsWith("file:///android_asset/")) {
                    val assetPath = modelPath.removePrefix("file:///android_asset/")
                    loadLive2DModelFromAssets(assetPath)
                } else {
                    loadLive2DModelFromPath(modelPath)
                }

                touchHandler = { event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = this@OverlayWindow.layoutParams?.x ?: 0
                            initialY = this@OverlayWindow.layoutParams?.y ?: 0
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = false
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) {
                                isDragging = true
                            }
                            if (isDragging) {
                                this@OverlayWindow.layoutParams?.apply {
                                    x = initialX + dx
                                    y = initialY + dy
                                }
                                overlayRoot?.let { root ->
                                    windowManager?.updateViewLayout(root, this@OverlayWindow.layoutParams)
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!isDragging) {
                                try {
                                    val intent = Intent(context, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Click launch error: ${e.message}")
                                }
                            }
                            true
                        }
                        else -> false
                    }
                }
            }

            overlayRoot = FrameLayout(context).apply {
                setBackgroundColor(0x00000000)
                addView(live2dView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }

            layoutParams = WindowManager.LayoutParams().apply {
                width = containerSize
                height = containerSize
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 200
            }

            wm.addView(overlayRoot, layoutParams)
            Log.d(TAG, "Overlay window added successfully, size=$containerSize")

        } catch (e: Exception) {
            Log.e(TAG, "show() error: ${e.message}", e)
            Toast.makeText(context, "悬浮窗创建失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getModelPath(): String {
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val customPath = prefs.getString("active_model_path", null)
        if (!customPath.isNullOrBlank()) {
            return customPath
        }
        return "file:///android_asset/vtuber/PurpleBird/PurpleBird.model3.json"
    }

    fun hide() {
        try {
            overlayRoot?.let {
                windowManager?.removeView(it)
            }
            overlayRoot = null
            live2dView?.destroy()
            live2dView = null
        } catch (e: Exception) {
            Log.e(TAG, "hide() error: ${e.message}", e)
        }
    }

    fun isShowing(): Boolean = overlayRoot != null

    private fun updateSize() {
        if (overlayRoot == null || windowManager == null || layoutParams == null) return

        try {
            val density = context.resources.displayMetrics.density
            val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            val sizePercent = prefs.getInt("overlay_size_percent", 100)
            val containerSize = ((120 * sizePercent) / 100 * density).toInt()

            layoutParams?.apply {
                width = containerSize
                height = containerSize
            }

            overlayRoot?.let { root ->
                windowManager?.updateViewLayout(root, layoutParams)
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateSize error: ${e.message}", e)
        }
    }

    fun cleanup() {
        try {
            hide()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup error: ${e.message}", e)
        }
    }
}