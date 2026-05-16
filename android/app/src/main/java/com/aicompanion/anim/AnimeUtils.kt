package com.aicompanion.anim

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.RecyclerView

object AnimeUtils {

    private const val DEFAULT_DURATION = 400L
    private const val STAGGER_DELAY = 60L

    fun slideInFromRight(view: View, delay: Long = 0, duration: Long = DEFAULT_DURATION) {
        view.translationX = 300f
        view.alpha = 0f
        view.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(AnimeInterpolators.easeOutBack)
            .start()
    }

    fun slideInFromLeft(view: View, delay: Long = 0, duration: Long = DEFAULT_DURATION) {
        view.translationX = -300f
        view.alpha = 0f
        view.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(AnimeInterpolators.easeOutBack)
            .start()
    }

    fun slideInFromBottom(view: View, delay: Long = 0, duration: Long = DEFAULT_DURATION) {
        view.translationY = 200f
        view.alpha = 0f
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(AnimeInterpolators.easeOutCubic)
            .start()
    }

    fun fadeInScale(view: View, delay: Long = 0, duration: Long = DEFAULT_DURATION) {
        view.scaleX = 0.6f
        view.scaleY = 0.6f
        view.alpha = 0f
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(AnimeInterpolators.easeOutBack)
            .start()
    }

    fun fadeIn(view: View, delay: Long = 0, duration: Long = 350L) {
        view.alpha = 0f
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(AnimeInterpolators.easeOutCubic)
            .start()
    }

    fun fadeOut(view: View, duration: Long = 250L, onComplete: (() -> Unit)? = null) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AnimeInterpolators.easeInCubic)
            .withEndAction { onComplete?.invoke() }
            .start()
    }

    fun springScale(view: View, from: Float = 0.85f, to: Float = 1f, duration: Long = 500L) {
        view.scaleX = from
        view.scaleY = from
        val scaleXHolder = PropertyValuesHolder.ofFloat("scaleX", from, to)
        val scaleYHolder = PropertyValuesHolder.ofFloat("scaleY", from, to)
        ObjectAnimator.ofPropertyValuesHolder(view, scaleXHolder, scaleYHolder).apply {
            this.duration = duration
            interpolator = OvershootInterpolator(1.2f)
            start()
        }
    }

    fun staggerSlideIn(recyclerView: RecyclerView, fromRight: Boolean = true) {
        recyclerView.post {
            val layoutManager = recyclerView.layoutManager ?: return@post
            val childCount = layoutManager.childCount
            for (i in 0 until childCount) {
                val child = layoutManager.getChildAt(i) ?: continue
                val delay = i.toLong() * STAGGER_DELAY
                if (fromRight) {
                    slideInFromRight(child, delay)
                } else {
                    slideInFromLeft(child, delay)
                }
            }
        }
    }

    fun staggerFadeInScale(recyclerView: RecyclerView) {
        recyclerView.post {
            val layoutManager = recyclerView.layoutManager ?: return@post
            val childCount = layoutManager.childCount
            for (i in 0 until childCount) {
                val child = layoutManager.getChildAt(i) ?: continue
                val delay = i.toLong() * STAGGER_DELAY
                fadeInScale(child, delay)
            }
        }
    }

    fun pulse(view: View, scale: Float = 1.08f, duration: Long = 300L) {
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(duration / 2)
            .setInterpolator(AnimeInterpolators.easeOutCubic)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(duration / 2)
                    .setInterpolator(AnimeInterpolators.easeOutBack)
                    .start()
            }
            .start()
    }

    fun setupTouchScale(view: View, scaleDown: Float = 0.92f, scaleUp: Float = 1f) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(scaleDown)
                        .scaleY(scaleDown)
                        .setDuration(100)
                        .setInterpolator(AnimeInterpolators.easeOutCubic)
                        .start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(scaleUp)
                        .scaleY(scaleUp)
                        .setDuration(250)
                        .setInterpolator(AnimeInterpolators.easeOutBack)
                        .start()
                }
            }
            false
        }
    }

    fun chatMessageIn(view: View, isUser: Boolean, position: Int) {
        val delay = (position % 5) * 30L
        if (isUser) {
            view.translationX = 150f
            view.alpha = 0f
            view.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(350)
                .setStartDelay(delay)
                .setInterpolator(AnimeInterpolators.easeOutBack)
                .start()
        } else {
            view.translationX = -150f
            view.alpha = 0f
            view.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(350)
                .setStartDelay(delay)
                .setInterpolator(AnimeInterpolators.easeOutBack)
                .start()
        }
    }

    fun shimmer(view: View) {
        view.alpha = 0.7f
        view.animate()
            .alpha(1f)
            .setDuration(600)
            .setInterpolator(AnimeInterpolators.easeInOutCubic)
            .withEndAction {
                view.animate()
                    .alpha(0.85f)
                    .setDuration(400)
                    .start()
            }
            .start()
    }

    fun bounceIn(view: View, delay: Long = 0) {
        view.scaleX = 0f
        view.scaleY = 0f
        view.alpha = 0f
        val scaleXHolder = PropertyValuesHolder.ofFloat("scaleX", 0f, 1.15f, 1f)
        val scaleYHolder = PropertyValuesHolder.ofFloat("scaleY", 0f, 1.15f, 1f)
        val alphaHolder = PropertyValuesHolder.ofFloat("alpha", 0f, 1f)
        ObjectAnimator.ofPropertyValuesHolder(view, scaleXHolder, scaleYHolder, alphaHolder).apply {
            duration = 500
            startDelay = delay
            interpolator = AnimeInterpolators.easeOutElastic
            start()
        }
    }

    fun clearAnimations(view: View) {
        view.animate().cancel()
        view.translationX = 0f
        view.translationY = 0f
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
    }
}
