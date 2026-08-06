package com.aicompanion.pixelpet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import android.view.View

/**
 * 像素宠物渲染View
 *
 * 使用Android Canvas API进行像素完美渲染:
 * - Paint.isFilterBitmap = false (无滤波缩放)
 * - 支持任意缩放倍数
 * - 自动居中绘制
 * - 透明背景
 */
class PixelPetView(context: Context) : View(context) {

    private val paint = Paint().apply {
        isFilterBitmap = false  // 像素完美：不进行双线性滤波
        isDither = false
    }

    private var currentBitmap: Bitmap? = null
    private var displayScale: Float = DefaultRenderConfig.SCALE

    /** 设置要显示的帧位图 */
    fun setFrame(bitmap: Bitmap?) {
        this.currentBitmap = bitmap
        invalidate()
    }

    /** 设置显示缩放倍数 */
    fun setScale(scale: Float) {
        this.displayScale = scale.coerceAtLeast(1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = currentBitmap ?: return

        val scaledW = (bmp.width * displayScale).toInt()
        val scaledH = (bmp.height * displayScale).toInt()

        // 居中绘制
        val left = (width - scaledW) / 2f
        val top = (height - scaledH) / 2f

        val src = android.graphics.Rect(0, 0, bmp.width, bmp.height)
        val dst = android.graphics.RectF(left, top, left + scaledW, top + scaledH)

        canvas.drawBitmap(bmp, src, dst, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 建议最小尺寸
        val minSize = (DefaultRenderConfig.SPRITE_WIDTH * displayScale).toInt().coerceAtLeast(48)
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(minSize),
            MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(minSize)
        )
    }

    /** 清除当前帧 */
    fun clear() {
        currentBitmap?.recycle()
        currentBitmap = null
        invalidate()
    }

    /** 销毁资源 */
    fun destroy() {
        clear()
    }

    companion object {
        private const val TAG = "PixelPetView"
    }
}
