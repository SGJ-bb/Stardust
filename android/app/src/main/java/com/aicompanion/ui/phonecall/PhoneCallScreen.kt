/** 星尘语音通话界面 */
package com.aicompanion.ui.phonecall

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.animations.clickScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 语音通话界面 - Jetpack Compose 版本
 *
 * 完整复刻旧版 PhoneCallActivity 的所有UI功能：
 * - 全屏深色渐变背景 + 环境光晕
 * - 头像区域 + 4层脉冲环动画 + 呼吸缩放
 * - VoiceWaveformView 波形图（4种模式）
 * - 状态信息显示（名称/状态/时长）
 * - 转写文本区
 * - 控制按钮组（静音/挂断/扬声器）
 */
@Composable
fun PhoneCallScreen(
    // === 基础信息 ===
    personaName: String = "星尘",
    avatarUrl: String? = null,

    // === 通话状态 ===
    isCallActive: Boolean = false,
    callStatus: String = "正在接听...",
    callDurationMs: Long = 0L,

    // === 转写文本 ===
    transcript: String = "",

    // === 控制状态 ===
    isMuted: Boolean = false,
    isSpeakerOn: Boolean = true,
    waveformMode: Int = 0, // 0=IDLE, 1=LISTENING, 2=AI_SPEAKING, 3=MUTED

    // === 回调 ===
    onHangUp: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
) {
    val colors = StradustTheme.colors

    // 拦截系统返回键：通话界面下返回应走挂断逻辑，而非直接退出
    androidx.activity.compose.BackHandler { onHangUp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        colors.background,
                        colors.backgroundSecondary,
                        Color(
                            red = colors.backgroundSecondary.red * 0.6f,
                            green = colors.backgroundSecondary.green * 0.6f,
                            blue = colors.backgroundSecondary.blue * 0.6f,
                            alpha = 1f
                        )
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .drawBehind {
                // 顶部环境光晕（径向渐变圆，300dp，主题glow色半透明）
                val glowRadius = size.width.coerceAtLeast(size.height) * 0.45f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(
                                red = colors.glow.red,
                                green = colors.glow.green,
                                blue = colors.glow.blue,
                                alpha = 0.15f
                            ),
                            Color.Transparent
                        ),
                        radius = glowRadius,
                        center = Offset(size.width / 2, glowRadius * 0.3f)
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 上方弹性空间
            Spacer(modifier = Modifier.weight(0.15f))

            // 头像+脉冲环 (200dp容器)
            PulseRingAvatar(
                avatarUrl = avatarUrl,
                isActive = waveformMode == 2 // AI_SPEAKING时激活脉冲
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 波形图 (match_parent width, 60dp height)
            VoiceWaveform(
                mode = waveformMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // AI名称 (26sp, bold, letterSpacing 0.05em)
            Text(
                text = personaName,
                color = colors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (0.05f).em
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 状态行 Row(绿点 + 状态文字 13sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // 绿点指示器
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = colors.tertiary,
                            shape = CircleShape
                        )
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = callStatus,
                    color = colors.primary.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 通话时长 (12sp, muted色)
            Text(
                text = formatDuration(callDurationMs),
                color = colors.textMuted,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 转写文本区 (weight=1, 半透明surfaceContainerLow背景, 16dp圆角, padding 12dp, maxLines=4)
            Text(
                text = transcript.ifBlank { "" },
                color = colors.textSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 4,
                lineHeight = 21.sp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(
                        color = colors.surfaceContainerLow.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 控制按钮Row [静音][挂断][扬声器]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 静音按钮（通话未激活时禁用）
                CallControlButton(
                    icon = if (isMuted) "🔇" else "🎤",
                    label = if (isMuted) "已静音" else "静音",
                    backgroundColor = if (isMuted) colors.error else colors.surface,
                    enabled = isCallActive,
                    onClick = onToggleMute
                )

                Spacer(modifier = Modifier.width(16.dp))

                // 挂断按钮（始终可点击，挂断是逃生通道）
                CallControlButton(
                    icon = "📞",
                    label = "挂断",
                    backgroundColor = colors.error,
                    isLarger = true,
                    enabled = true,
                    onClick = onHangUp
                )

                Spacer(modifier = Modifier.width(16.dp))

                // 扬声器按钮（通话未激活时禁用）
                CallControlButton(
                    icon = if (isSpeakerOn) "🔊" else "🔈",
                    label = if (isSpeakerOn) "扬声器" else "听筒",
                    backgroundColor = colors.surface,
                    enabled = isCallActive,
                    onClick = onToggleSpeaker
                )
            }

            // 底部导航栏间距
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

/**
 * 头像+脉冲环组合组件
 *
 * 包含：
 * - Box容器200dp圆形
 * - 4个Canvas绘制的圆环(DrawModifier)，使用 Animatable 做alpha脉冲动效
 * - 中心头像圆形(90dp)，带 themeGradient 边框(2dp)
 */
@Composable
private fun PulseRingAvatar(
    avatarUrl: String?,
    personaName: String = "星尘",
    isActive: Boolean = false,
) {
    val colors = StradustTheme.colors
    val density = LocalDensity.current

    // 4层脉冲环的动画状态
    val ringAlphas = remember { List(4) { Animatable(0f) } }

    // 头像缩放动画状态
    val avatarScale = remember { Animatable(1f) }

    // 脉冲环动画控制
    LaunchedEffect(isActive) {
        if (isActive) {
            // 启动4层脉冲环动画（每层延迟150ms）
            ringAlphas.forEachIndexed { index, animatable ->
                launch {
                    delay(index * 150L)
                    while (isActive) {
                        // alpha: 0 → maxAlpha → 0
                        val maxAlpha = 0.4f - index * 0.08f
                        animatable.animateTo(
                            targetValue = maxAlpha,
                            animationSpec = tween(
                                durationMillis = (600 + index * 150),
                                easing = FastOutSlowInEasing
                            )
                        )
                        animatable.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = (600 + index * 150),
                                easing = LinearEasing
                            )
                        )
                    }
                    // 停止时归零
                    animatable.snapTo(0f)
                }
            }

            // 头像呼吸缩放动画 (1.0 → 1.06 → 1.0)
            while (isActive) {
                avatarScale.animateTo(
                    targetValue = 1.06f,
                    animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
                )
                avatarScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
                )
            }
            avatarScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        } else {
            // 停止所有动画
            ringAlphas.forEach { it.snapTo(0f) }
            if (avatarScale.value != 1f) {
                avatarScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // 绘制4层脉冲环
        ringAlphas.forEachIndexed { index, alphaAnim ->
            val ringDp = (120 + index * 25).dp // 120~195dp
            Canvas(
                modifier = Modifier
                    .size(ringDp)
                    .drawBehind {
                        val strokeWidth = with(density) { 1.5.dp.toPx() }
                        drawCircle(
                            color = colors.glowStrong.copy(alpha = alphaAnim.value.coerceIn(0f, 1f)),
                            style = Stroke(width = strokeWidth)
                        )
                    }
            ) {}
        }

        // 中心头像圆形(90dp)，带 themeGradient 边框(2dp)
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .drawBehind {
                    // 绘制主题渐变边框
                    drawCircle(
                        brush = colors.themeGradient,
                        style = Stroke(width = with(density) { 2.dp.toPx() })
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // 加载真实头像图片：avatarUrl 有效时用 AsyncImage，否则用文字占位符
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "通话头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = personaInitials(personaName),
                    color = colors.textPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 语音波形组件 - 纯Compose Canvas实现
 *
 * 用 Canvas 绘制40根音频柱状图，支持4种模式：
 * - IDLE: 极小振幅(5%-8%h), 颜色 #30555577
 * - LISTENING: 正弦波+包络(35%h max), 颜色 #64FFDA(青色)
 * - AI_SPEAKING: 双正弦波叠加(30%h max), 颜色 #7C4DFF(紫色)
 * - MUTED: 固定3dp高度, 颜色 #40888888
 */
@Composable
private fun VoiceWaveform(
    mode: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var phase by remember { mutableFloatStateOf(0f) }

    // 动画循环：仅 LISTENING 和 AI_SPEAKING 模式下更新相位
    LaunchedEffect(mode) {
        when (mode) {
            1, 2 -> { // LISTENING or AI_SPEAKING
                while (isActive) {
                    phase += 0.08f
                    delay(30L)
                }
            }
            // mode 0 (IDLE) 或 mode 3 (MUTED) 时不启动动画循环，等待 mode 变化
        }
    }

    val barCount = 40
    val barWidthDp = 3.dp
    val barGapDp = 2.dp

    Canvas(
        modifier = modifier
            .drawBehind {
                val width = size.width
                val height = size.height
                val barWidthPx = with(density) { barWidthDp.toPx() }
                val barGapPx = with(density) { barGapDp.toPx() }
                val totalWidth = barCount * (barWidthPx + barGapPx)
                val startX = (width - totalWidth) / 2f
                val centerY = height / 2f

                for (i in 0 until barCount) {
                    val x = startX + i * (barWidthPx + barGapPx)
                    val normalizedPos = (i.toFloat() / barCount - 0.5f) * 2f

                    // 根据模式计算振幅（完整移植旧版数学公式）
                    val amplitude = when (mode) {
                        1 -> { // MODE_LISTENING: 正弦波+包络
                            val wave = kotlin.math.sin((i + phase * 3).toDouble() * 0.5).toFloat()
                            val envelope = 1f - normalizedPos * normalizedPos
                            (height * 0.35f * envelope * (0.3f + 0.7f * kotlin.math.abs(wave)))
                                .coerceIn(with(density) { 4.dp.toPx() }, height * 0.4f)
                        }
                        2 -> { // MODE_AI_SPEAKING: 双正弦波叠加
                            val wave1 = kotlin.math.sin((i * 0.3 + phase * 2.5).toDouble()).toFloat()
                            val wave2 = kotlin.math.sin((i * 0.7 + phase * 1.8).toDouble()).toFloat()
                            val envelope = 1f - normalizedPos * normalizedPos * 0.5f
                            (height * 0.3f * envelope * (0.4f + 0.6f * kotlin.math.abs(wave1 + wave2 * 0.5f)))
                                .coerceIn(with(density) { 4.dp.toPx() }, height * 0.4f)
                        }
                        3 -> { // MODE_MUTED: 固定高度
                            with(density) { 3.dp.toPx() }
                        }
                        else -> { // MODE_IDLE: 极小振幅
                            val wave = kotlin.math.sin((i * 0.2 + phase * 0.5).toDouble()).toFloat()
                            (height * 0.05f * (0.5f + 0.5f * kotlin.math.abs(wave)))
                                .coerceIn(with(density) { 2.dp.toPx() }, height * 0.08f)
                        }
                    }

                    // 根据模式计算颜色（完全复刻旧版逻辑）
                    val color = when (mode) {
                        1 -> { // MODE_LISTENING: 青色 #64FFDA
                            val alpha = (0.4f + 0.6f * (amplitude / (height * 0.4f))).coerceIn(0f, 1f)
                            Color(red = 100f / 255f, green = 255f / 255f, blue = 218f / 255f, alpha = alpha)
                        }
                        2 -> { // MODE_AI_SPEAKING: 紫色 #7C4DFF
                            val alpha = (0.4f + 0.6f * (amplitude / (height * 0.4f))).coerceIn(0f, 1f)
                            Color(red = 124f / 255f, green = 77f / 255f, blue = 255f / 255f, alpha = alpha)
                        }
                        3 -> { // MODE_MUTED: 灰色 #888888, alpha 0x40
                            Color(red = 136f / 255f, green = 136f / 255f, blue = 136f / 255f, alpha = 0x40 / 255f)
                        }
                        else -> { // MODE_IDLE: 深灰蓝 #555577, alpha 0x30
                            Color(red = 85f / 255f, green = 85f / 255f, blue = 119f / 255f, alpha = 0x30 / 255f)
                        }
                    }

                    // 绘制圆角矩形柱状图
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, centerY - amplitude / 2),
                        size = Size(barWidthPx, amplitude),
                        cornerRadius = CornerRadius(barWidthPx / 2)
                    )
                }
            }
    ) {}
}

/**
 * 控制按钮组件
 *
 * 参数:
 * @param icon 图标emoji
 * @param label 按钮标签文字
 * @param backgroundColor 按钮背景色
 * @param isLarger 是否为大型按钮（挂断按钮使用）
 * @param onClick 点击回调
 */
@Composable
private fun CallControlButton(
    icon: String,
    label: String,
    backgroundColor: Color,
    isLarger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = StradustTheme.colors

    val buttonWidth = if (isLarger) 72.dp else 60.dp
    val buttonHeight = if (isLarger) 72.dp else 64.dp
    val iconFontSize = if (isLarger) 24.sp else 20.sp
    val labelFontSize = if (isLarger) 10.sp else 9.sp
    val cornerRadius = if (isLarger) 18.dp else 14.dp

    Column(
        modifier = Modifier
            .width(buttonWidth)
            .height(buttonHeight)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clickScale(onClick = { if (enabled) onClick() })
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = icon,
            fontSize = iconFontSize
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            color = if (isLarger) colors.onError.copy(alpha = 0.8f) else colors.textMuted,
            fontSize = labelFontSize
        )
    }
}

/**
 * 格式化通话时长
 *
 * @param ms 毫秒数
 * @return 格式化后的时间字符串，如 "03:45"
 */
private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = (totalSeconds / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    return "%02d:%02d".format(minutes, seconds)
}

/**
 * 从人名中提取首字母作为头像占位符
 */
private fun personaInitials(name: String): String {
    return name.take(1).ifBlank { "?" }
}
