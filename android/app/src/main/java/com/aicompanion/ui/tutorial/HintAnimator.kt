package com.aicompanion.ui.tutorial

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.aicompanion.R

/**
 * 手势提示动画器
 *
 * 在目标 View 的镂空区域附近播放循环手势动画，引导用户进行交互。
 * 支持多种动画类型：点击、滑动、脉冲、打字等。
 *
 * 使用方式：
 * 1. 构造时传入目标区域和动画类型
 * 2. 调用 [start] 启动循环动画
 * 3. 在绘制循环中调用 [getAnimatedPath] 获取当前帧的 Path 并绘制
 * 4. 调用 [stop] 停止动画并释放资源
 *
 * @property context 上下文，用于获取资源和颜色
 * @property targetBounds 目标 View 的边界矩形（屏幕坐标）
 * @property animationType 手势动画类型
 */
class HintAnimator(
    private val context: Context,
    private val targetBounds: RectF,
    private val animationType: HintAnimation
) {
    /** 动画是否正在运行 */
    @Volatile
    private var isRunning = false

    /** 屏幕密度，用于 dp→px 转换 */
    private val density: Float = context.resources.displayMetrics.density

    /** 品牌主色画笔 - 用于绘制手势路径 */
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_primary)
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** 放射短线画笔 - TAP 动画中的放射线条 */
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_primary)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }

    /** 光标画笔 - TYPE 动画的闪烁光标 */
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_primary)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
    }

    /** 当前动画进度值 [0f, 1f] */
    private var animProgress = 0f

    /** 当前帧的动画路径 */
    private val currentPath = Path()

    /** 驱动动画的 ValueAnimator 实例 */
    private var animator: ValueAnimator? = null

    /** 目标区域的中心 X 坐标 */
    private val centerX: Float get() = targetBounds.centerX()

    /** 目标区域的中心 Y 坐标 */
    private val centerY: Float get() = targetBounds.centerY()

    /** 目标区域的半径/半宽（用于计算动画范围） */
    private val targetRadius: Float
        get() = kotlin.math.max(targetBounds.width(), targetBounds.height()) / 2f + 12f * density

    // ======================== 动画生命周期 ========================

    /**
     * 启动手势动画
     * 根据不同的 [animationType] 创建对应的 ValueAnimator 并开始无限循环播放
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        animator = when (animationType) {
            HintAnimation.NONE -> null
            HintAnimation.TAP -> createTapAnimator()
            HintAnimation.SWIPE_LEFT -> createSwipeLeftAnimator()
            HintAnimation.SWIPE_RIGHT -> createSwipeRightAnimator()
            HintAnimation.SWIPE_UP -> createSwipeUpAnimator()
            HintAnimation.PULSE -> createPulseAnimator()
            HintAnimation.TYPE -> createTypeAnimator()
        }?.apply {
            // 无限循环播放
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animation ->
                animProgress = animation.animatedFraction
            }
            start()
        }
    }

    /**
     * 停止手势动画并释放资源
     */
    fun stop() {
        isRunning = false
        animator?.cancel()
        animator = null
        animProgress = 0f
        currentPath.reset()
    }

    /**
     * 查询动画是否正在运行
     */
    fun isAnimating(): Boolean = isRunning

    /**
     * 手动更新动画进度（供外部 AnimatorSet 驱动时使用）
     * @param progress 进度值 [0f, 1f]
     */
    fun updateProgress(progress: Float) {
        animProgress = progress.coerceIn(0f, 1f)
    }

    /** 获取当前动画进度 [0f, 1f] */
    fun getAnimProgress(): Float = animProgress

    /** 获取主画笔（供外部绘制使用） */
    fun getPaint(): Paint = paint

    /** 获取当前动画类型 */
    fun getAnimationType(): HintAnimation = animationType

    /** 获取放射线画笔（TAP 动画使用） */
    fun getRayPaint(): Paint = rayPaint

    /** 获取光标画笔（TYPE 动画使用） */
    fun getCursorPaint(): Paint = cursorPaint

    // ======================== 路径计算 ========================

    /**
     * 根据当前动画进度和动画类型，计算并返回当前帧的绘制路径
     *
     * 此方法在每帧绘制时由外部调用，返回的 Path 应直接用于 Canvas 绘制。
     * 不同动画类型的路径生成逻辑如下：
     *
     * - **TAP**: 从目标中心向外扩散的缩放圆环 + 放射短线
     * - **SWIPE_LEFT**: 带箭头的左滑路径（从右到左）
     * - **SWIPE_RIGHT**: 带箭头的右滑路径（从左到右）
     * - **SWIPE_UP**: 带箭头的上滑路径（从下到上）
     * - **PULSE**: 围绕目标的呼吸式缩放圆环
     * - **TYPE**: 在目标位置闪烁的光标竖线
     *
     * @param progress 当前动画进度 [0f, 1f]
     * @return 计算好的绘制 Path 对象（可复用）
     */
    fun getAnimatedPath(progress: Float): Path {
        currentPath.reset()

        return when (animationType) {
            HintAnimation.NONE -> currentPath
            HintAnimation.TAP -> calculateTapPath(progress)
            HintAnimation.SWIPE_LEFT -> calculateSwipePath(progress, isLeft = true)
            HintAnimation.SWIPE_RIGHT -> calculateSwipePath(progress, isLeft = false)
            HintAnimation.SWIPE_UP -> calculateSwipeUpPath(progress)
            HintAnimation.PULSE -> calculatePulsePath(progress)
            HintAnimation.TYPE -> calculateTypePath(progress)
        }
    }

    /**
     * 获取放射线段路径（仅 TAP 动画使用）
     * 与主路径分离绘制，便于控制不同透明度
     */
    fun getRayPath(progress: Float): Path {
        val rayPath = Path()
        if (animationType != HintAnimation.TAP) return rayPath

        // 放射线只在圆环扩散到一定大小后出现
        if (progress < 0.3f) return rayPath

        val rayProgress = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f)
        val baseRadius = targetRadius * 0.5f
        val maxRadius = targetRadius * 1.4f
        val currentRadius = baseRadius + (maxRadius - baseRadius) * rayProgress
        val rayLength = 10f * density * (1f - rayProgress) // 线条随扩散缩短
        val alpha = ((1f - rayProgress) * 255).toInt().coerceIn(0, 255)

        rayPaint.alpha = alpha

        // 8 条放射线，均匀分布
        val rayCount = 8
        for (i in 0 until rayCount) {
            val angle = Math.PI * 2 * i / rayCount.toDouble()
            val startX = centerX + kotlin.math.cos(angle).toFloat() * currentRadius
            val startY = centerY + kotlin.math.sin(angle).toFloat() * currentRadius
            val endX = centerX + kotlin.math.cos(angle).toFloat() * (currentRadius + rayLength)
            val endY = centerY + kotlin.math.sin(angle).toFloat() * (currentRadius + rayLength)

            rayPath.moveTo(startX, startY)
            rayPath.lineTo(endX, endY)
        }

        return rayPath
    }

    /**
     * 获取光标路径（仅 TYPE 动画使用）
     */
    fun getCursorPath(progress: Float): Path {
        val cursorPath = Path()
        if (animationType != HintAnimation.TYPE) return cursorPath

        // 光标闪烁：快速 on/off 循环
        val blinkPhase = (progress * 3f) % 1f // 每 1/3 周期闪烁一次
        val visible = blinkPhase < 0.5f
        cursorPaint.alpha = if (visible) 255 else 30

        if (visible) {
            // 在目标区域右侧绘制竖线光标
            val cursorX = targetBounds.right + 6f * density
            val cursorTop = targetBounds.top + targetBounds.height() * 0.2f
            val cursorBottom = targetBounds.bottom - targetBounds.height() * 0.2f
            cursorPath.moveTo(cursorX, cursorTop)
            cursorPath.lineTo(cursorX, cursorBottom)
        }

        return cursorPath
    }

    // ======================== 各类动画的具体实现 ========================

    /**
     * TAP 点击动画路径
     *
     * 效果：圆环从目标中心 scale(0.5→1.2)，alpha(1→0)，循环 1500ms
     * 插值器：OvershootInterpolator（带弹性过冲效果）
     */
    private fun calculateTapPath(progress: Float): Path {
        // Overshoot 效果：先 overshoot 再回弹
        val overshoot = OvershootInterpolator(2.5f).getInterpolation(progress)
        val scale = 0.5f + (1.2f - 0.5f) * overshoot
        val radius = targetRadius * scale
        val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)

        paint.alpha = alpha
        currentPath.addCircle(centerX, centerY, radius, Path.Direction.CW)
        return currentPath
    }

    /**
     * SWIPE 左右滑动动画路径
     *
     * 效果：箭头沿水平方向滑入并消失
     * - SWIPE_LEFT: 从右到左，1200ms
     * - SWIPE_RIGHT: 从左到右，1200ms
     * 插值器：LinearOutSlowInInterpolator
     *
     * @param progress 动画进度
     * @param isLeft 是否为左滑方向
     */
    private fun calculateSwipePath(progress: Float, isLeft: Boolean): Path {
        val interpolator = DecelerateInterpolator()
        val t = interpolator.getInterpolation(progress)

        // 箭头滑动的总距离
        val swipeDistance = targetBounds.width() * 1.2f + 40f * density
        val arrowSize = 14f * density // 箭头头部大小

        // 起点/终点根据方向决定
        val startX = if (isLeft) centerX + swipeDistance / 2f else centerX - swipeDistance / 2f
        val endX = if (isLeft) centerX - swipeDistance / 2f else centerX + swipeDistance / 2f

        // 当前箭头中心位置
        val currentX = startX + (endX - startX) * t
        val arrowY = centerY

        // 箭头透明度：首尾淡入淡出
        val fadeIn = (t / 0.2f).coerceIn(0f, 1f)
        val fadeOut = ((1.0f - t) / 0.2f).coerceIn(0f, 1f)
        val alpha = (fadeIn * fadeOut * 255.0f).toInt().coerceIn(0, 255)
        paint.alpha = alpha

        // 绘制箭头路径：一条横线 + 三角形头部
        val halfLen = swipeDistance * 0.25f // 箭身长度的一半

        // 箭身
        val bodyStart = if (isLeft) currentX + arrowSize * 0.5f else currentX - halfLen
        val bodyEnd = if (isLeft) currentX + halfLen else currentX - arrowSize * 0.5f

        currentPath.moveTo(bodyStart, arrowY)
        currentPath.lineTo(bodyEnd, arrowY)

        // 箭头头部（三角形）
        val tipX = if (isLeft) bodyEnd else bodyStart
        val tipDir = if (isLeft) 1f else -1f

        currentPath.moveTo(tipX, arrowY)
        currentPath.lineTo(tipX - arrowSize * tipDir, arrowY - arrowSize * 0.6f)
        currentPath.moveTo(tipX, arrowY)
        currentPath.lineTo(tipX - arrowSize * tipDir, arrowY + arrowSize * 0.6f)

        return currentPath
    }

    /**
     * SWIPE_UP 上滑动画路径
     *
     * 效果：箭头从下往上滑入并消失，带箭头头部
     * 插值器：LinearOutSlowInInterpolator
     */
    private fun calculateSwipeUpPath(progress: Float): Path {
        val interpolator = DecelerateInterpolator()
        val t = interpolator.getInterpolation(progress)

        val swipeDistance = targetBounds.height() * 0.8f + 30f * density
        val arrowSize = 14f * density

        val startY = centerY + swipeDistance / 2f
        val endY = centerY - swipeDistance / 2f
        val currentY = startY + (endY - startY) * t

        // 淡入淡出
        val fadeIn = (t / 0.2f).coerceIn(0f, 1f)
        val fadeOut = ((1.0f - t) / 0.2f).coerceIn(0f, 1f)
        val alpha = (fadeIn * fadeOut * 255.0f).toInt().coerceIn(0, 255)
        paint.alpha = alpha

        // 竖向箭身
        val halfLen = swipeDistance * 0.25f
        val bodyTop = currentY - halfLen
        val bodyBottom = currentY + arrowSize * 0.5f

        currentPath.moveTo(centerX, bodyBottom)
        currentPath.lineTo(centerX, bodyTop)

        // 向上箭头头部
        currentPath.moveTo(centerX, bodyTop)
        currentPath.lineTo(centerX - arrowSize * 0.6f, bodyTop + arrowSize)
        currentPath.moveTo(centerX, bodyTop)
        currentPath.lineTo(centerX + arrowSize * 0.6f, bodyTop + arrowSize)

        return currentPath
    }

    /**
     * PULSE 脉冲呼吸动画路径
     *
     * 效果：圆环围绕目标区域呼吸缩放 (1.0→1.15→1.0)，无限循环 2000ms
     * 插值器：AccelerateDecelerateInterpolator
     */
    private fun calculatePulsePath(progress: Float): Path {
        val interpolator = AccelerateDecelerateInterpolator()
        val t = interpolator.getInterpolation(progress)

        // 正弦波形缩放：1.0 → 1.15 → 1.0
        val scale = 1.0f + 0.15f * kotlin.math.sin(t * Math.PI).toFloat()
        val radius = targetRadius * scale

        // 呼吸式透明度变化
        val alpha = (180 + 75f * kotlin.math.sin(t * Math.PI).toFloat()).toInt().coerceIn(0, 255)
        paint.alpha = alpha

        currentPath.addCircle(centerX, centerY, radius, Path.Direction.CW)
        return currentPath
    }

    /**
     * TYPE 打字光标动画路径
     *
     * 效果：在目标输入框位置显示闪烁的打字光标
     * 通过独立的 [getCursorPath] 方法获取实际光标路径
     * 此方法返回一个微弱的底座圆角矩形作为视觉锚点
     */
    private fun calculateTypePath(progress: Float): Path {
        // 底座：围绕目标区域的一个微弱圆角矩形框
        val phase = (progress * 2f) % 1f
        val alpha = (60 + 40f * kotlin.math.sin(phase * Math.PI * 2).toFloat()).toInt().coerceIn(30, 100)
        paint.alpha = alpha
        paint.strokeWidth = 1.5f * density

        val inset = 4f * density
        currentPath.addRoundRect(
            RectF(
                targetBounds.left - inset,
                targetBounds.top - inset,
                targetBounds.right + inset,
                targetBounds.bottom + inset
            ),
            8f * density,
            8f * density,
            Path.Direction.CW
        )
        return currentPath
    }

    // ======================== 各类动画的 Animator 创建工厂 ========================

    /** 创建 TAP 动画器：1500ms，OvershootInterpolator */
    private fun createTapAnimator(): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500L
            interpolator = OvershootInterpolator(2.5f)
        }
    }

    /** 创建 SWIPE_LEFT 动画器：1200ms，LinearOutSlowInInterpolator */
    private fun createSwipeLeftAnimator(): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200L
            interpolator = DecelerateInterpolator()
        }
    }

    /** 创建 SWIPE_RIGHT 动画器：1200ms，LinearOutSlowInInterpolator */
    private fun createSwipeRightAnimator(): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200L
            interpolator = DecelerateInterpolator()
        }
    }

    /** 创建 SWIPE_UP 动画器：1200ms，LinearOutSlowInInterpolator */
    private fun createSwipeUpAnimator(): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200L
            interpolator = DecelerateInterpolator()
        }
    }

    /** 创建 PULSE 动画器：2000ms，AccelerateDecelerateInterpolator */
    private fun createPulseAnimator(): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000L
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    /** 创建 TYPE 打字光标动画器：1000ms 快速循环模拟闪烁 */
    private fun createTypeAnimator(): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L // 快速闪烁周期
        }
    }
}
