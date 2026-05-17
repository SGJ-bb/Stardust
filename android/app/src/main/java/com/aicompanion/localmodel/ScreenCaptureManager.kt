package com.aicompanion.localmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.aicompanion.util.AppLogger

class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureManager"
        const val REQUEST_CODE_SCREEN_CAPTURE = 7777
        const val VIRTUAL_DISPLAY_WIDTH = 540
        const val VIRTUAL_DISPLAY_HEIGHT = 960
        const val VIRTUAL_DISPLAY_DPI = 120
    }

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastCaptureTime = 0L
    private val captureIntervalMs = 500L

    val isCapturing: Boolean get() = mediaProjection != null

    fun getScreenCaptureIntent(): Intent? {
        val activityContext = findActivityContext() ?: return null
        val mpm = activityContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            ?: return null
        return mpm.createScreenCaptureIntent()
    }

    fun startCapture(resultCode: Int, data: Intent): Boolean {
        if (mediaProjection != null) return true

        val activityContext = findActivityContext()
        val mpm = (activityContext ?: context).getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            ?: return false

        try {
            mediaProjection = mpm.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    AppLogger.d(TAG, "MediaProjection stopped")
                    release()
                }
            }, handler)

            setupVirtualDisplay()
            AppLogger.d(TAG, "startCapture: success")
            return true
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "startCapture SecurityException: ${e.message}")
            release()
            return false
        } catch (e: Exception) {
            AppLogger.e(TAG, "startCapture failed: ${e.message}")
            release()
            return false
        }
    }

    fun captureScreen(): Bitmap? {
        val reader = imageReader ?: return null
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < captureIntervalMs) {
            return null
        }
        lastCaptureTime = now

        try {
            val image: Image? = reader.acquireLatestImage()
            if (image == null) return null

            val planes = image.planes
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val yRowStride = yPlane.rowStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            val width = image.width
            val height = image.height

            val argb = IntArray(width * height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val yIdx = y * yRowStride + x
                    val uvIdx = (y / 2) * uvRowStride + (x / 2) * uvPixelStride

                    val yVal = yPlane.buffer[yIdx].toInt() and 0xFF
                    val uVal = uPlane.buffer[uvIdx].toInt() and 0xFF
                    val vVal = vPlane.buffer[uvIdx].toInt() and 0xFF

                    val r = (yVal + 1.370705 * (vVal - 128)).toInt().coerceIn(0, 255)
                    val g = (yVal - 0.337633 * (uVal - 128) - 0.698001 * (vVal - 128)).toInt().coerceIn(0, 255)
                    val b = (yVal + 1.732446 * (uVal - 128)).toInt().coerceIn(0, 255)

                    argb[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            image.close()
            return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            AppLogger.e(TAG, "captureScreen failed: ${e.message}")
            return null
        }
    }

    fun captureScreenCompressed(quality: Int = 80): ByteArray? {
        val bitmap = captureScreen() ?: return null
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    fun stopCapture() {
        release()
    }

    private fun setupVirtualDisplay() {
        val mp = mediaProjection ?: return

        imageReader = ImageReader.newInstance(
            VIRTUAL_DISPLAY_WIDTH, VIRTUAL_DISPLAY_HEIGHT,
            android.graphics.PixelFormat.RGBA_8888, 2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            try {
                val image = reader.acquireLatestImage()
                image?.close()
            } catch (_: Exception) {}
        }, handler)

        mp.createVirtualDisplay(
            "ScreenCapture",
            VIRTUAL_DISPLAY_WIDTH, VIRTUAL_DISPLAY_HEIGHT, VIRTUAL_DISPLAY_DPI,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, handler
        )
    }

    private fun release() {
        try { imageReader?.close() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        imageReader = null
        mediaProjection = null
    }

    private fun findActivityContext(): Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
