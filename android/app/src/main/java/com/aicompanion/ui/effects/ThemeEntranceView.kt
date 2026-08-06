/** Theme entrance animation — full-screen Canvas rendering, each color scheme has its own aesthetic scene */
package com.aicompanion.ui.effects

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.*
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.aicompanion.theme.ThemeManager
import java.util.Random
import kotlin.math.*

/**
 * 9 theme scene mappings:
 * sakura_grad→Cherry Blossom Fall | peach_grad→Peach Blossom Dream | lavender_grad→Butterfly Dance
 * blue_grad→Ocean Waves & Bubbles | emerald_grad→Firefly Glow | sunset_grad→Sunset Glow
 * rose_gold→Maple Leaves Falling | mint_grad→Morning Dew Fade | midnight→Starry Night & Meteors
 */
class ThemeEntranceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ===== Particle type enum =====
    private enum class ParticleType {
        PETAL, BUTTERFLY, WAVE_BUBBLE, FIREFLY, RAY_DUST,
        MAPLE_LEAF, DEW_DROPLET, STAR_METEOR
    }

    // ===== Scene config =====
    private data class SceneConfig(
        val type: ParticleType,
        val skyTop: Int, val skyMid: Int, val skyBottom: Int,
        val particleColor: Int, val accentColor: Int,
        val particleCount: Int
    )

    private val scenes = mapOf(
        "sakura_grad" to SceneConfig(ParticleType.PETAL,
            Color.parseColor("#1a0a2e"), Color.parseColor("#3a1535"), Color.parseColor("#fce7f3"),
            Color.parseColor("#ffb7c5"), Color.parseColor("#ff6b9d"), 45),
        "peach_grad" to SceneConfig(ParticleType.PETAL,
            Color.parseColor("#1a0a04"), Color.parseColor("#3a2010"), Color.parseColor("#fff0e8"),
            Color.parseColor("#ffd4b8"), Color.parseColor("#ff9a76"), 35),
        "lavender_grad" to SceneConfig(ParticleType.BUTTERFLY,
            Color.parseColor("#0a0618"), Color.parseColor("#2a1040"), Color.parseColor("#ede9fe"),
            Color.parseColor("#c4b5fd"), Color.parseColor("#a78bfa"), 18),
        "blue_grad" to SceneConfig(ParticleType.WAVE_BUBBLE,
            Color.parseColor("#001a33"), Color.parseColor("#0a2848"), Color.parseColor("#dbeafe"),
            Color.parseColor("#93c5fd"), Color.parseColor("#60a5fa"), 30),
        "emerald_grad" to SceneConfig(ParticleType.FIREFLY,
            Color.parseColor("#021a0f"), Color.parseColor("#0a3018"), Color.parseColor("#d1fae5"),
            Color.parseColor("#6ee7b7"), Color.parseColor("#34d399"), 32),
        "sunset_grad" to SceneConfig(ParticleType.RAY_DUST,
            Color.parseColor("#1a0a02"), Color.parseColor("#3a2006"), Color.parseColor("#fef3c7"),
            Color.parseColor("#fcd34d"), Color.parseColor("#fbbf24"), 25),
        "rose_gold" to SceneConfig(ParticleType.MAPLE_LEAF,
            Color.parseColor("#1a0608"), Color.parseColor("#3a1518"), Color.parseColor("#fef2f2"),
            Color.parseColor("#fda4af"), Color.parseColor("#e8b4b8"), 55),
        "mint_grad" to SceneConfig(ParticleType.DEW_DROPLET,
            Color.parseColor("#031a18"), Color.parseColor("#0a3030"), Color.parseColor("#ecfeff"),
            Color.parseColor("#a5f3fc"), Color.parseColor("#67e8f9"), 28),
        "midnight" to SceneConfig(ParticleType.STAR_METEOR,
            Color.parseColor("#000010"), Color.parseColor("#0a0a28"), Color.parseColor("#1a1a40"),
            Color.parseColor("#a5b4fc"), Color.parseColor("#6366f1"), 80)
    )

    // ===== Render state =====
    private val rng = Random(System.currentTimeMillis())
    private var config: SceneConfig? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL) }
    private var bgGradient: LinearGradient? = null
    private var bgBitmap: Bitmap? = null
    private var bgCanvas: Canvas? = null

    // Particle system
    private data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var size: Float,
        var rotation: Float, var rotSpeed: Float,
        var alpha: Float, var life: Float, var maxLife: Float,
        var phase: Float = 0f,       // butterfly wing / firefly glow phase
        var wobbleAmp: Float = 0f,   // wobble amplitude
        var wobbleFreq: Float = 0f,  // wobble frequency
        var trail: MutableList<FloatArray>? = null, // trail points
        var scale: Float = 1f
    )
    private val particles = mutableListOf<Particle>()
    private var frameCount = 0L

    // Animation control
    private var isRunning = true
    private var isFadingOut = false
    private var globalAlpha = 255
    private var fadeAnimator: ValueAnimator? = null
    private var titleAlpha = 0f
    private var titleScale = 0.5f
    private var hintAlpha = 0f
    private var hintPhase = 0f

    // Size cache
    private var viewW = 0f
    private var viewH = 0f

    // Callback
    var onDismissed: (() -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** Initialize entrance animation for specified theme */
    fun setup(schemeId: String) {
        config = scenes[schemeId] ?: scenes["midnight"]!!
        buildBackground()
        spawnParticles()
        startTitleAnimation()
    }

    // ==================== Background build (offscreen cache) ====================

    private fun buildBackground() {
        val cfg = config ?: return
        if (viewW <= 0 || viewH <= 0) return

        bgBitmap?.recycle()
        bgBitmap = Bitmap.createBitmap(viewW.toInt(), viewH.toInt(), Bitmap.Config.ARGB_8888)
        bgCanvas = Canvas(bgBitmap!!)

        // Sky gradient
        bgGradient = LinearGradient(0f, 0f, 0f, viewH,
            intArrayOf(cfg.skyTop, cfg.skyMid, cfg.skyBottom), null, Shader.TileMode.CLAMP)
        paint.shader = bgGradient
        bgCanvas?.drawRect(0f, 0f, viewW, viewH, paint)
        paint.shader = null

        // Draw scenery silhouette (by theme)
        drawScenery(bgCanvas!!, cfg)
    }

    /** Draw static scenery silhouette */
    private fun drawScenery(canvas: Canvas, cfg: SceneConfig) {
        val w = viewW; val h = viewH
        paint.style = Paint.Style.FILL
        paint.alpha = 25

        when (cfg.type) {
            // Distant mountains (sakura/peach/rose gold)
            ParticleType.PETAL, ParticleType.MAPLE_LEAF -> {
                paint.color = cfg.particleColor; paint.alpha = 30
                drawMountain(canvas, w, h * 0.55f, 0.15f, -0.05f)
                paint.alpha = 50
                drawMountain(canvas, w, h * 0.62f, 0.22f, 0.08f)
                paint.alpha = 70
                drawMountain(canvas, w, h * 0.70f, 0.30f, -0.02f)
                // Moon
                paint.color = Color.WHITE; paint.alpha = 60
                canvas.drawCircle(w * 0.78f, h * 0.22f, 28f, paint)
            }
            // Lavender field hills
            ParticleType.BUTTERFLY -> {
                paint.color = cfg.particleColor; paint.alpha = 35
                drawHills(canvas, w, h * 0.58f, 3, 0.12f)
                paint.alpha = 55
                drawHills(canvas, w, h * 0.66f, 4, 0.18f)
            }
            // Ocean floor sand + coral
            ParticleType.WAVE_BUBBLE -> {
                paint.color = cfg.accentColor; paint.alpha = 25
                canvas.drawRect(0f, h * 0.65f, w, h, paint)
                // Coral rocks
                paint.color = cfg.particleColor; paint.alpha = 40
                for (i in 0..6) {
                    val cx = w * (0.1f + i * 0.13f)
                    val ch = h * (0.68f + sin(i * 1.5f) * 0.06f)
                    canvas.drawCircle(cx, ch, 12f + i % 3 * 6f, paint)
                }
            }
            // Forest tree silhouettes
            ParticleType.FIREFLY -> {
                paint.color = cfg.accentColor; paint.alpha = 20
                canvas.drawRect(0f, h * 0.60f, w, h, paint)
                // Pine trees
                paint.color = cfg.particleColor; paint.alpha = 45
                for (i in 0..8) {
                    val tx = w * (0.06f + i * 0.11f)
                    val th = h * (0.48f + (i % 3) * 0.07f)
                    drawPineTree(canvas, tx, th, 22f + (i % 4) * 8f)
                }
                // Moon
                paint.color = Color.WHITE; paint.alpha = 70
                canvas.drawCircle(w * 0.75f, h * 0.18f, 24f, paint)
                glowPaint.color = cfg.accentColor; glowPaint.alpha = 30
                canvas.drawCircle(w * 0.75f, h * 0.18f, 36f, glowPaint)
            }
            // Canyon sunset
            ParticleType.RAY_DUST -> {
                paint.color = cfg.accentColor; paint.alpha = 30
                for (layer in 0..3) {
                    val baseY = h * (0.52f + layer * 0.10f)
                    drawCanyon(canvas, w, baseY, 0.14f + layer * 0.06f, layer * 0.15f)
                    paint.alpha = 35 + layer * 15
                }
                // Sun halo
                paint.color = cfg.particleColor; paint.alpha = 40
                canvas.drawCircle(w * 0.5f, h * 0.38f, 45f, paint)
                glowPaint.color = Color.YELLOW; glowPaint.alpha = 25
                canvas.drawCircle(w * 0.5f, h * 0.38f, 70f, glowPaint)
            }
            // Grass texture
            ParticleType.DEW_DROPLET -> {
                paint.color = cfg.accentColor; paint.alpha = 18
                canvas.drawRect(0f, h * 0.62f, w, h, paint)
                // Grass wave lines
                paint.color = cfg.particleColor; paint.alpha = 35
                paint.strokeWidth = 2f; paint.style = Paint.Style.STROKE
                for (row in 0..2) {
                    val baseY = h * (0.64f + row * 0.08f)
                    path.reset()
                    path.moveTo(0f, baseY)
                    for (x in 0..20) {
                        path.lineTo(x * w / 20f, baseY + sin(x * 0.8f + row) * (6f + row * 3f))
                    }
                    canvas.drawPath(path, paint)
                }
                paint.style = Paint.Style.FILL
            }
            // Starry city skyline
            ParticleType.STAR_METEOR -> {
                paint.color = cfg.particleColor; paint.alpha = 20
                // City skyline
                path.reset()
                path.moveTo(0f, h)
                var x = 0f
                while (x < w) {
                    val bw = 15f + rng.nextFloat() * 35f
                    val bh = 20f + rng.nextFloat() * 80f
                    path.lineTo(x, h - bh)
                    path.lineTo(x + bw, h - bh)
                    path.lineTo(x + bw, h)
                    x += bw + (3f + rng.nextFloat() * 12f)
                }
                canvas.drawPath(path, paint)
            }
        }
    }

    // ===== Scenery helper draw methods =====

    private val path = Path()

    private fun drawMountain(c: Canvas, w: Float, baseY: Float, amp: Float, offset: Float) {
        path.reset(); path.moveTo(0f, c.height.toFloat())
        path.lineTo(0f, baseY + amp * c.height * sin(offset))
        for (i in 1..12) {
            val px = w * i / 12f
            val py = baseY + amp * c.height * sin(i * 1.2f + offset) * (0.5f + abs(sin(i * 0.7f)) * 0.5f)
            path.lineTo(px, py)
        }
        path.lineTo(w, c.height.toFloat()); path.close()
        c.drawPath(path, paint)
    }

    private fun drawHills(c: Canvas, w: Float, baseY: Float, count: Int, amp: Float) {
        path.reset(); path.moveTo(0f, c.height.toFloat())
        path.lineTo(0f, baseY)
        for (i in 0..count * 4) {
            val px = w * i / (count * 4f)
            val py = baseY + sin(i * 0.6f) * c.height * amp
            path.lineTo(px, py)
        }
        path.lineTo(w, c.height.toFloat()); path.close()
        c.drawPath(path, paint)
    }

    private fun drawCanyon(c: Canvas, w: Float, baseY: Float, amp: Float, jaggedness: Float) {
        path.reset(); path.moveTo(0f, c.height.toFloat())
        path.lineTo(0f, baseY)
        for (i in 0..20) {
            val px = w * i / 20f
            val py = (baseY - abs(sin(i * jaggedness + 0.5f)) * c.height.toFloat() * amp -
                     cos(i * 1.7f + jaggedness) * c.height.toFloat() * amp * 0.4f)
            path.lineTo(px, kotlin.math.max(py, baseY - c.height.toFloat() * 0.25f))
        }
        path.lineTo(w, c.height.toFloat()); path.close()
        c.drawPath(path, paint)
    }

    private fun drawPineTree(c: Canvas, cx: Float, groundY: Float, size: Float) {
        // Triangular crown
        for (tri in 0..2) {
            val ty = groundY - size * (0.6f + tri * 0.35f)
            val tw = size * (0.5f - tri * 0.12f)
            path.reset()
            path.moveTo(cx, ty - size * 0.5f)
            path.lineTo(cx - tw, ty)
            path.lineTo(cx + tw, ty)
            path.close()
            c.drawPath(path, paint)
        }
        // Trunk
        c.drawRect(cx - size * 0.08f, groundY - size * 0.15f, cx + size * 0.08f, groundY, paint)
    }

    // ==================== Particle spawn ====================

    private fun spawnParticles() {
        particles.clear()
        val cfg = config ?: return
        repeat(cfg.particleCount) { spawnParticle(cfg.type, cfg) }
    }

    private fun spawnParticle(type: ParticleType, cfg: SceneConfig) {
        val w = viewW; val h = viewH
        val p = when (type) {
            ParticleType.PETAL -> Particle(
                x = rng.nextFloat() * w, y = -rng.nextFloat() * h * 0.3f,
                vx = (rng.nextDouble() - 0.5f).toFloat() * 0.8f,
                vy = 0.8f + rng.nextFloat() * 1.2f,
                size = 6f + rng.nextFloat() * 8f,
                rotation = rng.nextFloat() * 360f,
                rotSpeed = (rng.nextDouble() - 0.5f).toFloat() * 3f,
                alpha = 180f + rng.nextFloat() * 75f,
                life = 0f, maxLife = 300f + rng.nextFloat() * 400f,
                wobbleAmp = 0.3f + rng.nextFloat() * 0.6f,
                wobbleFreq = 0.01f + rng.nextFloat() * 0.02f,
                scale = 0.3f + rng.nextFloat() * 0.7f
            )
            ParticleType.BUTTERFLY -> Particle(
                x = rng.nextFloat() * w, y = h * 0.2f + rng.nextFloat() * h * 0.5f,
                vx = (rng.nextDouble() - 0.5f).toFloat() * 1.2f,
                vy = (rng.nextDouble() - 0.5f).toFloat() * 0.6f,
                size = 10f + rng.nextFloat() * 8f,
                rotation = 0f, rotSpeed = 0f,
                alpha = 160f + rng.nextFloat() * 95f,
                life = 9999f, maxLife = 9999f,   // immortal
                phase = rng.nextFloat() * PI.toFloat(),
                wobbleAmp = 0.8f, wobbleFreq = 0.008f + rng.nextFloat() * 0.012f
            )
            ParticleType.WAVE_BUBBLE -> if (rng.nextDouble() > 0.4f) Particle(
                x = rng.nextFloat() * w, y = h + rng.nextFloat() * 50f,
                vx = (rng.nextDouble() - 0.5f).toFloat() * 0.3f,
                vy = -(0.6f + rng.nextFloat() * 1.0f),
                size = 3f + rng.nextFloat() * 7f,
                rotation = 0f, rotSpeed = 0f,
                alpha = 120f + rng.nextFloat() * 100f,
                life = 0f, maxLife = 250f + rng.nextFloat() * 300f,
                scale = 0f
            ) else Particle(
                x = rng.nextFloat() * w, y = h * 0.7f,
                vx = (rng.nextDouble() - 0.5f).toFloat() * 0.5f,
                vy = 0f,
                size = 1f + rng.nextFloat() * 2f,
                rotation = 0f, rotSpeed = 0f,
                alpha = 80f + rng.nextFloat() * 60f,
                life = 0f, maxLife = 150f + rng.nextFloat() * 200f,
                phase = rng.nextFloat() * (2f * PI.toFloat())
            )
            ParticleType.FIREFLY -> Particle(
                x = rng.nextFloat() * w, y = h * 0.3f + rng.nextFloat() * h * 0.5f,
                vx = (rng.nextDouble() - 0.5f).toFloat() * 0.4f,
                vy = (rng.nextDouble() - 0.5f).toFloat() * 0.3f,
                size = 2f + rng.nextFloat() * 4f,
                rotation = 0f, rotSpeed = 0f,
                alpha = 0f, life = 0f, maxLife = 9999f,
                phase = rng.nextFloat() * (2f * PI.toFloat()),
                trail = mutableListOf(), wobbleAmp = 0.5f, wobbleFreq = 0.01f
            )
            ParticleType.RAY_DUST -> Particle(
                x = w * 0.5f + (rng.nextDouble() - 0.5f).toFloat() * w * 0.15f,
                y = h * 0.42f,
                vx = (rng.nextDouble() - 0.5f).toFloat() * 2.5f,
                vy = -1.0f - rng.nextFloat() * 2.0f,
                size = 1.5f + rng.nextFloat() * 3f,
                rotation = 0f, rotSpeed = 0f,
                alpha = 180f + rng.nextFloat() * 75f,
                life = 0f, maxLife = 180f + rng.nextFloat() * 220f,
                scale = 0f
            )
            ParticleType.MAPLE_LEAF -> Particle(
                x = rng.nextFloat() * w, y = -rng.nextFloat() * h * 0.3f,
                vx = (rng.nextDouble() - 0.5f).toFloat() * 1.0f,
                vy = 0.9f + rng.nextFloat() * 1.4f,
                size = 8f + rng.nextFloat() * 10f,
                rotation = rng.nextFloat() * 360f,
                rotSpeed = (rng.nextDouble() - 0.5f).toFloat() * 5f,
                alpha = 170f + rng.nextFloat() * 85f,
                life = 0f, maxLife = 280f + rng.nextFloat() * 350f,
                wobbleAmp = 0.4f + rng.nextFloat() * 0.8f,
                wobbleFreq = 0.012f + rng.nextFloat() * 0.02f,
                scale = 0.4f + rng.nextFloat() * 0.6f
            )
            ParticleType.DEW_DROPLET -> Particle(
                x = w * 0.15f + rng.nextFloat() * w * 0.7f,
                y = h * 0.65f + rng.nextFloat() * h * 0.2f,
                vx = 0f, vy = 0f,
                size = 3f + rng.nextFloat() * 6f,
                rotation = 0f, rotSpeed = 0f,
                alpha = 0f, life = 0f,
                maxLife = 200f + rng.nextFloat() * 300f,
                phase = rng.nextFloat() * PI.toFloat(),
                scale = 0f
            )
            ParticleType.STAR_METEOR -> if (rng.nextDouble() > 0.85f) {
                // Meteor
                Particle(
                    x = rng.nextFloat() * w * 0.8f,
                    y = -10f,
                    vx = 2.0f + rng.nextFloat() * 3.0f,
                    vy = 2.5f + rng.nextFloat() * 2.5f,
                    size = 1.5f + rng.nextFloat() * 2.5f,
                    rotation = 0f, rotSpeed = 0f,
                    alpha = 255f, life = 0f, maxLife = 60f + rng.nextFloat() * 60f,
                    trail = mutableListOf()
                )
            } else {
                // Fixed star
                Particle(
                    x = rng.nextFloat() * w,
                    y = rng.nextFloat() * h * 0.55f,
                    vx = 0f, vy = 0f,
                    size = 1f + rng.nextFloat() * 2.5f,
                    rotation = 0f, rotSpeed = 0f,
                    alpha = 100f + rng.nextFloat() * 155f,
                    life = 0f, maxLife = 9999f,
                    phase = rng.nextFloat() * (2f * PI.toFloat())
                )
            }
        }
        particles.add(p)
    }

    // ==================== Title animation ====================

    private fun startTitleAnimation() {
        // Title pop-in
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                titleScale = 0.5f + anim.animatedFraction * 0.5f
                titleAlpha = anim.animatedFraction
            }
            start()
        }
        // Hint delayed appearance
        postDelayed({
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600
                addUpdateListener { hintAlpha = it.animatedFraction }
                start()
            }
        }, 1200)
    }

    // ==================== Draw loop ====================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewW = w.toFloat()
        viewH = h.toFloat()
        if (config != null) buildBackground()
    }

    override fun onDraw(canvas: Canvas) {
        if (config == null || !isRunning) return

        // 1. Background (offscreen cache copy)
        bgBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        // 2. Update and draw particles
        updateAndDrawParticles(canvas)

        // 3. Title text
        drawTitle(canvas)

        // 4. Tap hint
        drawHint(canvas)

        // 5. Fade out overlay
        if (isFadingOut && globalAlpha < 255) {
            paint.color = Color.BLACK; paint.alpha = globalAlpha
            canvas.drawRect(0f, 0f, viewW, viewH, paint)
        }

        frameCount++
        invalidate() // continuous redraw
    }

    // ==================== Particle update & draw ====================

    private fun updateAndDrawParticles(canvas: Canvas) {
        val cfg = config ?: return
        val deadIndices = mutableListOf<Int>()

        particles.forEachIndexed { index, p ->
            p.life += 1f
            val progress = p.life / p.maxLife

            when (cfg.type) {
                // === Petal/Maple: gravity fall + wobble + spin ===
                ParticleType.PETAL, ParticleType.MAPLE_LEAF -> {
                    p.x += p.vx + sin(frameCount * p.wobbleFreq + p.phase) * p.wobbleAmp
                    p.y += p.vy
                    p.rotation += p.rotSpeed
                    // Fade in/out
                    p.alpha = when {
                        progress < 0.1f -> (progress / 0.1f) * (180f + rng.nextFloat() * 75f)
                        progress > 0.85f -> (1f - progress) / 0.15f * 255f
                        else -> 180f + rng.nextFloat() * 75f
                    }.coerceIn(0f, 255f)

                    canvas.save()
                    canvas.translate(p.x, p.y)
                    canvas.rotate(p.rotation)
                    canvas.scale(p.scale, p.scale)
                    if (cfg.type == ParticleType.MAPLE_LEAF) drawMapleLeaf(canvas, p.size, p.alpha.toInt(), cfg)
                    else drawPetal(canvas, p.size, p.alpha.toInt(), cfg)
                    canvas.restore()

                    if (p.y > viewH + 30f) deadIndices.add(index)
                }

                // === Butterfly: Lissajous wandering + wing flapping ===
                ParticleType.BUTTERFLY -> {
                    val t = frameCount * 0.008f + p.phase
                    p.x += sin(t * 1.3f) * p.wobbleAmp + p.vx * 0.02f
                    p.y += cos(t * 0.9f) * p.wobbleAmp * 0.6f + p.vy * 0.02f
                    // Boundary bounce
                    if (p.x < 0 || p.x > viewW) p.vx *= -1f
                    if (p.y < viewH * 0.15f || p.y > viewH * 0.75f) p.vy *= -1f

                    val wingPhase = sin(frameCount * 0.15f + p.phase * 2f)
                    drawButterfly(canvas, p.x, p.y, p.size, wingPhase, p.alpha.toInt(), cfg)
                }

                // === Wave + Bubble ===
                ParticleType.WAVE_BUBBLE -> {
                    if (p.size > 4f) {
                        // Bubble rising
                        p.y += p.vy
                        p.x += sin(frameCount * 0.03f + p.phase) * 0.8f
                        p.alpha = when {
                            progress < 0.15f -> (progress / 0.15f) * 220f
                            progress > 0.8f -> (1f - progress) / 0.2f * 220f
                            else -> 220f
                        }.coerceIn(0f, 255f)
                        // Bubble circle
                        paint.color = cfg.particleColor; paint.alpha = (p.alpha * 0.5f).toInt()
                        canvas.drawCircle(p.x, p.y, p.size, paint)
                        paint.color = Color.WHITE; paint.alpha = (p.alpha * 0.6f).toInt()
                        canvas.drawCircle(p.x - p.size * 0.25f, p.y - p.size * 0.25f, p.size * 0.3f, paint)

                        if (p.y < viewH * 0.4f || p.alpha <= 0f) deadIndices.add(index)
                    } else {
                        // Wave line particle
                        val waveY = viewH * 0.72f + sin(frameCount * 0.02f + p.phase) * 12f +
                                     sin(frameCount * 0.035f + p.phase * 2f) * 6f
                        p.x += p.vx * 0.3f
                        p.alpha = (sin(frameCount * 0.025f + p.phase) * 0.5f + 0.5f) * 140f
                        paint.color = cfg.accentColor; paint.alpha = p.alpha.toInt()
                        canvas.drawCircle(p.x, waveY, p.size, paint)
                        if (p.x < -10f || p.x > viewW + 10f) deadIndices.add(index)
                    }
                }

                // === Firefly: random walk + blink + glow trail ===
                ParticleType.FIREFLY -> {
                    // Random direction change
                    if ((frameCount.toLong() + index.toLong()) % 60L == 0L) {
                        p.vx = (rng.nextDouble() - 0.5f).toFloat() * 0.8f
                        p.vy = (rng.nextDouble() - 0.5f).toFloat() * 0.6f
                    }
                    p.x += p.vx
                    p.y += p.vy
                    p.phase += 0.05f
                    // 闪烁
                    val blink = (sin(p.phase) * 0.5f + 0.5f)
                    p.alpha = blink * 255f

                    // Record trail
                    if (blink > 0.7f) {
                        p.trail?.add(floatArrayOf(p.x, p.y, p.alpha))
                        if ((p.trail?.size ?: 0) > 8) p.trail?.removeAt(0)
                    }

                    // Draw trail
                    p.trail?.forEachIndexed { ti, pt ->
                        val ratio = ti.toFloat() / (p.trail?.size ?: 1)
                        glowPaint.color = cfg.particleColor; glowPaint.alpha = (pt[2] * ratio * 0.3f).toInt()
                        canvas.drawCircle(pt[0], pt[1], p.size * ratio, glowPaint)
                    }
                    // Glow body
                    glowPaint.color = cfg.accentColor; glowPaint.alpha = p.alpha.toInt()
                    canvas.drawCircle(p.x, p.y, p.size * 2f, glowPaint)
                    paint.color = Color.WHITE; paint.alpha = (p.alpha * 0.8f).toInt()
                    canvas.drawCircle(p.x, p.y, p.size * 0.6f, paint)

                    // Boundary clamp
                    if (p.x < 0) p.x = viewW; if (p.x > viewW) p.x = 0f
                    if (p.y < viewH * 0.2f) p.vy = abs(p.vy); if (p.y > viewH * 0.8f) p.vy = -abs(p.vy)
                }

                // === Sunset rays + dust ===
                ParticleType.RAY_DUST -> {
                    p.x += p.vx
                    p.y += p.vy
                    p.alpha = when {
                        progress < 0.1f -> (progress / 0.1f) * 255f
                        progress > 0.85f -> (1f - progress) / 0.15f * 255f
                        else -> 255f
                    }.coerceIn(0f, 255f)
                    p.scale = min(1f, progress * 4f)

                    // Dust particle
                    paint.color = cfg.particleColor; paint.alpha = (p.alpha * 0.7f).toInt()
                    canvas.save()
                    canvas.translate(p.x, p.y)
                    canvas.scale(p.scale, p.scale)
                    canvas.drawCircle(0f, 0f, p.size, paint)
                    // Cross ray
                    paint.alpha = (p.alpha * 0.3f).toInt()
                    canvas.drawLine(-p.size * 2f, 0f, p.size * 2f, 0f, paint)
                            canvas.drawLine(0f, -p.size * 2f, 0f, p.size * 2f, paint)
                    canvas.restore()

                    if (p.y < -20f || p.x < -50f || p.x > viewW + 50f) deadIndices.add(index)
                }

                // === Dew droplet: fixed position grow → fade ===
                ParticleType.DEW_DROPLET -> {
                    val delay = p.phase * 60f // use phase to simulate delayed birth
                    if (p.life > delay) {
                        val localProgress = (p.life - delay) / (p.maxLife - delay)
                        // Growth phase
                        p.scale = if (localProgress < 0.2f) localProgress / 0.2f
                                   else if (localProgress < 0.7f) 1f
                                   else 1f - (localProgress - 0.7f) / 0.3f
                        p.alpha = if (localProgress < 0.1f) localProgress / 0.1f * 200f
                                  else if (localProgress > 0.7f) (1f - localProgress) / 0.3f * 200f
                                  else 200f

                        // 露珠主体（带高光）
                        paint.color = cfg.particleColor; paint.alpha = (p.alpha * 0.4f).toInt()
                        canvas.drawCircle(p.x, p.y, p.size * p.scale, paint)
                        paint.color = Color.WHITE; paint.alpha = (p.alpha * 0.7f).toInt()
                        canvas.drawCircle(p.x - p.size * 0.25f, p.y - p.size * 0.25f, p.size * 0.3f * p.scale, paint)
                    }

                    if (p.life > p.maxLife) deadIndices.add(index)
                }

                // === Stars + Meteors ===
                ParticleType.STAR_METEOR -> {
                    if (p.maxLife == 9999f) {
                        // Fixed star闪烁
                        val twinkle = sin(frameCount * 0.04f + p.phase) * 0.5f + 0.5f
                        p.alpha = 80f + twinkle * 175f
                        paint.color = cfg.particleColor; paint.alpha = p.alpha.toInt()
                        canvas.drawCircle(p.x, p.y, p.size, paint)
                        // Large star glow
                        if (p.size > 2f) {
                            glowPaint.color = cfg.accentColor; glowPaint.alpha = (p.alpha * 0.2f).toInt()
                            canvas.drawCircle(p.x, p.y, p.size * 2.5f, glowPaint)
                        }
                    } else {
                        // Meteor
                        p.x += p.vx
                        p.y += p.vy
                        p.alpha = when {
                            progress < 0.1f -> (progress / 0.1f) * 255f
                            progress > 0.85f -> (1f - progress) / 0.15f * 255f
                            else -> 255f
                        }.coerceIn(0f, 255f)

                        // Trail
                        p.trail?.add(floatArrayOf(p.x, p.y, p.alpha))
                        if ((p.trail?.size ?: 0) > 15) p.trail?.removeAt(0)

                        // Draw trail
                        p.trail?.forEachIndexed { ti, pt ->
                            val ratio = ti.toFloat() / (p.trail?.size ?: 1)
                            paint.color = cfg.accentColor; paint.alpha = (pt[2] * ratio * 0.6f).toInt()
                            val tailSize = p.size * ratio
                            canvas.drawCircle(pt[0], pt[1], tailSize, paint)
                        }
                        // Meteor head
                        paint.color = Color.WHITE; paint.alpha = p.alpha.toInt()
                        canvas.drawCircle(p.x, p.y, p.size, paint)
                        glowPaint.color = cfg.particleColor; glowPaint.alpha = (p.alpha * 0.5f).toInt()
                        canvas.drawCircle(p.x, p.y, p.size * 2f, glowPaint)

                        if (p.y > viewH + 20f || p.x > viewW + 20f) deadIndices.add(index)
                    }
                }
            }
        }

        // Remove dead particles and spawn new ones
        if (deadIndices.isNotEmpty()) {
            deadIndices.sortedDescending().forEach { particles.removeAt(it) }
            repeat(deadIndices.size) { spawnParticle(cfg.type, cfg) }
        }

        // Starry sky special: occasionally spawn meteors
        if (cfg.type == ParticleType.STAR_METEOR && frameCount % 90L == 0L && rng.nextDouble() > 0.5f) {
            spawnParticle(ParticleType.STAR_METEOR, cfg)
        }
    }

    // ==================== Particle shape drawing ====================

    /** Petal shape */
    private fun drawPetal(canvas: Canvas, size: Float, alpha: Int, cfg: SceneConfig) {
        paint.color = cfg.particleColor; paint.alpha = alpha
        path.reset()
        // Bezier curve petal
        path.moveTo(0f, -size)
        path.cubicTo(size * 0.8f, -size * 0.8f, size * 0.9f, size * 0.2f, 0f, size * 0.5f)
        path.cubicTo(-size * 0.9f, size * 0.2f, -size * 0.8f, -size * 0.8f, 0f, -size)
        canvas.drawPath(path, paint)
    }

    /** Maple leaf shape */
    private fun drawMapleLeaf(canvas: Canvas, size: Float, alpha: Int, cfg: SceneConfig) {
        paint.color = cfg.particleColor; paint.alpha = alpha
        path.reset()
        val s = size * 0.6f
        // Maple leaf outline (5 points)
        path.moveTo(0f, -s * 1.5f)           // 顶尖
        path.lineTo(s * 0.4f, -s * 0.8f)     // 右上
        path.lineTo(s * 1.1f, -s * 0.9f)     // 右尖1
        path.lineTo(s * 0.5f, -s * 0.3f)     // 右内
        path.lineTo(s * 1.0f, s * 0.4f)      // 右尖2
        path.lineTo(s * 0.2f, s * 0.2f)      // 右下内
        path.lineTo(s * 0.4f, s * 1.2f)      // 右底尖
        path.moveTo(0f, s * 1.2f)             // 底中
        path.lineTo(-s * 0.4f, s * 1.2f)     // 左底尖
        path.lineTo(-s * 0.2f, s * 0.2f)     // 左下内
        path.lineTo(-s * 1.0f, s * 0.4f)     // 左尖2
        path.lineTo(-s * 0.5f, -s * 0.3f)    // 左内
        path.lineTo(-s * 1.1f, -s * 0.9f)    // 左尖1
        path.lineTo(-s * 0.4f, -s * 0.8f)    // 左上
        path.close()
        canvas.drawPath(path, paint)
    }

    /** Butterfly shape (with wing flapping) */
    private fun drawButterfly(canvas: Canvas, x: Float, y: Float, size: Float, wingPhase: Float, alpha: Int, cfg: SceneConfig) {
        canvas.save()
        canvas.translate(x, y)
        val wingAngle = wingPhase * 45f // wing flap angle

        // Left wing
        canvas.save()
        canvas.rotate(-wingAngle)
        paint.color = cfg.particleColor; paint.alpha = alpha
        path.reset()
        path.moveTo(0f, 0f)
        path.cubicTo(-size, -size * 0.3f, -size * 1.2f, -size * 0.9f, -size * 0.3f, -size * 0.6f)
        path.cubicTo(-size * 0.5f, -size * 0.2f, -size * 0.3f, 0f, 0f, 0f)
        canvas.drawPath(path, paint)
        canvas.restore()

        // Right wing
        canvas.save()
        canvas.rotate(wingAngle)
        path.reset()
        path.moveTo(0f, 0f)
        path.cubicTo(size, -size * 0.3f, size * 1.2f, -size * 0.9f, size * 0.3f, -size * 0.6f)
        path.cubicTo(size * 0.5f, -size * 0.2f, size * 0.3f, 0f, 0f, 0f)
        canvas.drawPath(path, paint)
        canvas.restore()

        // Body
        paint.color = cfg.accentColor; paint.alpha = (alpha * 0.8f).toInt()
        canvas.drawCircle(0f, 0f, size * 0.15f, paint)

        canvas.restore()
    }

    // ==================== Title & hint text ====================

    private fun drawTitle(canvas: Canvas) {
        val scheme = try { ThemeManager.getCurrentScheme(context) } catch (_: Exception) { null }
        val name = scheme?.name ?: "Stradust"

        canvas.save()
        canvas.translate(viewW / 2f, viewH * 0.32f)
        canvas.scale(titleScale, titleScale)

        // Main title shadow glow
        textPaint.textSize = viewW * 0.07f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = try { Color.parseColor(scheme?.primaryColor ?: "") } catch (_: Exception) { Color.WHITE }
        textPaint.alpha = (titleAlpha * 30).toInt()
        canvas.drawText(name, 3f, 3f, textPaint)

        // Main title
        textPaint.alpha = (titleAlpha * 255).toInt()
        canvas.drawText(name, 0f, 0f, textPaint)

        // English subtitle
        val nameEn = config?.type?.name ?: ""
        if (nameEn.isNotEmpty()) {
            textPaint.textSize = viewW * 0.026f
            textPaint.typeface = Typeface.DEFAULT
            textPaint.color = Color.WHITE
            textPaint.alpha = (titleAlpha * 140).toInt()
            canvas.drawText(nameEn.replace("_", " ").uppercase(), 0f, textPaint.textSize + 8f, textPaint)
        }

        canvas.restore()
    }

    private fun drawHint(canvas: Canvas) {
        hintPhase += 0.04f
        val breathAlpha = (sin(hintPhase) * 0.4f + 0.6f) * hintAlpha

        textPaint.textSize = viewW * 0.028f
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = Color.WHITE
        textPaint.alpha = (breathAlpha * 200).toInt()
        canvas.drawText("Tap anywhere to enter", viewW / 2f, viewH * 0.88f, textPaint)
    }

    // ==================== Touch interaction ====================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && !isFadingOut) {
            dismiss()
            return true
        }
        return super.onTouchEvent(event)
    }

    /** Fade out dismiss */
    fun dismiss() {
        if (isFadingOut) return
        isFadingOut = true
        fadeAnimator = ValueAnimator.ofInt(0, 255).apply {
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { globalAlpha = it.animatedValue as Int }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    this@ThemeEntranceView.isRunning = false
                    this@ThemeEntranceView.visibility = GONE
                    onDismissed?.invoke()
                }
            })
            start()
        }
    }

    /** Force stop (called when Activity is destroyed) */
    fun forceStop() {
        isRunning = false
        fadeAnimator?.cancel()
        bgBitmap?.recycle()
        bgBitmap = null
    }

    override fun onDetachedFromWindow() {
        forceStop()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val TAG = "ThemeEntrance"
    }
}
