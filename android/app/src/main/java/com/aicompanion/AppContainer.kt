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
        // 把「工具调用轮次」设置同步给 ApiClient 的静态默认值（其它调用点无需再传参）
        try {
            val sm = settingsManager
            if (sm != null) {
                com.aicompanion.network.ApiClient.defaultToolLoopMaxIterations = sm.aiToolLoopMaxRounds
                // 把 UI 树采集参数应用到无障碍服务（服务未连接时会在 onServiceConnected 再应用一次）
                com.aicompanion.screen.ScreenRecognitionService.applyConfig(sm)
            }
        } catch (_: Exception) {}
        // 迁移旧版 App UI 知识到统一技能库（只执行一次）
        try {
            com.aicompanion.skill.SkillStore(appContext).migrateFromAppSkillsIfNeeded()
        } catch (_: Exception) {}
        // 导入随 APK 打包的内置技能（assets/skills/*.md，与用户数据无关）
        try {
            com.aicompanion.skill.SkillStore(appContext).importBundled()
        } catch (_: Exception) {}
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
                    // 只看"文件在不在"是不够的：本工程里 sherpa 和 onnxruntime 各带一份
                    // libonnxruntime.so，留错那份要到第一次推理才会 FATAL。
                    // 所以启动时真加载一次（后台线程，不挡启动），加载不了就降级。
                    Thread {
                        val loaded = runCatching { onnxEmbedder.initialize() }.getOrDefault(false)
                        if (loaded) {
                            com.aicompanion.util.AppLogger.i("AppContainer", "ONNX Runtime 加载成功，local 模式可用")
                            runCatching { onnxEmbedder.release() }   // 只做探测，不常驻 24MB
                        } else {
                            com.aicompanion.util.AppLogger.w("AppContainer", "ONNX Runtime 不可用，自动降级到 tfidf")
                            runCatching { com.aicompanion.rag.RagConfig.embeddingMode = "tfidf" }
                        }
                    }.start()
                }
            } catch (e: Throwable) {
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
        // 闹钟/日程不再走 App 内部实现（AIActionManager + AlarmManager）：
        // 那几个工具写的是 App 私有数据，用户要去 App 里的「日程」页才看得到，
        // 而同样的话在系统闹钟/系统日历里看不到 —— 同一件事两条数据。
        // 现在统一为：
        //   「X 点叫我」      → system 工具的 alarm.set / timer.set（真·系统闹钟）
        //   「记一下这个安排」 → calendar 工具的 add（写进手机日历，用户在系统日历里就能看见）
        // 旧工具保留类定义但不再注册，避免历史闹钟失效（AlarmActivity 仍能响）。
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
        // 系统日历（手机日历 App 里那份）：查/加/删日程，权限靠 shell 通道自授权
        PluginRegistry.register(com.aicompanion.calendar.SystemCalendarPlugin(context))
        PluginRegistry.register(MilestonePlugin(context))
        PluginRegistry.register(MemorableMomentPlugin(context))
        PluginRegistry.register(TimeCapsulePlugin(context))
        PluginRegistry.register(AlbumPlugin(context))
        // 手机操作工具：LLM 通过工具调用形式操作手机
        PluginRegistry.register(PhoneOperationPlugin(context))
        // 技能库：跨 App 自动化技能的查询/创建/修改
        PluginRegistry.register(SkillPlugin(context))
        // 命令执行：在设备上跑 shell（默认关闭，需在设置中开启）
        PluginRegistry.register(ShellPlugin(context))
        // 系统功能目录：闹钟/亮度/免打扰/飞行模式/截屏/电池… 共用一条 shell，按需扩展
        PluginRegistry.register(SystemShellPlugin(context))
        // 文件读写：agent 级文件操作（工作区沙箱内，不经过 shell）
        PluginRegistry.register(FilePlugin(context))
        // 系统动作：拨号/开应用/系统设置等确定性的系统能力，走官方 Intent 而非模拟点击
        PluginRegistry.register(SystemActionPlugin(context))
        // 任务计划：多步任务先列计划再执行，每步更新状态（agent 式执行的骨架）
        PluginRegistry.register(PlanPlugin(context))
        // 设备控制：shell(2000) 身份下的设置读写/点击输入/截图/应用管理，写操作自带验证
        PluginRegistry.register(DevicePlugin(context))
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
