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
    private var _settingsManager: SettingsManager? = null
    private var _affectionManager: AffectionManager? = null
    private var _achievementManager: AchievementManager? = null
    private var _apiClient: ApiClient? = null
    private var _contextManager: ContextManager? = null
    private var _personaRagManager: PersonaRagManager? = null
    private var _favoriteManager: FavoriteManager? = null
    private var _nicknameManager: NicknameManager? = null
    private var _voiceManager: VoiceManager? = null
    private var _momentsManager: MemorableMomentsManager? = null
    private var _actionManager: AIActionManager? = null
    private var _stickerManager: StickerManager? = null
    private var _searchMemoryPlugin: SearchMemoryPlugin? = null
    private var _nicknamePlugin: NicknamePlugin? = null
    private var _generateImagePlugin: GenerateImagePlugin? = null

    val settingsManager: SettingsManager get() = _settingsManager!!
    val affectionManager: AffectionManager get() = _affectionManager!!
    val achievementManager: AchievementManager get() = _achievementManager!!
    val apiClient: ApiClient? get() = _apiClient
    val contextManager: ContextManager get() = _contextManager!!
    val personaRagManager: PersonaRagManager get() = _personaRagManager!!
    val favoriteManager: FavoriteManager get() = _favoriteManager!!
    val nicknameManager: NicknameManager get() = _nicknameManager!!
    val voiceManager: VoiceManager get() = _voiceManager!!
    val momentsManager: MemorableMomentsManager get() = _momentsManager!!
    val actionManager: AIActionManager get() = _actionManager!!
    val stickerManager: StickerManager get() = _stickerManager!!

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        _settingsManager = SettingsManager(appContext)
        _affectionManager = AffectionManager(appContext, getActivePersonaId(appContext))
        _achievementManager = AchievementManager(appContext, getActivePersonaId(appContext))
        _momentsManager = MemorableMomentsManager(appContext, getActivePersonaId(appContext))
        _actionManager = AIActionManager(appContext)
        _stickerManager = StickerManager(appContext)
        _contextManager = ContextManager(appContext)
        _personaRagManager = PersonaRagManager(appContext, getActivePersonaId(appContext))
        _favoriteManager = FavoriteManager(appContext, getActivePersonaId(appContext))
        _nicknameManager = NicknameManager(appContext)
        _voiceManager = VoiceManager(appContext)
        rebuildApiClient()
        registerBuiltinPlugins(appContext)
    }

    fun rebuildApiClient() {
        val sm = _settingsManager ?: return
        if (sm.chatApiUrl.isNotBlank()) {
            _apiClient = ApiClient(sm.chatApiUrl, sm.chatApiKey, sm.chatModel)
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

    fun isInitialized(): Boolean = _settingsManager != null

    private fun getActivePersonaId(context: Context): String {
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("active_persona_id", "default") ?: "default"
    }
}
