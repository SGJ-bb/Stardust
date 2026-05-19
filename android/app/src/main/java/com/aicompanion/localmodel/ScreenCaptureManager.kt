package com.aicompanion.localmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.aicompanion.util.AppLogger
import java.nio.ByteBuffer

class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureManager"
        const val REQUEST_CODE_SCREEN_CAPTURE = 7777
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var screenWidth = 540
    private var screenHeight = 960
    private var screenDensity = 120

    private val captureLock = Any()
    private var cachedBitmap: Bitmap? = null
    private var hasNewFrame = false

    val isCapturing: Boolean get() = mediaProjection != null

    fun getScreenCaptureIntent(): Intent? {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            ?: return null
        return mpm.createScreenCaptureIntent()
    }

    fun startCapture(resultCode: Int, data: Intent): Boolean {
        if (mediaProjection != null) return true

        if (!ScreenCaptureService.isRunning) {
            startForegroundService()
            var waited = 0
            while (!ScreenCaptureService.isRunning && waited < 3000) {
                try { Thread.sleep(100) } catch (_: InterruptedException) { break }
                waited += 100
            }
            if (!ScreenCaptureService.isRunning) {
                AppLogger.e(TAG, "ScreenCaptureService failed to start within 3s")
                return false
            }
        }

        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            ?: return false

        try {
            mediaProjection = mpm.getMediaProjection(resultCode, data)
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "getMediaProjection SecurityException: ${e.message}")
            release()
            return false
        } catch (e: Exception) {
            AppLogger.e(TAG, "getMediaProjection failed: ${e.message}")
            release()
            return false
        }

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                AppLogger.d(TAG, "MediaProjection stopped")
                mainHandler.post { release() }
            }
        }, mainHandler)

        setupVirtualDisplay()
        AppLogger.d(TAG, "startCapture: success")
        return true
    }

    private fun startForegroundService() {
        try {
            val serviceIntent = Intent(context, ScreenCaptureService::class.java)
            context.startService(serviceIntent)
            AppLogger.d(TAG, "ScreenCaptureService start requested")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start ScreenCaptureService: ${e.message}")
        }
    }

    private fun setupVirtualDisplay() {
        val mp = mediaProjection ?: return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val metrics = DisplayMetrics()
        wm?.defaultDisplay?.getRealMetrics(metrics)

        screenWidth = (metrics.widthPixels * 3) / 4
        screenHeight = (metrics.heightPixels * 3) / 4
        screenWidth = (screenWidth / 2) * 2
        screenHeight = (screenHeight / 2) * 2
        screenDensity = (metrics.densityDpi * 3) / 4

        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        captureHandler = Handler(handlerThread!!.looper)

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            try {
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val bitmap = imageToBitmap(image)
                image.close()
                if (bitmap != null) {
                    synchronized(captureLock) {
                        cachedBitmap?.recycle()
                        cachedBitmap = bitmap
                        hasNewFrame = true
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "onImageAvailable error: ${e.message}")
            }
        }, captureHandler)

        virtualDisplay = mp.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            object : VirtualDisplay.Callback() {
                override fun onPaused() {
                    AppLogger.d(TAG, "VirtualDisplay paused")
                }
                override fun onResumed() {
                    AppLogger.d(TAG, "VirtualDisplay resumed")
                }
                override fun onStopped() {
                    AppLogger.d(TAG, "VirtualDisplay stopped")
                }
            },
            captureHandler
        )

        AppLogger.d(TAG, "VirtualDisplay created: ${screenWidth}x${screenHeight}@${screenDensity}dpi")
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null

        val plane = planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height

        if (pixelStride == 4 && rowStride == width * 4) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
            return bitmap
        }

        val rowPadding = rowStride - width * pixelStride
        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        if (rowPadding == 0) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    fun captureScreen(): Bitmap? {
        if (mediaProjection == null) {
            AppLogger.w(TAG, "captureScreen: MediaProjection is null")
            return null
        }

        synchronized(captureLock) {
            if (cachedBitmap != null && hasNewFrame) {
                hasNewFrame = false
                return Bitmap.createBitmap(cachedBitmap!!)
            }
        }

        val reader = imageReader ?: return null
        try {
            val image = reader.acquireLatestImage()
            if (image != null) {
                val bitmap = imageToBitmap(image)
                image.close()
                if (bitmap != null) {
                    synchronized(captureLock) {
                        cachedBitmap?.recycle()
                        cachedBitmap = bitmap
                        return Bitmap.createBitmap(cachedBitmap!!)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "captureScreen direct failed: ${e.message}")
        }

        synchronized(captureLock) {
            if (cachedBitmap != null) {
                return Bitmap.createBitmap(cachedBitmap!!)
            }
        }

        AppLogger.w(TAG, "captureScreen: no frame available yet")
        return null
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
        stopForegroundService()
    }

    private fun stopForegroundService() {
        try {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to stop ScreenCaptureService: ${e.message}")
        }
    }

    private fun release() {
        try { virtualDisplay?.release() } catch (e: Exception) {
            AppLogger.e(TAG, "virtualDisplay release error: ${e.message}")
        }
        virtualDisplay = null

        try { imageReader?.setOnImageAvailableListener(null, null) } catch (_: Exception) {}
        try { imageReader?.close() } catch (e: Exception) {
            AppLogger.e(TAG, "imageReader close error: ${e.message}")
        }
        imageReader = null

        try { mediaProjection?.stop() } catch (e: Exception) {
            AppLogger.e(TAG, "mediaProjection stop error: ${e.message}")
        }
        mediaProjection = null

        try { handlerThread?.quitSafely() } catch (_: Exception) {}
        handlerThread = null
        captureHandler = null

        synchronized(captureLock) {
            cachedBitmap?.recycle()
            cachedBitmap = null
            hasNewFrame = false
        }
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
