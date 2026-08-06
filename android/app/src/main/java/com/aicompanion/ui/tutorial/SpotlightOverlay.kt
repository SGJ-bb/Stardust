package com.aicompanion.ui.tutorial

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.aicompanion.R
import com.aicompanion.anim.AnimeInterpolators
import kotlin.math.max
import kotlin.math.min

/**
 * 聚光灯遮罩层 (Spotlight Overlay)
 *
 * 全屏半透明遮罩，在目标 View 位置镂空显示（聚光灯效果），
 * 并展示引导提示气泡、步骤指示器和操作按钮。
 *
 * 核心功能：
 * - 绘制半透明黑色遮罩 + 镂空高亮区域（带柔和发光边缘）
 * - 自动定位提示气泡（根据目标位置选择上方/下方）
 * - 播放手势提示动画（通过 [HintAnimator]）
 * - 步骤切换时的平滑过渡动画（镂空区域 morphing + 气泡 crossfade）
 * - 完全消费触摸事件，防止穿透到下层 View
 *
 * @property context 上下文
 * @property attrs XML 属性集
 * @property defStyleAttr 默认样式属性
 */
class SpotlightOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ======================== 常量与配置 ========================

    companion object {
        /** 遮罩层背景色：70% 不透明度黑色 */
        private const val MASK_COLOR = 0xB3000000.toInt()

        /** 发光边缘的额外扩展(dp) */
        private const val GLOW_PADDING_DP = 8f

        /** 发光边缘透明度 */
        private const val GLOW_ALPHA = 60

        /** 步骤指示器圆点尺寸(dp) */
        private const val INDICATOR_DOT_SIZE_DP = 6f

        /** 步骤指示器圆点间距(dp) */
        private const val INDICATOR_GAP_DP = 8f

        /** 圆角矩形的圆角半径(dp) */
        private const val ROUNDED_RECT_RADIUS_DP = 16f

        /** Tooltip 入场动画时长(ms) */
        private const val TOOLTIP_ENTER_DURATION = 300L

        /** 镂空区域 morph 过渡动画时长(ms) */
        private const val MORPH_DURATION = 300L

        /** Dismiss 淡出动画时长(ms) */
        private const val DISMISS_DURATION = 250L
    }

    // ======================== 屏幕密度 ========================

    private val density: Float = context.resources.displayMetrics.density

    // ======================== 绘制相关 ========================

    /** 遮罩层画笔：填充全屏半透明区域 */
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MASK_COLOR
        style = Paint.Style.FILL
    }

    /** 发光边缘画笔：绘制镂空区域的柔和外发光效果 */
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_primary)
        style = Paint.Style.STROKE
        strokeWidth = GLOW_PADDING_DP * density
        alpha = GLOW_ALPHA
        strokeCap = Paint.Cap.ROUND
    }

    /** 镂空区域的 Path 对象 */
    private val holePath = Path()

    /** 用于反向裁剪的全屏路径 */
    private val screenPath = Path()

    /** 镂空区域的矩形边界 */
    private val holeRectF = RectF()

    /** morph 动画过程中的起始矩形（用于插值计算） */
    private val fromRectF = RectF()

    /** 镂空形状类型 */
    private var holeShape: TargetShape = TargetShape.CIRCLE

    /** 镂空区域额外内边距(px) */
    private var holePaddingPx = 0f

    /** 是否正在执行 morph 过渡动画 */
    private var isMorphing = false

    // ======================== UI 组件 ========================

    /** 提示气泡容器 */
    private lateinit var tooltipContainer: LinearLayout

    /** 提示标题 TextView */
    private lateinit var tooltipTitle: TextView

    /** 描述文字 TextView */
    private lateinit var tooltipDesc: TextView

    /** 手势提示文字 TextView（可选） */
    private lateinit var hintTextView: TextView

    /** 步骤指示器容器 */
    private lateinit var stepIndicators: LinearLayout

    /** "下一步"按钮 */
    private lateinit var btnNext: TextView

    /** "跳过"按钮 */
    private lateinit var btnSkip: TextView

    /** 手势动画器实例 */
    private var hintAnimator: HintAnimator? = null

    /** 手势动画驱动 ValueAnimator（用于持续触发重绘） */
    private var hintAnimDriver: ValueAnimator? = null

    /** 当前总步骤数 */
    private var totalSteps = 0

    /** 当前步骤索引 */
    private var currentStepIndex = 0

    // ======================== 回调 ========================

    /** 下一步按钮点击回调 */
    var onNextClick: (() -> Unit)? = null

    /** 跳过按钮点击回调 */
    var onSkipClick: (() -> Unit)? = null

    // ======================== 初始化 ========================

    init {
        // 设置为覆盖整个屏幕且不参与布局测量
        setWillNotDraw(false)

        // 消费所有触摸事件，防止穿透到底层 View
        setOnTouchListener { _, _ -> true }

        // 确保可以接收点击事件
        isClickable = true
        isFocusable = true

        // 初始化 UI 组件
        initTooltipView()
    }

    /**
     * 初始化提示气泡和所有 UI 子组件
     * 采用代码动态创建方式，不依赖 XML 布局文件
     */
    private fun initTooltipView() {
        val brandPrimary = ContextCompat.getColor(context, R.color.brand_primary)
        val textPrimary = ContextCompat.getColor(context, R.color.text_primary)
        val textSecondary = ContextCompat.getColor(context, R.color.text_secondary)
        val surfaceCard = ContextCompat.getColor(context, R.color.surface_card)

        // ===== 外层容器：垂直排列的 LinearLayout =====
        tooltipContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * density).toInt(),
                (16 * density).toInt(),
                (20 * density).toInt(),
                (16 * density).toInt()
            )
            // 背景：带圆角的深色卡片
            background = createRoundedRectDrawable(surfaceCard, (16 * density).toInt())
            alpha = 0f // 初始不可见，等待入场动画
            visibility = INVISIBLE
        }

        // ===== 标题 TextView =====
        tooltipTitle = TextView(context).apply {
            text = ""
            setTextColor(textPrimary)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (6 * density).toInt() }
        }
        tooltipContainer.addView(tooltipTitle)

        // ===== 描述文字 TextView =====
        tooltipDesc = TextView(context).apply {
            text = ""
            setTextColor(textSecondary)
            textSize = 14f
            setLineSpacing(0f, 1.3f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (8 * density).toInt() }
        }
        tooltipContainer.addView(tooltipDesc)

        // ===== 手势提示文字（可选，默认隐藏）=====
        hintTextView = TextView(context).apply {
            text = ""
            setTextColor(brandPrimary)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (12 * density).toInt() }
            visibility = GONE
        }
        tooltipContainer.addView(hintTextView)

        // ===== 步骤指示器容器 =====
        stepIndicators = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (16 * density).toInt() }
        }
        tooltipContainer.addView(stepIndicators)

        // ===== 按钮行容器 =====
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER or Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        tooltipContainer.addView(buttonRow)

        // ===== 跳过按钮（描边样式）=====
        btnSkip = TextView(context).apply {
            text = "跳过"
            setTextColor(textSecondary)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding((20 * density).toInt(), (10 * density).toInt(), (20 * density).toInt(), (10 * density).toInt())
            background = createStrokeDrawable(textSecondary, (density).toInt(), (22 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = (12 * density).toInt() }
            setOnClickListener { onSkipClick?.invoke() }
        }
        buttonRow.addView(btnSkip)

        // ===== 下一步按钮（渐变品牌色药丸形）=====
        btnNext = TextView(context).apply {
            text = "下一步"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((24 * density).toInt(), (10 * density).toInt(), (24 * density).toInt(), (10 * density).toInt())
            background = createGradientPillDrawable()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onNextClick?.invoke() }
        }
        buttonRow.addView(btnNext)

        // 将气泡容器添加到本 View
        addView(tooltipContainer)
    }

    // ======================== 绘制逻辑 ========================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (holeRectF.isEmpty) return

        // ---- 第1步：构建反向镂空路径 ----
        buildHolePath()

        // ---- 第2步：绘制全屏遮罩（带镂空区域）----
        canvas.drawPath(holePath, maskPaint)

        // ---- 第3步：绘制镂空边缘发光效果 ----
        drawGlowEdge(canvas)

        // ---- 第4步：绘制手势提示动画 ----
        hintAnimator?.let { animator ->
            if (animator.isAnimating()) {
                val path = animator.getAnimatedPath(animator.getAnimProgress())
                canvas.drawPath(path, animator.getPaint())

                // TAP 动画额外绘制放射线
                if (animator.getAnimationType() == HintAnimation.TAP) {
                    val rayPath = animator.getRayPath(animator.getAnimProgress())
                    canvas.drawPath(rayPath, animator.getRayPaint())
                }

                // TYPE 动画额外绘制光标
                if (animator.getAnimationType() == HintAnimation.TYPE) {
                    val cursorPath = animator.getCursorPath(animator.getAnimProgress())
                    canvas.drawPath(cursorPath, animator.getCursorPaint())
                }
            }
        }
    }

    /**
     * 构建镂空路径
     * 使用 Path.Op.DIFFERENCE 从全屏矩形中减去镂空区域
     */
    private fun buildHolePath() {
        holePath.reset()

        // 全屏矩形路径
        screenPath.reset()
        screenPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)

        // 镂空区域路径
        val clipPath = Path()
        when (holeShape) {
            TargetShape.CIRCLE -> {
                val radius = max(holeRectF.width(), holeRectF.height()) / 2f
                clipPath.addCircle(holeRectF.centerX(), holeRectF.centerY(), radius, Path.Direction.CW)
            }
            TargetShape.RECTANGLE -> {
                clipPath.addRect(holeRectF, Path.Direction.CW)
            }
            TargetShape.ROUNDED_RECT -> {
                val radius = ROUNDED_RECT_RADIUS_DP * density
                clipPath.addRoundRect(holeRectF, radius, radius, Path.Direction.CW)
            }
        }

        // 差集运算：全屏 - 镂空区域 = 带洞的遮罩
        holePath.op(screenPath, clipPath, Path.Op.DIFFERENCE)
    }

    /**
     * 在镂空边缘绘制柔和的外发光效果
     * 通过绘制一个稍大的低透明度形状来实现
     */
    private fun drawGlowEdge(canvas: Canvas) {
        val glowExpand = GLOW_PADDING_DP * density * 1.5f
        val glowRectF = RectF(
            holeRectF.left - glowExpand,
            holeRectF.top - glowExpand,
            holeRectF.right + glowExpand,
            holeRectF.bottom + glowExpand
        )

        when (holeShape) {
            TargetShape.CIRCLE -> {
                val radius = max(glowRectF.width(), glowRectF.height()) / 2f
                canvas.drawCircle(glowRectF.centerX(), glowRectF.centerY(), radius, glowPaint)
            }
            TargetShape.RECTANGLE -> {
                canvas.drawRect(glowRectF, glowPaint)
            }
            TargetShape.ROUNDED_RECT -> {
                val radius = ROUNDED_RECT_RADIUS_DP * density + glowExpand
                canvas.drawRoundRect(glowRectF, radius, radius, glowPaint)
            }
        }
    }

    // ======================== 公开 API：设置目标区域 ========================

    /**
     * 设置镂空目标区域
     *
     * 将目标 View 的屏幕坐标转换为本 View 的相对坐标，
     * 更新镂空矩形并触发重绘。
     *
     * @param bounds 目标 View 的屏幕坐标 Rect
     * @param shape 镂空形状
     * @param paddingDp 额外内边距(dp)，围绕目标 View 边缘扩展
     */
    fun setTarget(bounds: Rect, shape: TargetShape, paddingDp: Float) {
        val paddingPx = paddingDp * density
        holePaddingPx = paddingPx
        holeShape = shape

        // 将屏幕坐标转换为本 View 的相对坐标
        val location = IntArray(2)
        getLocationOnScreen(location)
        val offsetX = -location[0].toFloat()
        val offsetY = -location[1].toFloat()

        holeRectF.set(
            bounds.left.toFloat() + offsetX - paddingPx,
            bounds.top.toFloat() + offsetY - paddingPx,
            bounds.right.toFloat() + offsetX + paddingPx,
            bounds.bottom.toFloat() + offsetY + paddingPx
        )

        invalidate()
    }

    // ======================== 公开 API：显示提示气泡 ========================

    /**
     * 显示提示气泡
     *
     * 根据目标区域在屏幕中的位置自动决定气泡放置方向：
     * - 目标在上半屏 → 气泡放在下方
     * - 目标在下半屏 → 气泡放在上方
     * 同时确保气泡不超出屏幕边界。
     *
     * 入场动画：从对应方向 slideUp/slideDown + fadeIn (300ms)
     *
     * @param title 引导标题
     * @param description 引导描述
     * @param hintText 可选的手势提示文字
     */
    fun showTooltip(title: String, description: String, hintText: String?) {
        // 更新文案内容
        tooltipTitle.text = title
        tooltipDesc.text = description

        if (hintText != null && hintText.isNotBlank()) {
            hintTextView.text = hintText
            hintTextView.visibility = VISIBLE
        } else {
            hintTextView.visibility = GONE
        }

        // 测量气泡尺寸以进行定位
        tooltipContainer.measure(
            MeasureSpec.makeMeasureSpec(width - (32 * density).toInt(), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val tooltipWidth = tooltipContainer.measuredWidth
        val tooltipHeight = tooltipContainer.measuredHeight

        // 计算气泡位置
        val gapFromTarget = 12f * density // 气泡与目标区域的间距

        // 判断目标区域在屏幕的上半部分还是下半部分
        val targetCenterY = holeRectF.centerY()
        val isInUpperHalf = targetCenterY < height / 2f

        // 计算气泡的 left/top 坐标
        var tooltipLeft = holeRectF.centerX() - tooltipWidth / 2f
        var tooltipTop: Float

        if (isInUpperHalf) {
            // 目标在上半屏 → 气泡放在目标下方
            tooltipTop = holeRectF.bottom + gapFromTarget
        } else {
            // 目标在下半屏 → 气泡放在目标上方
            tooltipTop = holeRectF.top - gapFromTarget - tooltipHeight
        }

        // 边界约束：确保不超出屏幕左右边界
        val marginHorizontal = 16f * density
        tooltipLeft = tooltipLeft.coerceIn(marginHorizontal, (width - tooltipWidth - marginHorizontal))

        // 边界约束：确保不超出屏幕上下边界
        if (tooltipTop < marginHorizontal) {
            tooltipTop = holeRectF.bottom + gapFromTarget // 回落到下方
        }
        if (tooltipTop + tooltipHeight > height - marginHorizontal) {
            tooltipTop = holeRectF.top - gapFromTarget - tooltipHeight // 回落到上方
        }

        // 应用布局参数
        val currentParams = tooltipContainer.layoutParams as? LayoutParams
        if (currentParams != null) {
            currentParams.width = tooltipWidth
            currentParams.height = tooltipHeight
            currentParams.leftMargin = tooltipLeft.toInt()
            currentParams.topMargin = tooltipTop.toInt()
            currentParams.gravity = Gravity.TOP or Gravity.START
            tooltipContainer.layoutParams = currentParams
        } else {
            tooltipContainer.layoutParams = LayoutParams(tooltipWidth, tooltipHeight).apply {
                leftMargin = tooltipLeft.toInt()
                topMargin = tooltipTop.toInt()
                gravity = Gravity.TOP or Gravity.START
            }
        }

        // 显示并执行入场动画
        tooltipContainer.visibility = VISIBLE
        tooltipContainer.alpha = 0f

        if (isInUpperHalf) {
            // 从下方滑入
            tooltipContainer.translationY = 30f * density
        } else {
            // 从上方滑入
            tooltipContainer.translationY = -30f * density
        }

        tooltipContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(TOOLTIP_ENTER_DURATION)
            .setInterpolator(AnimeInterpolators.easeOutCubic)
            .start()
    }

    // ======================== 公开 API：手势动画控制 ========================

    /**
     * 启动手势提示动画
     *
     * 创建 [HintAnimator] 实例并启动循环动画，
     * 同时注册一个持续的 ValueAnimator 来驱动 onDraw 重绘。
     *
     * @param animationType 动画类型
     * @param targetBounds 目标区域边界
     */
    fun startHintAnimation(animationType: HintAnimation, targetBounds: RectF) {
        // 先停止已有动画
        stopHintAnimation()

        if (animationType == HintAnimation.NONE) return

        hintAnimator = HintAnimator(context, targetBounds, animationType)
        hintAnimator?.start()

        // 创建驱动重绘的 ValueAnimator（每帧触发 invalidate）
        hintAnimDriver = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when (animationType) {
                HintAnimation.TAP -> 1500L
                HintAnimation.SWIPE_LEFT, HintAnimation.SWIPE_RIGHT, HintAnimation.SWIPE_UP -> 1200L
                HintAnimation.PULSE -> 2000L
                HintAnimation.TYPE -> 1000L
                else -> 1500L
            }
            repeatCount = ValueAnimator.INFINITE
            interpolator = null // 线性进度
            addUpdateListener { invalidate() }
            start()
        }
    }

    /**
     * 停止并清除手势动画
     * 释放 [HintAnimator] 和驱动 Animator 的资源
     */
    fun stopHintAnimation() {
        hintAnimDriver?.cancel()
        hintAnimDriver = null
        hintAnimator?.stop()
        hintAnimator = null
        invalidate()
    }

    // ======================== 公开 API：步骤过渡动画 ========================

    /**
     * 切换步骤时的过渡动画
     *
     * 镂空区域从当前位置平滑 morph 到新位置 (300ms easeOut)，
     * 气泡内容在 morph 动画完成一半时 crossfade (200ms)。
     *
     * @param newBounds 新的目标区域屏幕坐标
     * @param newShape 新的镂空形状
     * @param paddingDp 新的内边距(dp)
     */
    fun transitionTo(newBounds: Rect, newShape: TargetShape, paddingDp: Float) {
        // 记录起始状态
        fromRectF.set(holeRectF)

        // 将新边界转换为相对坐标
        val paddingPx = paddingDp * density
        val location = IntArray(2)
        getLocationOnScreen(location)
        val offsetX = -location[0].toFloat()
        val offsetY = -location[1].toFloat()

        val toRectF = RectF(
            newBounds.left.toFloat() + offsetX - paddingPx,
            newBounds.top.toFloat() + offsetY - paddingPx,
            newBounds.right.toFloat() + offsetX + paddingPx,
            newBounds.bottom.toFloat() + offsetY + paddingPx
        )

        isMorphing = true

        // ValueAnimator 驱动 holeRectF 从起始位置插值到目标位置
        val morphAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MORPH_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                // 四个边分别线性插值
                holeRectF.set(
                    fromRectF.left + (toRectF.left - fromRectF.left) * fraction,
                    fromRectF.top + (toRectF.top - fromRectF.top) * fraction,
                    fromRectF.right + (toRectF.right - fromRectF.right) * fraction,
                    fromRectF.bottom + (toRectF.bottom - fromRectF.bottom) * fraction
                )
                holeShape = newShape
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isMorphing = false
                }
            })
            start()
        }
    }

    // ======================== 公开 API：移除遮罩 ========================

    /**
     * 完全移除遮罩层
     *
     * 执行淡出动画 (250ms)，完成后将自身从父容器中移除。
     *
     * @param onComplete 动画完成后的回调
     */
    fun dismiss(onComplete: () -> Unit) {
        // 先停止所有动画
        stopHintAnimation()

        animate()
            .alpha(0f)
            .setDuration(DISMISS_DURATION)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                // 移除气泡入场动画残留状态
                tooltipContainer.animate().cancel()
                // 从父容器移除自身
                (parent as? ViewGroup)?.removeView(this@SpotlightOverlay)
                onComplete()
            }
            .start()
    }

    // ======================== 步骤指示器管理 ========================

    /**
     * 更新步骤指示器的圆点显示
     *
     * @param total 总步骤数
     * @param current 当前步骤索引（从 0 开始）
     */
    fun updateStepIndicators(total: Int, current: Int) {
        totalSteps = total
        currentStepIndex = current.coerceIn(0, total - 1)

        // 清除旧指示器
        stepIndicators.removeAllViews()

        val brandPrimary = ContextCompat.getColor(context, R.color.brand_primary)
        val mutedColor = ContextCompat.getColor(context, R.color.text_muted)
        val dotSizePx = (INDICATOR_DOT_SIZE_DP * density).toInt()
        val gapPx = (INDICATOR_GAP_DP * density).toInt()

        for (i in 0 until total) {
            val dot = View(context).apply {
                val params = LinearLayout.LayoutParams(dotSizePx, dotSizePx)
                if (i > 0) params.marginStart = gapPx
                layoutParams = params
                // 当前步骤用品牌色，其余用灰色
                background = createOvalDrawable(if (i == current) brandPrimary else mutedColor)
            }
            stepIndicators.addView(dot)
        }
    }

    /**
     * 更新"下一步"按钮的文字（最后一步改为"完成了"等）
     */
    fun updateNextButton(isLastStep: Boolean) {
        btnNext.text = if (isLastStep) "完成了" else "下一步"
    }

    // ======================== Drawable 工厂方法 ========================

    /**
     * 创建圆角矩形背景 Drawable
     */
    private fun createRoundedRectDrawable(color: Int, cornerRadiusPx: Int): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = cornerRadiusPx.toFloat()
        }
    }

    /**
     * 创建描边按钮背景 Drawable
     */
    private fun createStrokeDrawable(strokeColor: Int, strokeWidthPx: Int, cornerRadiusPx: Int): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.TRANSPARENT)
            setStroke(strokeWidthPx, strokeColor)
            cornerRadius = cornerRadiusPx.toFloat()
        }
    }

    /**
     * 创建品牌色渐变药丸形按钮背景 Drawable
     */
    private fun createGradientPillDrawable(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
            colors = intArrayOf(
                ContextCompat.getColor(context, R.color.brand_primary),
                ContextCompat.getColor(context, R.color.gradient_purple_end)
            )
            cornerRadius = 22f * density
        }
    }

    /**
     * 创建圆形（椭圆）Drawable 用于步骤指示器圆点
     */
    private fun createOvalDrawable(color: Int): android.graphics.drawable.Drawable {
        return android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply {
            paint.color = color
        }
    }
}
