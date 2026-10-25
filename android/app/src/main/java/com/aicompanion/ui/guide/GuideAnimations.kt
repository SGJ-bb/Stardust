/** 引导页用的动效：用 Compose 动画"演"一遍用户会在手机上看到什么。 */
package com.aicompanion.ui.guide

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun GuideAnimation(anim: GuideAnim, accent: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "guide")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (anim) {
            GuideAnim.WAKE_WAVE -> WakeWave(t, accent)
            GuideAnim.TAP_RIPPLE -> TapRipple(t, accent)
            GuideAnim.TOGGLE_ON -> ToggleOn(t, accent)
            GuideAnim.PERMISSION_LADDER -> PermissionLadder(t, accent)
            GuideAnim.RADAR_SCAN -> RadarScan(t, accent)
            GuideAnim.CHECK_STEPS -> CheckSteps(t, accent)
            GuideAnim.TERMINAL -> Terminal(t, accent)
        }
    }
}

/* ---------------------------------------------------------------- 语音波形 */

@Composable
private fun WakeWave(t: Float, accent: Color) {
    val bars = 11
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(bars) { i ->
                val phase = t * 2f * PI.toFloat() + i * 0.45f
                val h = 10f + (sin(phase) * 0.5f + 0.5f) * 34f
                Box(
                    Modifier
                        .width(5.dp)
                        .height(h.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent.copy(alpha = 0.35f + 0.55f * (h / 44f)))
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val hit = t > 0.62f
        Text(
            text = if (hit) "命中 · 星尘星尘" else "…… 星尘星尘",
            color = if (hit) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (hit) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/* ---------------------------------------------------------------- 点按涟漪 */

@Composable
private fun TapRipple(t: Float, accent: Color) {
    // 0~0.55 手指下落，0.55 之后涟漪扩散
    val press = (t / 0.55f).coerceIn(0f, 1f)
    val ripple = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)
    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(accent.copy(alpha = 0.12f), radius = r)
            // 目标按钮
            drawCircle(accent.copy(alpha = 0.85f), radius = r * 0.36f)
            if (ripple > 0f) {
                drawCircle(
                    color = accent.copy(alpha = (1f - ripple) * 0.55f),
                    radius = r * (0.36f + 0.6f * ripple),
                    style = Stroke(width = 3f),
                )
            }
            // 手指
            val fy = size.height * (1.05f - 0.35f * press)
            drawCircle(
                color = accent.copy(alpha = 0.9f),
                radius = 9f,
                center = Offset(size.width / 2f, fy),
            )
        }
        Text(
            "点击",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/* ---------------------------------------------------------------- 开关 */

@Composable
private fun ToggleOn(t: Float, accent: Color) {
    val on = (t / 0.45f).coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(84.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (on > 0.5f) accent else MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .padding(4.dp)
                    .offset(x = (40.dp * on))
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (on > 0.85f) {
                    Icon(Icons.Default.Check, null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (on > 0.85f) "已开启" else "未开启",
            color = if (on > 0.85f) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

/* ---------------------------------------------------------------- 权限阶梯 */

@Composable
private fun PermissionLadder(t: Float, accent: Color) {
    val items = listOf("无障碍", "通知使用权", "使用情况访问", "电池无限制")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, label ->
            val lit = t * (items.size + 1) > i + 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (lit) accent else accent.copy(alpha = 0.18f))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    fontSize = 13.sp,
                    color = if (lit) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                if (lit) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Check, null,
                        tint = accent,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- 雷达扫描 */

@Composable
private fun RadarScan(t: Float, accent: Color) {
    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f * 0.92f
            drawCircle(accent.copy(alpha = 0.18f), radius = r, center = c, style = Stroke(2f))
            drawCircle(accent.copy(alpha = 0.10f), radius = r * 0.6f, center = c, style = Stroke(2f))
            // 扫描臂
            val angle = t * 2f * PI.toFloat() - PI.toFloat() / 2f
            drawLine(
                color = accent.copy(alpha = 0.85f),
                start = c,
                end = Offset(c.x + r * kotlin.math.cos(angle), c.y + r * kotlin.math.sin(angle)),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
            // 三个"服务点"，扫到就亮
            val dots = listOf(0.32f to 0.30f, 0.74f to 0.42f, 0.55f to 0.78f)
            dots.forEachIndexed { i, (dx, dy) ->
                val hitAt = 0.25f + i * 0.22f
                val alpha = if (t > hitAt) 0.95f else 0.18f
                drawCircle(
                    color = accent.copy(alpha = alpha),
                    radius = 5f,
                    center = Offset(size.width * dx, size.height * dy),
                )
            }
        }
        if (t > 0.85f) {
            Text("找到 :5555", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/* ---------------------------------------------------------------- 排查清单 */

@Composable
private fun CheckSteps(t: Float, accent: Color) {
    val items = listOf("录音权限已给", "通知栏服务在跑", "自启动 / 后台已放行")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, label ->
            val done = t * (items.size + 1) > i + 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (done) accent else Color.Transparent)
                        .then(
                            if (done) Modifier else Modifier.background(
                                MaterialTheme.colorScheme.surfaceVariant, CircleShape
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    fontSize = 13.sp,
                    color = if (done) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/* ---------------------------------------------------------------- 终端 */

@Composable
private fun Terminal(t: Float, accent: Color) {
    val lines = listOf(
        "$ adb tcpip 5555",
        "restarting in TCP mode port: 5555",
        "$ pm grant com.aicompanion ...WRITE_SECURE_SETTINGS",
        "done",
    )
    val shown = (t * (lines.size + 1)).toInt()
    Column(
        Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEachIndexed { i, line ->
            if (i < shown) {
                Text(
                    line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (line.startsWith("$")) accent else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
