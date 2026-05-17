package com.aicompanion.virtualworld

import android.content.Context
import android.content.SharedPreferences
import com.aicompanion.memory.MemoryManager
import com.aicompanion.network.ApiClient
import com.aicompanion.persona.PersonaManager
import com.aicompanion.prompt.PromptBuilder
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class VirtualWorldManager(private val context: Context, worldId: String = "") {

    companion object {
        private const val TAG = "VirtualWorldManager"
        private const val KEY_ENABLED = "vw_enabled"
        private const val KEY_RUNNING = "vw_running"
        private const val KEY_CONFIG = "vw_config"
        private const val KEY_STATE = "vw_state"
        private const val KEY_STORY = "vw_story"
        private const val KEY_LAST_TICK = "vw_last_tick"
        private const val KEY_IMAGE_API_URL = "vw_image_api_url"
        private const val KEY_IMAGE_API_KEY = "vw_image_api_key"
        private const val KEY_IMAGE_MODEL = "vw_image_model"

        fun prefsNameForWorld(worldId: String): String {
            return if (worldId.isBlank()) "virtual_world" else "virtual_world_$worldId"
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(prefsNameForWorld(worldId), Context.MODE_PRIVATE)

    private val globalPrefs: SharedPreferences =
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    val currentWorldId: String = worldId

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var isRunning: Boolean
        get() = prefs.getBoolean(KEY_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_RUNNING, value).apply()

    var config: WorldConfig
        get() = try {
            WorldConfig.fromJson(JSONObject(prefs.getString(KEY_CONFIG, "") ?: ""))
        } catch (_: Exception) {
            WorldConfig()
        }
        set(value) = prefs.edit().putString(KEY_CONFIG, value.toJson().toString()).apply()

    var state: WorldState
        get() = try {
            WorldState.fromJson(JSONObject(prefs.getString(KEY_STATE, "") ?: ""))
        } catch (_: Exception) {
            WorldState()
        }
        set(value) = prefs.edit().putString(KEY_STATE, value.toJson().toString()).apply()

    var lastTickTime: Long
        get() = prefs.getLong(KEY_LAST_TICK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_TICK, value).apply()

    var imageApiUrl: String
        get() = globalPrefs.getString("image_api_url", "") ?: ""
        set(value) = globalPrefs.edit().putString("image_api_url", value).apply()

    var imageApiKey: String
        get() = globalPrefs.getString("image_api_key", "") ?: ""
        set(value) = globalPrefs.edit().putString("image_api_key", value).apply()

    var imageModel: String
        get() = globalPrefs.getString("image_model", "dall-e-3") ?: "dall-e-3"
        set(value) = globalPrefs.edit().putString("image_model", value).apply()

    fun getStoryEvents(): List<StoryEvent> {
        val json = prefs.getString(KEY_STORY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { StoryEvent.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addStoryEvent(event: StoryEvent) {
        val events = getStoryEvents().toMutableList()
        events.add(event)
        if (events.size > 500) {
            val kept = events.takeLast(500)
            prefs.edit().putString(KEY_STORY, JSONArray().apply {
                kept.forEach { put(it.toJson()) }
            }.toString()).apply()
        } else {
            prefs.edit().putString(KEY_STORY, JSONArray().apply {
                events.forEach { put(it.toJson()) }
            }.toString()).apply()
        }
    }

    fun clearStory() {
        prefs.edit().putString(KEY_STORY, "[]").apply()
    }

    fun shouldTick(): Boolean {
        if (!isEnabled || !isRunning) return false
        val intervalMs = config.tickIntervalMinutes * 60_000L
        return System.currentTimeMillis() - lastTickTime >= intervalMs
    }

    fun advanceVirtualTime(): WorldState {
        val currentState = state
        val ratio = config.timeRatio.coerceAtLeast(1)
        val tickMinutes = config.tickIntervalMinutes
        val virtualMinutesAdvanced = tickMinutes * ratio

        var newHour = currentState.hourOfDay + (virtualMinutesAdvanced / 60)
        var newDay = currentState.dayCount

        while (newHour >= 24) {
            newHour -= 24
            newDay++
        }

        val newState = currentState.copy(
            dayCount = newDay,
            hourOfDay = newHour,
            virtualTimeMs = currentState.virtualTimeMs + virtualMinutesAdvanced * 60_000L
        )
        state = newState
        lastTickTime = System.currentTimeMillis()
        return newState
    }

    suspend fun runSimulationTick(): StoryEvent? {
        val sm = SettingsManager(context)
        if (sm.chatApiUrl.isBlank()) return null

        val config = this.config
        val currentState = advanceVirtualTime()
        val personaManager = PersonaManager(context)
        personaManager.load()

        return if (config.isGroupSimulation && config.memberPersonaIds.size > 1) {
            runGroupSimulation(sm, config, currentState, personaManager)
        } else {
            runSoloSimulation(sm, config, currentState, personaManager)
        }
    }

    private suspend fun runSoloSimulation(
        sm: SettingsManager,
        config: WorldConfig,
        currentState: WorldState,
        personaManager: PersonaManager
    ): StoryEvent? = withContext(Dispatchers.IO) {
        val personaId = config.memberPersonaIds.firstOrNull() ?: "default"
        val persona = personaManager.getPersona(personaId)
        val identity = PromptBuilder.buildIdentity(context, personaId)

        val memoryManager = MemoryManager(context, personaId)
        val memories = memoryManager.getLocalMemories().takeLast(5).map { it.fact }

        val recentEvents = getStoryEvents().takeLast(5).map {
            "[第${it.virtualDay}天${it.virtualHour}时] ${it.content}"
        }.joinToString("\n")

        val timeDesc = "第${currentState.dayCount}天 ${currentState.hourOfDay}:00"
        val systemPrompt = buildString {
            append("你是「${identity.name}」，生活在虚拟世界中。")
            if (identity.personality.isNotBlank()) append(" 性格${identity.personality}。")
            append("\n【世界观】${config.getFullLore()}")
            append("\n【当前状态】时间：$timeDesc。地点：${currentState.currentLocation}。天气：${currentState.currentWeather}。氛围：${currentState.currentMood}。")
            if (memories.isNotEmpty()) {
                append("\n【记忆】${memories.joinToString("；")}")
            }
            if (recentEvents.isNotBlank()) {
                append("\n【近期事件】\n$recentEvents")
            }
            append("\n【规则】根据当前时间和状态，描述你在这个时刻做了什么、遇到了什么、想了什么。1-3句话，像小说旁白。末尾用[[location:地点]][[weather:天气]][[mood:氛围]]标记状态变化。")
            if (config.imageGenEnabled && hasImageModelConfigured()) {
                append("\n如果这个场景值得配图，调用generate_image工具生成图片。只在场景特别重要或视觉感强时才生成。")
            }
        }

        val client = ApiClient(sm.chatApiUrl, sm.chatApiKey, sm.chatModel)
        return@withContext try {
            val imageToolDef = if (config.imageGenEnabled && hasImageModelConfigured()) {
                com.aicompanion.plugin.PluginRegistry.getPlugin("generate_image")?.getDefinition()
                    ?: com.aicompanion.models.ToolDefinition(
                        name = "generate_image",
                        description = "根据文字描述生成一张图片",
                        parameters = mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "prompt" to mapOf("type" to "string", "description" to "图片描述，英文效果更好"),
                                "style" to mapOf("type" to "string", "description" to "风格提示，可选")
                            ),
                            "required" to listOf("prompt")
                        )
                    )
            } else null

            val tools = if (imageToolDef != null) listOf(imageToolDef) else emptyList()

            val response = if (tools.isNotEmpty()) {
                com.aicompanion.AppContainer.setAssociatedEventId(null)
                com.aicompanion.AppContainer.setImagePluginWorldId(currentWorldId)
                client.sendChatWithToolLoop(
                    "virtual_world", "继续推演", identity.name, systemPrompt,
                    "neutral", "idle", emptyList(), emptyList(), "", tools
                ) { name, args ->
                    if (name == "generate_image") {
                        val eventId = java.util.UUID.randomUUID().toString()
                        com.aicompanion.AppContainer.setAssociatedEventId(eventId)
                        com.aicompanion.plugin.PluginRegistry.executePlugin(name, args)
                    } else {
                        com.aicompanion.plugin.PluginRegistry.executePlugin(name, args)
                    }
                }
            } else {
                client.sendSimplePrompt(systemPrompt, "继续推演")
            }

            if (response != null && response.text.isNotBlank()) {
                var content = response.text
                    .replace(Regex("\\[\\[emotion:\\w+\\]\\]"), "").trim()

                val newLocation = Regex("\\[\\[location:([^\\]]+)\\]\\]").find(content)?.groupValues?.get(1)
                    ?: currentState.currentLocation
                val newWeather = Regex("\\[\\[weather:([^\\]]+)\\]\\]").find(content)?.groupValues?.get(1)
                    ?: currentState.currentWeather
                val newMood = Regex("\\[\\[mood:([^\\]]+)\\]\\]").find(content)?.groupValues?.get(1)
                    ?: currentState.currentMood

                content = content
                    .replace(Regex("\\[\\[location:[^\\]]+\\]\\]"), "")
                    .replace(Regex("\\[\\[weather:[^\\]]+\\]\\]"), "")
                    .replace(Regex("\\[\\[mood:[^\\]]+\\]\\]"), "")
                    .trim()

                state = state.copy(
                    currentLocation = newLocation,
                    currentWeather = newWeather,
                    currentMood = newMood
                )

                val event = StoryEvent(
                    virtualDay = currentState.dayCount,
                    virtualHour = currentState.hourOfDay,
                    content = content,
                    speakerName = identity.name,
                    eventType = "action"
                )
                addStoryEvent(event)

                event
            } else null
        } catch (e: Exception) {
            AppLogger.e(TAG, "Simulation tick failed: ${e.message}")
            null
        }
    }

    private suspend fun runGroupSimulation(
        sm: SettingsManager,
        config: WorldConfig,
        currentState: WorldState,
        personaManager: PersonaManager
    ): StoryEvent? = withContext(Dispatchers.IO) {
        val recentEvents = getStoryEvents().takeLast(5).map {
            "[第${it.virtualDay}天${it.virtualHour}时] ${it.speakerName}：${it.content}"
        }.joinToString("\n")

        val timeDesc = "第${currentState.dayCount}天 ${currentState.hourOfDay}:00"
        val personaDescs = config.memberPersonaIds.mapNotNull { pid ->
            val p = personaManager.getPersona(pid) ?: return@mapNotNull null
            val identity = PromptBuilder.buildIdentity(context, pid)
            "「${identity.name}」性格${identity.personality}。${identity.speechStyle}。"
        }.joinToString(" ")

        val systemPrompt = buildString {
            append("你是虚拟世界的旁白。以下角色在这个世界中活动：$personaDescs")
            append("\n【世界观】${config.getFullLore()}")
            append("\n【当前状态】时间：$timeDesc。地点：${currentState.currentLocation}。天气：${currentState.currentWeather}。氛围：${currentState.currentMood}。")
            if (recentEvents.isNotBlank()) {
                append("\n【近期事件】\n$recentEvents")
            }
            append("\n【规则】推演这个时刻发生的事。描述2-3个角色的行动和互动，像小说片段。末尾用[[location:地点]][[weather:天气]][[mood:氛围]]标记状态变化。")
            if (config.imageGenEnabled && hasImageModelConfigured()) {
                append("\n如果这个场景值得配图，调用generate_image工具生成图片。只在场景特别重要或视觉感强时才生成。")
            }
        }

        val client = ApiClient(sm.chatApiUrl, sm.chatApiKey, sm.chatModel)
        return@withContext try {
            val imageToolDef = if (config.imageGenEnabled && hasImageModelConfigured()) {
                com.aicompanion.plugin.PluginRegistry.getPlugin("generate_image")?.getDefinition()
                    ?: com.aicompanion.models.ToolDefinition(
                        name = "generate_image",
                        description = "根据文字描述生成一张图片",
                        parameters = mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "prompt" to mapOf("type" to "string", "description" to "图片描述，英文效果更好"),
                                "style" to mapOf("type" to "string", "description" to "风格提示，可选")
                            ),
                            "required" to listOf("prompt")
                        )
                    )
            } else null

            val tools = if (imageToolDef != null) listOf(imageToolDef) else emptyList()

            val response = if (tools.isNotEmpty()) {
                com.aicompanion.AppContainer.setAssociatedEventId(null)
                com.aicompanion.AppContainer.setImagePluginWorldId(currentWorldId)
                client.sendChatWithToolLoop(
                    "virtual_world", "继续推演", "旁白", systemPrompt,
                    "neutral", "idle", emptyList(), emptyList(), "", tools
                ) { name, args ->
                    if (name == "generate_image") {
                        val eventId = java.util.UUID.randomUUID().toString()
                        com.aicompanion.AppContainer.setAssociatedEventId(eventId)
                        com.aicompanion.plugin.PluginRegistry.executePlugin(name, args)
                    } else {
                        com.aicompanion.plugin.PluginRegistry.executePlugin(name, args)
                    }
                }
            } else {
                client.sendSimplePrompt(systemPrompt, "继续推演")
            }

            if (response != null && response.text.isNotBlank()) {
                var content = response.text.trim()

                val newLocation = Regex("\\[\\[location:([^\\]]+)\\]\\]").find(content)?.groupValues?.get(1)
                    ?: currentState.currentLocation
                val newWeather = Regex("\\[\\[weather:([^\\]]+)\\]\\]").find(content)?.groupValues?.get(1)
                    ?: currentState.currentWeather
                val newMood = Regex("\\[\\[mood:([^\\]]+)\\]\\]").find(content)?.groupValues?.get(1)
                    ?: currentState.currentMood

                content = content
                    .replace(Regex("\\[\\[location:[^\\]]+\\]\\]"), "")
                    .replace(Regex("\\[\\[weather:[^\\]]+\\]\\]"), "")
                    .replace(Regex("\\[\\[mood:[^\\]]+\\]\\]"), "")
                    .trim()

                state = state.copy(
                    currentLocation = newLocation,
                    currentWeather = newWeather,
                    currentMood = newMood
                )

                val event = StoryEvent(
                    virtualDay = currentState.dayCount,
                    virtualHour = currentState.hourOfDay,
                    content = content,
                    speakerName = "群像",
                    eventType = "group_narrative"
                )
                addStoryEvent(event)

                event
            } else null
        } catch (e: Exception) {
            AppLogger.e(TAG, "Group simulation tick failed: ${e.message}")
            null
        }
    }

    suspend fun generateImageForEvent(content: String, eventId: String): String? =
        withContext(Dispatchers.IO) {
            if (imageApiUrl.isBlank() || imageApiKey.isBlank()) return@withContext null

            try {
                val prompt = content.take(200)
                val requestBody = JSONObject().apply {
                    put("model", imageModel)
                    put("prompt", prompt)
                    put("n", 1)
                    put("size", "512x512")
                }

                val request = okhttp3.Request.Builder()
                    .url(imageApiUrl)
                    .addHeader("Authorization", "Bearer $imageApiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build().newCall(request).execute()

                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val imageUrl = json.optJSONArray("data")?.optJSONObject(0)?.optString("url", "") ?: ""

                if (imageUrl.isNotBlank()) {
                    val localPath = downloadImage(imageUrl, eventId)
                    if (localPath != null) {
                        val events = getStoryEvents().toMutableList()
                        val idx = events.indexOfFirst { it.id == eventId }
                        if (idx >= 0) {
                            events[idx] = events[idx].copy(imageUrl = localPath)
                            prefs.edit().putString(KEY_STORY, JSONArray().apply {
                                events.forEach { put(it.toJson()) }
                            }.toString()).apply()
                        }
                        return@withContext localPath
                    }
                }
                null
            } catch (e: Exception) {
                AppLogger.e(TAG, "Image generation failed: ${e.message}")
                null
            }
        }

    private fun downloadImage(url: String, eventId: String): String? {
        return try {
            val dir = File(context.filesDir, "virtual_world/images")
            dir.mkdirs()
            val file = File(dir, "img_${eventId}.jpg")
            URL(url).openStream().use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun hasImageModelConfigured(): Boolean {
        return imageApiUrl.isNotBlank() && imageApiKey.isNotBlank()
    }

    fun hasChatModelConfigured(): Boolean {
        val sm = SettingsManager(context)
        return sm.chatApiUrl.isNotBlank() && sm.chatApiKey.isNotBlank()
    }

    fun resetWorld() {
        state = WorldState()
        clearStory()
        lastTickTime = 0L
    }

    fun saveUploadedImage(sourceUri: android.net.Uri): String? {
        return try {
            val dir = File(context.filesDir, "virtual_world/uploads")
            dir.mkdirs()
            val fileName = "upload_${System.currentTimeMillis()}.jpg"
            val destFile = File(dir, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            val config = this.config
            val updatedImages = config.uploadedImages + destFile.absolutePath
            this.config = config.copy(uploadedImages = updatedImages)
            destFile.absolutePath
        } catch (e: Exception) {
            AppLogger.e(TAG, "Save uploaded image failed: ${e.message}")
            null
        }
    }

    fun removeUploadedImage(path: String) {
        val config = this.config
        val updatedImages = config.uploadedImages.filter { it != path }
        this.config = config.copy(uploadedImages = updatedImages)
        try {
            File(path).delete()
        } catch (_: Exception) {}
    }

    fun getLatestStorySummary(maxEvents: Int = 3): String {
        val events = getStoryEvents().takeLast(maxEvents)
        if (events.isEmpty()) return ""
        val state = this.state
        return buildString {
            append("[虚拟世界状态] 第${state.dayCount}天 ${state.hourOfDay}:00，地点：${state.currentLocation}，天气：${state.currentWeather}，氛围：${state.currentMood}。")
            append("\n[近期剧情]")
            events.forEach { ev ->
                append("\n[第${ev.virtualDay}天${ev.virtualHour}时] ${ev.speakerName}：${ev.content}")
            }
        }
    }
}
