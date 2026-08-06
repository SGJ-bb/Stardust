package com.aicompanion.ui.coordinator

import android.content.SharedPreferences
import android.view.MotionEvent
import android.view.View
import android.view.ViewStub
import android.widget.Toast
import com.aicompanion.config.AppConfig
import com.aicompanion.live2d.Live2DWebView
import com.aicompanion.models.Action
import com.aicompanion.models.Emotion
import com.aicompanion.util.AppLogger
import com.aicompanion.voice.VoiceManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Live2DCoordinator(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val prefs: SharedPreferences,
    private val logHistory: MutableList<String>,
    private val voiceManagerProvider: () -> VoiceManager?,
    private val onPetMessage: (String, Emotion, Action) -> Unit
) : BaseCoordinator(context, lifecycleOwner) {

    companion object {
        private const val TAG = "Live2DCoordinator"
    }

    var live2dView: Live2DWebView? = null
        private set

    private var offsetX = 0f
    private var offsetY = 0f
    private var modelBaseScale = 1f
    private var modelNaturalW = 0f
    private var modelNaturalH = 0f
    private var lastLoadedModelPath: String? = null
    var isModelLoaded = false
        private set
    private var lastLive2DTouchTime = 0L

    private var longPressPending = false
    private var dragActive = false
    private var touchDownRawX = 0f
    private var touchDownRawY = 0f
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var longPressRunnable: Runnable? = null

    /** 获取触摸冷却时间（配置化） */
    private fun getTouchCooldown(): Long {
        return AppConfig.getLive2DTouchCooldown(prefs)
    }

    /** 设置触摸冷却时间 */
    fun setTouchCooldown(cooldownMs: Long) {
        prefs.edit().putLong("live2d_touch_cooldown", cooldownMs).apply()
    }

    fun loadSettings() {
        offsetX = prefs.getFloat("model_offset_x", 0f)
        offsetY = prefs.getFloat("model_offset_y", 0f)
        modelBaseScale = prefs.getFloat("model_scale", 1f)
    }

    /** 获取当前模型缩放（0.3 - 3.0） */
    fun getScale(): Float = prefs.getFloat("model_scale", 1f)

    /**
     * 实时设置模型缩放并持久化
     * - 保存到 SharedPreferences
     * - 立即调用 WebView.setModelScale 更新渲染
     * - 下次 loadModel 时也会读取此值
     */
    fun setScale(scale: Float) {
        val clamped = scale.coerceIn(AppConfig.LIVE2D_MODEL_SCALE_MIN, AppConfig.LIVE2D_MODEL_SCALE_MAX)
        prefs.edit().putFloat("model_scale", clamped).apply()
        modelBaseScale = clamped
        live2dView?.setModelScale(clamped)
    }

    fun hideView() {
        live2dView?.pauseRendering()
        live2dView?.visibility = View.GONE
    }

    fun ensureView(stub: ViewStub?): Live2DWebView? {
        if (live2dView != null) return live2dView
        if (stub == null) return null
        live2dView = stub.inflate() as? Live2DWebView
        return live2dView
    }

    /**
     * 直接创建 Live2DWebView（不依赖 ViewStub，用于 Compose 模式）
     * 在 AndroidView factory 中调用此方法获取视图
     */
    fun createView(context: android.content.Context): Live2DWebView? {
        if (live2dView != null) return live2dView
        try {
            live2dView = Live2DWebView(context)
            AppLogger.d(TAG, "createView: Live2DWebView created for Compose")
        } catch (e: Exception) {
            AppLogger.e(TAG, "createView failed: ${e.message}")
        }
        return live2dView
    }

    fun loadModel() {
        val webView = live2dView ?: return
        webView.visibility = View.VISIBLE
        webView.resumeRendering()

        webView.setOnModelInfo { width, height, baseScale ->
            modelNaturalW = width
            modelNaturalH = height
        }

        webView.setOnModelLoaded { success ->
            if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return@setOnModelLoaded
            handler.post {
                if (!success) {
                    val failedLog = live2dView?.getLog() ?: ""
                    logHistory.add("[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] 模型加载失败:")
                    failedLog.lines().takeLast(20).forEach { logHistory.add("  $it") }

                    val currentPath = prefs.getString("active_model_path", "")
                    if (!currentPath.isNullOrEmpty()) {
                        android.util.Log.w(TAG, "Custom model failed, falling back to default")
                        prefs.edit().remove("active_model_path").apply()
                        lastLoadedModelPath = null
                        live2dView?.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json")
                    } else {
                        Toast.makeText(context, "皮套加载失败", Toast.LENGTH_LONG).show()
                    }
                } else {
                    live2dView?.translationX = offsetX
                    live2dView?.translationY = offsetY
                    val scale = prefs.getFloat("model_scale", 1f)
                    live2dView?.setModelScale(scale.coerceIn(AppConfig.LIVE2D_MODEL_SCALE_MIN, AppConfig.LIVE2D_MODEL_SCALE_MAX))
                    isModelLoaded = true
                }
            }
        }

        setupTouch()

        try {
            val customModelPath = prefs.getString("active_model_path", "")
            if (!customModelPath.isNullOrEmpty() && !customModelPath.startsWith("file:///android_asset/")) {
                val file = File(customModelPath)
                if (file.exists() && file.isFile) {
                    lastLoadedModelPath = customModelPath
                    webView.loadLive2DModelFromPath(customModelPath)
                } else {
                    prefs.edit().remove("active_model_path").apply()
                    lastLoadedModelPath = null
                    webView.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json")
                }
            } else {
                lastLoadedModelPath = null
                webView.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "loadLive2DModel failed: ${e.message}", e)
            try {
                webView.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json")
            } catch (e: Exception) {
                AppLogger.e(TAG, "loadLive2DModel: ${e.message}")
            }
        }
    }

    private fun setupTouch() {
        val view = live2dView ?: return
        view.touchHandler = lambda@{ event ->
            if (!isModelLoaded) return@lambda false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    touchDownRawX = event.rawX
                    touchDownRawY = event.rawY
                    longPressPending = true
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    longPressRunnable = Runnable {
                        if (longPressPending) {
                            dragActive = true
                            longPressPending = false
                            lastTouchRawX = touchDownRawX
                            lastTouchRawY = touchDownRawY
                            live2dView?.alpha = 0.85f
                        }
                    }
                    longPressRunnable?.let { handler.postDelayed(it, 100) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragActive) {
                        val dx = event.rawX - lastTouchRawX
                        val dy = event.rawY - lastTouchRawY
                        live2dView?.translationX = (live2dView?.translationX ?: 0f) + dx
                        live2dView?.translationY = (live2dView?.translationY ?: 0f) + dy
                        lastTouchRawX = event.rawX
                        lastTouchRawY = event.rawY
                        return@lambda true
                    }
                    if (longPressPending) {
                        val dx = Math.abs(event.x - touchDownX)
                        val dy = Math.abs(event.y - touchDownY)
                        if (dx > 10 || dy > 10) {
                            longPressRunnable?.let { handler.removeCallbacks(it) }
                            longPressPending = false
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    if (dragActive) {
                        dragActive = false
                        live2dView?.alpha = 0.9f
                        offsetX = live2dView?.translationX ?: 0f
                        offsetY = live2dView?.translationY ?: 0f
                        prefs.edit()
                            .putFloat("model_offset_x", offsetX)
                            .putFloat("model_offset_y", offsetY)
                            .apply()
                        return@lambda true
                    }
                    if (longPressPending) {
                        longPressPending = false
                        val now = System.currentTimeMillis()
                        if (now - lastLive2DTouchTime < getTouchCooldown()) return@lambda true
                        lastLive2DTouchTime = now
                        val webViewHeight = live2dView?.height?.toFloat() ?: 1f
                        live2dView?.tapModelRegion(event.x, event.y, webViewHeight)
                        val normalizedY = event.y / webViewHeight.coerceAtLeast(1f)
                        val responseText = when {
                            normalizedY < 0.25f -> listOf("喵~", "嗯...好舒服", "不要摸头啦...", "再摸一下嘛~").random()
                            normalizedY < 0.5f -> listOf("干嘛戳我！", "别戳脸！", "哼，讨厌~", "呜...别戳了").random()
                            else -> listOf("嗯？", "抱抱~", "好暖和...", "嘿嘿~").random()
                        }
                        voiceManagerProvider()?.speak(responseText)
                        return@lambda true
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    longPressPending = false
                    if (dragActive) {
                        dragActive = false
                        live2dView?.alpha = 0.9f
                        offsetX = live2dView?.translationX ?: 0f
                        offsetY = live2dView?.translationY ?: 0f
                        prefs.edit()
                            .putFloat("model_offset_x", offsetX)
                            .putFloat("model_offset_y", offsetY)
                            .apply()
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun setEmotion(emotion: Emotion) {
        live2dView?.setEmotion(emotion)
    }

    fun setAction(action: Action) {
        live2dView?.setAction(action)
    }

    fun updatePositionAndScale() {
        live2dView?.post {
            live2dView?.translationX = offsetX
            live2dView?.translationY = offsetY
            live2dView?.setModelScale(modelBaseScale.coerceIn(AppConfig.LIVE2D_MODEL_SCALE_MIN, AppConfig.LIVE2D_MODEL_SCALE_MAX))
        }
    }

    fun checkModelChange() {
        val currentModelPath = prefs.getString("active_model_path", "")
        if (currentModelPath != lastLoadedModelPath) {
            loadModel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // 清理longPressRunnable
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null

        // 清理所有触摸状态
        dragActive = false
        longPressPending = false
        isModelLoaded = false

        // 原有清理逻辑
        live2dView?.cleanup()
        live2dView = null
    }
}
