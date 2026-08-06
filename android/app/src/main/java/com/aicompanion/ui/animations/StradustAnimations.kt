package com.aicompanion.ui.animations

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle

/**
 * 星尘全局动效规格系统
 * 统一所有 Compose 动画参数，确保视觉一致性
 */
object StradustAnimSpec {
    const val FADE_IN_MS = 300
    const val SLIDE_IN_MS = 400
    const val INSTANT_MS = 75
    const val QUICK_MS = 150
    const val STANDARD_MS = 250
    const val EMPHASIZED_MS = 400
    const val STAGGER_MS = 50

    val SpringDefault = spring<Float>(dampingRatio = 0.7f, stiffness = 400f)
    val SpringBouncy = spring<Float>(dampingRatio = 0.5f, stiffness = 300f)
    val SpringGentle = spring<Float>(dampingRatio = 0.85f, stiffness = 200f)
    val EaseOut = FastOutSlowInEasing
    val Decelerate = LinearOutSlowInEasing

    val FadeInSpec = tween<Float>(durationMillis = FADE_IN_MS, easing = EaseOut)
    val SlideInSpec = tween<Int>(durationMillis = SLIDE_IN_MS, easing = EaseOut)
}

/** 页面入场动画：淡入 + 从底部滑入 */
@Composable
fun StradustFadeSlideIn(
    modifier: Modifier = Modifier,
    delay: Int = 0,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = true,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(durationMillis = StradustAnimSpec.FADE_IN_MS + delay, easing = StradustAnimSpec.EaseOut),
        ) + slideInVertically(
            animationSpec = tween(durationMillis = StradustAnimSpec.SLIDE_IN_MS + delay, easing = StradustAnimSpec.EaseOut),
            initialOffsetY = { it / 3 },
        ),
    ) {
        content()
    }
}

/** 列表项交错入场 Modifier（使用基础动画实现） */
fun Modifier.stradustItemAnimation(index: Int = 0): Modifier {
    // 基础版本：直接返回自身（动画由各页面自行处理）
    // 如需入场动画，请使用 AnimatedVisibility 包裹列表项
    return this
}

/** 按压缩放 Modifier */
@Composable
fun Modifier.pressedScale(pressScale: Float = 0.96f): Modifier {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressScale else 1f,
        animationSpec = StradustAnimSpec.SpringDefault,
        label = "pressScale",
    )

    return this then Modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                isPressed = true
                tryAwaitRelease()
                isPressed = false
            })
        }
}

/** 弹性出现/消失动画 */
@Composable
fun StradustSpringAppear(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = StradustAnimSpec.STANDARD_MS, easing = StradustAnimSpec.Decelerate))
            + scaleIn(animationSpec = StradustAnimSpec.SpringDefault, initialScale = 0.85f),
        exit = fadeOut(animationSpec = tween(durationMillis = StradustAnimSpec.QUICK_MS, easing = StradustAnimSpec.EaseOut))
            + scaleOut(animationSpec = tween(durationMillis = StradustAnimSpec.QUICK_MS, easing = StradustAnimSpec.EaseOut), targetScale = 0.85f),
    ) {
        content()
    }
}

/** 数字滚动动画 */
@Composable
fun AnimatedCounter(targetValue: Int, modifier: Modifier = Modifier, style: TextStyle = androidx.compose.material3.LocalTextStyle.current) {
    val animatedValue by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = tween(durationMillis = StradustAnimSpec.EMPHASIZED_MS, easing = StradustAnimSpec.Decelerate),
        label = "counter",
    )
    androidx.compose.material3.Text(text = animatedValue.toInt().toString(), style = style, modifier = modifier)
}

/** 渐入渐出切换动画 */
@Composable
fun <T : Any> StradustCrossfade(targetState: T, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            (fadeIn(
                animationSpec = tween(durationMillis = StradustAnimSpec.FADE_IN_MS, easing = StradustAnimSpec.Decelerate)
            ) togetherWith fadeOut(
                animationSpec = tween(durationMillis = StradustAnimSpec.QUICK_MS, easing = StradustAnimSpec.EaseOut)
            ))
        },
        label = "crossfade",
    ) { state ->
        content(state)
    }
}

/** 点击缩放反馈 Modifier */
@Composable
fun Modifier.clickScale(targetScale: Float = 0.95f, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    var isClicked by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isClicked) targetScale else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "clickScale",
    )

    LaunchedEffect(isClicked) {
        if (isClicked) {
            kotlinx.coroutines.delay(100)
            isClicked = false
        }
    }

    return this then Modifier
        .semantics { this.role = role }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(onClick) {
            detectTapGestures(onTap = { isClicked = true; onClick() })
        }
}
