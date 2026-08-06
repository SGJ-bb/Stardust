/** 悬浮窗窗口: 创建系统级悬浮窗(TYPE_APPLICATION_OVERLAY), 包含Live2D/像素宠物渲染区/拖拽/点击跳转 */
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
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.aicompanion.live2d.Live2DWebView
import com.aicompanion.pixelpet.PixelPetView
import com.aicompanion.pixelpet.PixelAnimationEngine
import com.aicompanion.pixelpet.PixelPetManager
import com.aicompanion.pixelpet.getRenderConfig
import com.aicompanion.settings.SettingsManager
import com.aicompanion.ui.MainActivity

class OverlayWindow(context: Context) {

    private val context: Context = context.applicationContext

    private var windowManager: WindowManager? = null
    private var overlayRoot: FrameLayout? = null
    private var live2dView: Live2DWebView? = null
    private var pixelPetView: PixelPetView? = null
    private var pixelEngine: PixelAnimationEngine? = null
    private val petManager: PixelPetManager by lazy { PixelPetManager(context) }
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

    /** 获取当前要显示的 View（live2d 或 pixel），用于绑定触摸事件 */
    private fun getCurrentContentView(): View? {
        return pixelPetView ?: live2dView
    }

    /** 为指定 View 绑定拖拽 + 点击跳转的触摸监听器 */
    private fun setupTouchHandler(view: View) {
        view.setOnTouchListener { _, event ->
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
                        // 非拖拽 → 点击：像素模式触发交互动作，Live2D模式跳转主页
                        if (pixelPetView != null && pixelEngine != null) {
                            pixelEngine?.triggerInteraction()
                        } else {
                            try {
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Click launch error: ${e.message}")
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** 初始化 Live2D 模式：创建 WebView 并加载模型 */
    private fun initLive2DMode(modelPath: String, containerSize: Int) {
        live2dView = Live2DWebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            loadLive2DModelFromPath(modelPath)
        }
        setupTouchHandler(live2dView!!)
    }

    /** 初始化像素宠物模式：创建 PixelPetView + 启动动画引擎 */
    private fun initPixelMode(containerSize: Int) {
        val activePet = petManager.getActivePet() ?: run {
            Log.w(TAG, "No active pixel pet, fallback to placeholder")
            // 没有激活的像素宠物时显示提示
            live2dView = Live2DWebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(0x00000000)
            }
            setupTouchHandler(live2dView!!)
            return
        }

        val actions = petManager.getPetActions(activePet.id)
        val renderConfig = activePet.getRenderConfig()

        // 创建像素渲染视图
        pixelPetView = PixelPetView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setScale(renderConfig.scale)
        }

        // 创建并启动动画引擎
        pixelEngine = PixelAnimationEngine().apply {
            onFrameReady = { frame, _ ->
                val bitmap = if (frame != null) {
                    petManager.loadFrameBitmap(frame.imagePath)
                } else null
                if (bitmap != null) {
                    pixelPetView?.setFrame(bitmap)
                }
            }
            onActionChanged = { _, _ ->
                Log.d(TAG, "Pixel action changed")
            }
            registerActions(actions)
            play()
        }

        setupTouchHandler(pixelPetView!!)
        Log.d(TAG, "Pixel pet mode initialized: ${activePet.name}, ${actions.size} actions")
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    fun show() {
        if (!hasOverlayPermission()) {
            Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show()
            return
        }

        if (overlayRoot != null || windowManager == null) return
        try {
            val sm = SettingsManager(context)
            val live2dEnabled = sm.live2dEnabled
            val petMode = petManager.getPetMode() // "live2d" | "pixel"

            val wm = windowManager ?: return
            val density = context.resources.displayMetrics.density
            val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            val sizePercent = prefs.getInt("overlay_size_percent", 100)
            val containerSize = ((120 * sizePercent) / 100 * density).toInt()

            // 根据模式初始化对应的视图
            when (petMode) {
                "pixel" -> {
                    initPixelMode(containerSize)
                }
                else -> {
                    // live2d 模式或回退
                    if (live2dEnabled) {
                        val modelPath = getModelPath()
                        initLive2DMode(modelPath, containerSize)
                    } else {
                        // Live2D关闭且非像素模式 → 占位
                        live2dView = Live2DWebView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(0x00000000)
                        }
                        setupTouchHandler(live2dView!!)
                    }
                }
            }

            // 构建根布局，添加当前活动的视图
            val contentView = getCurrentContentView()
            overlayRoot = FrameLayout(context).apply {
                setBackgroundColor(0x00000000)
                contentView?.let {
                    addView(it, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ))
                }
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
            Log.d(TAG, "Overlay window added successfully, mode=$petMode, size=$containerSize")

            if (petMode != "pixel" && !live2dEnabled) {
                Toast.makeText(context, "Live2D 已关闭，请在设置中开启或切换到像素宠物模式", Toast.LENGTH_SHORT).show()
            }

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
            // 停止像素动画引擎
            pixelEngine?.stop()
            pixelEngine = null

            // 销毁像素视图
            pixelPetView?.destroy()
            pixelPetView = null

            // 移除悬浮窗
            overlayRoot?.let {
                windowManager?.removeView(it)
            }
            overlayRoot = null

            // 销毁 Live2D 视图
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
