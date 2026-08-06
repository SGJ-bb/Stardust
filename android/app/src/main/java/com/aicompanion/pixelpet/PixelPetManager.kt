package com.aicompanion.pixelpet

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 像素宠物管理器
 *
 * 负责:
 * - 宠物/动作/帧的持久化存储 (SharedPreferences + 文件系统)
 * - 图片生成API调用
 * - 提示词构建
 * - 帧图文件缓存管理
 */
class PixelPetManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("pixel_pet_prefs", Context.MODE_PRIVATE)
    private val baseDir: File by lazy {
        File(context.filesDir, "pixel_pets").apply { mkdirs() }
    }

    companion object {
        const val TAG = "PixelPetManager"
        private const val KEY_PETS = "saved_pets"
        private const val KEY_ACTIVE_ID = "active_pet_id"
        private const val KEY_GEN_CONFIG = "gen_config"
        private const val KEY_PET_MODE = "pet_mode"  // "live2d" | "pixel"
    }

    // ════════════════════ 模式切换 ═════════════════════

    fun getPetMode(): String {
        return prefs.getString(KEY_PET_MODE, "live2d") ?: "live2d"
    }

    fun setPetMode(mode: String) {
        prefs.edit().putString(KEY_PET_MODE, mode).apply()
    }

    // ════════════════════ 宠物 CRUD ═════════════════════

    fun savePets(pets: List<PixelPet>) {
        val arr = JSONArray()
        for (pet in pets) {
            arr.put(serializePet(pet))
        }
        prefs.edit().putString(KEY_PETS, arr.toString()).apply()
    }

    fun loadPets(): List<PixelPet> {
        val raw = prefs.getString(KEY_PETS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> deserializePet(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            Log.e(TAG, "loadPets error", e)
            emptyList()
        }
    }

    fun getActivePet(): PixelPet? {
        val activeId = prefs.getString(KEY_ACTIVE_ID, null) ?: return null
        return loadPets().find { it.id == activeId }
    }

    fun setActivePet(pet: PixelPet) {
        val pets = loadPets().map {
            it.copy(isActive = it.id == pet.id)
        }.toMutableList()

        // 如果宠物不在列表中则添加
        if (pets.none { it.id == pet.id }) {
            pets.add(pet.copy(isActive = true))
        }

        savePets(pets)
        prefs.edit().putString(KEY_ACTIVE_ID, pet.id).apply()
    }

    fun createPet(
        name: String,
        description: String?,
        referenceImagePath: String?,
        basePrompt: String,
        negativePrompt: String?
    ): PixelPet {
        val pet = PixelPet(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            referenceImagePath = referenceImagePath,
            basePrompt = basePrompt,
            negativePrompt = negativePrompt,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val pets = loadPets().toMutableList()
        pets.add(pet)
        savePets(pets)
        return pet
    }

    fun deletePet(id: String) {
        val pets = loadPets().filter { it.id != id }
        savePets(pets)

        // 清理关联的帧图文件
        val actionDir = File(baseDir, id)
        actionDir.deleteRecursively()
    }

    // ════════════════════ 动作管理 ═════════════════════

    /** 获取宠物的所有动作（含帧数据） */
    fun getActionsForPet(petId: String): List<PetAction> {
        val actionDir = File(baseDir, petId)
        if (!actionDir.exists()) return emptyList()

        return actionDir.listFiles()?.mapNotNull { actionFile ->
            try {
                val json = JSONObject(actionFile.readText())
                deserializeAction(json, petId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load action ${actionFile.name}", e)
                null
            }
        }?.sortedBy { it.sortOrder } ?: emptyList()
    }

    /** getActionsForPet 的别名（OverlayWindow 使用） */
    fun getPetActions(petId: String): List<PetAction> = getActionsForPet(petId)

    /** 保存动作 */
    fun saveAction(action: PetAction) {
        val actionDir = File(baseDir, action.petId)
        actionDir.mkdirs()
        val file = File(actionDir, "action_${action.id}.json")
        file.writeText(serializeAction(action).toString())
    }

    /** 创建新动作 */
    fun createAction(
        petId: String,
        name: String,
        displayName: String,
        prompt: String,
        frameCount: Int,
        loopMode: LoopMode,
        isBuiltin: Boolean = false,
    ): PetAction {
        val action = PetAction(
            id = UUID.randomUUID().toString(),
            petId = petId,
            name = name,
            displayName = displayName,
            prompt = prompt,
            frameCount = frameCount,
            frameDuration = 125,
            loopMode = loopMode,
            isBuiltin = isBuiltin,
            triggerEvents = null,
            sortOrder = (System.currentTimeMillis() / 1000).toInt(),
            createdAt = System.currentTimeMillis(),
            frames = emptyList(),
        )
        saveAction(action)
        return action
    }

    /** 删除动作 */
    fun deleteAction(petId: String, actionId: String) {
        val actionDir = File(baseDir, petId)
        // 删除动作JSON
        File(actionDir, "action_$actionId.json").delete()
        // 删除该动作的所有帧图
        val frameDir = File(actionDir, actionId)
        frameDir.deleteRecursively()
    }

    /** 创建内置默认动作 */
    fun createBuiltinActions(petId: String, actionNames: List<String>): List<PetAction> {
        val selectedTemplates = BUILTIN_ACTION_TEMPLATES.filter { it.name in actionNames }
        val actions = mutableListOf<PetAction>()

        for (template in selectedTemplates) {
            val action = PetAction(
                id = UUID.randomUUID().toString(),
                petId = petId,
                name = template.name,
                displayName = template.displayName,
                prompt = template.prompt,
                frameCount = template.frameCount,
                frameDuration = template.frameDuration.toLong(),
                loopMode = template.loopMode,
                isBuiltin = true,
                createdAt = System.currentTimeMillis(),
                frames = (0 until template.frameCount).map { idx ->
                    PixelFrame(
                        id = UUID.randomUUID().toString(),
                        actionId = "",  // 稍后填充
                        frameIndex = idx,
                        imagePath = "",
                        status = FrameStatus.GENERATING,
                    )
                },
            )
            // 为每帧分配actionId
            val updatedFrames = action.frames.map { it.copy(actionId = action.id) }
            actions.add(action.copy(frames = updatedFrames))
            saveAction(actions.last())
        }

        return actions
    }

    // ════════════════════ 帧图管理 ═════════════════════

    /** 保存单帧图片到文件 */
    fun saveFrameImage(petId: String, actionId: String, frameIndex: Int, imageBytes: ByteArray): String {
        val frameDir = File(File(baseDir, petId), actionId)
        frameDir.mkdirs()
        val fileName = String.format("frame_%02d.png", frameIndex)
        val file = File(frameDir, fileName)
        file.writeBytes(imageBytes)
        return file.absolutePath
    }

    /** 加载帧位图 */
    fun loadFrameBitmap(imagePath: String): android.graphics.Bitmap? {
        return try {
            BitmapFactory.decodeFile(imagePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap: $imagePath", e)
            null
        }
    }

    /** 更新帧状态到JSON */
    fun updateFrameStatus(petId: String, actionId: String, frameIndex: Int, status: FrameStatus, imagePath: String?) {
        val action = getActionsForPet(petId).find { it.id == actionId } ?: return
        val updatedFrames = action.frames.map { frame ->
            if (frame.frameIndex == frameIndex) {
                frame.copy(status = status, imagePath = imagePath ?: frame.imagePath)
            } else frame
        }
        saveAction(action.copy(frames = updatedFrames))
    }

    // ════════════════════ 生成配置 ═════════════════════

    fun getGenConfig(): ImageGenConfig {
        val raw = prefs.getString(KEY_GEN_CONFIG, null)
        return if (raw != null) {
            try {
                deserializeGenConfig(JSONObject(raw))
            } catch (e: Exception) {
                ImageGenConfig()
            }
        } else {
            ImageGenConfig()
        }
    }

    fun saveGenConfig(config: ImageGenConfig) {
        prefs.edit().putString(KEY_GEN_CONFIG, serializeGenConfig(config).toString()).apply()
    }

    // ════════════════════ 提示词构建 ═════════════════════

    /**
     * 构建单帧完整提示词
     * 公式: [基础描述] + [像素风格修饰] + [动作描述] + [帧序号描述]
     */
    fun buildFramePrompt(
        basePrompt: String,
        actionPrompt: String,
        frameIndex: Int,
        totalFrames: Int,
        stylePrompt: String,
    ): String {
        val frameHints = listOf(
            "start of animation, initial pose",
            "transitioning into movement, early phase",
            "mid-action, peak of motion",
            "transitioning out of movement",
            "returning toward rest position",
            "near end of cycle, settling down",
        )
        val hint = frameHints.getOrNull(frameIndex)
            ?: "frame ${frameIndex + 1} of $totalFrames"

        return listOf(
            basePrompt.trim(),
            stylePrompt.trim(),
            actionPrompt.trim(),
            "$hint, frame ${frameIndex + 1} of $totalFrames",
        ).filter { it.isNotEmpty() }.joinToString(", ")
    }

    // ════════════════════ 序列化辅助 ═════════════════════

    private fun serializePet(pet: PixelPet): JSONObject {
        return JSONObject().apply {
            put("id", pet.id)
            put("name", pet.name)
            put("description", pet.description)
            put("reference_image_path", pet.referenceImagePath)
            put("base_prompt", pet.basePrompt)
            put("negative_prompt", pet.negativePrompt)
            put("sprite_width", pet.spriteWidth)
            put("sprite_height", pet.spriteHeight)
            put("fps", pet.fps)
            put("scale", pet.scale)
            put("render_mode", pet.renderMode)
            put("is_active", pet.isActive)
            put("created_at", pet.createdAt)
            put("updated_at", pet.updatedAt)
        }
    }

    private fun deserializePet(json: JSONObject): PixelPet {
        return PixelPet(
            id = json.getString("id"),
            name = json.optString("name"),
            description = json.optString("description").ifEmpty { null },
            referenceImagePath = json.optString("reference_image_path").ifEmpty { null },
            basePrompt = json.getString("base_prompt"),
            negativePrompt = json.optString("negative_prompt").ifEmpty { null },
            spriteWidth = json.optInt("sprite_width", DefaultRenderConfig.SPRITE_WIDTH),
            spriteHeight = json.optInt("sprite_height", DefaultRenderConfig.SPRITE_HEIGHT),
            fps = json.optInt("fps", DefaultRenderConfig.FPS),
            scale = json.optDouble("scale")?.toFloat() ?: DefaultRenderConfig.SCALE,
            renderMode = json.optString("render_mode", "pixel_perfect"),
            isActive = json.optBoolean("is_active", false),
            createdAt = json.optLong("created_at", 0L),
            updatedAt = json.optLong("updated_at", 0L),
        )
    }

    private fun serializeAction(action: PetAction): JSONObject {
        return JSONObject().apply {
            put("id", action.id)
            put("pet_id", action.petId)
            put("name", action.name)
            put("display_name", action.displayName)
            put("description", action.description)
            put("prompt", action.prompt)
            put("frame_count", action.frameCount)
            put("frame_duration", action.frameDuration)
            put("loop_mode", action.loopMode.name)
            put("is_builtin", action.isBuiltin)
            put("trigger_events", JSONArray(action.triggerEvents ?: emptyList<String>()))
            put("sort_order", action.sortOrder)
            put("created_at", action.createdAt)
            // 序列化帧数据
            val framesArr = JSONArray()
            for (frame in action.frames) {
                framesArr.put(JSONObject().apply {
                    put("id", frame.id)
                    put("action_id", frame.actionId)
                    put("frame_index", frame.frameIndex)
                    put("image_path", frame.imagePath)
                    put("image_hash", frame.imageHash)
                    put("prompt_used", frame.promptUsed)
                    put("status", frame.status.name)
                    put("generated_at", frame.generatedAt)
                })
            }
            put("frames", framesArr)
        }
    }

    private fun deserializeAction(json: JSONObject, petId: String): PetAction {
        val framesArray = json.optJSONArray("frames")
        val frames = if (framesArray != null) {
            (0 until framesArray.length()).map { i ->
                val f = framesArray.getJSONObject(i)
                PixelFrame(
                    id = f.getString("id"),
                    actionId = f.getString("action_id"),
                    frameIndex = f.getInt("frame_index"),
                    imagePath = f.optString("image_path"),
                    imageHash = f.optString("image_hash").ifEmpty { null },
                    promptUsed = f.optString("prompt_used"),
                    status = FrameStatus.valueOf(f.optString("status", "GENERATING")),
                    generatedAt = f.optLong("generated_at")?.takeIf { it > 0L },
                )
            }
        } else emptyList()

        val triggerEvents = json.optJSONArray("trigger_events")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        }

        return PetAction(
            id = json.getString("id"),
            petId = petId.ifEmpty { json.optString("pet_id") },
            name = json.getString("name"),
            displayName = json.getString("display_name"),
            description = json.optString("description").ifEmpty { null },
            prompt = json.getString("prompt"),
            frameCount = json.optInt("frame_count", 4),
            frameDuration = json.optLong("frame_duration", 125L),
            loopMode = LoopMode.fromString(json.optString("loop_mode", "LOOP")),
            isBuiltin = json.optBoolean("is_builtin", false),
            triggerEvents = triggerEvents,
            sortOrder = json.optInt("sort_order", 0),
            createdAt = json.optLong("created_at", 0L),
            frames = frames,
        )
    }

    private fun serializeGenConfig(config: ImageGenConfig): JSONObject {
        return JSONObject().apply {
            put("provider", config.provider)
            put("api_url", config.apiUrl)
            put("api_key", config.apiKey)
            put("model", config.model)
            put("style_prompt", config.stylePrompt)
            put("size", config.size)
            put("steps", config.steps)
            put("cfg_scale", config.cfgScale)
            put("batch_size", config.batchSize)
        }
    }

    private fun deserializeGenConfig(json: JSONObject): ImageGenConfig {
        return ImageGenConfig(
            provider = json.optString("provider", "custom"),
            apiUrl = json.optString("api_url"),
            apiKey = json.optString("api_key"),
            model = json.optString("model"),
            stylePrompt = json.optString("style_prompt", ImageGenConfig.DEFAULT_PIXEL_STYLE_PROMPT),
            size = json.optString("size", "64x64"),
            steps = json.optInt("steps", 20),
            cfgScale = json.optDouble("cfg_scale")?.toFloat() ?: 7.0f,
            batchSize = json.optInt("batch_size", 1),
        )
    }
}
