package com.aicompanion.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import com.aicompanion.ui.MainActivity

class OverlayWindow(context: Context) {

    private val context: Context = context.applicationContext

    private var windowManager: WindowManager? = null
    private var overlayRoot: FrameLayout? = null
    private var petView: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bounceRunnable: Runnable? = null
    private var bouncePhase = 0

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

    @SuppressLint("ClickableViewAccessibility")
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

            petView = ImageView(context).apply {
                setImageDrawable(createPetDrawable())
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(0x00000000)
            }

            overlayRoot = FrameLayout(context).apply {
                setBackgroundColor(0x00000000)
                addView(petView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }

            overlayRoot?.setOnTouchListener { _, event ->
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
            startBounceAnimation()
            Log.d(TAG, "Overlay window added successfully, size=$containerSize")

        } catch (e: Exception) {
            Log.e(TAG, "show() error: ${e.message}", e)
            Toast.makeText(context, "悬浮窗创建失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createPetDrawable(): android.graphics.drawable.Drawable {
        val size = 200
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        paint.color = 0xFF667eea.toInt()
        canvas.drawOval(
            android.graphics.RectF(20f, 20f, 180f, 180f),
            paint
        )

        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawOval(android.graphics.RectF(55f, 60f, 80f, 85f), paint)
        canvas.drawOval(android.graphics.RectF(120f, 60f, 145f, 85f), paint)

        paint.color = 0xFF333355.toInt()
        canvas.drawOval(android.graphics.RectF(62f, 68f, 75f, 81f), paint)
        canvas.drawOval(android.graphics.RectF(127f, 68f, 140f, 81f), paint)

        paint.color = 0xFFFF6B9D.toInt()
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawArc(
            android.graphics.RectF(70f, 110f, 130f, 145f),
            0f, 180f, false, paint
        )

        paint.color = 0xFF667eea.toInt()
        paint.style = android.graphics.Paint.Style.FILL
        val path = android.graphics.Path()
        path.moveTo(40f, 40f)
        path.lineTo(20f, 0f)
        path.lineTo(60f, 25f)
        path.close()
        canvas.drawPath(path, paint)

        path.reset()
        path.moveTo(160f, 40f)
        path.lineTo(180f, 0f)
        path.lineTo(140f, 25f)
        path.close()
        canvas.drawPath(path, paint)

        return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
    }

    private fun startBounceAnimation() {
        bounceRunnable = object : Runnable {
            override fun run() {
                if (overlayRoot == null) return
                try {
                    bouncePhase = (bouncePhase + 1) % 60
                    val offset = (kotlin.math.sin(bouncePhase * Math.PI / 30.0) * 5).toFloat()
                    petView?.translationY = offset
                    mainHandler.postDelayed(this, 50)
                } catch (e: Exception) {
                    Log.e(TAG, "Bounce animation error: ${e.message}")
                }
            }
        }
        bounceRunnable?.let { mainHandler.postDelayed(it, 100) }
    }

    fun hide() {
        try {
            bounceRunnable?.let { mainHandler.removeCallbacks(it) }
            overlayRoot?.let {
                windowManager?.removeView(it)
            }
            overlayRoot = null
            petView = null
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
            bounceRunnable?.let { mainHandler.removeCallbacks(it) }
            hide()
        } catch (e: Exception) {
            Log.e(TAG, "cleanup error: ${e.message}", e)
        }
    }
}
