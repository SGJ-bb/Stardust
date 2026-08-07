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
        // 初始化应用分类器（用户自定义 App 分类的持久化存储）
        com.aicompanion.screen.AppCategoryClassifier.init(appContext)
        // RagConfig已在CompanionApp.onCreate中初始化,这里只做模型降级检测
        checkOnnxModelDegradation(appContext)
        registerBuiltinPlugins(appContext)
        // 初始化 API 客户端（确保 HomeActivity 等页面可直接使用）
        rebuildApiClient()
    }

    /**
     * ONNX模型自动降级: 如果选择了local模式但模型未就绪,降级到tfidf
     */
    private fun checkOnnxModelDegradation(context: Context) {
        if (com.aicompanion.rag.RagConfig.embeddingMode == "local") {
            try {
                val onnxEmbedder = com.aicompanion.rag.OnnxEmbedder(context)
                if (!onnxEmbedder.isModelReady()) {
                    com.aicompanion.util.AppLogger.w("AppContainer", "ONNX模型未就绪,自动降级到tfidf模式")
                    com.aicompanion.rag.RagConfig.embeddingMode = "tfidf"
                } else {
                    com.aicompanion.util.AppLogger.i("AppContainer", "ONNX模型已就绪,使用local模式")
                }
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e("AppContainer", "ONNX模型检测失败,降级到tfidf: ${e.message}")
                com.aicompanion.rag.RagConfig.embeddingMode = "tfidf"
            }
        }
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
        // 记忆工具插件：AI 自动添加日历事件/里程碑/难忘时刻/时光胶囊
        PluginRegistry.register(CalendarEventPlugin(context))
        PluginRegistry.register(MilestonePlugin(context))
        PluginRegistry.register(MemorableMomentPlugin(context))
        PluginRegistry.register(TimeCapsulePlugin(context))
        PluginRegistry.register(AlbumPlugin(context))
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
