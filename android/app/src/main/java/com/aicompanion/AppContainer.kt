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

    val apiClient: ApiClient? get() = _apiClient
    val affectionManager: AffectionManager
        get() = _affectionManager ?: AffectionManager(appContext!!, readActivePersonaId()).also { _affectionManager = it }
    val achievementManager: AchievementManager
        get() = _achievementManager ?: AchievementManager(appContext!!, readActivePersonaId()).also { _achievementManager = it }
    val stickerManager: StickerManager
        get() = _stickerManager ?: StickerManager(appContext!!).also { _stickerManager = it }
    val contextManager: ContextManager
        get() = _contextManager ?: ContextManager(appContext!!).also { _contextManager = it }
    val favoriteManager: FavoriteManager
        get() = _favoriteManager ?: FavoriteManager(appContext!!, readActivePersonaId()).also { _favoriteManager = it }
    val nicknameManager: NicknameManager
        get() = _nicknameManager ?: NicknameManager(appContext!!).also { _nicknameManager = it }
    val voiceManager: VoiceManager
        get() = _voiceManager ?: VoiceManager(appContext!!).also { _voiceManager = it }
    val momentsManager: MemorableMomentsManager
        get() = _momentsManager ?: MemorableMomentsManager(appContext!!, readActivePersonaId()).also { _momentsManager = it }
    val actionManager: AIActionManager
        get() = _actionManager ?: AIActionManager(appContext!!).also { _actionManager = it }
    val personaRagManager: PersonaRagManager
        get() = _personaRagManager ?: PersonaRagManager(appContext!!, readActivePersonaId()).also { _personaRagManager = it }

    fun initialize(appContext: Context) {
        this.appContext = appContext
        settingsManager = SettingsManager(appContext)
        registerBuiltinPlugins(appContext)
    }

    private fun readActivePersonaId(): String {
        return appContext?.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            ?.getString("active_persona_id", "default") ?: "default"
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

    fun setSearchMemoryCallback(callback: (String, Int) -> String) {
        _searchMemoryPlugin?.onSearchMemory = callback
    }

    fun setSearchDiaryCallback(callback: (String, Int) -> String) {
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
