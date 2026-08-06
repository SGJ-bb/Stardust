package com.aicompanion.ui.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator

/**
 * 星尘动效工具类
 *
 * 基于 Anime.js + Impeccable 动效原则：
 * 1. 物理弹簧动画优先（SpringAnimation），避免弹跳/弹性缓动
 * 2. 时长分级：即时(75ms) → 微交互(150ms) → 标准(250ms) → 强调(400ms) → 复杂(550ms)
 * 3. 缓动曲线：FastOutSlowIn 为主，LinearOutSlowIn 为辅
 * 4. 交错动画：列表项逐个入场，间隔 50ms
 * 5. Impeccable 反模式：不用 bounce/elastic，不用纯线性
 */
object MotionUtils {

    // ===== 时长常量 (与 dimens.xml 同步) =====
    const val INSTANT = 75L
    const val QUICK = 150L
    const val STANDARD = 250L
    const val EMPHASIZED = 400L
    const val COMPLEX = 550L

    // ===== 交错间隔 =====
    const val STAGGER_DEFAULT = 50L
    const val STAGGER_TIGHT = 30L
    const val STAGGER_LOOSE = 80L

    // ===== 弹簧参数 (Impeccable: 无弹跳，阻尼比 > 0.8) =====
    private const val SPRING_STIFFNESS_DEFAULT = 400f
    private const val SPRING_DAMPING_DEFAULT = 0.75f
    private const val SPRING_STIFFNESS_GENTLE = 200f
    private const val SPRING_DAMPING_GENTLE = 0.85f
    private const val SPRING_STIFFNESS_SNAPPY = 800f
    private const val SPRING_DAMPING_SNAPPY = 0.7f

    // ===== 缓动插值器 (Impeccable: 标准曲线) =====
    /** 主缓动：快速启动，缓慢停止 (Material standard) */
    val EASE_STANDARD = FastOutSlowInInterpolator()

    /** 减速缓动：线性启动，缓慢停止 (Material decelerate) */
    val EASE_DECELERATE = LinearOutSlowInInterpolator()

    /** 轻微过冲：用于缩放/旋转的入场 (Impeccable: 仅允许微过冲) */
    val EASE_OVERSHOOT = OvershootInterpolator(0.8f)

    // ===== 弹簧动画 =====

    /**
     * 创建弹簧动画
     * @param view 目标视图
     * @param property DynamicAnimation属性 (TRANSLATION_X, TRANSLATION_Y 等)
     * @param finalPosition 终点位置
     * @param stiffness 刚度 (越大越快)
     * @param dampingRatio 阻尼比 (越大越不弹)
     */
    fun spring(
        view: View,
        property: DynamicAnimation.ViewProperty,
        finalPosition: Float,
        stiffness: Float = SPRING_STIFFNESS_DEFAULT,
        dampingRatio: Float = SPRING_DAMPING_DEFAULT
    ): SpringAnimation {
        return SpringAnimation(view, property).apply {
            spring = SpringForce(finalPosition).apply {
                this.stiffness = stiffness
                this.dampingRatio = dampingRatio
            }
        }
    }

    /** 弹簧 X 轴位移 */
    fun springTranslateX(view: View, toX: Float, stiffness: Float = SPRING_STIFFNESS_DEFAULT): SpringAnimation {
        return spring(view, DynamicAnimation.TRANSLATION_X, toX, stiffness)
    }

    /** 弹簧 Y 轴位移 */
    fun springTranslateY(view: View, toY: Float, stiffness: Float = SPRING_STIFFNESS_DEFAULT): SpringAnimation {
        return spring(view, DynamicAnimation.TRANSLATION_Y, toY, stiffness)
    }

    /** 弹簧缩放 (Impeccable: 微过冲但无弹跳，X+Y同步) */
    fun springScale(view: View, toScale: Float = 1f, stiffness: Float = SPRING_STIFFNESS_SNAPPY): SpringAnimation {
        val sy = spring(view, DynamicAnimation.SCALE_Y, toScale, stiffness, SPRING_DAMPING_SNAPPY)
        val sx = spring(view, DynamicAnimation.SCALE_X, toScale, stiffness, SPRING_DAMPING_SNAPPY)
        sx.start()
        sy.start()
        return sx
    }

    /** 弹簧缩放 (X+Y 同步) */
    fun springScaleXY(view: View, toScale: Float = 1f): Pair<SpringAnimation, SpringAnimation> {
        val sx = SpringAnimation(view, DynamicAnimation.SCALE_X).apply {
            spring = SpringForce(toScale).apply {
                stiffness = SPRING_STIFFNESS_SNAPPY
                dampingRatio = SPRING_DAMPING_SNAPPY
            }
        }
        val sy = SpringAnimation(view, DynamicAnimation.SCALE_Y).apply {
            spring = SpringForce(toScale).apply {
                stiffness = SPRING_STIFFNESS_SNAPPY
                dampingRatio = SPRING_DAMPING_SNAPPY
            }
        }
        return sx to sy
    }

    /** 弹簧透明度 */
    fun springAlpha(view: View, toAlpha: Float = 1f): SpringAnimation {
        return spring(view, DynamicAnimation.ALPHA, toAlpha, SPRING_STIFFNESS_GENTLE, SPRING_DAMPING_GENTLE)
    }

    // ===== 属性动画 =====

    /** 淡入 */
    fun fadeIn(view: View, duration: Long = STANDARD, delay: Long = 0, onEnd: (() -> Unit)? = null) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            this.duration = duration
            this.startDelay = delay
            interpolator = EASE_DECELERATE
            onEnd?.let { callback ->
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { callback() }
                })
            }
        }.start()
    }

    /** 淡出 */
    fun fadeOut(view: View, duration: Long = QUICK, delay: Long = 0, onEnd: (() -> Unit)? = null) {
        ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f).apply {
            this.duration = duration
            this.startDelay = delay
            interpolator = EASE_STANDARD
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    onEnd?.invoke()
                }
            })
        }.start()
    }

    /** 从底部滑入 (标准入场) */
    fun slideInFromBottom(view: View, distance: Float = 60f, duration: Long = STANDARD, delay: Long = 0) {
        view.translationY = distance
        view.alpha = 0f
        view.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, distance, 0f).apply {
            this.duration = duration
            this.startDelay = delay
            interpolator = EASE_DECELERATE
        }.start()
        ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            this.duration = duration
            this.startDelay = delay
            interpolator = EASE_DECELERATE
        }.start()
    }

    /** 从顶部滑入 */
    fun slideInFromTop(view: View, distance: Float = -60f, duration: Long = STANDARD, delay: Long = 0) {
        view.translationY = distance
        view.alpha = 0f
        view.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, distance, 0f).apply {
            this.duration = duration
            this.startDelay = delay
            interpolator = EASE_DECELERATE
        }.start()
        ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            this.duration = duration
            this.startDelay = delay
            interpolator = EASE_DECELERATE
        }.start()
    }

    /** 缩放入场 (Impeccable: 微过冲，0.9→1.0) */
    fun scaleIn(view: View, fromScale: Float = 0.9f, duration: Long = STANDARD, delay: Long = 0) {
        view.scaleX = fromScale
        view.scaleY = fromScale
        view.alpha = 0f
        view.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(view, View.SCALE_X, fromScale, 1f).apply {
            this.duration = duration
            this.startDelay = delay
            interpolator = EASE_OVERSHOOT
        }.start()
        ObjectAnimator.ofFloat(view, View.SCALE_Y, fromScale, 1f).apply {
            this.duration = duration
            this.startDelay = delay
            interpolator = EASE_OVERSHOOT
        }.start()
        ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            this.duration = (duration * 0.6).toLong()
            this.startDelay = delay
            interpolator = EASE_DECELERATE
        }.start()
    }

    /** 缩放退场 */
    fun scaleOut(view: View, toScale: Float = 0.9f, duration: Long = QUICK, onEnd: (() -> Unit)? = null) {
        ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, toScale).apply {
            this.duration = duration
            interpolator = EASE_STANDARD
        }.start()
        ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, toScale).apply {
            this.duration = duration
            interpolator = EASE_STANDARD
        }.start()
        ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f).apply {
            this.duration = duration
            interpolator = EASE_STANDARD
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    onEnd?.invoke()
                }
            })
        }.start()
    }

    // ===== 交错动画 (Anime.js stagger 原则) =====

    /**
     * 交错淡入列表项
     * @param views 视图列表
     * @param staggerDelay 项间延迟 (默认50ms)
     * @param duration 单项动画时长
     */
    fun staggerFadeIn(views: List<View>, staggerDelay: Long = STAGGER_DEFAULT, duration: Long = STANDARD) {
        views.forEachIndexed { index, view ->
            fadeIn(view, duration, delay = index * staggerDelay)
        }
    }

    /**
     * 交错从底部滑入
     * @param views 视图列表
     * @param staggerDelay 项间延迟
     * @param distance 滑入距离
     */
    fun staggerSlideInFromBottom(
        views: List<View>,
        staggerDelay: Long = STAGGER_DEFAULT,
        distance: Float = 40f,
        duration: Long = STANDARD
    ) {
        views.forEachIndexed { index, view ->
            slideInFromBottom(view, distance, duration, delay = index * staggerDelay)
        }
    }

    /**
     * 交错缩放入场
     * @param views 视图列表
     * @param staggerDelay 项间延迟
     */
    fun staggerScaleIn(views: List<View>, staggerDelay: Long = STAGGER_DEFAULT, duration: Long = STANDARD) {
        views.forEachIndexed { index, view ->
            scaleIn(view, fromScale = 0.85f, duration = duration, delay = index * staggerDelay)
        }
    }

    // ===== 容器子视图交错 =====

    /**
     * 对 ViewGroup 子视图执行交错动画
     * @param parent 父容器
     * @param animator 单个视图的动画逻辑
     * @param staggerDelay 交错延迟
     */
    fun staggerChildren(
        parent: ViewGroup,
        staggerDelay: Long = STAGGER_DEFAULT,
        animator: (view: View, index: Int, delay: Long) -> Unit
    ) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            child.alpha = 0f
            animator(child, i, i * staggerDelay)
        }
    }

    // ===== 微交互 =====

    /** 按压缩放反馈 (Impeccable: 快速响应，75-150ms) */
    fun pressScale(view: View, scaleDown: Float = 0.95f) {
        ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, scaleDown).apply {
            duration = INSTANT
            interpolator = EASE_STANDARD
        }.start()
        ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, scaleDown).apply {
            duration = INSTANT
            interpolator = EASE_STANDARD
        }.start()
    }

    /** 释放缩放恢复 */
    fun releaseScale(view: View) {
        ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, 1f).apply {
            duration = QUICK
            interpolator = EASE_OVERSHOOT
        }.start()
        ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, 1f).apply {
            duration = QUICK
            interpolator = EASE_OVERSHOOT
        }.start()
    }

    /** 抖动反馈 (输入错误等) */
    fun shake(view: View, amplitude: Float = 10f, duration: Long = STANDARD) {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val value = amplitude * Math.sin(fraction * Math.PI * 4).toFloat() * (1f - fraction)
                view.translationX = value
            }
        }
        animator.start()
    }

    /** 脉冲发光 (新消息提示等) */
    fun pulse(view: View, minAlpha: Float = 0.4f, maxAlpha: Float = 1f, duration: Long = EMPHASIZED) {
        ObjectAnimator.ofFloat(view, View.ALPHA, minAlpha, maxAlpha, minAlpha).apply {
            this.duration = duration
            interpolator = EASE_STANDARD
            repeatCount = ValueAnimator.INFINITE
        }.start()
    }

    // ===== 高度变化动画 =====

    /** 展开动画 */
    fun expand(view: View, targetHeight: Int, duration: Long = STANDARD) {
        view.visibility = View.VISIBLE
        val currentHeight = view.height
        if (currentHeight == targetHeight) return

        val animator = ValueAnimator.ofInt(currentHeight, targetHeight).apply {
            this.duration = duration
            interpolator = EASE_DECELERATE
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                view.layoutParams = view.layoutParams.apply { height = value }
            }
        }
        animator.start()
    }

    /** 折叠动画 */
    fun collapse(view: View, duration: Long = STANDARD, onEnd: (() -> Unit)? = null) {
        val currentHeight = view.height
        if (currentHeight == 0) return

        val animator = ValueAnimator.ofInt(currentHeight, 0).apply {
            this.duration = duration
            interpolator = EASE_STANDARD
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                view.layoutParams = view.layoutParams.apply { height = value }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    onEnd?.invoke()
                }
            })
        }
        animator.start()
    }

    // ===== 工具方法 =====

    /** 取消视图所有 SpringAnimation */
    fun cancelSpringAnimations(view: View) {
        SpringAnimation(view, DynamicAnimation.TRANSLATION_X).cancel()
        SpringAnimation(view, DynamicAnimation.TRANSLATION_Y).cancel()
        SpringAnimation(view, DynamicAnimation.SCALE_X).cancel()
        SpringAnimation(view, DynamicAnimation.SCALE_Y).cancel()
        SpringAnimation(view, DynamicAnimation.ALPHA).cancel()
    }

    /** 重置视图变换 */
    fun resetTransform(view: View) {
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
        view.rotation = 0f
    }
}
