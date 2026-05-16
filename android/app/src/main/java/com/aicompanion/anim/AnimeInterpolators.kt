package com.aicompanion.anim

import android.animation.TimeInterpolator
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object AnimeInterpolators {

    val easeInOutQuint: TimeInterpolator = TimeInterpolator { t ->
        if (t < 0.5f) 16f * t.pow(5) else 1f - (-2f * t + 2f).pow(5) / 2f
    }

    val easeOutElastic: TimeInterpolator = TimeInterpolator { t ->
        if (t == 0f || t == 1f) t else {
            val c4 = (2 * Math.PI) / 3
            (2.0.pow(-10.0 * t) * sin((t * 10 - 0.75) * c4) + 1).toFloat()
        }
    }

    val easeOutBack: TimeInterpolator = TimeInterpolator { t ->
        val c1 = 1.70158f
        val c3 = c1 + 1f
        1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }

    val easeOutCubic: TimeInterpolator = TimeInterpolator { t ->
        1f - (1f - t).pow(3)
    }

    val easeInOutCubic: TimeInterpolator = TimeInterpolator { t ->
        if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
    }

    val easeOutQuart: TimeInterpolator = TimeInterpolator { t ->
        1f - (1f - t).pow(4)
    }

    val easeInOutQuart: TimeInterpolator = TimeInterpolator { t ->
        if (t < 0.5f) 8f * t.pow(4) else 1f - (-2f * t + 2f).pow(4) / 2f
    }

    val easeOutExpo: TimeInterpolator = TimeInterpolator { t ->
        if (t == 1f) 1f else (1f - 2.0.pow(-10.0 * t).toFloat())
    }

    val springBounce: TimeInterpolator = TimeInterpolator { t ->
        val damping = 0.4
        val frequency = 8.0
        (1.0 - cos(t * frequency * Math.PI) * Math.exp(-damping * t * 10)).coerceIn(0.0, 1.0 + damping * 0.3).toFloat()
    }

    val smoothStep: TimeInterpolator = TimeInterpolator { t ->
        t * t * (3 - 2 * t)
    }

    val easeOutCirc: TimeInterpolator = TimeInterpolator { t ->
        sqrt(1.0 - (t - 1.0).pow(2)).toFloat()
    }

    val easeInCubic: TimeInterpolator = TimeInterpolator { t ->
        t * t * t
    }
}
