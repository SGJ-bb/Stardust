/** 模型实时调整页: 拖动调整模型位置/大小/缩放 */
package com.aicompanion.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.aicompanion.R
import com.aicompanion.live2d.Live2DWebView
import java.io.File

class ModelAdjustActivity : Activity() {

    private lateinit var live2dView: Live2DWebView
    private lateinit var tvModelInfo: TextView
    private lateinit var tvScaleValue: TextView
    private lateinit var seekbarScale: SeekBar

    private var isModelLoaded = false
    private var modelScale = 1.0f
    private var offsetX = 0f
    private var offsetY = 0f

    private val mainHandler = Handler(Looper.getMainLooper())

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownRawX = 0f
    private var touchDownRawY = 0f
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private var longPressPending = false
    private var dragActive = false
    private var longPressRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_adjust)

        live2dView = findViewById(R.id.live2d_view)
        tvModelInfo = findViewById(R.id.tv_model_info)
        tvScaleValue = findViewById(R.id.tv_scale_value)
        seekbarScale = findViewById(R.id.seekbar_scale)

        loadSavedValues()
        loadModel()
        setupSeekBar()
        setupButtons()
        setupTouchHandler()
        applyTheme()
    }

    private fun applyTheme() {
        try {
            val scheme = com.aicompanion.theme.ThemeManager.getCurrentScheme(this)
            val tbColor = android.graphics.Color.parseColor(scheme.toolbarColor)
            findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)?.setBackgroundColor(tbColor)
                ?: findViewById<View>(R.id.toolbar_container)?.setBackgroundColor(tbColor)
            com.aicompanion.theme.ThemeManager.applyTheme(this)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "applyTheme error: ${e.message}")
        }
    }

    private fun loadSavedValues() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        offsetX = prefs.getFloat("model_offset_x", 0f)
        offsetY = prefs.getFloat("model_offset_y", 0f)
        modelScale = prefs.getFloat("model_scale", 1.0f)
    }

    private fun loadModel() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val customModelPath = prefs.getString("active_model_path", "")

        live2dView.translationX = offsetX
        live2dView.translationY = offsetY

        live2dView.setOnModelLoaded { success ->
            runOnUiThread {
                if (success) {
                    isModelLoaded = true
                    live2dView.setModelScale(modelScale)
                    seekbarScale.post {
                        seekbarScale.setProgress((modelScale * 100).toInt(), false)
                    }
                    tvModelInfo.text = "模型已加载 · 长按拖动 · 滑块缩放"
                } else {
                    if (!customModelPath.isNullOrEmpty()) {
                        prefs.edit().remove("active_model_path").apply()
                        live2dView.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json")
                        tvModelInfo.text = "自定义模型加载失败，已恢复默认"
                    } else {
                        tvModelInfo.text = "模型加载失败"
                    }
                }
            }
        }

        if (!customModelPath.isNullOrEmpty() && !customModelPath.startsWith("file:///android_asset/")) {
            val file = File(customModelPath)
            if (file.exists() && file.isFile) {
                live2dView.loadLive2DModelFromPath(customModelPath)
            } else {
                prefs.edit().remove("active_model_path").apply()
                live2dView.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json")
            }
        } else {
            live2dView.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json")
        }
    }

    private fun setupSeekBar() {
        seekbarScale.setProgress((modelScale * 100).toInt(), false)
        tvScaleValue.text = "${(modelScale * 100).toInt()}%"

        seekbarScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    modelScale = progress / 100f
                    tvScaleValue.text = "$progress%"
                    live2dView.setModelScale(modelScale)
                    tvModelInfo.text = "缩放: $progress% · 长按拖动移动"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                saveOffset()
            }
        })
    }

    private fun setupButtons() {
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_reset)?.setOnClickListener {
            offsetX = 0f
            offsetY = 0f
            modelScale = 1.0f
            live2dView.translationX = 0f
            live2dView.translationY = 0f
            live2dView.setModelScale(1.0f)
            seekbarScale.setProgress(100, false)
            tvScaleValue.text = "100%"
            tvModelInfo.text = "已重置"
            saveOffset()
            Toast.makeText(this, "已重置", Toast.LENGTH_SHORT).show()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_finish)?.setOnClickListener {
            saveOffset()
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_show_log)?.setOnClickListener {
            val log = live2dView.getLog()
            val errorLines = log.lines().filter {
                it.contains("ERROR", ignoreCase = true) ||
                it.contains("FAIL", ignoreCase = true) ||
                it.contains("exception", ignoreCase = true) ||
                it.contains("not found", ignoreCase = true) ||
                it.contains("MISS", ignoreCase = true) ||
                it.contains("not renderable", ignoreCase = true)
            }
            val displayLog = if (errorLines.isNotEmpty()) {
                buildString {
                    append("=== 错误摘要 ===\n")
                    append(errorLines.joinToString("\n"))
                    append("\n\n=== 最近日志 ===\n")
                    append(log.lines().takeLast(30).joinToString("\n"))
                }
            } else {
                log.lines().takeLast(50).joinToString("\n")
            }
            val scrollView = android.widget.ScrollView(this).apply {
                val textView = android.widget.TextView(this@ModelAdjustActivity).apply {
                    text = displayLog
                    textSize = 11f
                    setPadding(24, 16, 24, 16)
                    setTextIsSelectable(true)
                    setTextColor(android.graphics.Color.parseColor("#E0E0E0"))
                }
                addView(textView)
            }
            val dialogView = android.widget.FrameLayout(this).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
                addView(scrollView, android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }
            android.app.AlertDialog.Builder(this)
                .setTitle("Live2D 日志")
                .setView(dialogView)
                .setPositiveButton("复制全部日志") { _, _ ->
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Live2D日志", log))
                    Toast.makeText(this@ModelAdjustActivity, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("仅复制错误") { _, _ ->
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val errText = if (errorLines.isNotEmpty()) errorLines.joinToString("\n") else "无错误"
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Live2D错误", errText))
                    Toast.makeText(this@ModelAdjustActivity, "错误日志已复制", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("关闭", null)
                .show()
        }
    }

    private fun setupTouchHandler() {
        live2dView.touchHandler = { event ->
            handleDragTouch(event)
        }
    }

    private fun handleDragTouch(event: android.view.MotionEvent): Boolean {
        if (!isModelLoaded) return false

        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchDownRawX = event.rawX
                touchDownRawY = event.rawY

                longPressPending = true
                longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                longPressRunnable = Runnable {
                    if (longPressPending) {
                        dragActive = true
                        longPressPending = false
                        lastTouchRawX = touchDownRawX
                        lastTouchRawY = touchDownRawY
                        live2dView.alpha = 0.85f
                    }
                }
                val lpr = longPressRunnable ?: return@handleDragTouch true
                mainHandler.postDelayed(lpr, 300)
                return true
            }

            android.view.MotionEvent.ACTION_MOVE -> {
                if (dragActive) {
                    val dx = event.rawX - lastTouchRawX
                    val dy = event.rawY - lastTouchRawY
                    live2dView.translationX += dx
                    live2dView.translationY += dy
                    lastTouchRawX = event.rawX
                    lastTouchRawY = event.rawY
                    return true
                }

                if (longPressPending) {
                    val dx = Math.abs(event.x - touchDownX)
                    val dy = Math.abs(event.y - touchDownY)
                    if (dx > 10 || dy > 10) {
                        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                        longPressPending = false
                        return false
                    }
                }
                return true
            }

            android.view.MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { mainHandler.removeCallbacks(it) }

                if (dragActive) {
                    dragActive = false
                    live2dView.alpha = 1.0f
                    offsetX = live2dView.translationX
                    offsetY = live2dView.translationY
                    saveOffset()
                    tvModelInfo.text = "位置已保存 · 缩放: ${(modelScale * 100).toInt()}%"
                    return true
                }

                if (longPressPending) {
                    longPressPending = false
                    live2dView.tapModel(event.x, event.y)
                    return true
                }

                return true
            }

            android.view.MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                longPressPending = false
                if (dragActive) {
                    dragActive = false
                    live2dView.alpha = 1.0f
                    offsetX = live2dView.translationX
                    offsetY = live2dView.translationY
                    saveOffset()
                }
                return true
            }
        }
        return false
    }

    private fun saveOffset() {
        offsetX = live2dView.translationX
        offsetY = live2dView.translationY
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit()
            .putFloat("model_offset_x", offsetX)
            .putFloat("model_offset_y", offsetY)
            .putFloat("model_scale", modelScale)
            .apply()
        Log.d(TAG, "Saved: offset=($offsetX, $offsetY) scale=$modelScale")
    }

    override fun onDestroy() {
        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        live2dView.cleanup()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ModelAdjustActivity"
    }
}
