package com.aicompanion.pixelpet

/**
 * 像素宠物数据模型
 * 与PC端Rust模型保持一致的字段结构
 */
data class PixelPet(
    val id: String,
    val name: String,
    val description: String? = null,
    val referenceImagePath: String? = null,  // 本地文件路径
    val basePrompt: String,
    val negativePrompt: String? = null,
    // 渲染配置 (扁平字段，与Rust一致)
    val spriteWidth: Int = 64,
    val spriteHeight: Int = 64,
    val fps: Int = 8,
    val scale: Float = 3.0f,
    val renderMode: String = "pixel_perfect",
    val isActive: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * 宠物动作定义
 */
data class PetAction(
    val id: String,
    val petId: String,
    val name: String,           // idle, walk, jump...
    val displayName: String,    // 待机, 行走...
    val description: String? = null,
    val prompt: String,
    val frameCount: Int = 4,
    val frameDuration: Long = 125L,  // ms
    val loopMode: LoopMode = LoopMode.LOOP,
    val isBuiltin: Boolean = false,
    val triggerEvents: List<String>? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    // 运行时加载的帧数据
    val frames: List<PixelFrame> = emptyList(),
)

/**
 * 循环模式枚举
 */
enum class LoopMode {
    LOOP,       // 循环播放
    ONCE,       // 播放一次
    PINGPONG;   // 来回往返

    companion object {
        fun fromString(s: String): LoopMode {
            return when (s.uppercase()) {
                "ONCE" -> ONCE
                "PINGPONG" -> PINGPONG
                else -> LOOP
            }
        }
    }
}

/**
 * 单帧数据
 */
data class PixelFrame(
    val id: String,
    val actionId: String,
    val frameIndex: Int,
    val imagePath: String,
    val imageHash: String? = null,
    val promptUsed: String = "",
    val status: FrameStatus = FrameStatus.GENERATING,
    val generatedAt: Long? = null,
)

/**
 * 帧状态枚举
 */
enum class FrameStatus {
    GENERATING,
    READY,
    FAILED;
}

/**
 * 图片生成配置
 */
data class ImageGenConfig(
    var provider: String = "custom",
    var apiUrl: String = "",
    var apiKey: String = "",
    var model: String = "",
    var stylePrompt: String = DEFAULT_PIXEL_STYLE_PROMPT,
    var size: String = "64x64",
    var steps: Int = 20,
    var cfgScale: Float = 7.0f,
    var batchSize: Int = 1,
) {
    companion object {
        const val DEFAULT_PIXEL_STYLE_PROMPT =
            "pixel art, 16-bit style, retro game sprite, clean black outline, solid color fill, no anti-aliasing, transparent background"
    }
}

/** 默认渲染配置 */
object DefaultRenderConfig {
    const val SPRITE_WIDTH = 64
    const val SPRITE_HEIGHT = 64
    const val FPS = 8
    const val SCALE = 3.0f
}

/**
 * 内置默认动作模板 (与PC端BUILTIN_ACTIONS保持一致)
 */
val BUILTIN_ACTION_TEMPLATES = listOf(
    ActionTemplate("idle", "待机", "standing still, breathing gently, subtle movement", 4, 150, LoopMode.LOOP),
    ActionTemplate("walk", "行走", "walking animation, legs moving in walking cycle", 6, 120, LoopMode.LOOP),
    ActionTemplate("run", "跑步", "running fast, dynamic pose, energetic", 6, 80, LoopMode.LOOP),
    ActionTemplate("jump", "跳跃", "jumping in the air, happy excited expression", 6, 100, LoopMode.ONCE),
    ActionTemplate("sit", "坐下", "sitting down on the ground, relaxed posture", 4, 150, LoopMode.LOOP),
    ActionTemplate("sleep", "睡觉", "sleeping peacefully, eyes closed, zzz bubbles", 4, 200, LoopMode.LOOP),
    ActionTemplate("happy", "开心", "very happy and excited, jumping with joy, sparkles", 4, 100, LoopMode.ONCE),
    ActionTemplate("sad", "难过", "sad and downcast, tears in eyes", 4, 150, LoopMode.ONCE),
    ActionTemplate("angry", "生气", "angry and frustrated, puffed cheeks, steam from ears", 4, 100, LoopMode.ONCE),
    ActionTemplate("surprised", "惊讶", "surprised and shocked, wide open eyes", 4, 100, LoopMode.ONCE),
    ActionTemplate("wave", "招手", "waving hand hello or goodbye, friendly gesture", 4, 120, LoopMode.ONCE),
    ActionTemplate("dance", "跳舞", "dancing fun moves, grooving to music", 8, 120, LoopMode.LOOP),
)

/** 动作模板（不含运行时字段） */
data class ActionTemplate(
    val name: String,
    val displayName: String,
    val prompt: String,
    val frameCount: Int,
    val frameDuration: Long,
    val loopMode: LoopMode,
)

/**
 * 渲染配置数据类（从 PixelPet 扁平字段提取）
 */
data class RenderConfig(
    val spriteWidth: Int = DefaultRenderConfig.SPRITE_WIDTH,
    val spriteHeight: Int = DefaultRenderConfig.SPRITE_HEIGHT,
    val fps: Int = DefaultRenderConfig.FPS,
    val scale: Float = DefaultRenderConfig.SCALE,
    val renderMode: String = "pixel_perfect",
)

/** 从 PixelPet 扁平字段构建渲染配置 */
fun PixelPet.getRenderConfig(): RenderConfig {
    return RenderConfig(
        spriteWidth = this.spriteWidth,
        spriteHeight = this.spriteHeight,
        fps = this.fps,
        scale = this.scale,
        renderMode = this.renderMode,
    )
}
