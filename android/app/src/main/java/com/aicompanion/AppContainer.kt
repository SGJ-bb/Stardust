package com.aicompanion

import android.content.Context
import com.aicompanion.action.AIActionManager
import com.aicompanion.affection.AffectionManager
import com.aicompanion.gamify.AchievementManager
import com.aicompanion.memory.ContextManager
import com.aicompanion.memory.MemorableMomentsManager
import com.aicompanion.network.ApiClient
import com.aicompanion.plugin.*
import com.aicompanion.rag.PersonaRagManager
import com.aicompanion.settings.SettingsManager
import com.aicompanion.sticker.StickerManager
import com.aicompanion.ui.FavoriteManager
import com.aicompanion.ui.NicknameManager
import com.aicompanion.voice.VoiceManager

object AppContainer {
    var appContext: Context? = null
        private set
    var settingsManager: SettingsManager? = null
        private set
    private var _apiClient: ApiClient? = null
    private var _affectionManager: AffectionManager? = null
    private var _achievementManager: AchievementManager? = null
    private var _contextManager: ContextManager? = null
    private var _personaRagManager: PersonaRagManager? = null
    private var _favoriteManager: FavoriteManager? = null
    private var _nicknameManager: NicknameManager? = null
    private var _voiceManager: VoiceManager? = null
    private var _momentsManager: MemorableMomentsManager? = null
    private var _actionManager: AIActionManager? = null
    private var _stickerManager: StickerManager? = null
    private var _searchMemoryPlugin: SearchMemoryPlugin? = null
    private var _searchDiaryPlugin: SearchDiaryPlugin? = null
    private var _nicknamePlugin: NicknamePlugin? = null
    private var _generateImagePlugin: GenerateImagePlugin? = null

    private var _cachedPersonaId: String? = null

    val apiClient: ApiClient? get() = _apiClient
    val affectionManager: AffectionManager
        get() = getOrCreatePersonaAware(::_affectionManager, { _affectionManager = it }) { ctx, pid ->
            AffectionManager(ctx, pid)
        }
    val achievementManager: AchievementManager
        get() = getOrCreatePersonaAware(::_achievementManager, { _achievementManager = it }) { ctx, pid ->
            AchievementManager(ctx, pid)
        }
    val stickerManager: StickerManager
        get() = _stickerManager ?: StickerManager(requireContext()).also { _stickerManager = it }
    val contextManager: ContextManager
        get() = _contextManager ?: ContextManager(requireContext()).also { _contextManager = it }
    val favoriteManager: FavoriteManager
        get() = getOrCreatePersonaAware(::_favoriteManager, { _favoriteManager = it }) { ctx, pid ->
            FavoriteManager(ctx, pid)
        }
    val nicknameManager: NicknameManager
        get() = getOrCreatePersonaAware(::_nicknameManager, { _nicknameManager = it }) { ctx, pid ->
            NicknameManager(ctx, pid)
        }
    val voiceManager: VoiceManager
        get() = _voiceManager ?: VoiceManager(requireContext()).also { _voiceManager = it }
    val momentsManager: MemorableMomentsManager
        get() = getOrCreatePersonaAware(::_momentsManager, { _momentsManager = it }) { ctx, pid ->
            MemorableMomentsManager(ctx, pid)
        }
    val actionManager: AIActionManager
        get() = _actionManager ?: AIActionManager(requireContext()).also { _actionManager = it }
    val personaRagManager: PersonaRagManager
        get() = getOrCreatePersonaAware(::_personaRagManager, { _personaRagManager = it }) { ctx, pid ->
            PersonaRagManager(ctx, pid)
        }

    private fun requireContext(): Context {
        return appContext ?: throw IllegalStateException("AppContainer not initialized. Call initialize() first.")
    }

    private fun readActivePersonaId(): String {
        return appContext?.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            ?.getString("active_persona_id", "default") ?: "default"
    }

    private inline fun <T : Any> getOrCreatePersonaAware(
        currentGetter: () -> T?,
        setter: (T) -> Unit,
        factory: (Context, String) -> T
    ): T {
        val ctx = requireContext()
        val currentPid = readActivePersonaId()
        val current = currentGetter()
        if (current != null && _cachedPersonaId == currentPid) return current
        val newInstance = factory(ctx, currentPid)
        setter(newInstance)
        _cachedPersonaId = currentPid
        return newInstance
    }

    fun onPersonaChanged() {
        _affectionManager = null
        _achievementManager = null
        _favoriteManager = null
        _nicknameManager = null
        _momentsManager = null
        _personaRagManager = null
        _cachedPersonaId = null
    }

    fun initialize(appContext: Context) {
        this.appContext = appContext
        settingsManager = SettingsManager(appContext)
        com.aicompanion.network.ProviderAdapter.init(appContext)
        loadRagConfig(appContext)
        registerBuiltinPlugins(appContext)
    }

    private fun loadRagConfig(context: Context) {
        val prefs = context.getSharedPreferences("rag_config", android.content.Context.MODE_PRIVATE)
        com.aicompanion.rag.RagConfig.personaRagEnabled = prefs.getBoolean("persona_rag_enabled", true)
        com.aicompanion.rag.RagConfig.useCloudEmbedding = prefs.getBoolean("use_cloud_embedding", false)
        com.aicompanion.rag.RagConfig.cloudEmbeddingUrl = prefs.getString("cloud_embedding_url", "") ?: ""
        com.aicompanion.rag.RagConfig.cloudEmbeddingApiKey = prefs.getString("cloud_embedding_api_key", "") ?: ""
        com.aicompanion.rag.RagConfig.cloudEmbeddingModel = prefs.getString("cloud_embedding_model", "text-embedding-3-small") ?: "text-embedding-3-small"
        com.aicompanion.rag.RagConfig.minSimilarity = prefs.getFloat("min_similarity", 0.12f)
    }

    fun saveRagConfig() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences("rag_config", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("persona_rag_enabled", com.aicompanion.rag.RagConfig.personaRagEnabled)
            putBoolean("use_cloud_embedding", com.aicompanion.rag.RagConfig.useCloudEmbedding)
            putString("cloud_embedding_url", com.aicompanion.rag.RagConfig.cloudEmbeddingUrl)
            putString("cloud_embedding_api_key", com.aicompanion.rag.RagConfig.cloudEmbeddingApiKey)
            putString("cloud_embedding_model", com.aicompanion.rag.RagConfig.cloudEmbeddingModel)
            putFloat("min_similarity", com.aicompanion.rag.RagConfig.minSimilarity)
        }.apply()
    }

    fun rebuildApiClient() {
        val sm = settingsManager ?: return
        if (sm.chatApiUrl.isNotBlank()) {
            _apiClient = ApiClient(sm.chatApiUrl, sm.chatApiKey, sm.chatModel,
                sm.llmTemperature, sm.llmTopP, sm.llmFrequencyPenalty, sm.llmPresencePenalty, sm.llmMaxTokens,
                sm.apiProvider)
        }
    }

    private fun registerBuiltinPlugins(context: Context) {
        PluginRegistry.clear()
        PluginRegistry.register(AlarmPlugin(context))
        PluginRegistry.register(AlarmAtTimePlugin(context))
        PluginRegistry.register(SchedulePlugin(context))
        PluginRegistry.register(WebSearchPlugin(context))
        _searchMemoryPlugin = SearchMemoryPlugin()
        PluginRegistry.register(_searchMemoryPlugin!!)
        _searchDiaryPlugin = SearchDiaryPlugin()
        PluginRegistry.register(_searchDiaryPlugin!!)
        PluginRegistry.register(CurrentTimePlugin())
        _nicknamePlugin = NicknamePlugin()
        PluginRegistry.register(_nicknamePlugin!!)
        val sendStickerPlugin = SendStickerPlugin(context)
        PluginRegistry.register(sendStickerPlugin)
        _generateImagePlugin = GenerateImagePlugin(context)
        PluginRegistry.register(_generateImagePlugin!!)
    }

    fun setSearchMemoryCallback(callback: suspend (String, Int) -> String) {
        _searchMemoryPlugin?.onSearchMemory = callback
    }

    fun setSearchDiaryCallback(callback: suspend (String, Int) -> String) {
        _searchDiaryPlugin?.onSearchDiary = callback
    }

    fun setNicknameCallback(callback: (List<String>) -> Unit) {
        _nicknamePlugin?.onNicknamesGenerated = callback
    }

    fun setStickerCallback(callback: (String) -> Unit) {
        val plugin = PluginRegistry.getPlugin("send_sticker") as? SendStickerPlugin
        plugin?.onStickerSent = callback
    }

    fun setImageGeneratedCallback(callback: (String) -> Unit) {
        _generateImagePlugin?.onImageGenerated = callback
    }

    fun setAssociatedEventId(eventId: String?) {
        _generateImagePlugin?.associatedEventId = eventId
    }

    fun setImagePluginWorldId(worldId: String) {
        _generateImagePlugin?.worldId = worldId
    }

    fun isInitialized(): Boolean = settingsManager != null
}
