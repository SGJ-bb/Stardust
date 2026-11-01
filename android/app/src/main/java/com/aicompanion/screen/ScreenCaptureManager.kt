package com.aicompanion.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.aicompanion.util.AppLogger

/**
 * 屏幕截图（MediaProjection 路线）。
 *
 * 为什么要它：`AccessibilityService.takeScreenshot` 是 **Android 11 (API 30)** 才有的接口，
 * 这台机器是 API 29，调用点直接被版本判断跳过 —— 之前 OCR 一直"截图拿不到"就是这个原因，
 * 跟权限、ROM、伪装全都无关。
 *
 * MediaProjection 从 API 21 就有，是系统提供的正规截屏通道（就是录屏那套）。
 * 代价：**必须由用户手动点一次「立即开始」授权**，这是 Android 的设计，绕不过去，
 * 任何不 root 的第三方 App 想拿屏幕像素都得走这一步。授权一次之后，后面全部是代码。
 */
object ScreenCaptureManager {

    private const val TAG = "ScreenCapture"

    @Volatile
    private var projection: MediaProjection? = null

    @Volatile
    private var virtualDisplay: VirtualDisplay? = null

    @Volatile
    private var imageReader: ImageReader? = null

    @Volatile
    private var width = 0

    @Volatile
    private var height = 0

    @Volatile
    private var density = 0

    /** 已经拿到授权、可以截屏了吗 */
    val isReady: Boolean
        get() = projection != null && imageReader != null

    fun requestIntent(context: Context): Intent? {
        return try {
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mgr.createScreenCaptureIntent()
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建授权 Intent 失败: ${e.message}")
            null
        }
    }

    /** Activity 授权返回后调用；真正的初始化在 ScreenCaptureService 里做（前台服务要求） */
    fun start(context: Context, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            AppLogger.w(TAG, "截屏授权被拒绝 resultCode=$resultCode")
            return
        }
        val i = Intent(context, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动截屏服务失败: ${e.message}")
        }
    }

    /** 由前台服务调用：拿 MediaProjection 并建好虚拟屏 */
    fun attach(context: Context, resultCode: Int, data: Intent) {
        if (isReady) return
        try {
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = mgr.getMediaProjection(resultCode, data) ?: run {
                AppLogger.w(TAG, "getMediaProjection 返回 null")
                return
            }
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            width = dm.widthPixels
            height = dm.heightPixels
            density = dm.densityDpi

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val vd = p.createVirtualDisplay(
                "stardust-capture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null
            )
            projection = p
            imageReader = reader
            virtualDisplay = vd
            AppLogger.i(TAG, "截屏通道就绪 ${width}x$height dpi=$density")
        } catch (e: Exception) {
            AppLogger.e(TAG, "attach 失败: ${e.message}")
        }
    }

    /** 抓一帧。拿不到返回 null（不抛异常） */
    fun capture(): Bitmap? {
        val reader = imageReader ?: return null
        var image: android.media.Image? = null
        return try {
            // 等一小会儿让新帧进来：刚建好虚拟屏时第一帧可能是空的
            var tries = 0
            while (tries < 6) {
                image = reader.acquireLatestImage()
                if (image != null) break
                Thread.sleep(120)
                tries++
            }
            val img = image ?: return null
            val plane = img.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val bmpW = width + rowPadding / pixelStride
            val raw = Bitmap.createBitmap(bmpW, height, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(buffer)
            if (bmpW != width) {
                val cropped = Bitmap.createBitmap(raw, 0, 0, width, height)
                raw.recycle()
                cropped
            } else {
                raw
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "capture 失败: ${e.message}")
            null
        } finally {
            runCatching { image?.close() }
        }
    }

    fun release() {
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { projection?.stop() }
        virtualDisplay = null
        imageReader = null
        projection = null
        AppLogger.i(TAG, "截屏通道已释放")
    }
}
