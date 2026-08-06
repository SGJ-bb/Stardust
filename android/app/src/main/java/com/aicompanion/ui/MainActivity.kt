package com.aicompanion.ui

/**
 * 主界面Activity: 整个App最核心的文件, 负责所有业务逻辑调度
 * 包括: 聊天消息收发/系统感知(时间/电量)/上下文记忆/闹钟日程设置/搜索功能/
 *       好感度计算/日记定时触发/主动搭话/电量提醒/签到成就/难忘时刻评分/
 *       Live2D初始化/用户心情选择/新手引导等
 */
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aicompanion.util.AppLogger
import android.view.View
import android.view.ViewStub
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.aicompanion.theme.StradustTheme
import com.aicompanion.theme.ThemeId
import com.aicompanion.ui.navigation.StradustDestinations
import com.aicompanion.ui.navigation.StradustNavHost
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.AppContainer
import com.aicompanion.R
import com.aicompanion.affection.AffectionManager
import com.aicompanion.gamify.AchievementManager
import com.aicompanion.interaction.ProactiveInteractionEngine
import com.aicompanion.live2d.Live2DWebView
import com.aicompanion.emotion.EmotionAnalyzer
import com.aicompanion.emotion.EmotionParams
import com.aicompanion.models.Action
import com.aicompanion.models.ChatResponse
import com.aicompanion.humanizer.Humanizer
import com.aicompanion.memory.ContextManager
import com.aicompanion.memory.MemoryEntry
import com.aicompanion.memory.MemoryPool
import com.aicompanion.rag.PersonaRagManager
import com.aicompanion.rag.RagConfig
import com.aicompanion.models.Emotion
import com.aicompanion.network.ApiClient
import com.aicompanion.settings.SettingsManager
import com.aicompanion.theme.ThemeManager
import com.aicompanion.voice.VoiceManager
import com.aicompanion.predict.ChatPredictor
import com.aicompanion.ui.coordinator.AutoOperationCoordinator
import com.aicompanion.ui.coordinator.DiaryCoordinator
import com.aicompanion.ui.coordinator.FocusTimerCoordinator
import com.aicompanion.ui.coordinator.Live2DCoordinator
import com.aicompanion.ui.coordinator.OnboardingCoordinator
import com.aicompanion.ui.coordinator.ProactiveChatCoordinator
import com.aicompanion.ui.coordinator.VirtualWorldCoordinator
import android.widget.HorizontalScrollView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import android.media.AudioManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), StradustDestinations.AppHost {

    companion object {
        private const val TAG = "MainActivity"
        /** 角色数据迁移版本号：v1=原始(有bug), v2=修复 default_stardust id 检查 bug */
        private const val MIGRATION_VERSION = 2
    }

    // === Coordinators ===
    private var live2DCoordinator: Live2DCoordinator? = null
    private var focusTimerCoordinator: FocusTimerCoordinator? = null
    private var diaryCoordinator: DiaryCoordinator? = null
    private var proactiveChatCoordinator: ProactiveChatCoordinator? = null
    private var virtualWorldCoordinator: VirtualWorldCoordinator? = null
    private var onboardingCoordinator: OnboardingCoordinator? = null
    private var autoOperationCoordinator: AutoOperationCoordinator? = null
    private var themeEntranceView: com.aicompanion.ui.effects.ThemeEntranceView? = null

    private var recyclerChat: RecyclerView? = null
    private var etMessage: EditText? = null
    private var btnSend: ImageButton? = null
    private var btnVoice: ImageButton? = null
    private var isVoiceRecording = false
    private var currentAsrManager: com.aicompanion.voice.LocalAsrManager? = null
    private var voiceWaveformOverlay: View? = null
    private var btnSettings: ImageButton? = null
    private var btnStickerChat: ImageButton? = null
    private var btnImageUpload: ImageButton? = null
    private var btnMore: ImageButton? = null
    private var tvWeather: TextView? = null
    private var tvDaysLabel: TextView? = null
    private var progressAffection: ProgressBar? = null
    private var tvAffectionTitle: TextView? = null
    private var tvPetName: TextView? = null
    private var ivAiAvatarSmall: ImageView? = null

    private var settingsManager: SettingsManager? = null

    // P1初始化完成标记（防止P2在P1失败时执行）
    @Volatile private var p1Initialized = false

    private var statsManager: com.aicompanion.stats.PersonaStatsManager? = null
    private var affectionManager: AffectionManager? = null
    private var achievementManager: AchievementManager? = null
    private var apiClient: ApiClient? = null
    private var chatAdapter: ChatAdapter? = null
    private var voiceManager: VoiceManager? = null
    private var ttsManager: com.aicompanion.voice.TtsManager? = null
    private var proactiveEngine: ProactiveInteractionEngine? = null
    private var momentsManager: com.aicompanion.memory.MemorableMomentsManager? = null
    private var systemMonitor: com.aicompanion.services.SystemMonitor? = null
    private var aiActionManager: com.aicompanion.action.AIActionManager? = null
    private val humanizer = Humanizer()
    private var contextManager: ContextManager? = null
    private var personaRagManager: PersonaRagManager? = null
    private var groupChatManager: com.aicompanion.groupchat.GroupChatManager? = null
    private var favoriteManager: FavoriteManager? = null
    private var nicknameManager: NicknameManager? = null
    private var cachedAiName: String? = null
    private var _savedInstanceState: android.os.Bundle? = null
    private var cachedAiAvatarPath: String? = null
    private var emotionAnalyzer: com.aicompanion.emotion.EmotionAnalyzer? = null
    private var milestoneManager: com.aicompanion.milestone.MilestoneManager? = null

    // ===== 缓存管理器（统一管理所有首页数据缓存，使用 StateFlow 替代 volatile）=====
    private val cacheManager = com.aicompanion.util.CacheManager(this)

    // 为了保持兼容性，提供直接访问缓存值的方法
    private val personasCache: List<com.aicompanion.ui.home.PersonaCard> get() = cacheManager.getPersonas()
    private val wallpaperCache: String? get() = cacheManager.getWallpaper()
    private val aiAvatarCache: String? get() = cacheManager.getAiAvatar()
    private val daysCache: Int get() = cacheManager.getDays()
    private val daysCacheDate: String get() = cacheManager.getDaysDate()
    private val diaryCache: List<com.aicompanion.ui.diary.DiaryEntry> get() = cacheManager.getDiary()
    private val albumCache: List<com.aicompanion.ui.album.AlbumPhotoData> get() = cacheManager.getAlbum()
    private val profileCache: com.aicompanion.ui.profile.ProfileData? get() = cacheManager.getProfile()

    private var scrollPredictions: HorizontalScrollView? = null
    private var layoutPredictions: LinearLayout? = null
    private var chatPredictor: ChatPredictor? = null

    private val chatViewModel: ChatViewModel by viewModels()

    // 使用线程安全的快照访问
    private val messages: List<ChatMessage> get() = chatViewModel.getMessagesSnapshot()

    /** 全局数据版本号（任何页面数据变化时递增，触发所有 Composable 重组） */
    private val _dataVersionState = mutableIntStateOf(0)
    fun notifyDataChanged() {
        _dataVersionState.intValue++
        // 清除各页面数据缓存，确保下次读取时获取最新数据
        cacheManager.invalidateAll(listOf("diary", "album", "profile"))
    }
    /** 消息变更计数器（Compose 可观察 State，变化时自动触发重组） */
    private val _messageVersionState = mutableStateOf(0)
    private var _messageVersion: Int
        get() = _messageVersionState.value
        set(value) {
            _messageVersionState.value = value
            _dataVersionState.intValue++  // 同时触发所有页面重组
        }
    /** 当前是否正在等待 AI 回复（Compose 可观察） */
    private val _isTypingState = mutableStateOf(false)
    private var _isTyping: Boolean by _isTypingState
    /** 群聊打字状态（按 groupId 区分，AI 回复链路期间为 true） */
    private val _groupTypingStates = mutableStateMapOf<String, Boolean>()
    /** 当前是否正在加载中（Compose 可观察） */
    private val _isLoadingState = mutableStateOf(false)
    private var _isLoading: Boolean by _isLoadingState
    /** 天气数据（Compose 模式独立于 XML View） */
    private var _weatherText = "☀️"
    /** 天数标签（Compose 模式独立于 XML View） */
    private var _daysText = "第1天"

    // ===== 通话界面状态（PhoneCallScreen Compose 桥接） =====
    private var _callPersonaName = "星尘"
    private var _isCallActive = false
    private var _callStatus = "正在接听..."
    private var _callStartTime = 0L
    private var _callTranscript = ""
    private var _isCallMuted = false
    private var _isCallSpeakerOn = true
    private var _callWaveformMode = 0 // 0=IDLE, 1=LISTENING, 2=AI_SPEAKING, 3=MUTED
    /** 通话音频管理器：用于真实控制麦克风静音、扬声器开关、通话音量 */
    private val callAudioManager: AudioManager? by lazy {
        getSystemService(AUDIO_SERVICE) as? AudioManager
    }
    /** 通话进入时保存的原先音频状态，挂断时恢复 */
    private var _callSavedSpeakerphoneOn = false
    private var _callSavedMicMuted = false
    /** Compose 导航控制器（由 StradustNavHost 初始化后回调设置） */
    var navController: androidx.navigation.NavController? = null
        private set
    private val messageScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val memoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chatStorage by lazy { com.aicompanion.storage.ChatHistoryStorage(this) }
    private var isInForeground = false
    private var quotedMessage: ChatMessage? = null

    // Runnable 变量已移至各 Coordinator，此处保留 focusRunnable 用于 focusTimerCoordinator 之前的兼容

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        AppLogger.w(TAG, "pickImageLauncher: resultCode=${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            var uri = result.data?.data
            if (uri == null) {
                val clipData = result.data?.clipData
                if (clipData != null && clipData.itemCount > 0) {
                    uri = clipData.getItemAt(0).uri
                }
            }
            if (uri == null) {
                AppLogger.e(TAG, "pickImageLauncher: URI为空, data=${result.data}")
                Toast.makeText(this, "获取图片失败", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            AppLogger.w(TAG, "pickImageLauncher: uri=$uri")
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                AppLogger.w(TAG, "pickImageLauncher: takePersistableUriPermission失败: ${e.message}")
            }
            try {
                val file = File(filesDir, "chat_bg_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                if (file.exists() && file.length() > 0) {
                    AppLogger.w(TAG, "pickImageLauncher: 背景已保存 ${file.absolutePath} (${file.length()} bytes)")
                    getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                        .putString("chat_background", file.absolutePath).apply()
                    applyTheme()
                    Toast.makeText(this, "背景已更新", Toast.LENGTH_SHORT).show()
                } else {
                    AppLogger.e(TAG, "pickImageLauncher: 文件保存失败，文件为空")
                    Toast.makeText(this, "设置背景失败：文件为空", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "pickImageLauncher: ${e.javaClass.simpleName}: ${e.message}")
                Toast.makeText(this, "设置背景失败", Toast.LENGTH_SHORT).show()
            }
        } else {
            AppLogger.w(TAG, "pickImageLauncher: 用户取消或result非OK, code=${result.resultCode}")
        }
    }

    private val stickerPickLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val stickerPath = result.data?.getStringExtra("sticker_path")
            if (!stickerPath.isNullOrEmpty()) {
                addStickerMessage("user", stickerPath)
            }
        }
    }

    private val imageUploadLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                val dir = File(filesDir, "chat_images")
                dir.mkdirs()
                val fileName = "img_${System.currentTimeMillis()}.jpg"
                val destFile = File(dir, fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val options = android.graphics.BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        android.graphics.BitmapFactory.decodeFile(destFile.absolutePath, options)
                        val maxDim = 1280
                        val sampleSize = maxOf(
                            options.outWidth / maxDim,
                            options.outHeight / maxDim,
                            1
                        )
                        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                        }
                        val bitmap = android.graphics.BitmapFactory.decodeFile(destFile.absolutePath, decodeOptions)
                        if (bitmap == null) {
                            withContext(Dispatchers.Main) {
                                addImageMessage(destFile.absolutePath, emptyList())
                                sendToLLM("[用户发送了一张图片]")
                            }
                            return@launch
                        }
                        val stream = java.io.ByteArrayOutputStream()
                        var quality = 75
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
                        while (stream.toByteArray().size > 500_000 && quality > 30) {
                            quality -= 10
                            stream.reset()
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
                        }
                        bitmap.recycle()
                        val base64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                        val dataUrl = "data:image/jpeg;base64,$base64"
                        withContext(Dispatchers.Main) {
                            addImageMessage(destFile.absolutePath, listOf(dataUrl))
                            sendToLLM("用户发送了一张图片", imageUrls = listOf(dataUrl))
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            addImageMessage(destFile.absolutePath, emptyList())
                            sendToLLM("[用户发送了一张图片]")
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "图片上传失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * AI 头像选择器：从相册选图 → 复制到 personas/avatars/ → 写入 AvatarManager SP
     * 写入后立即刷新 cachedAiAvatarPath，触发 ChatScreen 重组显示新头像
     */
    private val pickAiAvatarLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                val dir = File(filesDir, "personas/avatars")
                if (!dir.exists()) dir.mkdirs()
                val destFile = File(dir, "avatar_${activePersonaId}_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                if (destFile.exists() && destFile.length() > 0) {
                    // 统一写入 AvatarManager（avatar_data SP）
                    com.aicompanion.util.AvatarManager.saveAiAvatarPath(this, activePersonaId, destFile.absolutePath)
                    // 同时写入 persona_data SP（兼容旧逻辑）
                    getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE).edit()
                        .putString("persona_avatar_path", destFile.absolutePath).apply()
                    // 更新 Persona 模型字段
                    try {
                        val pm = com.aicompanion.persona.PersonaManager(this)
                        pm.load()
                        pm.updatePersona(activePersonaId) { it.copy(avatarPath = destFile.absolutePath) }
                    } catch (_: Exception) {}
                    // 刷新缓存并触发 UI 重组
                    cachedAiAvatarPath = destFile.absolutePath
                    cacheManager.updateAiAvatar(destFile.absolutePath)
                    com.aicompanion.util.AvatarManager.evictCache(destFile.absolutePath)
                    refreshChatAdapterAvatars()
                    notifyDataChanged()
                    Toast.makeText(this, "AI 头像已更新", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "pickAiAvatarLauncher failed: ${e.message}")
                Toast.makeText(this, "设置头像失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 用户头像选择器：从相册选图 → 复制到 filesDir → 写入 AvatarManager SP
     */
    private val pickUserAvatarLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                val destFile = File(filesDir, "user_avatar_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                if (destFile.exists() && destFile.length() > 0) {
                    com.aicompanion.util.AvatarManager.saveUserAvatarPath(this, activePersonaId, destFile.absolutePath)
                    notifyDataChanged()
                    Toast.makeText(this, "用户头像已更新", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "pickUserAvatarLauncher failed: ${e.message}")
                Toast.makeText(this, "设置头像失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }

    private var currentUserMood = ""
    private var currentUserMoodName = ""
    private val logHistory = Collections.synchronizedList(mutableListOf<String>())

    private var activePersonaId: String = "default"

    private fun refreshActivePersonaId() {
        // 优先使用 SharedPreferences 的值（角色切换后最新值）
        // Intent 中的 persona_id 仅作为首次启动来源
        val raw = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("active_persona_id", null)
            ?: intent?.getStringExtra("persona_id")
            ?: "default"
        activePersonaId = if (raw.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) raw else "default"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // === P0: UI必需立即初始化 (阻塞主线程, <500ms) ===
        // 不调用 enableEdgeToEdge()：传统模式下系统自动避开导航栏，
        // adjustResize 会把窗口缩小到键盘上方，输入栏自然贴合键盘（与群聊 Activity 一致）
        _savedInstanceState = savedInstanceState

        // 激活检查：未激活则跳转激活页面
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("is_activated", false)) {
            startActivity(Intent(this, ActivationActivity::class.java))
            finish()
            return
        }

        refreshActivePersonaId()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
            }
        }

        // 初始化全局主题状态
        com.aicompanion.theme.ThemeState.init(this)

        // 设置 Compose 内容 (快速显示UI)
        setContent {
            StradustTheme(
                themeId = com.aicompanion.theme.ThemeState.currentThemeId,
                forceDarkMode = com.aicompanion.theme.ThemeState.currentDarkMode,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = StradustTheme.colors.background,
                ) {
                    StradustNavHost(
                        host = this@MainActivity as StradustDestinations.AppHost,
                        onNavControllerReady = { controller -> this@MainActivity.navController = controller },
                    )
                }
            }
        }

        // 初始化View (兼容旧布局)
        try {
            initStep("Views") { initViews() }
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.w(TAG, "initViews失败: ${e.message}")
        }

        // === P1: 核心功能 (异步, 不阻塞UI显示) ===
        lifecycleScope.launch {
            initCoreComponents()
        }
    }

    /**
     * P1阶段: 核心功能异步初始化
     * 包含核心聊天功能所需的组件
     */
    private fun initCoreComponents() {
        if (isFinishing || isDestroyed) return

        try {
            initStep("SettingsManager") { settingsManager = SettingsManager(this) }
            initStep("EnsureDirs") { ensureAppDirs() }
            initStep("MigratePersonas") { migratePersonasToCharacterCards() }
            initStep("ApiClient") { rebuildApiClient() }
            initStep("ContextManager") { contextManager = ContextManager(this, activePersonaId) }
            initStep("PersonaRag") { personaRagManager = PersonaRagManager(this, activePersonaId) }
            initStep("ChatViewModel") { initChatViewModel() }
            initStep("ChatAdapter") { initChatAdapter() }
            initStep("LoadMessages") { loadChatHistory() }
            initStep("Welcome") { loadWelcomeMessage() }

            // P1初始化成功标记
            p1Initialized = true

            // P1完成后启动P2
            lifecycleScope.launch {
                initAuxiliaryComponents()
            }
        } catch (e: Exception) {
            fatal("initCoreComponents", e)
            p1Initialized = false
        }
    }

    /**
     * P2阶段: 辅助功能延迟初始化
     * 包含不影响核心聊天功能的辅助组件
     */
    private fun initAuxiliaryComponents() {
        // P1失败时跳过P2，防止组件崩溃
        if (!p1Initialized) {
            com.aicompanion.util.AppLogger.e(TAG, "P1初始化失败，跳过P2辅助组件初始化")
            return
        }
        if (isFinishing || isDestroyed) return

        try {
            initStep("AffectionManager") { affectionManager = AffectionManager(this, activePersonaId) }
            initStep("StatsManager") { statsManager = com.aicompanion.stats.PersonaStatsManager(this, activePersonaId) }
            initStep("AchievementManager") { achievementManager = AchievementManager(this, activePersonaId) }
            initStep("MomentsManager") { momentsManager = com.aicompanion.memory.MemorableMomentsManager(this, activePersonaId) }
            initStep("MilestoneManager") { milestoneManager = com.aicompanion.milestone.MilestoneManager(this, activePersonaId) }
            initStep("SystemMonitor") {
                val monitor = com.aicompanion.services.SystemMonitor(this)
                monitor.startMonitoring()
                monitor.onBatteryLow = { percentage ->
                    if (!isFinishing && !isDestroyed) {
                        triggerBatteryAlert(percentage)
                    }
                }
                systemMonitor = monitor

                settingsManager?.onScreenRecognitionChanged = { enabled ->
                    if (enabled) {
                        try {
                            val intent = Intent(this, com.aicompanion.screen.ScreenRecognitionService::class.java)
                            startService(intent)
                        } catch (_: Exception) {}
                    }
                }
                settingsManager?.onVoiceRecognitionChanged = { _ ->
                }
            }
            initStep("AIActionManager") {
                aiActionManager = com.aicompanion.action.AIActionManager(this)
                AppContainer.setNicknameCallback { nicknames ->
                    nicknameManager?.addDiscoveredBatch(nicknames, "llm")
                    nicknameManager?.let { saveDiscoveredNicknames(it) }
                }
                AppContainer.setSearchMemoryCallback { query, topK ->
                    searchMemory(query, topK)
                }
                AppContainer.setSearchDiaryCallback { query, topK ->
                    searchDiary(query, topK)
                }
                AppContainer.setStickerCallback { stickerPath ->
                    runOnUiThread {
                        addStickerMessage("ai", stickerPath)
                    }
                }
                AppContainer.setImageGeneratedCallback { imagePath ->
                    runOnUiThread {
                        addGeneratedImageMessage(imagePath)
                    }
                }
            }
            initStep("GroupChatManager") {
                groupChatManager = com.aicompanion.groupchat.GroupChatManager(this)
                groupChatManager?.load()
            }
            initStep("WireNickname") {
                val personaPrefs = getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE)
                val appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                contextManager?.userNickname = personaPrefs.getString("user_nickname", null)
                    ?: appPrefs.getString("user_call_name", "")
                    ?: "用户"
            }
            initStep("FavoriteManager") { favoriteManager = FavoriteManager(this, activePersonaId) }
            initStep("NicknameManager") {
                nicknameManager = NicknameManager(this, activePersonaId)
                // 更新 ChatViewModel 的引用
                chatViewModel.nicknameManager = nicknameManager
            }
            initStep("VoiceManager") { voiceManager = VoiceManager(this) }
            initStep("TtsManager") {
                ttsManager = com.aicompanion.voice.TtsManager(this)
                // 初始化语音播放控制器，供 Compose 聊天界面语音气泡使用
                com.aicompanion.ui.chat.VoicePlaybackController.init(ttsManager!!)
            }
            initStep("ProactiveEngine") { settingsManager?.let { proactiveEngine = ProactiveInteractionEngine(it) } }
            initStep("PersonaCompress") { initPersonaCompression() }
            initStep("LoadAvatar") { loadAiAvatar() }
            initStep("Live2DCoordinator") {
                live2DCoordinator = Live2DCoordinator(
                    this, this,
                    prefs = getSharedPreferences("app_prefs", MODE_PRIVATE),
                    logHistory = logHistory,
                    voiceManagerProvider = { voiceManager },
                    onPetMessage = { text, emotion, action -> addPetMessage(text, emotion, action) }
                )
                if (settingsManager?.live2dEnabled == true) {
                    live2DCoordinator?.loadSettings()
                } else {
                    live2DCoordinator?.hideView()
                }
            }
            initStep("Live2DModel") {
                if (settingsManager?.live2dEnabled == true) {
                    live2DCoordinator?.ensureView(findViewById(R.id.view_stub_live2d))
                    live2DCoordinator?.loadModel()
                }
            }
            initStep("ClickListeners") { setupClickListeners() }
            initStep("ApplyTheme") { applyTheme() }
            initStep("UpdateDisplay") { updateAffectionDisplay() }
            initStep("Weather") { updateWeather() }
            initStep("Proactive") {
                proactiveChatCoordinator = ProactiveChatCoordinator(
                    this, this,
                    messageScope = messageScope,
                    settingsManagerProvider = { settingsManager },
                    apiClientProvider = { apiClient },
                    contextManagerProvider = { contextManager },
                    proactiveEngineProvider = { proactiveEngine },
                    getPersonaInfo = { getPersonaInfo(it) },
                    getMessages = { messages },
                    isInForeground = { isInForeground },
                    onPetMessage = { text, emotion, action -> addPetMessage(text, emotion, action) },
                    onUpdatePetDisplay = { response -> updatePetDisplay(response) }
                )
                scheduleProactiveChat()
            }
            initStep("VirtualWorld") {
                virtualWorldCoordinator = VirtualWorldCoordinator(this, this, memoryScope)
                scheduleVirtualWorldTick()
            }
            initStep("DiaryTimer") {
                diaryCoordinator = DiaryCoordinator(
                    this, this,
                    memoryScope = memoryScope,
                    settingsManagerProvider = { settingsManager },
                    apiClientProvider = { apiClient },
                    affectionManagerProvider = { affectionManager },
                    contextManagerProvider = { contextManager },
                    milestoneManagerProvider = { milestoneManager },
                    getPersonaInfo = { getPersonaInfo() },
                    getMessages = { messages },
                    onPetMessage = { text, emotion, action -> addPetMessage(text, emotion, action) },
                    onSetLoading = { setLoading(it) }
                )
                scheduleDiaryTimer()
            }
            initStep("BatteryOptimization") { requestBatteryOptimization() }
            initStep("EntranceAnim") { showThemeEntranceIfDue() }

            // P2完成后，更新ChatViewModel的延迟初始化引用
            chatViewModel.aiActionManager = aiActionManager
            chatViewModel.statsManager = statsManager

            messageScope.launch(Dispatchers.IO) {
                try {
                    com.aicompanion.prompt.PromptBuilder.buildIdentity(this@MainActivity, activePersonaId)
                } catch (e: Exception) {
                    com.aicompanion.util.AppLogger.e(TAG, "buildIdentity预热失败: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "initAuxiliaryComponents失败: ${e.message}", e)
        }
    }

    private fun initStep(name: String, block: () -> Unit) {
        try { block() } catch (e: Exception) {
            Log.e(TAG, "[INIT FAIL] $name: ${e.javaClass.simpleName}: ${e.message}", e)
            com.aicompanion.util.ErrorHandler.handleInitError(name, e, this)
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as? android.os.PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "requestBatteryOptimization: ${e.message}") }
            }
        }
    }

    private fun initPersonaCompression() {
        val client = apiClient ?: return
        val ctxMgr = contextManager ?: return

        val personaText = buildFullPersonaText()
        if (personaText.length > 3000 && ctxMgr.memoryPool.isEmpty) {
            messageScope.launch(Dispatchers.IO) {
                try {
                    val systemPrompt = "将以下角色设定压缩为500字以内的概要，保留核心性格、背景和关键特征。只输出概要内容，不要其他说明。"
                    val response = client.sendSimplePrompt(systemPrompt, personaText)
                    if (response != null && response.text.isNotBlank()) {
                        ctxMgr.memoryPool.add(MemoryEntry(
                            content = response.text.take(500),
                            category = "角色概要",
                            sourceTurn = 0
                        ))
                        ctxMgr.memoryPool.saveToStorage()
                    }
                } catch (e: Exception) {
                    com.aicompanion.util.AppLogger.e(TAG, "initPersonaCompression: ${e.message}")
                }
            }
        }

        // 缺陷3修复:移除长度限制,只要有内容就构建索引
        if (personaRagManager != null) {
            val fields = buildPersonaFields()
            if (fields.values.any { it.isNotBlank() }) {
                messageScope.launch(Dispatchers.IO) {
                    personaRagManager?.buildIndex(fields)
                }
                AppLogger.i(TAG, "Persona RAG索引构建已触发: ${fields.size}个字段")
            }
        }
    }

    private fun buildFullPersonaText(): String {
        val personaId = activePersonaId
        val personaPrefs = getSharedPreferences("persona_data_$personaId", MODE_PRIVATE)

        val name = personaPrefs.getString("persona_name", null)
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("ai_name", "星尘") ?: "星尘"

        return buildString {
            append("你是「$name」。")
            val fields = buildPersonaFields()
            for ((_, text) in fields) {
                if (text.isNotBlank()) append("\n$text")
            }
        }
    }

    private fun buildPersonaFields(): Map<String, String> {
        val personaId = activePersonaId
        val personaPrefs = getSharedPreferences("persona_data_$personaId", MODE_PRIVATE)

        val name = personaPrefs.getString("persona_name", null)
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("ai_name", "星尘") ?: "星尘"

        val fields = mutableMapOf<String, String>()
        fields["name"] = "你是「$name」。"
        (personaPrefs.getString("persona_desc", "") ?: "").takeIf { it.isNotBlank() }?.let { fields["desc"] = "简介：$it" }
        (personaPrefs.getString("persona_appearance", "") ?: "").takeIf { it.isNotBlank() }?.let { fields["appearance"] = "外貌：$it" }
        (personaPrefs.getString("persona_personality", "") ?: "").takeIf { it.isNotBlank() }?.let { fields["personality"] = "性格：$it" }
        (personaPrefs.getString("persona_speech_style", "") ?: "").takeIf { it.isNotBlank() }?.let { fields["speechStyle"] = "说话风格：$it" }
        (personaPrefs.getString("persona_catchphrases", "") ?: "").takeIf { it.isNotBlank() }?.let { fields["catchphrases"] = "常用口头禅：$it" }
        (personaPrefs.getString("persona_preferences", "") ?: "").takeIf { it.isNotBlank() }?.let { fields["preferences"] = "喜好：$it" }
        (personaPrefs.getString("world_setting", "") ?: "").takeIf { it.isNotBlank() }?.let { fields["worldSetting"] = "世界观设定：$it" }
        (personaPrefs.getString("world_relationship", "") ?: "").takeIf { it.isNotBlank() }?.let { fields["worldRelationship"] = "你和用户的关系：$it" }
        (personaPrefs.getString("world_rules", "") ?: "").takeIf { it.isNotBlank() }?.let { fields["worldRules"] = "规则：$it" }
        return fields
    }

    private fun fatal(step: String, e: Exception) {
        Log.e(TAG, "[FATAL] $step: ${e.javaClass.simpleName}: ${e.message}", e)
        Toast.makeText(this, "严重错误: $step - ${e.message}", Toast.LENGTH_LONG).show()
    }

    private fun animateEntrance() {
    }

    private fun initViews() {
        // Compose 模式下这些 View 可能不存在，安全获取
        try {
            recyclerChat = findViewById(R.id.recycler_chat)
            etMessage = findViewById(R.id.et_message)
            btnSend = findViewById(R.id.btn_send)
            btnVoice = findViewById(R.id.btn_voice)
            btnSettings = findViewById(R.id.btn_settings)
            btnStickerChat = findViewById(R.id.btn_sticker_chat)
            btnImageUpload = findViewById(R.id.btn_image_upload)
            btnMore = findViewById(R.id.btn_more)
        } catch (e: Exception) {
            AppLogger.w(TAG, "initViews: Compose模式下按钮View不存在，跳过初始化: ${e.message}")
        }

        try {
            findViewById<View>(R.id.btn_phone_call)?.setOnClickListener {
            try {
                val intent = Intent(this, com.aicompanion.ui.PhoneCallActivity::class.java)
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_PERSONA_ID, activePersonaId)

                // 强制从 PersonaManager 获取最新角色名称，避免显示默认的"星尘"
                val realName = try {
                    val pm = com.aicompanion.persona.PersonaManager(this)
                    pm.load()
                    val p = pm.getPersona(activePersonaId)
                    if (p != null && p.name.isNotBlank() && p.name != "星尘") p.name
                    else cachedAiName ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("ai_name", "") ?: ""
                } catch (_: Exception) { cachedAiName ?: "" }

                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_PERSONA_NAME,
                    realName.ifBlank { "星尘" })
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_SCOPE, "persona")
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_SCOPE_ID, activePersonaId)
                milestoneManager?.recordMilestone("first_call", "第一次通话", "第一次和星尘打电话", "call")
                startActivity(intent)
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e(TAG, "phoneCall: ${e.message}")
            }
        }

            findViewById<View>(R.id.btn_phone_call)?.setOnLongClickListener {
                startActivity(Intent(this, BedtimeRadioActivity::class.java))
                true
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "initViews: 电话按钮不存在（Compose模式下可忽略）")
        }

        try {
            tvWeather = findViewById(R.id.tv_weather)
        tvDaysLabel = findViewById(R.id.tv_days_label)
        progressAffection = findViewById(R.id.progress_affection)
        tvAffectionTitle = findViewById(R.id.tv_affection_title)
        tvPetName = findViewById(R.id.tv_pet_name)
        ivAiAvatarSmall = findViewById(R.id.iv_ai_avatar_small)
        scrollPredictions = findViewById(R.id.scroll_predictions)
            layoutPredictions = findViewById(R.id.layout_predictions)
        } catch (e: Exception) {
            AppLogger.w(TAG, "initViews: 信息显示View不存在（Compose模式下可忽略）: ${e.message}")
        }
    }

    private fun ensureAppDirs() {
        try {
            val modelsDir = File(getExternalFilesDir(null), "live2d_models")
            if (!modelsDir.exists()) modelsDir.mkdirs()
            val downloadDir = File(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS), "Live2D")
            if (!downloadDir.exists()) downloadDir.mkdirs()
        } catch (e: Exception) {
            Log.e(TAG, "ensureAppDirs: ${e.message}")
        }
    }

    /**
     * 数据迁移：将 PersonaManager 的旧角色数据迁移到 CharacterCardManager
     * 解决更新后角色丢失的问题
     *
     * 修复历史 bug：
     * - v1 原始逻辑：检查条件 `existingCards[0].id != "default"` 永远为 true（实际默认卡 id 是 "default_stardust"），
     *   导致迁移被永久跳过，用户旧角色找不回来
     * - v2 修复：改用版本号机制，已迁移到最新版本则跳过；已有用户卡则直接更新版本号
     * - 三层防护：MainActivity 主迁移 + PersonaManager 数据源 + CharacterCardManager.migrateFromLegacyPersonas 兜底扫描
     */
    private fun migratePersonasToCharacterCards() {
        try {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            // 版本号机制：v1=原始(有bug), v2=修复 default_stardust bug
            val currentVersion = prefs.getInt("personas_migration_version", 0)
            if (currentVersion >= MIGRATION_VERSION) {
                AppLogger.d(TAG, "migratePersonasToCharacterCards: 已是最新版本(v$currentVersion)，跳过")
                return
            }

            val cardManager = com.aicompanion.character.CharacterCardManager(this)
            val existingCards = cardManager.getAllCards()
            val defaultIds = setOf("default_stardust", "default")
            val hasUserCards = existingCards.any { it.id !in defaultIds }

            // 已有用户卡 → 直接更新版本号，跳过迁移
            if (hasUserCards) {
                AppLogger.d(TAG, "migratePersonasToCharacterCards: 已有用户卡，直接更新版本号到 v$MIGRATION_VERSION")
                prefs.edit().putInt("personas_migration_version", MIGRATION_VERSION).apply()
                return
            }

            // 第一层：从 PersonaManager 迁移
            val personaManager = com.aicompanion.persona.PersonaManager(this)
            personaManager.load()
            val oldPersonas = personaManager.getAllPersonas()

            var migratedCount = 0
            val existingIds = existingCards.map { it.id }.toSet()
            for (persona in oldPersonas) {
                // 跳过默认星尘（CharacterCardManager 已有默认角色 default_stardust）
                if (persona.id == "default" && persona.name == "星尘") continue
                // 跳过已存在的卡（避免重复迁移）
                if (persona.id in existingIds) continue

                val card = com.aicompanion.models.CharacterCard(
                    id = persona.id,
                    name = persona.name,
                    description = persona.description,
                    personality = persona.personality,
                    scenario = "",
                    firstMes = "",
                    mesExample = "",
                    creatorNotes = "",
                    systemPrompt = persona.prompt,
                    postHistoryInstructions = "",
                    alternateGreetings = emptyList(),
                    tags = emptyList(),
                    creator = "",
                    characterVersion = "1.0",
                    avatarPath = persona.avatarPath,
                    isActive = persona.isDefault,
                    createdAt = persona.createdAt,
                    worldInfoId = ""
                )
                cardManager.addCard(card)
                migratedCount++
            }

            if (migratedCount > 0) {
                AppLogger.i(TAG, "migratePersonasToCharacterCards: 从 PersonaManager 迁移 $migratedCount 个角色")
            } else {
                // 第二层：PersonaManager 也没数据 → 调用 CharacterCardManager 的兜底扫描
                val fallbackCount = cardManager.migrateFromLegacyPersonas()
                if (fallbackCount > 0) {
                    AppLogger.i(TAG, "migratePersonasToCharacterCards: 兜底扫描恢复 $fallbackCount 个角色")
                }
            }
            // 更新版本号（无论是否迁移到数据，都标记为最新版本，避免下次重复扫描）
            prefs.edit().putInt("personas_migration_version", MIGRATION_VERSION).apply()
        } catch (e: Exception) {
            AppLogger.e(TAG, "migratePersonasToCharacterCards: 迁移失败 - ${e.message}", e)
        }
    }

    /**
     * 角色切换后重建所有依赖 personaId 的组件
     * 确保记忆池、好感度、统计等数据完全隔离
     */
    private fun rebuildPersonaDependentComponents() {
        com.aicompanion.util.AppLogger.w("MainActivity", "角色切换: 重建组件, newPersonaId=$activePersonaId")

        // 重建角色依赖的管理器
        affectionManager = AffectionManager(this, activePersonaId)
        statsManager = com.aicompanion.stats.PersonaStatsManager(this, activePersonaId)
        achievementManager = AchievementManager(this, activePersonaId)
        momentsManager = com.aicompanion.memory.MemorableMomentsManager(this, activePersonaId)
        contextManager = ContextManager(this, activePersonaId)
        personaRagManager = PersonaRagManager(this, activePersonaId)
        favoriteManager = FavoriteManager(this, activePersonaId)
        nicknameManager = NicknameManager(this, activePersonaId)
        milestoneManager = com.aicompanion.milestone.MilestoneManager(this, activePersonaId)

        // 缺陷6修复:角色切换后立即构建新角色的RAG索引
        messageScope.launch(Dispatchers.IO) {
            val fields = buildPersonaFields()
            if (fields.values.any { it.isNotBlank() }) {
                personaRagManager?.buildIndex(fields)
                AppLogger.i(TAG, "角色切换后RAG索引已重建: ${fields.size}个字段")
            }
        }

        // 设置昵称
        val personaPrefs = getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE)
        val appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        contextManager?.userNickname = personaPrefs.getString("user_nickname", null)
            ?: appPrefs.getString("user_call_name", "")
            ?: "用户"

        // 重建 ChatViewModel 和 SubjectivityEngine
        chatViewModel.contextManager = contextManager
        chatViewModel.emotionAnalyzer = apiClient?.let { com.aicompanion.emotion.EmotionAnalyzer(it) }
        chatViewModel.emotionGuardian = com.aicompanion.emotion.EmotionGuardian(applicationContext)
        chatViewModel.subjectivityEngine = com.aicompanion.emotion.SubjectivityEngine(applicationContext, activePersonaId)
        chatViewModel.activePersonaId = activePersonaId
        chatViewModel.personaRagManager = personaRagManager
        chatViewModel.statsManager = statsManager
        chatViewModel.nicknameManager = nicknameManager

        // 刷新 PromptBuilder 缓存
        com.aicompanion.prompt.PromptBuilder.invalidateCache()
        com.aicompanion.prompt.PromptBuilder.buildIdentity(this, activePersonaId)

        // 清除头像缓存（防止切换角色后显示上一个角色的头像）
        cacheManager.invalidate("aiAvatar")
        cachedAiAvatarPath = null

        // 重新加载聊天记录
        chatViewModel.clearMessages()
        chatAdapter?.notifyDataSetChanged()
        loadChatHistory()

        // 刷新 UI
        updateAffectionDisplay()
        loadAiAvatar()
        refreshChatAdapterAvatars()
        // Sync persona name to ChatAdapter and cache
        try {
            val pm = com.aicompanion.persona.PersonaManager(this)
            pm.load()
            val persona = pm.getPersona(activePersonaId)
            val name = persona?.name?.takeIf { it.isNotBlank() }
                ?: getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE)
                    .getString("persona_name", null)
                ?: "星尘"
            chatAdapter?.personaName = name
            cachedAiName = name
        } catch (_: Exception) {}

        // 通知 UI 数据已变更，触发 ProfileScreen 等页面刷新缓存
        notifyDataChanged()
    }

    private fun rebuildApiClient() {
        val sm = settingsManager ?: return
        if (sm.chatApiUrl.isNotBlank()) {
            apiClient = ApiClient(sm.chatApiUrl, sm.chatApiKey, sm.chatModel,
                sm.llmTemperature, sm.llmTopP, sm.llmFrequencyPenalty, sm.llmPresencePenalty, sm.llmMaxTokens,
                sm.apiProvider)
        }
        chatPredictor = ChatPredictor(this, sm)
        chatViewModel.apiClient = apiClient
        chatViewModel.settingsManager = sm
        chatViewModel.chatPredictor = chatPredictor
    }

    private fun initChatViewModel() {
        chatViewModel.activePersonaId = activePersonaId
        chatViewModel.chatStorage = com.aicompanion.storage.ChatHistoryStorage(applicationContext)
        chatViewModel.contextManager = contextManager
        chatViewModel.apiClient = apiClient
        chatViewModel.settingsManager = settingsManager
        chatViewModel.chatPredictor = chatPredictor
        chatViewModel.nicknameManager = nicknameManager
        chatViewModel.personaRagManager = personaRagManager
        chatViewModel.aiActionManager = aiActionManager
        chatViewModel.statsManager = statsManager
        chatViewModel.emotionGuardian = com.aicompanion.emotion.EmotionGuardian(applicationContext)
        chatViewModel.subjectivityEngine = com.aicompanion.emotion.SubjectivityEngine(applicationContext, activePersonaId)
        chatViewModel.buildIdentity = {
            com.aicompanion.prompt.PromptBuilder.buildIdentity(this@MainActivity, activePersonaId)
        }
        observeChatViewModelEvents()
    }

    private fun observeChatViewModelEvents() {
        lifecycleScope.launch {
            chatViewModel.uiEvents.collect { event ->
                if (isFinishing || isDestroyed) return@collect
                when (event) {
                    is ChatViewModel.UiEvent.ScrollToPosition -> recyclerChat?.scrollToPosition(event.position)
                    is ChatViewModel.UiEvent.ShowToast -> Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                    is ChatViewModel.UiEvent.SetTypingIndicator -> {
                        chatAdapter?.setTypingIndicator(event.isTyping)
                        _isTyping = event.isTyping
                        _messageVersion++
                    }
                    is ChatViewModel.UiEvent.SetLoading -> {
                        setLoading(event.isLoading)
                        _isLoading = event.isLoading
                        _messageVersion++
                    }
                    is ChatViewModel.UiEvent.OnPetMessageAdded -> handlePetMessageUi(event.msg, event.emotion, event.action)
                    is ChatViewModel.UiEvent.UpdatePetDisplay -> updatePetDisplay(event.response)
                    is ChatViewModel.UiEvent.TriggerTtsAndPlay -> triggerTtsAndPlay(event.text, event.emotion, event.message)
                    is ChatViewModel.UiEvent.ShowPredictions -> showPredictions(event.predictions)
                    is ChatViewModel.UiEvent.TryAttachVirtualWorldImage -> tryAttachVirtualWorldImage(event.message)
                    is ChatViewModel.UiEvent.CheckTurnsDiaryTrigger -> checkTurnsDiaryTrigger()
                    is ChatViewModel.UiEvent.CheckNewSession -> addPetMessage(
                        "📝 记忆池已达${event.poolChars}字！建议开启新会话以压缩记忆并生成日记。\n点击功能面板 → 新会话 来继续",
                        Emotion.NEUTRAL, Action.IDLE
                    )
                    is ChatViewModel.UiEvent.AddGeneratedImage -> addGeneratedImageMessage(event.imagePath)
                    is ChatViewModel.UiEvent.NotifyItemInserted -> chatAdapter?.notifyItemInserted(event.position)
                    is ChatViewModel.UiEvent.NotifyItemRangeInserted -> chatAdapter?.notifyItemRangeInserted(event.positionStart, event.itemCount)
                    is ChatViewModel.UiEvent.NotifyItemRangeRemoved -> chatAdapter?.notifyItemRangeRemoved(event.positionStart, event.itemCount)
                    is ChatViewModel.UiEvent.NotifyItemRemoved -> chatAdapter?.notifyItemRemoved(event.position)
                    is ChatViewModel.UiEvent.NotifyItemChanged -> chatAdapter?.notifyItemChanged(event.position, event.payload ?: "")
                }
            }
        }
    }

    private fun initChatAdapter() {
        chatAdapter = ChatAdapter(messages)
        chatAdapter?.cacheSkinSettings(this)

        var aiAvatarPath: String? = null
        var personaName = "星尘"
        if (activePersonaId != "default") {
            val pm = com.aicompanion.persona.PersonaManager(this)
            pm.load()
            val persona = pm.getPersona(activePersonaId)
            if (persona != null && persona.avatarPath.isNotBlank()) {
                aiAvatarPath = persona.avatarPath
                chatAdapter?.aiAvatarOverride = persona.avatarPath
            }
            personaName = persona?.name?.takeIf { it.isNotBlank() } ?: "星尘"
        } else {
            val pm = com.aicompanion.persona.PersonaManager(this)
            pm.load()
            val persona = pm.getPersona("default")
            personaName = persona?.name?.takeIf { it.isNotBlank() } ?: "星尘"
        }
        chatAdapter?.personaName = personaName
        chatAdapter?.currentPersonaId = activePersonaId
        if (aiAvatarPath.isNullOrBlank()) {
            aiAvatarPath = com.aicompanion.util.AvatarManager.getAiAvatarPath(this, activePersonaId)
        }
        val userAvatarPath = com.aicompanion.util.AvatarManager.getUserAvatarPath(this, activePersonaId)
        chatAdapter?.cacheAvatarPaths(userAvatarPath, aiAvatarPath, activePersonaId)

        chatAdapter?.onNavigateToProfile = {
            val intent = android.content.Intent(this@MainActivity, com.aicompanion.ui.ProfileActivity::class.java)
            startActivity(intent)
        }

        recyclerChat?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
            itemAnimator = null
            setItemViewCacheSize(6)
            isNestedScrollingEnabled = true
            recycledViewPool.setMaxRecycledViews(1, 10)
            recycledViewPool.setMaxRecycledViews(2, 10)
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        chatAdapter?.onFeedback = { position, isLike ->
            if (position < messages.size) {
                val msg = messages[position]
                if (!msg.isUser) {
                    msg.feedback = if (isLike) 1 else -1
                    saveMessageFeedback(position, if (isLike) 1 else -1)
                    val ach = achievementManager?.updateProgress("feedback", getTotalPositiveFeedback())
                    if (ach != null) showAchievementUnlock(ach)
                    if (isLike) { affectionManager?.addAffection(1); updateAffectionDisplay(); checkAiMomentTrigger() }
                }
            }
        }

        chatAdapter?.onDeleteMessage = { position ->
            if (position >= 0 && position < messages.size) {
                val msg = messages[position]
                val msgId = msg.id
                favoriteManager?.removeFavorite(msgId)
                chatViewModel.removeMessage(position)
                chatAdapter?.notifyItemRemoved(position)
                saveChatHistory()
                // 同步删除JSON文件中的对应消息（修复：删除后重进不会恢复）
                chatViewModel.chatStorage?.deleteMessage("persona", activePersonaId, msgId)
            }
        }

        chatAdapter?.onQuoteMessage = { position ->
            if (position >= 0 && position < messages.size) {
                quotedMessage = messages[position]
                showQuoteBar()
            }
        }

        chatAdapter?.onFavoriteMessage = { position ->
            if (position in messages.indices) {
                val fm = favoriteManager
                if (fm != null) {
                    val msg = messages[position]
                    if (fm.isFavorited(msg.id)) {
                        fm.removeFavorite(msg.id)
                        msg.isFavorited = false
                        Toast.makeText(this@MainActivity, "已取消收藏", Toast.LENGTH_SHORT).show()
                    } else {
                        fm.addFavorite(msg)
                        msg.isFavorited = true
                        Toast.makeText(this@MainActivity, "已收藏", Toast.LENGTH_SHORT).show()
                    }
                    chatAdapter?.notifyItemChanged(position)
                    saveChatHistory()
                }
            }
        }

        chatAdapter?.onReactionMessage = { position, emoji ->
            if (position >= 0 && position < messages.size) {
                val msg = messages[position]
                if (emoji.isEmpty()) {
                    msg.reactionEmoji = ""
                } else {
                    msg.reactionEmoji = emoji
                }
                favoriteManager?.updateReaction(msg.id, emoji)
                chatAdapter?.notifyItemChanged(position)
                saveChatHistory()
            }
        }

        chatAdapter?.ttsManager = ttsManager
        chatAdapter?.onPlayVoice = { msg ->
            if (ttsManager?.isPlaying == true) {
                ttsManager?.stopPlayback()
            } else {
                ttsManager?.playAudio(msg.audioPath, msg.audioUrl)
            }
        }
    }

    // Live2D 方法已移至 Live2DCoordinator
    private fun loadLive2DSettings() { live2DCoordinator?.loadSettings() }
    private fun hideLive2DView() { live2DCoordinator?.hideView() }
    private fun ensureLive2DView(): Live2DWebView? = live2DCoordinator?.ensureView(findViewById(R.id.view_stub_live2d))
    // setupLive2DTouch 已移至 Live2DCoordinator

    private fun setupClickListeners() {
        btnSend?.setOnClickListener { sendMessage() }

        etMessage?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_UP) {
                sendMessage()
                true
            } else false
        }

        btnVoice?.setOnClickListener {
            if (isVoiceRecording) {
                stopVoiceRecording()
            } else {
                startVoiceRecording()
            }
        }

        btnSettings?.setOnClickListener {
            navigateToSettings()
        }

        findViewById<View>(R.id.btn_moments)?.setOnClickListener {
            startActivity(Intent(this, com.aicompanion.moments.MomentsActivity::class.java))
        }

        findViewById<View>(R.id.btn_calendar)?.setOnClickListener {
            try { startActivity(Intent(this, com.aicompanion.calendar.CalendarActivity::class.java)) } catch (e: Exception) {
                Toast.makeText(this, "无法打开日历", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btn_album)?.setOnClickListener {
            try { startActivity(Intent(this, com.aicompanion.album.MemorialAlbumActivity::class.java)) } catch (e: Exception) {
                Toast.makeText(this, "无法打开纪念相册", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btn_diary)?.let { btnDiary ->
            btnDiary.setOnClickListener {
                startActivity(Intent(this, com.aicompanion.ui.DiaryActivity::class.java))
            }
            btnDiary.setOnLongClickListener {
                startActivity(Intent(this, com.aicompanion.ui.TimeCapsuleActivity::class.java))
                true
            }
        }

        btnStickerChat?.let { btn ->
            btn.setOnClickListener {
                try {
                    stickerPickLauncher.launch(
                        Intent(this, com.aicompanion.sticker.StickerActivity::class.java)
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开表情包", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnImageUpload?.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            imageUploadLauncher.launch(intent)
        }
        btnMore?.let { btn ->
            btn.setOnClickListener { showFeaturePanel() }
        }
        ivAiAvatarSmall?.setOnClickListener {
            try {
                val intent = Intent(this, ProfileActivity::class.java)
                intent.putExtra("persona_id", activePersonaId)
                startActivity(intent)
            } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "setupClickListeners: ${e.message}") }
        }

        // 注意：chatAdapter 的 onFeedback/onDeleteMessage/onQuoteMessage 等回调已在 initChatAdapter() 中设置
        // 此处不再重复设置，避免覆盖
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showFeaturePanel() {
        data class FeatureItem(val iconRes: Int, val label: String, val index: Int)

        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val contentView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 32)

            TextView(this@MainActivity).apply {
                text = "功能面板"
                textSize = 18f
                setTextColor(android.graphics.Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 8, 0, 16)
                addView(this)
            }

            android.view.View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { setMargins(0, 0, 0, 16) }
                setBackgroundColor(0x22ffffff)
                addView(this)
            }

            val features = listOf(
                FeatureItem(R.drawable.ic_checkin, "每日签到", 0),
                FeatureItem(R.drawable.ic_trophy, "成就殿堂", 1),
                FeatureItem(R.drawable.ic_diary, "心情日记", 2),
                FeatureItem(R.drawable.ic_write, "AI写日记", 9),
                FeatureItem(R.drawable.ic_focus, "专注计时", 3),
                FeatureItem(R.drawable.ic_model, "切换皮套", 4),
                FeatureItem(R.drawable.ic_background, "换壁纸", 5),
                FeatureItem(R.drawable.ic_log, "运行日志", 6),
                FeatureItem(R.drawable.ic_help, "操作教程", 7),
                FeatureItem(R.drawable.ic_robot, "手机自动化", 8),
                FeatureItem(R.drawable.ic_memory, "记忆池", 10),
                FeatureItem(R.drawable.ic_refresh, "新会话", 11),
                FeatureItem(R.drawable.ic_emoji, "表情包", 12),
                FeatureItem(android.R.drawable.ic_menu_gallery, "皮肤商店", 13),
                FeatureItem(R.drawable.ic_log, "聊天记录", 14),
                FeatureItem(android.R.drawable.ic_menu_gallery, "纪念相册", 15),
                FeatureItem(android.R.drawable.ic_menu_delete, "清空记录", 99)
            )

            val gridLayout = android.widget.GridLayout(this@MainActivity).apply {
                rowCount = 2; columnCount = 4
                alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS
            }

            features.forEach { feature ->
                val itemLayout = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setPadding(8, 12, 8, 12)

                    val iconSize = (40 * resources.displayMetrics.density).toInt()
                    ImageView(this@MainActivity).apply {
                        setImageResource(feature.iconRes)
                        setColorFilter(0xFFc4b5fd.toInt())
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { gravity = android.view.Gravity.CENTER }
                        addView(this)
                    }

                    TextView(this@MainActivity).apply {
                        text = feature.label; textSize = 11f; setTextColor(0xFFaabbdd.toInt())
                        gravity = android.view.Gravity.CENTER; setPadding(0, 4, 0, 0)
                        addView(this)
                    }

                    setOnClickListener {
                        bottomSheet.dismiss()
                        when (feature.index) {
                            0 -> performCheckIn()
                            1 -> try { startActivity(Intent(this@MainActivity, AchievementActivity::class.java)) } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "showFeaturePanel: ${e.message}") }
                            2 -> try { startActivity(Intent(this@MainActivity, DiaryActivity::class.java)) } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "showFeaturePanel: ${e.message}") }
                            3 -> if (focusTimerCoordinator?.isActive == true) cancelFocusTimer() else startFocusTimer()
                            4 -> try { startActivity(Intent(this@MainActivity, ModelManagerActivity::class.java)) } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "showFeaturePanel: ${e.message}") }
                            5 -> changeWallpaper()
                            6 -> showLogViewer()
                            7 -> showTutorial()
                            8 -> showAutoOperationDialog()
                            9 -> triggerManualDiary()
                            10 -> try {
                                val intent = Intent(this@MainActivity, MemoryPoolActivity::class.java)
                                intent.putExtra("persona_id", activePersonaId)
                                startActivity(intent)
                            } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "showFeaturePanel: ${e.message}") }
                            11 -> showNewSessionDialog()
                            12 -> startActivity(Intent(this@MainActivity, com.aicompanion.sticker.StickerActivity::class.java))
                            13 -> startActivity(Intent(this@MainActivity, com.aicompanion.ui.SkinShopActivity::class.java))
                            14 -> try {
                                val intent = Intent(this@MainActivity, com.aicompanion.ui.ChatHistoryActivity::class.java)
                                intent.putExtra("scope", "persona")
                                intent.putExtra("scopeId", activePersonaId)
                                intent.putExtra("scopeName", cachedAiName ?: "星尘")
                                startActivity(intent)
                            } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "showFeaturePanel: ${e.message}") }
                            15 -> try { startActivity(Intent(this@MainActivity, com.aicompanion.album.MemorialAlbumActivity::class.java)) } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "showFeaturePanel: ${e.message}") }
                            99 -> {
                                android.app.AlertDialog.Builder(this@MainActivity)
                                    .setTitle("清空聊天记录")
                                    .setMessage("确定要清空所有聊天记录吗？此操作不可撤销。")
                                    .setPositiveButton("清空") { _, _ ->
                                        val oldSize = messages.size
                                        chatViewModel.clearMessages()
                                        chatAdapter?.notifyItemRangeRemoved(0, oldSize)
                                        saveChatHistory()
                                        // 同步删除JSON文件（修复：清空后重进不会恢复）
                                        chatViewModel.chatStorage?.deleteScope("persona", activePersonaId)
                                        Toast.makeText(this@MainActivity, "聊天记录已清空", Toast.LENGTH_SHORT).show()
                                        _messageVersion++
                                    }
                                    .setNegativeButton("取消", null)
                                    .show()
                            }
                        }
                    }
                }
                val params = android.widget.GridLayout.LayoutParams().apply {
                    width = 0; columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                }
                itemLayout.layoutParams = params
                gridLayout.addView(itemLayout)
            }
            addView(gridLayout)
        }
        bottomSheet.setContentView(contentView)
        bottomSheet.behavior.peekHeight = (280 * resources.displayMetrics.density).toInt()
        bottomSheet.show()
    }

    private fun startVoiceRecording() {
        if (isVoiceRecording) {
            AppLogger.w(TAG, "startVoiceRecording: 已在录音中，先停止")
            stopVoiceRecording()
        }
        isVoiceRecording = true
        btnVoice?.alpha = 0.5f
        btnVoice?.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        showVoiceWaveformOverlay()

        try {
            currentAsrManager?.cancel()
            currentAsrManager = com.aicompanion.voice.LocalAsrManager(this)
            val asrManager = currentAsrManager ?: return
            asrManager.setListener(object : com.aicompanion.voice.AsrListener {
                override fun onPartialResult(text: String) {
                    runOnUiThread { etMessage?.setHint("识别中: $text") }
                }
                override fun onFinalResult(text: String) {
                    runOnUiThread {
                        stopVoiceRecording()
                        if (text.isNotBlank()) {
                            etMessage?.setText(text)
                            etMessage?.setHint("输入消息...")
                            sendMessage()
                        } else {
                            android.widget.Toast.makeText(this@MainActivity, "未识别到语音内容", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                override fun onError(error: String) {
                    runOnUiThread {
                        stopVoiceRecording()
                        etMessage?.setHint("输入消息...")
                        android.widget.Toast.makeText(this@MainActivity, "语音识别: $error", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onReady() {
                    runOnUiThread { etMessage?.setHint("正在聆听...") }
                }
                override fun onEndOfSpeech() {
                    runOnUiThread { etMessage?.setHint("识别中...") }
                }
            })
            asrManager.startListening()
        } catch (e: Exception) {
            stopVoiceRecording()
            AppLogger.e(TAG, "语音识别启动失败: ${e.message}", e)
        }
    }

    private fun stopVoiceRecording() {
        isVoiceRecording = false
        btnVoice?.alpha = 1.0f
        btnVoice?.backgroundTintList = null
        hideVoiceWaveformOverlay()
        try {
            currentAsrManager?.cancel()
        } catch (_: Exception) {}
        currentAsrManager = null
    }

    private fun showVoiceWaveformOverlay() {
        try {
            val rootView = findViewById<android.view.View>(android.R.id.content) ?: return
            val parent = rootView as? android.view.ViewGroup ?: return

            if (voiceWaveformOverlay != null) {
                voiceWaveformOverlay?.visibility = android.view.View.VISIBLE
                return
            }

            val density = resources.displayMetrics.density
            val overlay = android.widget.FrameLayout(this).apply {
                setBackgroundColor(0xDD0A0A1A.toInt())
                val size = (280 * density).toInt()
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    (200 * density).toInt(),
                    android.view.Gravity.BOTTOM
                )
                setPadding(0, (16 * density).toInt(), 0, (16 * density).toInt())
            }

            val waveformView = VoiceWaveformBarView(this).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    (120 * density).toInt()
                )
            }
            overlay.addView(waveformView)

            val tvHint = android.widget.TextView(this).apply {
                text = "🎤 正在录音，点击麦克风停止"
                setTextColor(0xFF81D4FA.toInt())
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }
            overlay.addView(tvHint)

            parent.addView(overlay)
            voiceWaveformOverlay = overlay
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "showVoiceWaveformOverlay: ${e.message}")
        }
    }

    private fun hideVoiceWaveformOverlay() {
        try {
            voiceWaveformOverlay?.visibility = android.view.View.GONE
        } catch (_: Exception) {}
    }

    class VoiceWaveformBarView(context: android.content.Context) : android.view.View(context) {
        private var phase = 0f
        private val barCount = 50
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val barWidth = 3f * resources.displayMetrics.density
        private val barGap = 2f * resources.displayMetrics.density

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val totalWidth = barCount * (barWidth + barGap)
            val startX = (w - totalWidth) / 2f
            val centerY = h / 2f

            phase += 0.1f

            for (i in 0 until barCount) {
                val x = startX + i * (barWidth + barGap)
                val normalizedPos = (i.toFloat() / barCount - 0.5f) * 2f
                val wave1 = Math.sin((i * 0.4 + phase * 3.0).toDouble()).toFloat()
                val wave2 = Math.sin((i * 0.8 + phase * 2.2).toDouble()).toFloat()
                val envelope = 1f - normalizedPos * normalizedPos
                val amplitude = (h * 0.35f * envelope * (0.3f + 0.7f * Math.abs(wave1 + wave2 * 0.4f)))
                    .coerceIn(4f * resources.displayMetrics.density, h * 0.4f)

                val alpha = (0.4f + 0.6f * (amplitude / (h * 0.4f))).coerceIn(0f, 1f)
                paint.color = android.graphics.Color.argb((alpha * 255).toInt(), 0x64, 0xFF, 0xDA)

                val rect = android.graphics.RectF(x, centerY - amplitude / 2, x + barWidth, centerY + amplitude / 2)
                canvas.drawRoundRect(rect, barWidth / 2, barWidth / 2, paint)
            }

            postInvalidateDelayed(66)
        }
    }

    private fun sendMessage() {
        val text = etMessage?.text?.toString()?.trim() ?: ""
        if (text.isEmpty()) return
        AppLogger.w(TAG, "sendMessage: 用户发送 '${text.take(50)}'")
        etMessage?.text?.clear()
        scrollPredictions?.visibility = View.GONE

        val quote = quotedMessage
        hideQuoteBar()

        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val displayText = if (quote != null) {
            val quoteLabel = if (quote.isUser) "用户" else "AI"
            val preview = if (quote.text.length > 30) quote.text.take(30) + "…" else quote.text
            "$text\n\n↪ 回复「${quoteLabel}」：$preview"
        } else {
            text
        }
        addMessage(ChatMessage(text = displayText, time = time, isUser = true, userMood = currentUserMood))
        chatAdapter?.notifyItemInserted(messages.size - 1)
        recyclerChat?.scrollToPosition(messages.size - 1)
        statsManager?.recordUserMessage(text)
        saveChatHistory()
        saveMessageToFile(messages.last())

        affectionManager?.addMessage()
        milestoneManager?.recordMilestone("first_chat", "第一次对话", "和星尘的第一次对话", "chat")
        affectionManager?.evaluateUserBehavior(text, currentUserMoodName.let { nm ->
            try { Emotion.valueOf(nm.uppercase()) } catch (_: Exception) { Emotion.NEUTRAL }
        })
        updateAffectionDisplay()
        checkAiMomentTrigger()

        val chatAch = achievementManager?.updateProgress("chat", messages.count { it.isUser })
        if (chatAch != null) showAchievementUnlock(chatAch)

        triggerMomentsScoringIfNeeded()

        sendToLLM(text)
    }

    /** Compose UI 调用的发送消息入口 */
    fun composeSendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        // 清空输入框（如果存在）
        etMessage?.text?.clear()
        scrollPredictions?.visibility = View.GONE

        val quote = quotedMessage
        hideQuoteBar()

        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val displayText = if (quote != null) {
            val quoteLabel = if (quote.isUser) "用户" else "AI"
            val preview = if (quote.text.length > 30) quote.text.take(30) + "…" else quote.text
            "$trimmed\n\n↪ 回复「${quoteLabel}」：$preview"
        } else {
            trimmed
        }
        addMessage(ChatMessage(text = displayText, time = time, isUser = true, userMood = currentUserMood))
        chatAdapter?.notifyItemInserted(messages.size - 1)
        recyclerChat?.scrollToPosition(messages.size - 1)
        statsManager?.recordUserMessage(trimmed)
        saveChatHistory()
        saveMessageToFile(messages.last())

        affectionManager?.addMessage()
        milestoneManager?.recordMilestone("first_chat", "第一次对话", "和星尘的第一次对话", "chat")
        affectionManager?.evaluateUserBehavior(trimmed, currentUserMoodName.let { nm ->
            try { Emotion.valueOf(nm.uppercase()) } catch (_: Exception) { Emotion.NEUTRAL }
        })
        updateAffectionDisplay()
        checkAiMomentTrigger()

        val chatAch = achievementManager?.updateProgress("chat", messages.count { it.isUser })
        if (chatAch != null) showAchievementUnlock(chatAch)

        triggerMomentsScoringIfNeeded()

        sendToLLM(trimmed)
        _messageVersion++
    }

    private fun showQuoteBar() {
        val quote = quotedMessage ?: return
        val layoutInput = findViewById<LinearLayout>(R.id.layout_input) ?: return
        val parent = layoutInput.parent as? android.view.ViewGroup ?: return
        val inputIndex = parent.indexOfChild(layoutInput)

        val existingBar = parent.findViewById<View>(R.id.layout_quote_bar)
        existingBar?.let { parent.removeView(it) }

        val quoteBar = LinearLayout(this).apply {
            id = R.id.layout_quote_bar
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF1a1a3e.toInt())
            setPadding(12, 8, 12, 8)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM
            )
            setOnClickListener { /* keep visible */ }
        }

        val quoteIcon = TextView(this).apply {
            text = "💬"
            textSize = 14f
            setPadding(0, 0, 8, 0)
        }
        quoteBar.addView(quoteIcon)

        val quoteLabel = TextView(this).apply {
            text = if (quote.isUser) "回复用户: " else "回复AI: "
            textSize = 11f
            setTextColor(0xFFc4b5fd.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        quoteBar.addView(quoteLabel)

        val quoteText = TextView(this).apply {
            text = if (quote.text.length > 30) quote.text.take(30) + "…" else quote.text
            textSize = 12f
            setTextColor(0xFF8899bb.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        quoteBar.addView(quoteText)

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(0xFF667788.toInt())
            setPadding(12, 0, 0, 0)
            setOnClickListener {
                hideQuoteBar()
            }
        }
        quoteBar.addView(closeBtn)

        parent.addView(quoteBar, inputIndex)
    }

    private fun hideQuoteBar() {
        quotedMessage = null
        val parent = findViewById<LinearLayout>(R.id.layout_input)?.parent as? android.view.ViewGroup
        parent?.findViewById<View>(R.id.layout_quote_bar)?.let { parent.removeView(it) }
    }

    private var momentsScoreCounter = 0
    private fun triggerMomentsScoringIfNeeded() {
        momentsScoreCounter++
        if (momentsScoreCounter % 20 == 0) {
            triggerMomentsScoring()
        }
    }

    private fun checkAiMomentTrigger() {
        val apiClient = AppContainer.apiClient ?: return
        val am = affectionManager ?: return
        val affectionLevel = am.affectionLevel

        val momentsMgr = com.aicompanion.moments.MomentsManager(this)
        if (!momentsMgr.shouldAiPost(affectionLevel)) return

        val personaPrefs = getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE)
        val name = personaPrefs.getString("persona_name", null)
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("ai_name", "星尘") ?: "星尘"
        val prompt = buildString {
            append("你是「$name」。")
            personaPrefs.getString("persona_personality", "")?.takeIf { it.isNotBlank() }?.let { append("\n性格：$it") }
            personaPrefs.getString("persona_speech_style", "")?.takeIf { it.isNotBlank() }?.let { append("\n说话风格：$it") }
            personaPrefs.getString("persona_desc", "")?.takeIf { it.isNotBlank() }?.let { append("\n简介：$it") }
        }

        messageScope.launch {
            momentsMgr.generateAiMoment(
                apiClient,
                name,
                prompt,
                affectionLevel,
                activePersonaId
            )
        }
    }

    private fun triggerMomentsScoring() {
        val client = apiClient ?: return
        val sm = settingsManager ?: return
        if (sm.chatApiUrl.isBlank()) return

        messageScope.launch {
            try {
                val persona = getPersonaInfo()
                val texts = messages.map { it.text }
                val scored = withContext(Dispatchers.IO) {
                    client.scoreMemorableMoments(texts, persona.first, persona.second)
                }
                if (scored.isNotEmpty()) {
                    momentsManager?.addMoments(scored)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Moments scoring failed: ${e.message}")
            }
        }
    }

    private fun loadAiAvatar() {
        var avatarPath: String? = null

        if (activePersonaId != "default") {
            val pm = com.aicompanion.persona.PersonaManager(this)
            pm.load()
            val persona = pm.getPersona(activePersonaId)
            if (persona != null && persona.avatarPath.isNotBlank()) {
                avatarPath = persona.avatarPath
            }
        }

        if (avatarPath.isNullOrBlank()) {
            avatarPath = com.aicompanion.util.AvatarManager.getAiAvatarPath(this, activePersonaId)
        }

        if (avatarPath?.isNotEmpty() == true) {
            val file = File(avatarPath)
            if (file.exists()) {
                val path = avatarPath
                messageScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(path, options)
                            options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, 96, 96)
                            options.inJustDecodeBounds = false
                            BitmapFactory.decodeFile(path, options)
                        } catch (_: Exception) {
                            try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
                        }
                    }
                    bitmap?.let { ivAiAvatarSmall?.setImageBitmap(it) }
                }
            }
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (width > reqWidth || height > reqHeight) {
            val halfW = width / 2
            val halfH = height / 2
            while (halfW / inSampleSize >= reqWidth && halfH / inSampleSize >= reqHeight) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun refreshChatAdapterAvatars() {
        var aiAvatarPath: String? = null
        if (activePersonaId != "default") {
            val pm = com.aicompanion.persona.PersonaManager(this)
            pm.load()
            val persona = pm.getPersona(activePersonaId)
            if (persona != null && persona.avatarPath.isNotBlank()) {
                aiAvatarPath = persona.avatarPath
                chatAdapter?.aiAvatarOverride = persona.avatarPath
            }
        }
        if (aiAvatarPath.isNullOrBlank()) {
            aiAvatarPath = com.aicompanion.util.AvatarManager.getAiAvatarPath(this, activePersonaId)
        }
        val userAvatarPath = com.aicompanion.util.AvatarManager.getUserAvatarPath(this, activePersonaId)
        chatAdapter?.cacheAvatarPaths(userAvatarPath, aiAvatarPath, activePersonaId)
        chatAdapter?.notifyItemRangeChanged(0, chatAdapter?.itemCount ?: 0)
    }

    private fun sendToLLM(message: String, imageUrls: List<String> = emptyList()) {
        if (settingsManager?.offlineMode == true) {
            val offlineNlp = com.aicompanion.nlp.OfflineNLP()
            val result = offlineNlp.processMessage(message)
            addPetMessage(result.text, result.emotion, result.action)
            return
        }

        chatViewModel.sendToLLM(
            message = message,
            imageUrls = imageUrls,
            currentUserMoodName = currentUserMoodName,
            buildSystemContext = { buildSystemContext(it) },
            getPersonaInfo = { getPersonaInfo(it) },
            isFinishing = { isFinishing },
            isDestroyed = { isDestroyed }
        )
    }

    private fun showPredictions(predictions: List<String>) {
        // 同步更新 Compose 状态（Compose 模式下使用）
        updatePredictions(predictions)
        val container = layoutPredictions ?: return
        val scrollView = scrollPredictions ?: return
        container.removeAllViews()
        if (predictions.isEmpty()) {
            scrollView.visibility = View.GONE
            return
        }
        val dp = resources.displayMetrics.density
        for (text in predictions) {
            val tv = TextView(this).apply {
                this.text = text
                setTextColor(0xFFe0e0f0.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                setBackgroundResource(R.drawable.bg_prediction_chip)
                setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    etMessage?.setText(text)
                    etMessage?.setSelection(text.length)
                    sendMessage()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), (6 * dp).toInt())
            container.addView(tv, lp)
        }
        scrollView.visibility = View.VISIBLE
    }

    private fun addPetMessage(text: String, emotion: Emotion, action: Action) {
        if (isFinishing || isDestroyed) return
        val msg = chatViewModel.addPetMessageInternal(text, emotion, action) ?: return
        handlePetMessageUi(msg, emotion, action)
    }

    private fun handlePetMessageUi(msg: ChatMessage, emotion: Emotion, action: Action) {
        chatAdapter?.notifyItemInserted(messages.size - 1)
        recyclerChat?.scrollToPosition(messages.size - 1)
        statsManager?.recordAiMessage(msg.text)
        statsManager?.recordEmotion(emotion.name)

        live2DCoordinator?.setEmotion(emotion)
        live2DCoordinator?.setAction(action)
        if (settingsManager?.live2dEnabled != true) {
            live2DCoordinator?.hideView()
        }

        val aiName = cachedAiName ?: getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE)
            .getString("persona_name",
                getSharedPreferences("app_prefs", MODE_PRIVATE).getString("ai_name", "星尘"))
            .also { cachedAiName = it } ?: "星尘"
        val avatarPath = cachedAiAvatarPath ?: getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE)
            .getString("persona_avatar_path", "").also { cachedAiAvatarPath = it } ?: ""

        if (!hasWindowFocus()) {
            systemMonitor?.showAiMessageNotification(aiName, msg.text)
        }
    }

    private fun triggerTtsAndPlay(text: String, emotion: Emotion, message: ChatMessage) {
        val tm = ttsManager ?: run {
            AppLogger.w(TAG, "triggerTtsAndPlay: ttsManager为null")
            Toast.makeText(this, "语音合成未初始化", Toast.LENGTH_SHORT).show()
            return
        }
        val engineMode = tm.engineMode

        val persona = try {
            com.aicompanion.persona.PersonaManager(this).let { pm ->
                pm.load()
                pm.getPersona(activePersonaId)
            }
        } catch (_: Exception) { null }
        val personaVoice = persona?.ttsVoice?.takeIf { it.isNotBlank() }
        val personaPitch = persona?.ttsPitch?.takeIf { it != 0f }
        val personaRate = persona?.ttsRate?.takeIf { it != 0f }

        AppLogger.w(TAG, "triggerTtsAndPlay: engine=$engineMode, cloudConfigured=${tm.isCloudConfigured}, personaVoice=$personaVoice, text=${text.take(30)}...")

        if (engineMode == com.aicompanion.voice.TtsManager.ENGINE_LOCAL ||
            (engineMode == com.aicompanion.voice.TtsManager.ENGINE_AUTO && !tm.isCloudConfigured)) {
            AppLogger.w(TAG, "triggerTtsAndPlay: 使用本地TTS")
            // 语音气泡模式下，本地TTS无法生成音频文件，跳过自动播放（避免突然出声）
            val playMode = settingsManager?.ttsPlayMode
                ?: com.aicompanion.settings.SettingsManager.TTS_MODE_AUTO_PLAY
            if (playMode == com.aicompanion.settings.SettingsManager.TTS_MODE_BUBBLE_ONLY) {
                AppLogger.w(TAG, "triggerTtsAndPlay: bubble_only模式+本地TTS，跳过播放")
                return
            }
            val pitchOffset = (personaPitch ?: 0f) - (settingsManager?.ttsPitch ?: 0f)
            val rateOffset = (personaRate ?: 0f) - (settingsManager?.ttsRate ?: 0f)
            voiceManager?.speak(text, emotion, pitchOffset, rateOffset)
            return
        }

        memoryScope.launch {
            try {
                AppLogger.w(TAG, "triggerTtsAndPlay: 开始云端TTS合成, voice=$personaVoice")
                val result = tm.synthesizeWithVoice(text, personaVoice, emotion)
                AppLogger.w(TAG, "triggerTtsAndPlay: 合成结果 success=${result.success}, path=${result.audioPath?.take(50)}, url=${result.audioUrl?.take(50)}, error=${result.error}")
                if (result.success && (result.audioPath != null || result.audioUrl != null)) {
                    message.audioPath = result.audioPath
                    message.audioUrl = result.audioUrl
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            // 触发 Compose 重组以显示语音气泡
                            _messageVersion++
                            // 根据播放模式决定行为
                            val playMode = settingsManager?.ttsPlayMode
                                ?: com.aicompanion.settings.SettingsManager.TTS_MODE_AUTO_PLAY
                            if (playMode == com.aicompanion.settings.SettingsManager.TTS_MODE_AUTO_PLAY) {
                                // 直接朗读：自动播放
                                val playKey = result.audioPath ?: result.audioUrl
                                com.aicompanion.ui.chat.VoicePlaybackController.setPlaying(playKey)
                                tm.playAudio(result.audioPath, result.audioUrl) {
                                    com.aicompanion.ui.chat.VoicePlaybackController.setPlaying(null)
                                }
                            }
                            // bubble_only 模式：只生成语音气泡，不自动播放，用户点击气泡才播放
                        }
                    }
                } else if (!result.success) {
                    AppLogger.w(TAG, "TTS失败(${engineMode})，回退本地: ${result.error}")
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this@MainActivity, "语音合成失败: ${result.error ?: "未知错误"}", Toast.LENGTH_LONG).show()
                        }
                    }
                    val playMode = settingsManager?.ttsPlayMode
                        ?: com.aicompanion.settings.SettingsManager.TTS_MODE_AUTO_PLAY
                    if (playMode != com.aicompanion.settings.SettingsManager.TTS_MODE_BUBBLE_ONLY) {
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) {
                                val pitchOffset = (personaPitch ?: 0f) - (settingsManager?.ttsPitch ?: 0f)
                                val rateOffset = (personaRate ?: 0f) - (settingsManager?.ttsRate ?: 0f)
                                voiceManager?.speak(text, emotion, pitchOffset, rateOffset)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "triggerTtsAndPlay: ${e.javaClass.simpleName}: ${e.message}")
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this@MainActivity, "语音合成异常: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                val playMode = settingsManager?.ttsPlayMode
                    ?: com.aicompanion.settings.SettingsManager.TTS_MODE_AUTO_PLAY
                if (playMode != com.aicompanion.settings.SettingsManager.TTS_MODE_BUBBLE_ONLY) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            val pitchOffset = (personaPitch ?: 0f) - (settingsManager?.ttsPitch ?: 0f)
                            val rateOffset = (personaRate ?: 0f) - (settingsManager?.ttsRate ?: 0f)
                            voiceManager?.speak(text, emotion, pitchOffset, rateOffset)
                        }
                    }
                }
            }
        }
    }

    private fun addMessage(msg: ChatMessage) {
        chatViewModel.addMessage(msg)
        _messageVersion++
    }

    private fun addImageMessage(imagePath: String, imageUrls: List<String>) {
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val msg = ChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            text = "[图片]",
            time = time,
            isUser = true,
            timestamp = System.currentTimeMillis(),
            stickerPath = imagePath,
            imageUrls = imageUrls
        )
        addMessage(msg)
        chatAdapter?.notifyItemInserted(messages.size - 1)
        recyclerChat?.scrollToPosition(messages.size - 1)
        saveChatHistory()
    }

    private fun addStickerMessage(sender: String, stickerPath: String) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val stickerName = try {
            com.aicompanion.AppContainer.stickerManager.getAllStickers().find { it.filePath == stickerPath }?.emotion?.ifBlank { null } ?: "表情包"
        } catch (_: Exception) { "表情包" }
        val msg = ChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            text = "[表情包: $stickerName]",
            time = time,
            isUser = sender == "user",
            timestamp = System.currentTimeMillis(),
            stickerPath = stickerPath
        )
        addMessage(msg)
        chatAdapter?.notifyItemInserted(messages.size - 1)
        recyclerChat?.scrollToPosition(messages.size - 1)
        saveChatHistory()
        if (sender == "user") statsManager?.recordStickerSent() else statsManager?.recordStickerReceived()
        if (sender == "user") {
            sendToLLM("[用户发送了一个表情包: $stickerName]")
        }
    }

    private fun addGeneratedImageMessage(imagePath: String) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val msg = ChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            text = "[图片]",
            time = time,
            isUser = false,
            timestamp = System.currentTimeMillis(),
            generatedImagePath = imagePath
        )
        addMessage(msg)
        chatAdapter?.notifyItemInserted(messages.size - 1)
        recyclerChat?.scrollToPosition(messages.size - 1)
        saveChatHistory()
    }

    private suspend fun getPersonaInfo(query: String = ""): Pair<String, String> = withContext(Dispatchers.IO) {

        val identity = com.aicompanion.prompt.PromptBuilder.buildIdentity(this@MainActivity, activePersonaId)

        val fullPrompt = buildString {
            append(com.aicompanion.prompt.PromptBuilder.buildPersonaFull(identity))

            if (identity.userNickname.isNotBlank()) {
                append("\n叫用户「${identity.userNickname}」。")
            } else {
                val discovered = nicknameManager?.getActiveNicknames() ?: emptyList()
                if (discovered.isNotEmpty()) {
                    val nicknamesStr = discovered.joinToString("、") { "「$it」" }
                    append("\n可以叫用户：$nicknamesStr。")
                } else {
                    append("\n可以用summarize_nicknames工具给用户取称呼。")
                }
            }

            if (query.isNotBlank() && RagConfig.personaRagEnabled && RagConfig.ragMode == "auto") {
                checkAndRebuildPersonaIndex()
                if (personaRagManager?.isReady() == true) {
                    try {
                        val ragChunks = personaRagManager?.retrieveSync(query, 3) ?: emptyList()
                        if (ragChunks.isNotEmpty()) {
                            append("\n\n[相关设定]")
                            for (chunk in ragChunks) {
                                append("\n$chunk")
                            }
                        }
                    } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "getPersonaInfo: ${e.message}") }
                }
            }

            val ctxBlock = contextManager?.getContextBlock() ?: ""
            if (ctxBlock.isNotBlank()) {
                append("\n\n[上下文]\n$ctxBlock")
            }

            // 注入主体性状态 (情绪+驱动力+疲劳+风格指令)
            try {
                val stateBlock = chatViewModel.subjectivityEngine?.getStatePromptBlock()
                if (!stateBlock.isNullOrBlank()) {
                    append(stateBlock)
                }
            } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "getPersonaInfo: subjectivity: ${e.message}") }

            append(com.aicompanion.prompt.PromptBuilder.getCoreRules(this@MainActivity))
            append("\n- 有强烈情绪时用send_sticker工具发表情包。用户让你发表情包时必须调用send_sticker工具，不要只用文字描述。可以反问和主动关心。保持角色不跳戏。")
            append("\n- 当用户提到过去的事情、问你记不记得什么、或需要回忆历史经历时，主动使用search_diary工具搜索日记。")
        }
        Pair(identity.name, fullPrompt)
    }

    private fun checkAndRebuildPersonaIndex() {
        val rag = personaRagManager ?: return
        val fields = buildPersonaFields()
        val newHash = fields.values.joinToString("|").hashCode().toString()
        if (rag.currentHash() == newHash) return
        //AppLogger.d(TAG, "checkAndRebuildPersonaIndex: hash changed, rebuilding")
        messageScope.launch {
            withContext(Dispatchers.IO) {
                rag.buildIndex(fields)
            }
        }
    }

    private suspend fun searchMemory(query: String, topK: Int): String {
        return chatViewModel.searchMemory(query, topK)
    }

    private suspend fun searchDiary(query: String, topK: Int): String {
        return chatViewModel.searchDiary(query, topK)
    }

    private fun cosineSim(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var nA = 0f; var nB = 0f
        for (i in a.indices) { dot += a[i] * b[i]; nA += a[i] * a[i]; nB += b[i] * b[i] }
        val denom = kotlin.math.sqrt(nA) * kotlin.math.sqrt(nB)
        return if (denom > 0f) dot / denom else 0f
    }

    private fun updatePetDisplay(response: ChatResponse) {
    }

    private fun setLoading(loading: Boolean) {
        if (isDestroyed) return
        btnSend?.isEnabled = !loading
        btnSend?.alpha = if (loading) 0.4f else 1.0f
    }

    private fun applyTheme() {
        // Compose 模式下主题由 StradustTheme + ThemeState 自动管理
        // 此方法仅用于旧 XML 布局的后备兼容
        if (recyclerChat == null) {
            AppLogger.w(TAG, "applyTheme: Compose模式下跳过旧版主题应用（由StradustTheme自动管理）")
            return
        }
        try {
            val scheme = ThemeManager.getCurrentScheme(this)
            val primaryDark = safeParseColor(scheme.primaryColorDark, 0xFF1a1a2e.toInt())
            val accentColor = safeParseColor(scheme.accentColor, 0xFFaabbdd.toInt())

            findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)?.setBackgroundColor(primaryDark)
            findViewById<LinearLayout>(R.id.layout_input)?.setBackgroundColor(primaryDark)

            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val bgPath = prefs.getString("chat_background", "")
            val bgView = findViewById<ImageView>(R.id.iv_chat_background)
            val uiLayer = findViewById<FrameLayout>(R.id.ui_layer)
            if (!bgPath.isNullOrEmpty()) {
                val path = bgPath
                uiLayer?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val file = File(path)
                        if (!file.exists()) {
                            AppLogger.w(TAG, "applyTheme: 背景文件不存在 $path")
                            withContext(Dispatchers.Main) {
                                uiLayer?.setBackgroundResource(R.drawable.bg_gradient)
                                bgView?.visibility = View.GONE
                            }
                            return@launch
                        }
                        val bgOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(path, bgOptions)
                        bgOptions.inSampleSize = calculateSampleSize(bgOptions.outWidth, bgOptions.outHeight, 1080, 1920)
                        bgOptions.inJustDecodeBounds = false
                        val bmp = BitmapFactory.decodeFile(path, bgOptions)
                        if (bmp == null) {
                            AppLogger.e(TAG, "applyTheme: 解码背景失败 $path")
                            return@launch
                        }
                        if (bmp.isRecycled) {
                            AppLogger.e(TAG, "applyTheme: 背景bitmap已recycled")
                            return@launch
                        }
                        AppLogger.w(TAG, "applyTheme: 背景加载成功 ${bmp.width}x${bmp.height}")
                        withContext(Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) {
                                bgView?.setImageBitmap(bmp)
                                bgView?.alpha = 0.3f
                                bgView?.visibility = View.VISIBLE
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "applyTheme: 加载背景异常: ${e.message}")
                    }
                }
            } else {
                uiLayer?.setBackgroundResource(R.drawable.bg_gradient)
                bgView?.visibility = View.GONE
            }

            ThemeManager.applyTheme(this)
            chatAdapter?.cacheSkinSettings(this)

            if (settingsManager?.simpleScreenMode == true) {
                live2DCoordinator?.hideView()
                findViewById<ImageView>(R.id.iv_chat_background)?.visibility = View.GONE
            } else if (settingsManager?.live2dEnabled == true) {
                val view = live2DCoordinator?.ensureView(findViewById(R.id.view_stub_live2d))
                if (view != null) {
                    view.visibility = View.VISIBLE
                    view.resumeRendering()
                    // 如果模型还没加载, 加载模型
                    if (live2DCoordinator?.live2dView?.url.isNullOrBlank() || live2DCoordinator?.live2dView?.url == "about:blank") {
                        live2DCoordinator?.loadModel()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyTheme: ${e.message}")
        }
    }

    private fun updateAffectionDisplay() {
        val am = affectionManager ?: return
        val daysLabel = "第${am.getDaysSinceFirstUse()}天"
        tvDaysLabel?.text = daysLabel
        _daysText = daysLabel
        progressAffection?.progress = am.affectionLevel
        if (am.affectionLevel >= 60) milestoneManager?.recordMilestone("affection_friend", "成为朋友", "好感度达到朋友级别", "affection")
        if (am.affectionLevel >= 90) milestoneManager?.recordMilestone("affection_close", "亲密伙伴", "好感度达到亲密级别", "affection")
        tvAffectionTitle?.text = am.getAffectionTitle()
        tvAffectionTitle?.setTextColor(am.getAffectionColor())
        val name = try {
            val pm = com.aicompanion.persona.PersonaManager(this@MainActivity)
            pm.load()
            val persona = pm.getPersona(activePersonaId)
            persona?.name?.takeIf { it.isNotBlank() }
                ?: getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE)
                    .getString("persona_name", null)
                ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("ai_name", "星尘")
                ?: "星尘"
        } catch (_: Exception) {
            getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE)
                .getString("persona_name", null) ?: "星尘"
        }
        tvPetName?.text = "✨ $name"
        chatAdapter?.personaName = name
        checkPersonalityEvolution()
        checkUserPersonalitySummary()
    }

    private fun checkUserPersonalitySummary() {
        val am = affectionManager ?: return
        if (!am.shouldTriggerPersonalitySummary()) return
        val client = apiClient ?: return
        val sm = settingsManager ?: return
        if (sm.userPersonalityDef.isNotBlank()) return

        val personaPrefs = getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE)
        val personaName = personaPrefs.getString("persona_name", null)
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("ai_name", "星尘") ?: "星尘"
        val recentMessages = messages.takeLast(30)
        val chatSummary = recentMessages.joinToString("\n") { msg ->
            val speaker = if (msg.isUser) "用户" else personaName
            "$speaker: ${msg.text.take(100)}"
        }
        if (chatSummary.isBlank()) return

        messageScope.launch(Dispatchers.IO) {
            try {
                val result = client.summarizeUserPersonality(
                    personaName = personaName,
                    recentChatSummary = chatSummary,
                    currentSummary = sm.getAiSummarizedPersonality(activePersonaId),
                    affectionLevel = am.affectionLevel
                )
                if (!result.isNullOrBlank()) {
                    sm.setAiSummarizedPersonality(activePersonaId, result)
                    com.aicompanion.prompt.PromptBuilder.invalidateCache()
                    //AppLogger.d(TAG, "User personality summarized: affection=${am.affectionLevel}")
                }
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.w(TAG, "User personality summary failed: ${e.message}")
            }
        }
    }

    private fun checkPersonalityEvolution() {
        val am = affectionManager ?: return
        if (!am.shouldTriggerPersonalityEvolution()) return
        val client = apiClient ?: return
        val personaPrefs = getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE)
        val personaName = personaPrefs.getString("persona_name", null)
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("ai_name", "星尘") ?: "星尘"
        val currentPersonality = personaPrefs.getString("persona_personality", "") ?: ""
        val currentSpeechStyle = personaPrefs.getString("persona_speech_style", "") ?: ""
        val worldSetting = personaPrefs.getString("world_setting", "") ?: ""
        val recentMessages = messages.takeLast(20)
        val chatSummary = recentMessages.joinToString("\n") { msg ->
            val speaker = if (msg.isUser) "用户" else personaName
            "$speaker: ${msg.text.take(100)}"
        }
        messageScope.launch(Dispatchers.IO) {
            try {
                val result = client.evolvePersonality(
                    personaName = personaName,
                    currentPersonality = currentPersonality,
                    currentSpeechStyle = currentSpeechStyle,
                    affectionLevel = am.affectionLevel,
                    recentChatSummary = chatSummary,
                    worldSetting = worldSetting
                ) ?: return@launch
                val cleaned = result.trim()
                    .removePrefix("```json").removePrefix("```")
                    .removeSuffix("```").trim()
                val json = org.json.JSONObject(cleaned)
                val newPersonality = json.optString("personality", "")
                val newSpeechStyle = json.optString("speech_style", "")
                if (newPersonality.isNotBlank() || newSpeechStyle.isNotBlank()) {
                    val editor = personaPrefs.edit()
                    if (newPersonality.isNotBlank()) editor.putString("persona_personality", newPersonality)
                    if (newSpeechStyle.isNotBlank()) editor.putString("persona_speech_style", newSpeechStyle)
                    editor.apply()
                    val pm = com.aicompanion.persona.PersonaManager(this@MainActivity)
                    pm.load()
                    pm.updatePersona(activePersonaId) { persona ->
                        persona.copy(
                            personality = if (newPersonality.isNotBlank()) newPersonality else persona.personality,
                            speechStyle = if (newSpeechStyle.isNotBlank()) newSpeechStyle else persona.speechStyle
                        )
                    }
                    //AppLogger.d(TAG, "Personality evolved: affection=${am.affectionLevel}")
                }
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.w(TAG, "Personality evolution failed: ${e.message}")
            }
        }
    }

    private fun updateWeather() {
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val (emoji, temp) = when (month) {
            in 3..5 -> when { hour < 6 -> "🌸" to 18; hour < 12 -> "☀️" to 25; hour < 18 -> "🌤" to 22; else -> "🌙" to 15 }
            in 6..8 -> when { hour < 6 -> "🌙" to 28; hour < 12 -> "☀️" to 35; hour < 18 -> "🔥" to 32; else -> "🌙" to 26 }
            in 9..11 -> when { hour < 6 -> "🍂" to 18; hour < 12 -> "☀️" to 22; hour < 18 -> "🍁" to 16; else -> "🌙" to 12 }
            else -> when { hour < 6 -> "❄️" to 3; hour < 12 -> "☀️" to 8; hour < 18 -> "🌨" to 5; else -> "🌙" to -2 }
        }
        tvWeather?.text = "$emoji $temp°"
        tvWeather?.visibility = View.VISIBLE
        _weatherText = "$emoji $temp°"
    }

    // (已移至 override fun performCheckIn() 并合并 CheckInManager 逻辑)

    private fun getYesterday(): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    private fun showAchievementUnlock(achievement: com.aicompanion.models.Achievement) {
        Toast.makeText(this, "🏆 成就解锁: ${achievement.title}", Toast.LENGTH_LONG).show()
    }

    private fun tryAttachVirtualWorldImage(message: String) {
        val lower = message.lowercase()
        val needVwImage = listOf(
            "你在做什么", "你在干嘛", "你在干什么", "你在哪", "虚拟世界",
            "世界怎么样", "世界发生了什么", "你在世界", "世界最近", "你那边"
        ).any { lower.contains(it) }
        if (!needVwImage) return

        try {
            val vwManager = findActiveVirtualWorld() ?: return
            val events = vwManager.getStoryEvents()
            val lastWithImage = events.lastOrNull { it.imageUrl.isNotBlank() } ?: return
            val imgFile = File(lastWithImage.imageUrl)
            if (!imgFile.exists()) return
            addStickerMessage("ai", lastWithImage.imageUrl)
        } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "tryAttachVirtualWorldImage: ${e.message}") }
    }

    private fun findActiveVirtualWorld(): com.aicompanion.virtualworld.VirtualWorldManager? {
        return virtualWorldCoordinator?.findActiveVirtualWorld()
    }

    private fun buildSystemContext(message: String): String {
        val lower = message.lowercase()
        val sb = StringBuilder()

        val needTime = listOf("几点", "什么时间", "时间", "日期", "今天几号", "星期", "几点钟", "now time", "what time").any { lower.contains(it) }
        val needBattery = listOf(
            "电量", "电池", "电量百分比", "还有多少电", "还剩多少电", "电量剩余",
            "手机电量", "电池电量", "充", "充电", "power", "battery"
        ).any { lower.contains(it) } || lower.contains("电")

        val needVirtualWorld = listOf(
            "你在做什么", "你在干嘛", "你在干什么", "你在哪", "你最近在做什么",
            "你最近怎么样", "你在忙什么", "你在哪里", "虚拟世界", "世界怎么样",
            "世界发生了什么", "你在世界", "世界最近", "你那边", "你在那"
        ).any { lower.contains(it) }

        val now = java.util.Calendar.getInstance()
        val currentTimeStr = java.text.SimpleDateFormat("yyyy年M月d日 EEEE HH:mm", java.util.Locale.getDefault()).format(now.time)

        if (needTime) {
            sb.append("[系统信息] 当前时间：$currentTimeStr")
        }

        if (needBattery) {
            try {
                val percentage = getBatteryPercentage()
                if (percentage >= 0) {
                    val batteryManager = getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
                    val isCharging = batteryManager?.isCharging == true
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append("[系统信息] 当前手机电量：${percentage}%${if (isCharging) "（充电中）" else ""}")
                }
            } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "buildSystemContext: ${e.message}") }
        }

        if (needVirtualWorld) {
            try {
                val vwManager = findActiveVirtualWorld()
                if (vwManager != null) {
                    val summary = vwManager.getLatestStorySummary(3)
                    if (summary.isNotBlank()) {
                        if (sb.isNotEmpty()) sb.append("\n")
                        sb.append(summary)
                    }
                }
            } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "buildSystemContext: ${e.message}") }
        }

        return sb.toString()
    }

    private fun getBatteryPercentage(): Int {
        try {
            val batteryIntent = applicationContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    return (level * 100) / scale
                }
            }
        } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "getBatteryPercentage: ${e.message}") }
        return -1
    }

    private fun showLogViewer() {
        val allLogs = com.aicompanion.util.AppLogger.getRecentLogs(300)
        val live2dLog = live2DCoordinator?.live2dView?.getLog()?.takeLast(3000) ?: ""
        val historyLog = logHistory.joinToString("\n").takeLast(500)

        val errorLogs = allLogs.lines().filter { it.contains("E/") }.joinToString("\n")
        val live2dErrors = live2dLog.lines().filter {
            it.contains("ERROR", ignoreCase = true) ||
            it.contains("FAIL", ignoreCase = true) ||
            it.contains("exception", ignoreCase = true) ||
            it.contains("not found", ignoreCase = true) ||
            it.contains("missing", ignoreCase = true) ||
            it.contains("not renderable", ignoreCase = true)
        }.joinToString("\n")

        val tabs = listOf("错误", "Live2D", "全部")
        val tabContents = listOf(
            buildString {
                if (live2dErrors.isNotBlank()) { append("[Live2D 错误]\n"); append(live2dErrors); append("\n\n") }
                if (errorLogs.isNotBlank()) { append("[应用错误]\n"); append(errorLogs) }
                if (isBlank()) append("无错误")
            },
            if (live2dLog.isNotBlank()) live2dLog else "暂无Live2D日志",
            buildString {
                append(allLogs)
                if (historyLog.isNotBlank()) { append("\n\n=== 历史 ===\n"); append(historyLog) }
                if (isBlank()) append("暂无日志")
            }
        )

        val textView = android.widget.TextView(this).apply {
            textSize = 11f
            setPadding(24, 16, 24, 16)
            setTextIsSelectable(true)
            setTextColor(android.graphics.Color.parseColor("#E0E0E0"))
            text = tabContents[0]
        }
        val scrollView = android.widget.ScrollView(this).apply {
            addView(textView)
        }
        val dialogView = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
            addView(scrollView, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        var currentTab = 0
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("📋 运行日志 - ${tabs[0]}")
            .setView(dialogView)
            .setPositiveButton("复制当前") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("日志", textView.text))
                Toast.makeText(this@MainActivity, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("切换分类") { _, _ -> }
            .setNegativeButton("关闭", null)
            .show()

        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            currentTab = (currentTab + 1) % tabs.size
            textView.text = tabContents[currentTab]
            dialog.setTitle("📋 运行日志 - ${tabs[currentTab]}")
        }
    }

    private fun showTutorial() {
        if (onboardingCoordinator == null) {
            onboardingCoordinator = OnboardingCoordinator(
                this, this,
                messageScope = messageScope,
                settingsManagerProvider = { settingsManager },
                getAppPrefs = { getSharedPreferences("app_prefs", MODE_PRIVATE) },
                getPersonaInfo = { getPersonaInfo() },
                onPetMessage = { text, emotion, action -> addPetMessage(text, emotion, action) }
            )
        }
        onboardingCoordinator?.showLegacyTutorial()
    }

    /** 每次启动App都显示入场动画 */
    private fun showThemeEntranceIfDue() {
        try {
            // 仅在非配置重建（如旋转屏幕）时展示入场动画
            if (_savedInstanceState != null) return

            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val currentScheme = com.aicompanion.theme.ThemeManager.getCurrentScheme(this).id

            // 每次启动都播放入场动画（不再按主题去重）

            val rootLayout = findViewById<FrameLayout>(R.id.root_layout) ?: return

            themeEntranceView = com.aicompanion.ui.effects.ThemeEntranceView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setup(currentScheme)
                onDismissed = {
                    prefs.edit().apply {
                        putString("entrance_anim_scheme", currentScheme)
                        apply()
                    }
                    rootLayout.removeView(this@apply)
                    themeEntranceView = null
                }
            }
            rootLayout.addView(themeEntranceView)

            // 确保动画 View 获得正确的尺寸后再触发绘制
            themeEntranceView?.post {
                themeEntranceView?.requestLayout()
            }

            com.aicompanion.util.AppLogger.i(TAG, "入场动画已触发: scheme=$currentScheme")
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.w(TAG, "入场动画异常: ${e.message}")
        }
    }

    private fun showAutoOperationDialog() {
        if (autoOperationCoordinator == null) {
            autoOperationCoordinator = AutoOperationCoordinator(
                this, this,
                messageScope = messageScope,
                settingsManagerProvider = { settingsManager },
                apiClientProvider = { apiClient },
                onPetMessage = { text, emotion, action -> addPetMessage(text, emotion, action) }
            )
        }
        autoOperationCoordinator?.showAutoOperationDialog()
    }

    // executeAutoOperation 已移至 AutoOperationCoordinator

    /** 选择背景图片（Compose 换壁纸用） */
    override fun changeWallpaper() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            pickImageLauncher.launch(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_PICK).apply {
                    type = "image/*"
                }
                pickImageLauncher.launch(intent)
            } catch (e2: Exception) {
                com.aicompanion.util.AppLogger.e("MainActivity", "changeWallpaper: ${e2.message}")
                Toast.makeText(this, "无法打开图片选择器", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 引用回复：保存被引用的消息，下次发送时携带 */
    private var _replyingToMessage: ChatMessage? = null

    override fun onReplyToMessage(message: ChatMessage) {
        _replyingToMessage = message
        AppLogger.d(TAG, "onReplyToMessage: 引用消息 [${message.text.take(30)}]")
        // 触发 Compose 重组以显示回复预览条
        _messageVersion++
    }

    /** 获取当前正在回复的消息（供发送时构建带引用的消息） */
    fun getReplyingToMessage(): ChatMessage? = _replyingToMessage

    /** 清除引用回复状态（发送后调用） */
    fun clearReplyingTo() {
        _replyingToMessage = null
    }

    /** 删除单条消息 */
    override fun deleteMessage(message: ChatMessage) {
        try {
            val index = messages.indexOfFirst { it.id == message.id }
            if (index >= 0) {
                chatViewModel.removeMessage(index)
                // 同步到存储
                syncMessagesToStorage()
                AppLogger.d(TAG, "deleteMessage: 删除消息 [${message.text.take(30)}]")
                // 触发重组
                _messageVersion++
            } else {
                AppLogger.w(TAG, "deleteMessage: 消息不存在 [${message.id}]")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteMessage 失败: ${e.message}")
        }
    }

    /** 重新生成AI回复（删除AI消息后重新请求） */
    override fun regenerateMessage(message: ChatMessage) {
        if (message.isUser) {
            AppLogger.w(TAG, "regenerateMessage: 只能对AI消息进行重新生成")
            return
        }
        try {
            val currentMessages = messages  // 先获取快照
            val index = currentMessages.indexOfFirst { it.id == message.id }
            if (index >= 0) {
                // 删除AI消息
                chatViewModel.removeMessage(index)
                // 找到上一条用户消息
                var userMessageIndex = index - 1
                while (userMessageIndex >= 0 && !currentMessages[userMessageIndex].isUser) {
                    userMessageIndex--
                }
                if (userMessageIndex >= 0) {
                    val userMessage = currentMessages[userMessageIndex]
                    // 同步到存储
                    syncMessagesToStorage()
                    // 触发重组
                    _messageVersion++
                    // 重新请求AI回复
                    AppLogger.d(TAG, "regenerateMessage: 重新生成 [用户消息: ${userMessage.text.take(30)}]")
                    regenerateAiReply(userMessage)
                } else {
                    AppLogger.w(TAG, "regenerateMessage: 找不到对应的用户消息")
                }
            } else {
                AppLogger.w(TAG, "regenerateMessage: 消息不存在 [${message.id}]")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "regenerateMessage 失败: ${e.message}")
        }
    }

    /** 编辑用户消息并重发 */
    override fun editAndResendMessage(message: ChatMessage, newText: String) {
        if (!message.isUser) {
            AppLogger.w(TAG, "editAndResendMessage: 只能编辑用户消息")
            return
        }
        try {
            val index = messages.indexOfFirst { it.id == message.id }
            if (index >= 0) {
                // 删除用户消息及其后的所有AI回复
                val messagesToDelete = mutableListOf<ChatMessage>()
                messagesToDelete.add(messages[index])
                // 删除该消息后的连续AI回复
                var aiIndex = index + 1
                while (aiIndex < messages.size && !messages[aiIndex].isUser) {
                    messagesToDelete.add(messages[aiIndex])
                    aiIndex++
                }
                // 从列表中删除(使用chatViewModel的方法)
                for (msg in messagesToDelete) {
                    val msgIndex = messages.indexOf(msg)
                    if (msgIndex >= 0) {
                        chatViewModel.removeMessage(msgIndex)
                    }
                }
                // 同步到存储
                syncMessagesToStorage()
                // 触发重组
                _messageVersion++
                // 发送新消息
                AppLogger.d(TAG, "editAndResendMessage: 编辑重发 [新内容: ${newText.take(30)}]")
                sendMessage(newText)
            } else {
                AppLogger.w(TAG, "editAndResendMessage: 消息不存在 [${message.id}]")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "editAndResendMessage 失败: ${e.message}")
        }
    }

    /** 同步消息到存储 */
    private fun syncMessagesToStorage() {
        try {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            // 先删除当天存储，再添加所有消息
            chatStorage.deleteDate("chat", activePersonaId, today)
            val storedMessages = messages.map { msg ->
                com.aicompanion.storage.StoredMessage(
                    id = msg.id,
                    text = msg.text,
                    time = msg.time,
                    isUser = msg.isUser,
                    emotion = (msg.emotion?.name ?: "NEUTRAL"),
                    isFavorited = msg.isFavorited,
                )
            }
            chatStorage.addMessages("chat", activePersonaId, storedMessages)
        } catch (e: Exception) {
            AppLogger.e(TAG, "syncMessagesToStorage 失败: ${e.message}")
        }
    }

    /** 内部方法：重新生成AI回复 */
    private fun regenerateAiReply(userMessage: ChatMessage) {
        _isTyping = true
        // 使用 chatViewModel 的发送逻辑
        sendToLLM(userMessage.text)
    }

    private fun startFocusTimer() {
        if (focusTimerCoordinator == null) {
            focusTimerCoordinator = FocusTimerCoordinator(
                this, this,
                appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE),
                affectionManagerProvider = { affectionManager },
                achievementManagerProvider = { achievementManager },
                onPetMessage = { text, emotion, action -> addPetMessage(text, emotion, action) },
                onUpdateAffectionDisplay = { updateAffectionDisplay() },
                onCheckAiMomentTrigger = { checkAiMomentTrigger() },
                onShowAchievementUnlock = { ach -> showAchievementUnlock(ach) }
            )
        }
        focusTimerCoordinator?.start()
    }

    private fun cancelFocusTimer() {
        focusTimerCoordinator?.cancel()
    }

    private fun scheduleDiaryTimer() {
        diaryCoordinator?.scheduleDiaryTimer(activePersonaId)
    }

    private fun checkTurnsDiaryTrigger() {
        diaryCoordinator?.checkTurnsDiaryTrigger(activePersonaId)
    }

    private fun triggerManualDiary() {
        diaryCoordinator?.triggerManualDiary(activePersonaId)
    }

    // autoTriggerDiary 和 analyzeLocalMood 已移至 DiaryCoordinator

    private fun showNewSessionDialog() {
        val ctxMgr = contextManager ?: return
        val poolChars = ctxMgr.memoryPool.getPoolCharCount()
        val stats = ctxMgr.getSessionStats()

        android.app.AlertDialog.Builder(this)
            .setTitle("🔄 开启新会话")
            .setMessage("当前会话状态：\n$stats\n\n" +
                    "开启新会话将：\n" +
                    "1. 根据记忆池生成今日日记\n" +
                    "2. 将记忆池压缩保留（${poolChars}字 → ~500字）\n" +
                    "3. 清空当前对话记录\n" +
                    "4. 新会话继承压缩后的记忆\n\n" +
                    "确定要继续吗？")
            .setPositiveButton("确定") { _, _ ->
                createNewSession()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createNewSession() {
        val client = apiClient ?: return
        val ctxMgr = contextManager ?: return

        setLoading(true)
        messageScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ctxMgr.createNewSession(client) { poolBlock ->
                        val dm = com.aicompanion.diary.DiaryManager(this@MainActivity, activePersonaId)
                        dm.saveLlmDiary(
                            "今日记忆池摘要:\n$poolBlock",
                            emptyList(),
                            affectionManager?.affectionLevel ?: 0
                        )
                    }
                }
                val oldSize = messages.size
                chatViewModel.clearMessages()
                chatAdapter?.notifyItemRangeRemoved(0, oldSize)
                saveChatHistory()
                _messageVersion++
                // 新会话重置疲劳, 保留情绪和驱动力
                chatViewModel.subjectivityEngine?.onNewSession()
                addPetMessage("✨ 新会话已开启！我已保留了核心记忆并生成了日记~", Emotion.HAPPY, Action.TAIL_FLICK)
            } catch (e: Exception) {
                Log.e(TAG, "createNewSession: ${e.message}")
                addPetMessage("开启新会话失败: ${e.message}", Emotion.SAD, Action.IDLE)
            } finally {
                setLoading(false)
            }
        }
    }

    // analyzeLocalMood 已移至 DiaryCoordinator

    // triggerProactiveChat 已移至 ProactiveChatCoordinator

    private fun scheduleProactiveChat() {
        proactiveChatCoordinator?.schedule()
    }

    private fun scheduleVirtualWorldTick() {
        virtualWorldCoordinator?.scheduleTick()
    }

    private fun triggerBatteryAlert(percentage: Int) {
        if (isInForeground) return
        val client = apiClient ?: return
        val sm = settingsManager ?: return
        if (sm.chatApiUrl.isBlank()) return

        val chatHistory = messages.takeLast(sm.contextTurns).map { msg ->
            Pair(msg.isUser, msg.text)
        }

        messageScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val memCtx = contextManager?.memoryPool?.getPoolBlock()
                    val persona = getPersonaInfo()
                    client.generateNagContent(
                        persona.first, persona.second,
                        systemAlert = "主人的手机电量只剩 $percentage% 了！请提醒主人及时充电，语气要关心和温柔。",
                        memoryContext = memCtx,
                        chatHistory = chatHistory
                    )
                }
                if (response != null && response.text.isNotBlank() && response.errorMessage == null) {
                    addPetMessage(response.text, response.emotion, response.action)
                    updatePetDisplay(response)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Battery alert failed: ${e.message}")
            }
        }
    }

    private fun loadWelcomeMessage() {
        if (onboardingCoordinator == null) {
            onboardingCoordinator = OnboardingCoordinator(
                this, this,
                messageScope = messageScope,
                settingsManagerProvider = { settingsManager },
                getAppPrefs = { getSharedPreferences("app_prefs", MODE_PRIVATE) },
                getPersonaInfo = { getPersonaInfo() },
                onPetMessage = { text, emotion, action -> addPetMessage(text, emotion, action) }
            )
        }
        onboardingCoordinator?.loadWelcomeMessage()
    }

    // showOnboardingDialog 已移至 OnboardingCoordinator

    private fun getChatPrefsName(): String {
        return chatViewModel.getChatPrefsName()
    }

    private fun loadChatHistory() {
        try {
            val loaded = chatViewModel.loadChatHistory()
            if (loaded.isNotEmpty()) {
                chatViewModel.applyLoadedMessages(loaded)
                chatAdapter?.notifyItemRangeInserted(0, messages.size)
                if (messages.isNotEmpty()) recyclerChat?.scrollToPosition(messages.size - 1)
                AppLogger.w(TAG, "loadChatHistory: 加载了${loaded.size}条消息")
            }
            _messageVersion++
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "loadChatHistory失败: ${e.message}", e)
        }
    }

    private var chatSaveJob: kotlinx.coroutines.Job? = null

    private fun saveChatHistory() {
        chatViewModel.saveChatHistory()
    }

    private fun doSaveChatHistory() {
        chatViewModel.doSaveChatHistory()
    }

    private fun saveMessageToFile(msg: ChatMessage) {
        chatViewModel.saveMessageToFile(msg)
    }

    private fun saveDiscoveredNicknames(manager: NicknameManager) {
        val all = manager.getAllDiscovered()
        if (all.isEmpty()) return
        val arr = org.json.JSONArray()
        all.forEach { entry ->
            arr.put(org.json.JSONObject().apply {
                put("nickname", entry.nickname)
                put("source", entry.source)
                put("timestamp", entry.timestamp)
            })
        }
        getSharedPreferences("nickname_data", MODE_PRIVATE).edit().putString("discovered_nicknames", arr.toString()).apply()
    }

    private fun saveMessageFeedback(position: Int, feedback: Int) {
        saveChatHistory()
    }

    private fun getTotalPositiveFeedback(): Int = messages.count { !it.isUser && it.feedback > 0 }

    private fun safeParseColor(colorStr: String?, default: Int): Int {
        if (colorStr.isNullOrEmpty()) return default
        return try { Color.parseColor(colorStr) } catch (_: Exception) { default }
    }

    private var lastSkinHash: Int = 0

    override fun onResume() {
        super.onResume()
        isInForeground = true
        if (isFinishing || isDestroyed) return

        // 刷新角色ID（可能从设置页切换了角色）
        val oldPersonaId = activePersonaId
        refreshActivePersonaId()

        // 如果角色ID变了，重建所有角色相关组件
        if (oldPersonaId != activePersonaId) {
            rebuildPersonaDependentComponents()
        }

        // 检查主体性状态是否需要重建（从设置页重置后）
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("subjectivity_needs_rebuild", false)) {
            prefs.edit().remove("subjectivity_needs_rebuild").apply()
            chatViewModel.subjectivityEngine = com.aicompanion.emotion.SubjectivityEngine(applicationContext, activePersonaId)
            com.aicompanion.util.AppLogger.w(TAG, "onResume: 重建SubjectivityEngine（用户重置了内心状态）")
        }

        cachedAiName = null
        cachedAiAvatarPath = null
        com.aicompanion.prompt.PromptBuilder.invalidateCache()

        // 刷新头像
        loadAiAvatar()
        refreshChatAdapterAvatars()

        val currentSkinHash = com.aicompanion.theme.BubbleSkinManager.getActiveSkin(this).hashCode() +
            (com.aicompanion.theme.BubbleSkinManager.getActiveImageBubble(this)?.hashCode() ?: 0)
        if (currentSkinHash != lastSkinHash) {
            chatAdapter?.cacheSkinSettings(this)
            lastSkinHash = currentSkinHash
        }

        if (settingsManager?.live2dEnabled == true) {
            live2DCoordinator?.loadSettings()
            recyclerChat?.setWillNotDraw(true)
            // 确保视图已inflate并可见
            if (live2DCoordinator?.live2dView == null) {
                live2DCoordinator?.ensureView(findViewById(R.id.view_stub_live2d))
                live2DCoordinator?.loadModel()
            } else {
                live2DCoordinator?.live2dView?.let { view ->
                    if (view.visibility != View.VISIBLE) {
                        view.visibility = View.VISIBLE
                    }
                    view.resumeRendering()
                }
            }
            live2DCoordinator?.updatePositionAndScale()
            live2DCoordinator?.checkModelChange()
        } else {
            live2DCoordinator?.hideView()
            recyclerChat?.setWillNotDraw(false)
        }

        rebuildApiClient()
        updateWeather()
        updateAffectionDisplay()

        proactiveChatCoordinator?.onResume()
        virtualWorldCoordinator?.onResume()
        diaryCoordinator?.onResume()
        focusTimerCoordinator?.onResume()

        try {
            if (milestoneManager?.shouldNotifyAnniversary() == true) {
                val anniversaryMessages = milestoneManager?.getAnniversaryMessages() ?: emptyList()
                anniversaryMessages.forEach { msg ->
                    lifecycleScope.launch {
                        delay(2000)
                        addPetMessage(msg, Emotion.HAPPY, Action.IDLE)
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val capsuleMgr = com.aicompanion.capsule.TimeCapsuleManager(this)
            if (capsuleMgr.checkAndMarkToday()) {
                val dueCapsules = capsuleMgr.getDueCapsules()
                dueCapsules.forEach { capsule ->
                    capsuleMgr.markOpened(capsule.id)
                    val message = capsuleMgr.getOpeningMessage(capsule)
                    lifecycleScope.launch {
                        delay(3000)
                        addPetMessage(message, Emotion.HAPPY, Action.IDLE)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
        proactiveChatCoordinator?.onPause()
        virtualWorldCoordinator?.onPause()
        diaryCoordinator?.onPause()
        focusTimerCoordinator?.onPause()

        if (settingsManager?.backgroundRunning == true) {
            try {
                val serviceIntent = Intent(this, com.aicompanion.services.BackgroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        // === 清理所有 Coordinator ===
        listOf(
            live2DCoordinator, focusTimerCoordinator, diaryCoordinator,
            proactiveChatCoordinator, virtualWorldCoordinator, onboardingCoordinator,
            autoOperationCoordinator
        ).forEach { coordinator ->
            try { coordinator?.onDestroy() } catch (e: Exception) {
                AppLogger.e(TAG, "Coordinator cleanup failed: ${e.message}")
            }
        }

        // === 清理有 cleanup 方法的 Manager ===
        try { personaRagManager?.cleanup() } catch (e: Exception) {
            AppLogger.e(TAG, "PersonaRagManager cleanup failed: ${e.message}")
        }

        // === 清理系统监控和语音管理器 ===
        try { systemMonitor?.stopMonitoring() } catch (e: Exception) {
            AppLogger.e(TAG, "SystemMonitor cleanup failed: ${e.message}")
        }
        try {
            currentAsrManager?.cleanup()
            currentAsrManager = null
        } catch (e: Exception) {
            AppLogger.e(TAG, "ASRManager cleanup failed: ${e.message}")
        }
        try { voiceManager?.cleanup() } catch (e: Exception) {
            AppLogger.e(TAG, "VoiceManager cleanup failed: ${e.message}")
        }
        try { ttsManager?.cleanup() } catch (e: Exception) {
            AppLogger.e(TAG, "TtsManager cleanup failed: ${e.message}")
        }

        // === 清理其他 Manager 引用（无 cleanup 方法，仅置空）===
        settingsManager = null
        statsManager = null
        affectionManager = null
        achievementManager = null
        momentsManager = null
        groupChatManager = null
        favoriteManager = null
        nicknameManager = null
        milestoneManager = null
        emotionAnalyzer = null
        personaRagManager = null
        aiActionManager = null
        chatPredictor = null

        // === 清理 UI 组件 ===
        try { themeEntranceView?.forceStop() } catch (e: Exception) {
            AppLogger.e(TAG, "ThemeEntranceView cleanup failed: ${e.message}")
        }
        themeEntranceView = null

        // === 取消后台任务 ===
        chatSaveJob?.cancel()
        chatSaveJob = null
        try { doSaveChatHistory() } catch (e: Exception) {
            AppLogger.e(TAG, "Save chat history failed: ${e.message}")
        }
        messageScope.cancel()
        memoryScope.cancel()

        // === 清理回调引用 ===
        AppContainer.setNicknameCallback { }
        AppContainer.setSearchMemoryCallback { _, _ -> "" }
        AppContainer.setSearchDiaryCallback { _, _ -> "" }
        AppContainer.setStickerCallback { }
        AppContainer.setImageGeneratedCallback { }

        // === 清理适配器引用 ===
        chatAdapter?.onFeedback = null
        chatAdapter?.onDeleteMessage = null
        chatAdapter?.onQuoteMessage = null
        chatAdapter?.onFavoriteMessage = null
        chatAdapter?.onReactionMessage = null
        chatAdapter?.onPlayVoice = null
        chatAdapter?.ttsManager = null
        chatAdapter = null

        // === 清理缓存变量 ===
        cacheManager.cleanup()

        // === 清理 ViewModel 引用 ===
        chatViewModel.apiClient = null
        chatViewModel.settingsManager = null
        chatViewModel.contextManager = null
        chatViewModel.chatStorage = null
        chatViewModel.chatPredictor = null
        chatViewModel.nicknameManager = null
        chatViewModel.personaRagManager = null
        chatViewModel.aiActionManager = null
        chatViewModel.statsManager = null
        chatViewModel.emotionAnalyzer = null
        chatViewModel.emotionGuardian = null
        chatViewModel.subjectivityEngine = null

        // === 清理其他引用 ===
        voiceManager = null
        ttsManager = null
        systemMonitor = null
        contextManager = null

        AppLogger.d(TAG, "MainActivity onDestroy completed")
        super.onDestroy()
    }

    // ==================== Compose UI 桥接接口 ====================
    // 以下公开属性和方法供 Compose Screen（如 ChatScreen、FeaturePanel）调用
    // 确保 Compose UI 可以访问所有业务逻辑，同时不依赖 XML View

    /** 当前消息列表（只读，Compose ChatScreen 读取） */
    val chatMessages: List<ChatMessage> get() = messages.toList()

    /** 发送消息（Compose ChatScreen 调用） */
    fun onSendText(text: String) = composeSendMessage(text)

    /** 开始录音 */
    fun onStartVoice() = startVoiceRecording()

    /** 停止录音 */
    fun onStopVoice() = stopVoiceRecording()

    /** 当前是否正在录音 */
    val isVoiceRecordingState: Boolean get() = isVoiceRecording

    /** 当前用户心情代码 */
    val currentMood: String get() = currentUserMood

    /** 当前用户心情名称 */
    val currentMoodName: String get() = currentUserMoodName

    /** 设置用户心情 */
    fun setUserMood(mood: String, moodName: String) {
        currentUserMood = mood
        currentUserMoodName = moodName
    }

    /** 导航到设置页（Compose版 SettingsScreen，有section伸缩功能） */
    fun navigateToSettings() {
        try {
            navController?.navigate(com.aicompanion.ui.navigation.StradustDestinations.SETTINGS)
                ?: Toast.makeText(this, "导航失败", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 导航到功能面板各项（Compose 用） */
    fun navigateToFeature(index: Int) {
        when (index) {
            0 -> performCheckIn()
            1 -> try { startActivity(Intent(this, AchievementActivity::class.java)) } catch (e: Exception) { AppLogger.e(TAG, "navigateToFeature: ${e.message}") }
            2 -> try { startActivity(Intent(this, DiaryActivity::class.java)) } catch (e: Exception) { AppLogger.e(TAG, "navigateToFeature: ${e.message}") }
            3 -> if (focusTimerCoordinator?.isActive == true) cancelFocusTimer() else startFocusTimer()
            4 -> try { startActivity(Intent(this, ModelManagerActivity::class.java)) } catch (e: Exception) { AppLogger.e(TAG, "navigateToFeature: ${e.message}") }
            5 -> changeWallpaper()
            6 -> showLogViewer()
            7 -> showTutorial()
            8 -> showAutoOperationDialog()
            9 -> triggerManualDiary()
            10 -> try {
                val intent = Intent(this, MemoryPoolActivity::class.java)
                intent.putExtra("persona_id", activePersonaId)
                startActivity(intent)
            } catch (e: Exception) { AppLogger.e(TAG, "navigateToFeature: ${e.message}") }
            11 -> showNewSessionDialog()
            12 -> stickerPickLauncher.launch(Intent(this, com.aicompanion.sticker.StickerActivity::class.java))
            13 -> startActivity(Intent(this, SkinShopActivity::class.java))
            14 -> try {
                val intent = Intent(this, com.aicompanion.ui.ChatHistoryActivity::class.java)
                intent.putExtra("scope", "persona")
                intent.putExtra("scopeId", activePersonaId)
                intent.putExtra("scopeName", cachedAiName ?: "星尘")
                startActivity(intent)
            } catch (e: Exception) { AppLogger.e(TAG, "navigateToFeature: ${e.message}") }
            15 -> try { startActivity(Intent(this, com.aicompanion.album.MemorialAlbumActivity::class.java)) } catch (e: Exception) { AppLogger.e(TAG, "navigateToFeature: ${e.message}") }
            16 -> showClearConfirmDialog()
        }
    }

    /** 显示功能面板（Compose FeaturePanel 可直接用 ModalBottomSheet，此方法备用） */
    fun showFeaturePanelCompat() = showFeaturePanel()

    /** 显示清空确认对话框 */
    private fun showClearConfirmDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("清空聊天记录")
            .setMessage("确定要清空所有聊天记录吗？此操作不可撤销。")
            .setPositiveButton("清空") { _, _ ->
                val oldSize = messages.size
                chatViewModel.clearMessages()
                chatAdapter?.notifyItemRangeRemoved(0, oldSize)
                saveChatHistory()
                chatViewModel.chatStorage?.deleteScope("persona", activePersonaId)
                Toast.makeText(this, "聊天记录已清空", Toast.LENGTH_SHORT).show()
                _messageVersion++
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 获取好感度信息：(当前值, 最大值, 等级文字） */
    override fun getAffectionInfo(): Triple<Int, Int, String> {
        val current = affectionManager?.affectionLevel ?: 0
        val max = 100
        val level = when (current) {
            in 0..19 -> "1"
            in 20..39 -> "2"
            in 40..59 -> "3"
            in 60..79 -> "4"
            else -> "5"
        }
        return Triple(current, max, "Lv.$level")
    }

    /** 获取天气信息:(图标+温度文字, 天数标签) */
    override fun getWeatherInfo(): Pair<String, String> {
        return Pair(_weatherText, _daysText)
    }

    /** 获取 AI 名称 */
    override fun getAiName(): String = cachedAiName ?: "星尘"

    /**
     * 获取 AI 头像路径（用于 ChatScreen 气泡头像显示）
     *
     * 三轨制存储的统一兜底：
     * 1) cachedAiAvatarPath（从 persona_data_{personaId}.persona_avatar_path 读到的缓存）
     * 2) AvatarManager.getAiAvatarPath（从 avatar_data SP 读 ai_avatar_{personaId}）
     * 3) Persona.avatarPath（角色模型字段）
     *
     * 之前只读 1)，导致用户在档案里换过头像后聊天气泡仍不显示。
     */
    override fun getAiAvatarPath(): String? {
        // 使用 CacheManager 管理 TTL 缓存
        if (cacheManager.isValid("aiAvatar", com.aicompanion.util.CacheManager.AVATAR_TTL_MS)) {
            return aiAvatarCache
        }
        val result: String? = run {
            // 1) 优先用缓存（onResume 时会刷新）
            cachedAiAvatarPath?.takeIf { it.isNotBlank() && File(it).exists() }?.let { return@run it }

            // 2) AvatarManager 兜底（统一 SP 来源）
            val fromAvatarManager = com.aicompanion.util.AvatarManager
                .getAiAvatarPath(this, activePersonaId)
                .takeIf { it.isNotBlank() && File(it).exists() }
            if (fromAvatarManager != null) {
                cachedAiAvatarPath = fromAvatarManager
                return@run fromAvatarManager
            }

            // 3) Persona 模型字段兜底
            try {
                val persona = com.aicompanion.persona.PersonaManager(this).let { pm ->
                    pm.load()
                    pm.getPersona(activePersonaId)
                }
                persona?.avatarPath?.takeIf { it.isNotBlank() && File(it).exists() }?.let { return@run it }
            } catch (_: Exception) {}

            null
        }
        cacheManager.updateAiAvatar(result)
        return result
    }
    override fun getWallpaperPath(): String? {
        // 使用 CacheManager 管理 TTL 缓存
        if (cacheManager.isValid("wallpaper", com.aicompanion.util.CacheManager.WALLPAPER_TTL_MS)) {
            return wallpaperCache
        }
        val path = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("chat_background", null)?.takeIf { it.isNotBlank() && File(it).exists() }
        cacheManager.updateWallpaper(path)
        return path
    }

    /** 失效壁纸缓存（换壁纸后调用） */
    fun invalidateWallpaperCache() {
        cacheManager.invalidate("wallpaper")
    }

    /** 获取相处天数 */
    override fun getDaysTogether(): Int {
        // 按日期缓存（一天内值不变），使用 CacheManager 管理
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        if (daysCacheDate == today && daysCache > 0) {
            return daysCache
        }
        val firstLaunch = getSharedPreferences("app_prefs", MODE_PRIVATE).getLong("first_launch_time", 0)
        val days = if (firstLaunch == 0L) 1
        else maxOf(1, ((System.currentTimeMillis() - firstLaunch) / (1000 * 60 * 60 * 24)).toInt())
        cacheManager.updateDays(days, today)
        return days
    }

    // ===== AppHost 接口实现 =====
    override fun provideChatMessages(): List<ChatMessage> = chatMessages
    override fun sendMessage(text: String) = composeSendMessage(text)
    override fun startVoice() = startVoiceRecording()
    override fun stopVoice() = stopVoiceRecording()
    override fun isVoiceRecording(): Boolean = isVoiceRecording
    override fun onFeatureClick(index: Int) = navigateToFeature(index)
    override fun getMessageVersion(): Int = _messageVersion
    override val messageVersionState: androidx.compose.runtime.State<Int> get() = _messageVersionState
    override fun isTyping(): Boolean = _isTyping
    override val isTypingState: androidx.compose.runtime.State<Boolean> get() = _isTypingState
    override fun isLoading(): Boolean = _isLoading
    override val isLoadingState: androidx.compose.runtime.State<Boolean> get() = _isLoadingState
    override val dataVersionState: androidx.compose.runtime.State<Int> get() = _dataVersionState

    /** 切换消息收藏 */
    override fun toggleFavorite(msg: ChatMessage) {
        val pos = messages.indexOfFirst { it.id == msg.id }
        if (pos in messages.indices) {
            val fm = favoriteManager
            if (fm != null) {
                if (fm.isFavorited(msg.id)) {
                    fm.removeFavorite(msg.id)
                    msg.isFavorited = false
                    Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show()
                } else {
                    fm.addFavorite(msg)
                    msg.isFavorited = true
                    Toast.makeText(this, "已收藏", Toast.LENGTH_SHORT).show()
                }
                saveChatHistory()
                chatAdapter?.notifyItemChanged(pos)
                _messageVersion++
            }
        }
    }

    /** 设置消息反馈（点赞=true, 踩=false） */
    override fun setFeedback(position: Int, isLike: Boolean) {
        if (position < messages.size) {
            val msg = messages[position]
            if (!msg.isUser) {
                msg.feedback = if (isLike) 1 else -1
                saveMessageFeedback(position, if (isLike) 1 else -1)
                val ach = achievementManager?.updateProgress("feedback", getTotalPositiveFeedback())
                if (ach != null) showAchievementUnlock(ach)
                if (isLike) { affectionManager?.addAffection(1); updateAffectionDisplay(); checkAiMomentTrigger() }
                _messageVersion++
            }
        }
    }

    /** 引用消息（Compose 引用回复功能用） */
    fun setQuotedMessage(msg: ChatMessage?) {
        quotedMessage = msg
    }

    /** 获取当前引用的消息 */
    fun getQuotedMessage(): ChatMessage? = quotedMessage

    /** 选择图片上传（Compose 用） */
    override fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        imageUploadLauncher.launch(intent)
    }

    /** 选择 AI 头像（ProfileScreen 档案页换头像） */
    override fun pickAiAvatar() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        pickAiAvatarLauncher.launch(intent)
    }

    /** 选择用户头像（ProfileScreen 档案页换头像） */
    override fun pickUserAvatar() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        pickUserAvatarLauncher.launch(intent)
    }

    /** 获取用户头像路径（ProfileScreen 显示用） */
    override fun getUserAvatarPath(): String? {
        return com.aicompanion.util.AvatarManager
            .getUserAvatarPath(this, activePersonaId)
            .takeIf { it.isNotBlank() && File(it).exists() }
    }

    /** 选择表情包（Compose 用） */
    override fun pickSticker() {
        stickerPickLauncher.launch(Intent(this, com.aicompanion.sticker.StickerActivity::class.java))
    }

    /** 打开电话通话页面（Compose 导航） */
    override fun phoneCall() {
        try {
            // 获取真实角色名称
            val realName = try {
                val pm = com.aicompanion.persona.PersonaManager(this)
                pm.load()
                val p = pm.getPersona(activePersonaId)
                if (p != null && p.name.isNotBlank() && p.name != "星尘") p.name
                else cachedAiName ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("ai_name", "") ?: ""
            } catch (_: Exception) { cachedAiName ?: "" }

            // 初始化通话状态
            _callPersonaName = realName.ifBlank { "星尘" }
            _isCallActive = true
            _callStartTime = System.currentTimeMillis()
            _callStatus = "通话中"
            _callTranscript = ""
            _isCallMuted = false
            _isCallSpeakerOn = true
            _callWaveformMode = 2 // AI_SPEAKING (打招呼)

            // 配置 AudioManager 进入通话模式：保存原状态、开扬声器、调到通话音量
            callAudioManager?.let { am ->
                try {
                    _callSavedSpeakerphoneOn = am.isSpeakerphoneOn
                    _callSavedMicMuted = am.isMicrophoneMute
                    am.mode = AudioManager.MODE_IN_COMMUNICATION
                    am.isSpeakerphoneOn = true
                    am.isMicrophoneMute = false
                    // 调整到合适通话音量（最大值的 70%）
                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                    am.setStreamVolume(
                        AudioManager.STREAM_VOICE_CALL,
                        (maxVol * 0.7f).toInt().coerceIn(0, maxVol),
                        0,
                    )
                } catch (e: Exception) {
                    AppLogger.w(TAG, "AudioManager setup: ${e.message}")
                }
            }

            milestoneManager?.recordMilestone("first_call", "第一次通话", "第一次和星尘打电话", "call")

            // 导航到 Compose 通话页面（替代旧的 PhoneCallActivity）
            try {
                navController?.navigate(com.aicompanion.ui.navigation.StradustDestinations.PHONE_CALL)
            } catch (e: Exception) {
                // NavHost 不可用时回退到 Activity
                AppLogger.w(TAG, "NavHost不可用，回退到PhoneCallActivity: ${e.message}")
                val intent = Intent(this, com.aicompanion.ui.PhoneCallActivity::class.java)
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_PERSONA_ID, activePersonaId)
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_PERSONA_NAME, _callPersonaName)
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_SCOPE, "persona")
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_SCOPE_ID, activePersonaId)
                startActivity(intent)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "phoneCall: ${e.message}")
        }
    }

    /** 获取群聊列表数据（用于 GroupChatListScreen Compose 页面） */
    override fun getGroupChatList(): List<com.aicompanion.ui.groupchat.GroupChatInfo> {
        return try {
            val gcm = groupChatManager ?: return emptyList()
            // 重新加载最新数据
            gcm.load()
            gcm.getAllGroups().map { group ->
                com.aicompanion.ui.groupchat.GroupChatInfo(
                    id = group.id,
                    name = group.name.ifEmpty { "未命名群" },
                    lastMessage = group.lastMessagePreview,
                    memberCount = group.memberPersonaIds.size,
                    timestamp = formatTimestamp(group.lastMessageTime),
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getGroupChatList error: ${e.message}")
            emptyList()
        }
    }

    /** 获取指定群聊的消息列表（用于 GroupChatScreen） */
    override fun getGroupMessages(groupId: String): List<com.aicompanion.ui.groupchat.GroupMessage> {
        // 从 GroupChatManager 读取真实消息；空群聊时返回系统欢迎消息
        return try {
            val gcm = groupChatManager ?: return emptyList()
            val group = gcm.getGroup(groupId) ?: return emptyList()
            val oldMessages = gcm.getMessages(groupId)
            if (oldMessages.isNotEmpty()) {
                oldMessages.map { msg ->
                    com.aicompanion.ui.groupchat.GroupMessage(
                        id = msg.id,
                        text = msg.text,
                        senderName = msg.senderName,
                        isUser = msg.isUser,
                        isSystem = false,
                        time = msg.time,
                    )
                }
            } else {
                // 空群聊时返回一条欢迎系统消息
                listOf(
                    com.aicompanion.ui.groupchat.GroupMessage(
                        id = "sys_welcome",
                        text = "欢迎来到「${group.name}」群聊",
                        senderName = "系统",
                        isUser = false,
                        isSystem = true,
                        time = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date()),
                    )
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getGroupMessages error: ${e.message}")
            emptyList()
        }
    }

    /** 发送群聊消息（用于 GroupChatScreen）
     *
     * 实现用户消息持久化 + 触发 AI 群聊回复链路：
     * 1. 写入 GroupChatManager 与 ChatStorage
     * 2. 在后台协程中调用每个群成员 CharacterCard 的 LLM 生成回复
     * 3. 通过 notifyDataChanged() 触发 UI 重组
     */
    override fun sendGroupMessage(groupId: String, text: String) {
        try {
            val gcm = groupChatManager ?: run {
                Toast.makeText(this, "群聊未初始化", Toast.LENGTH_SHORT).show()
                return
            }
            val group = gcm.getGroup(groupId) ?: run {
                Toast.makeText(this, "群聊不存在", Toast.LENGTH_SHORT).show()
                return
            }
            if (text.isBlank()) return

            val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            // 1. 构造并持久化用户消息
            val userMsg = com.aicompanion.groupchat.GroupMessage(
                senderPersonaId = "user",
                senderName = "我",
                text = text,
                time = time,
                isUser = true,
            )
            gcm.addMessage(groupId, userMsg)
            try {
                val storage = com.aicompanion.storage.ChatHistoryStorage(this)
                storage.addMessage("group", groupId, com.aicompanion.storage.StoredMessage(
                    id = userMsg.id, text = userMsg.text, time = userMsg.time,
                    isUser = true, timestamp = userMsg.timestamp,
                    senderName = userMsg.senderName, senderPersonaId = "user",
                    emotion = "neutral",
                ))
            } catch (e: Exception) {
                AppLogger.e(TAG, "sendGroupMessage chatStorage: ${e.message}")
            }

            // 2. 触发 UI 重组（让用户气泡先显示）
            notifyDataChanged()

            // 3. 启动 AI 回复链路：根据发言模式选择回复成员
            val personaIds = group.memberPersonaIds
            if (personaIds.isEmpty()) {
                AppLogger.w(TAG, "sendGroupMessage: 群聊无成员，跳过 AI 回复")
                return
            }
            val triggerText = text
            val groupObj = group
            val appCtx = this@MainActivity

            memoryScope.launch {
                try {
                    // 进入 AI 回复链路：标记群聊正在输入
                    _groupTypingStates[groupId] = true
                    withContext(Dispatchers.Main) { notifyDataChanged() }

                    val cm = com.aicompanion.character.CharacterCardManager(appCtx)
                    val allCards = cm.getAllCards()

                    // 根据发言模式筛选/排序回复成员
                    val mentionedIds = parseMentionedPersonaIds(triggerText, allCards)
                        .filter { it in personaIds }

                    val speakerIds = when (groupObj.speakMode) {
                        "manual" -> {
                            // 手动模式：用户选中的成员 + @提及的成员
                            val manualIds = (_manualSelectedIds[groupId] ?: emptySet())
                                .filter { it in personaIds }
                            // @提及优先，加上手动选中的
                            (mentionedIds + manualIds).distinct()
                        }
                        "ai_judge" -> {
                            // AI判定模式：@提及的成员必须说话，其他成员由 AI 判断
                            // 这里先返回所有成员，后续在循环中用 AI 判断
                            (mentionedIds + personaIds).distinct()
                        }
                        "reactive" -> {
                            // 响应式：仅被 @提及的成员回复；无 @提及时随机选一位
                            if (mentionedIds.isNotEmpty()) mentionedIds
                            else listOf(personaIds.random())
                        }
                        "random" -> personaIds.shuffled().toList()
                        else -> personaIds // round_robin / auto / 未知：按原顺序（全都说）
                    }

                    for (personaId in speakerIds) {
                        val card = allCards.find { it.id == personaId } ?: continue
                        val isMentioned = personaId in mentionedIds

                        // AI判定模式：非 @提及的成员，用 AI 判断是否说话
                        if (groupObj.speakMode == "ai_judge" && !isMentioned) {
                            val shouldSpeak = shouldGroupPersonaSpeak(card, groupObj, triggerText)
                            if (!shouldSpeak) continue
                        }

                        // 手动模式：非 @提及且非手动选中的成员，跳过
                        if (groupObj.speakMode == "manual" && !isMentioned) {
                            val manualIds = _manualSelectedIds[groupId] ?: emptySet()
                            if (personaId !in manualIds) continue
                        }

                        val reply = generateGroupPersonaReply(card, groupObj, triggerText)
                        if (reply.isNullOrBlank()) continue

                        val aiTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date())
                        val aiMsg = com.aicompanion.groupchat.GroupMessage(
                            senderPersonaId = card.id,
                            senderName = card.name,
                            text = reply,
                            time = aiTime,
                            isUser = false,
                        )
                        gcm.addMessage(groupId, aiMsg)
                        try {
                            val storage = com.aicompanion.storage.ChatHistoryStorage(appCtx)
                            storage.addMessage("group", groupId, com.aicompanion.storage.StoredMessage(
                                id = aiMsg.id, text = aiMsg.text, time = aiMsg.time,
                                isUser = false, timestamp = aiMsg.timestamp,
                                senderName = aiMsg.senderName, senderPersonaId = card.id,
                                emotion = "neutral",
                            ))
                        } catch (_: Exception) {}

                        // 每条 AI 回复后触发 UI 刷新
                        withContext(Dispatchers.Main) { notifyDataChanged() }
                        // 避免连发过快，给用户阅读时间
                        kotlinx.coroutines.delay(800)
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "sendGroupMessage AI chain: ${e.message}")
                } finally {
                    // AI 回复链路结束：清除打字状态
                    _groupTypingStates[groupId] = false
                    withContext(Dispatchers.Main) { notifyDataChanged() }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendGroupMessage error: ${e.message}")
            Toast.makeText(this, "发送失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 解析文本中 @提及的角色 ID 列表（匹配格式：@角色名） */
    private fun parseMentionedPersonaIds(
        text: String,
        cards: List<com.aicompanion.models.CharacterCard>,
    ): List<String> {
        if (text.isBlank() || cards.isEmpty()) return emptyList()
        val mentioned = mutableListOf<String>()
        // 按 name 长度降序匹配，避免短名前缀误命中
        for (card in cards.sortedByDescending { it.name.length }) {
            if (card.name.isBlank()) continue
            if (text.contains("@${card.name}")) {
                mentioned.add(card.id)
            }
        }
        return mentioned.distinct()
    }

    /**
     * AI判定模式：判断指定角色是否应该在当前群聊上下文中说话
     *
     * 用 LLM 判断，返回 true=说话，false=沉默
     */
    private suspend fun shouldGroupPersonaSpeak(
        card: com.aicompanion.models.CharacterCard,
        group: com.aicompanion.groupchat.GroupChat,
        userText: String,
    ): Boolean {
        return try {
            val sm = settingsManager ?: return false
            if (sm.chatApiUrl.isBlank() || sm.chatApiKey.isBlank()) return false

            val client = com.aicompanion.network.ApiClient(
                sm.chatApiUrl, sm.chatApiKey, sm.chatModel,
                sm.llmTemperature, sm.llmTopP, sm.llmFrequencyPenalty,
                sm.llmPresencePenalty, sm.llmMaxTokens, sm.apiProvider,
            )

            val otherNames = group.memberPersonaIds
                .filter { it != card.id }
                .mapNotNull { pid ->
                    val cm = com.aicompanion.character.CharacterCardManager(this)
                    cm.getAllCards().find { it.id == pid }?.name
                }

            val prompt = buildString {
                append("你正在群聊中，你的名字是「${card.name}」。\n")
                append("群成员：${card.name}${if (otherNames.isNotEmpty()) "、" + otherNames.joinToString("、") else ""}\n")
                append("用户说：$userText\n\n")
                append("请判断你（${card.name}）是否应该在此时发言。")
                append("考虑：这句话是否与你相关？是否需要你回应？你是否对这个话题感兴趣？")
                append("只回复「说话」或「沉默」")
            }

            val response = client.sendSimplePrompt(prompt, "只回「说话」或「沉默」")
            val text = response?.text?.trim()?.lowercase() ?: "沉默"
            text.contains("说话") || text.contains("speak") || text.contains("yes") || text.contains("说")
        } catch (e: Exception) {
            AppLogger.w(TAG, "shouldGroupPersonaSpeak error: ${e.message}")
            false
        }
    }

    /** 生成单个 CharacterCard 在群聊场景下的回复（同步调用 LLM） */
    private fun generateGroupPersonaReply(
        card: com.aicompanion.models.CharacterCard,
        group: com.aicompanion.groupchat.GroupChat,
        triggerText: String,
    ): String? {
        return try {
            val sm = settingsManager ?: return null
            val apiUrl = sm.chatApiUrl
            val apiKey = sm.chatApiKey
            val model = sm.chatModel
            if (apiUrl.isBlank() || apiKey.isBlank()) {
                AppLogger.w(TAG, "generateGroupPersonaReply: API 未配置")
                return null
            }
            val cm = com.aicompanion.character.CharacterCardManager(this)
            val allCards = cm.getAllCards()
            val otherNames = group.memberPersonaIds
                .filter { it != card.id }
                .mapNotNull { pid ->
                    allCards.find { it.id == pid }?.name
                }
            val systemPrompt = buildString {
                append("你是「${card.name}」，正在一个多人群聊中。\n")
                if (card.personality.isNotBlank()) append("性格：${card.personality}\n")
                if (card.description.isNotBlank()) append("简介：${card.description}\n")
                if (card.systemPrompt.isNotBlank()) append("设定：${card.systemPrompt}\n")
                if (otherNames.isNotEmpty()) append("群里还有：${otherNames.joinToString("、")}\n")
                if (group.relationshipSetting.isNotBlank()) {
                    append("成员关系：${group.relationshipSetting}\n")
                }
                append("请用 1-2 句话回复用户的话，保持角色一致。不要复读，不要@他人，直接发言。")
            }
            val req = org.json.JSONObject().apply {
                put("model", model)
                put("messages", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("role", "system"); put("content", systemPrompt)
                    })
                    put(org.json.JSONObject().apply {
                        put("role", "user"); put("content", triggerText)
                    })
                })
                put("temperature", sm.llmTemperature)
                put("max_tokens", sm.llmMaxTokens.coerceAtMost(1024))
            }
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = req.toString().toRequestBody(mediaType)
            val rq = okhttp3.Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
            okhttp3.OkHttpClient().newBuilder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(40, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(rq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        AppLogger.e(TAG, "generateGroupPersonaReply HTTP ${resp.code}")
                        return null
                    }
                    val raw = resp.body?.string() ?: return null
                    val json = org.json.JSONObject(raw)
                    json.optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("content")?.trim()
                        ?.takeIf { it.isNotBlank() }
                }
        } catch (e: Exception) {
            AppLogger.e(TAG, "generateGroupPersonaReply: ${e.message}")
            null
        }
    }

    /** 创建新群聊：返回新群 ID，失败返回 null */
    override fun createGroup(name: String, memberPersonaIds: List<String>): String? {
        return try {
            val gcm = groupChatManager ?: return null
            val trimmedName = name.trim().ifBlank { "未命名群" }
            val members = if (memberPersonaIds.isEmpty()) {
                // 默认包含全部角色（与 HomeScreen 数据源一致）
                com.aicompanion.character.CharacterCardManager(this).getAllCards().map { it.id }
            } else memberPersonaIds
            val group = com.aicompanion.groupchat.GroupChat(
                name = trimmedName,
                memberPersonaIds = members,
            )
            gcm.addGroup(group)
            AppLogger.i(TAG, "createGroup: ${group.id} name=$trimmedName members=${members.size}")
            notifyDataChanged()
            group.id
        } catch (e: Exception) {
            AppLogger.e(TAG, "createGroup error: ${e.message}")
            null
        }
    }

    /** 删除指定群聊 */
    override fun deleteGroup(groupId: String) {
        try {
            val gcm = groupChatManager ?: return
            gcm.deleteGroup(groupId)
            AppLogger.i(TAG, "deleteGroup: $groupId")
            notifyDataChanged()
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteGroup error: ${e.message}")
        }
    }

    /** 获取指定群聊的成员名称列表 */
    override fun getGroupMemberNames(groupId: String): List<String> {
        return try {
            val gcm = groupChatManager ?: return emptyList()
            val group = gcm.getGroup(groupId) ?: return emptyList()
            // 使用 CharacterCardManager（与 HomeScreen 数据源一致）
            val cm = com.aicompanion.character.CharacterCardManager(this)
            val cards = cm.getAllCards()
            group.memberPersonaIds.mapNotNull { pid ->
                cards.find { it.id == pid }?.name
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getGroupMemberNames error: ${e.message}")
            emptyList()
        }
    }

    /** 获取指定群聊的打字状态（派生 State：map 改变时自动重组） */
    override fun getGroupTypingState(groupId: String): androidx.compose.runtime.State<Boolean> {
        return androidx.compose.runtime.derivedStateOf { _groupTypingStates[groupId] ?: false }
    }

    /** 群聊手动选择状态：groupId → 选中的 personaId 集合 */
    private val _manualSelectedIds = mutableMapOf<String, Set<String>>()

    /** 获取指定群聊的发言模式 */
    override fun getGroupSpeakMode(groupId: String): String {
        return try {
            val gcm = groupChatManager ?: return "round_robin"
            val group = gcm.getGroup(groupId) ?: return "round_robin"
            group.speakMode.ifBlank { "round_robin" }
        } catch (_: Exception) { "round_robin" }
    }

    /** 设置指定群聊的发言模式 */
    override fun setGroupSpeakMode(groupId: String, mode: String) {
        try {
            val gcm = groupChatManager ?: return
            val group = gcm.getGroup(groupId) ?: return
            gcm.updateGroup(group.copy(speakMode = mode))
            notifyDataChanged()
        } catch (e: Exception) {
            AppLogger.e(TAG, "setGroupSpeakMode error: ${e.message}")
        }
    }

    /** 获取指定群聊的成员 personaId 列表 */
    override fun getGroupMemberPersonaIds(groupId: String): List<String> {
        return try {
            val gcm = groupChatManager ?: return emptyList()
            val group = gcm.getGroup(groupId) ?: return emptyList()
            group.memberPersonaIds
        } catch (_: Exception) { emptyList() }
    }

    /** 获取手动模式下已选中的成员 */
    override fun getManualSelectedIds(groupId: String): Set<String> {
        return _manualSelectedIds[groupId] ?: emptySet()
    }

    /** 设置手动模式下已选中的成员 */
    override fun setManualSelectedIds(groupId: String, ids: Set<String>) {
        _manualSelectedIds[groupId] = ids
        notifyDataChanged()
    }

    /** 格式化时间戳为可读字符串 */
    private fun formatTimestamp(timestamp: Long): String {
        return try {
            val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        } catch (e: Exception) {
            ""
        }
    }

    // ===== HomeScreen 相关的 AppHost 方法 =====

    /** 获取所有角色卡片（用于 HomeScreen 展示） */
    override fun getPersonas(): List<com.aicompanion.ui.home.PersonaCard> {
        // 使用 CacheManager 管理 TTL 缓存
        if (cacheManager.isValid("personas", com.aicompanion.util.CacheManager.PERSONAS_TTL_MS)) {
            return personasCache
        }
        return try {
            // 从 CharacterCardManager 读取数据（与 CharacterCardScreen 数据源一致）
            val cardManager = com.aicompanion.character.CharacterCardManager(this)
            val cards = cardManager.getAllCards()
            val result = cards.map { card ->
                // 获取该角色的好感度管理器
                val am = com.aicompanion.affection.AffectionManager(this, card.id)
                com.aicompanion.ui.home.PersonaCard(
                    id = card.id,
                    name = card.name,
                    avatarPath = com.aicompanion.util.AvatarManager.getAiAvatarPath(this, card.id, card.avatarPath).ifBlank { null },
                    description = card.personality.ifBlank { card.description.ifBlank { "暂无简介" } },
                    lastChatTime = formatRelativeTime(card.createdAt),
                    messageCount = getMessageCount(card.id),
                    affectionLevel = am.affectionLevel,
                )
            }
            cacheManager.updatePersonas(result)
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "getPersonas error: ${e.message}")
            emptyList()
        }
    }

    /** 失效角色列表缓存（添加/删除角色后调用） */
    fun invalidatePersonasCache() {
        cacheManager.invalidate("personas")
    }

    /** 获取指定角色的消息数量 */
    private fun getMessageCount(personaId: String): Int {
        return try {
            val prefsName = com.aicompanion.persona.PersonaManager(this).getChatPrefsName(personaId)
            val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
            val json = prefs.getString("messages", "[]") ?: "[]"
            val arr = org.json.JSONArray(json)
            arr.length()
        } catch (e: Exception) {
            0
        }
    }

    /** 格式化相对时间（如：2小时前、昨天） */
    private fun formatRelativeTime(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0) return "暂无聊天"
        return try {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            when {
                diff < 60_000 -> "刚刚"
                diff < 3_600_000 -> "${diff / 60_000}分钟前"
                diff < 86_400_000 -> "${diff / 3_600_000}小时前"
                diff < 172_800_000 -> "昨天"
                diff < 604_800_000 -> "${diff / 86_400_000}天前"
                else -> {
                    val sdf = java.text.SimpleDateFormat("MM-dd", Locale.getDefault())
                    sdf.format(java.util.Date(timestamp))
                }
            }
        } catch (e: Exception) {
            "暂无聊天"
        }
    }

    /** 显示添加角色对话框/页面（Compose版 CharacterCardScreen，与主UI风格一致） */
    override fun showAddPersonaDialog() {
        try {
            navController?.navigate(com.aicompanion.ui.navigation.StradustDestinations.CHARACTER_CARD_CREATE)
                ?: Toast.makeText(this, "导航失败", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "创建角色失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 将 CharacterCard 数据同步到 persona_data_{personaId} SharedPreferences
     *
     * 根因：聊天系统（PromptBuilder、getAiName、rebuildPersonaDependentComponents 等）
     * 从 persona_data_{id} SharedPreferences 和 PersonaManager 读取角色数据，
     * 而 HomeScreen 的角色列表来自 CharacterCardManager。两套数据源不同步
     * 导致：顶栏名字显示"星尘"、AI 回复时用了错误角色的设定。
     *
     * 此方法在导航到私聊前调用，确保 persona_data 中有该角色的完整数据。
     */
    private fun syncCharacterCardToPersonaData(personaId: String) {
        try {
            val cm = com.aicompanion.character.CharacterCardManager(this)
            val card = cm.getAllCards().find { it.id == personaId } ?: return

            // 1) 写入 persona_data_{personaId} SharedPreferences
            val prefs = getSharedPreferences("persona_data_$personaId", MODE_PRIVATE)
            prefs.edit().apply {
                putString("persona_name", card.name)
                putString("persona_desc", card.description)
                putString("persona_personality", card.personality)
                putString("persona_avatar_path", card.avatarPath)
                putString("persona_greeting", card.firstMes)
                // systemPrompt 存为 prompt 字段（PromptBuilder 会读取）
                putString("persona_prompt", card.systemPrompt)
                // scenario 存为 world_setting（PromptBuilder 未直接读，但 recoverFromSharedPreferences 会用）
                putString("world_setting", card.scenario)
            }.apply()

            // 2) 同步到 PersonaManager 索引（确保 getPersona(id) 能找到）
            val pm = com.aicompanion.persona.PersonaManager(this)
            pm.load()
            val existing = pm.getPersona(personaId)
            val persona = com.aicompanion.persona.Persona(
                id = personaId,
                name = card.name,
                prompt = card.systemPrompt,
                avatarPath = card.avatarPath,
                personality = card.personality,
                description = card.description,
            )
            if (existing == null) {
                pm.addPersona(persona)
            } else {
                pm.updatePersona(personaId) { existing.copy(
                    name = card.name.ifBlank { existing.name },
                    prompt = card.systemPrompt.ifBlank { existing.prompt },
                    avatarPath = card.avatarPath.ifBlank { existing.avatarPath },
                    personality = card.personality.ifBlank { existing.personality },
                    description = card.description.ifBlank { existing.description },
                ) }
            }

            AppLogger.i(TAG, "syncCharacterCardToPersonaData: id=$personaId name=${card.name}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "syncCharacterCardToPersonaData error: ${e.message}")
        }
    }

    /** 导航到指定角色的聊天界面 */
    override fun navigateToPersonaChat(personaId: String) {
        try {
            // 先将 CharacterCard 数据同步到 persona_data（确保私聊设定隔离）
            syncCharacterCardToPersonaData(personaId)

            // 设置当前活跃角色
            val pm = com.aicompanion.persona.PersonaManager(this)
            pm.load()
            pm.setActivePersona(personaId)

            // 刷新 activePersonaId 并重建角色依赖组件
            refreshActivePersonaId()
            rebuildPersonaDependentComponents()

            // 触发 Compose 重组
            _messageVersion++

            // 通过 NavController 导航到聊天页面（不重启 Activity）
            navController?.navigate(com.aicompanion.ui.navigation.StradustDestinations.CHAT)
        } catch (e: Exception) {
            AppLogger.e(TAG, "navigateToPersonaChat error: ${e.message}")
            Toast.makeText(this, "切换角色失败", Toast.LENGTH_SHORT).show()
        }
    }

    /** 打开动态页面 */
    override fun openMoments() {
        try {
            startActivity(Intent(this, com.aicompanion.moments.MomentsActivity::class.java))
        } catch (e: Exception) {
            AppLogger.e(TAG, "openMoments error: ${e.message}")
        }
    }

    /** 打开日记页面（需指定角色 ID） */
    override fun openDiary(personaId: String) {
        try {
            // 设置当前角色，确保日记数据对应
            if (personaId.isNotBlank()) {
                val pm = com.aicompanion.persona.PersonaManager(this)
                pm.load()
                if (pm.getPersona(personaId) != null) {
                    pm.setActivePersona(personaId)
                    refreshActivePersonaId()
                }
            }
            // 通过 NavController 导航到 Compose 日记页（DIARY 是底部 tab 之一）
            // 使用底部 tab 统一导航配置，避免栈堆积和状态丢失
            val nc = navController ?: return
            nc.navigate(com.aicompanion.ui.navigation.StradustDestinations.DIARY) {
                popUpTo(nc.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "openDiary error: ${e.message}")
        }
    }

    /** 打开角色档案页面（需指定角色 ID） */
    override fun openProfile(personaId: String) {
        try {
            // 设置当前活跃角色，确保 ProfileScreen 展示对应角色数据
            val pm = com.aicompanion.persona.PersonaManager(this)
            pm.load()
            if (personaId.isNotBlank() && pm.getPersona(personaId) != null) {
                pm.setActivePersona(personaId)
            }

            // 刷新 activePersonaId 并重建角色依赖组件
            refreshActivePersonaId()
            rebuildPersonaDependentComponents()

            // 通过 NavController 导航到个人中心页面（PROFILE 是底部 tab 之一）
            // 关键修复：使用底部 tab 统一导航配置（popUpTo + saveState + restoreState + launchSingleTop）
            // 否则会出现"点档案跳到我的，点回主页却一直在档案页"的导航错乱
            val nc = navController ?: return
            nc.navigate(com.aicompanion.ui.navigation.StradustDestinations.PROFILE) {
                popUpTo(nc.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "openProfile error: ${e.message}")
            Toast.makeText(this, "打开档案失败", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== VirtualWorldScreen 相关的 AppHost 方法 =====

    /**
     * 获取虚拟世界状态（从 VirtualWorldManager 读取真实数据）
     * 如果虚拟世界未初始化或不可用，返回默认值
     */
    override fun getVirtualWorldState(): com.aicompanion.ui.virtualworld.VirtualWorldInfo {
        return try {
            val worldManager = findActiveVirtualWorld()
            if (worldManager == null) {
                // 虚拟世界不可用，返回默认状态
                com.aicompanion.ui.virtualworld.VirtualWorldInfo(
                    day = 1,
                    hour = "08:00",
                    location = "起始之地",
                    weather = "晴朗",
                    mood = "平静",
                    isSimulating = false,
                    events = emptyList(),
                )
            } else {
                // 从 VirtualWorldManager 读取真实数据
                val state = worldManager.state
                val events = worldManager.getStoryEvents().takeLast(20) // 最近20条事件

                com.aicompanion.ui.virtualworld.VirtualWorldInfo(
                    day = state.dayCount,
                    hour = String.format("%02d:%02d", state.hourOfDay, state.minuteOfHour),
                    location = state.currentLocation,
                    weather = state.currentWeather,
                    mood = state.currentMood,
                    isSimulating = worldManager.isRunning,
                    events = events,
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getVirtualWorldState error: ${e.message}")
            // 出错时返回默认值，保证 UI 不崩溃
            com.aicompanion.ui.virtualworld.VirtualWorldInfo()
        }
    }

    /**
     * 切换虚拟场景
     * sceneIndex: 0=卧室, 1=花园, 2=书房, 3=厨房
     */
    override fun changeVirtualScene(sceneIndex: Int) {
        try {
            val worldManager = findActiveVirtualWorld() ?: run {
                Toast.makeText(this, "请先启动虚拟世界", Toast.LENGTH_SHORT).show()
                return
            }

            // 场景名称映射
            val sceneNames = listOf("卧室", "花园", "书房", "厨房")
            if (sceneIndex in sceneNames.indices) {
                val newLocation = sceneNames[sceneIndex]
                val currentState = worldManager.state
                worldManager.state = currentState.copy(currentLocation = newLocation)
                AppLogger.i(TAG, "切换虚拟场景到: $newLocation")
                notifyDataChanged() // 触发 VirtualWorldScreen 重组
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "changeVirtualScene error: ${e.message}")
            Toast.makeText(this, "切换场景失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 切换模拟状态（开始/暂停推演）
     */
    override fun toggleVirtualSimulation() {
        try {
            val worldManager = findActiveVirtualWorld() ?: run {
                Toast.makeText(this, "请先配置并启用虚拟世界", Toast.LENGTH_SHORT).show()
                return
            }

            if (worldManager.isRunning) {
                // 暂停推演
                worldManager.isRunning = false
                virtualWorldCoordinator?.onPause() // 暂定时钟调度
                Toast.makeText(this, "推演已暂停", Toast.LENGTH_SHORT).show()
                AppLogger.i(TAG, "虚拟世界推演已暂停")
            } else {
                // 开始推演前检查前置条件
                if (!worldManager.hasChatModelConfigured()) {
                    Toast.makeText(this, "请先在设置中配置聊天API", Toast.LENGTH_LONG).show()
                    return
                }

                val config = worldManager.config
                if (config.getFullLore().isBlank()) {
                    Toast.makeText(this, "请先编辑世界观设定", Toast.LENGTH_LONG).show()
                    return
                }

                // 启动推演
                worldManager.isRunning = true
                worldManager.isEnabled = true
                if (worldManager.lastTickTime == 0L) {
                    worldManager.lastTickTime = System.currentTimeMillis()
                }
                virtualWorldCoordinator?.scheduleTick()
                Toast.makeText(this, "虚拟世界推演已启动", Toast.LENGTH_SHORT).show()
                AppLogger.i(TAG, "虚拟世界推演已启动")
            }
            notifyDataChanged() // 触发 VirtualWorldScreen 重组
        } catch (e: Exception) {
            AppLogger.e(TAG, "toggleVirtualSimulation error: ${e.message}")
            Toast.makeText(this, "操作失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 虚拟世界互动操作
     * action: interact(互动), feed(喂食), play(玩耍), photo(拍照)
     *
     * 实现逻辑：
     * - interact/feed/play: 更新 WorldState.currentMood，并触发一次 AI 短对话（异步）
     * - photo: 调起系统图库选择图片
     * 任何操作后调用 notifyDataChanged() 触发 VirtualWorldScreen 重组
     */
    override fun onVirtualWorldInteraction(action: String) {
        when (action) {
            "interact" -> {
                Toast.makeText(this, "与角色互动中...", Toast.LENGTH_SHORT).show()
                updateVwMoodAndChat("开心", "（用户主动与角色互动了一下）")
            }
            "feed" -> {
                Toast.makeText(this, "喂食成功~ 🍖", Toast.LENGTH_SHORT).show()
                updateVwMoodAndChat("满足", "（用户给角色喂了食物）")
            }
            "play" -> {
                Toast.makeText(this, "玩耍时间！🎮", Toast.LENGTH_SHORT).show()
                updateVwMoodAndChat("兴奋", "（用户陪角色玩耍了一会儿）")
            }
            "photo" -> {
                try {
                    val intent = Intent(Intent.ACTION_PICK)
                    intent.type = "image/*"
                    imageUploadLauncher.launch(intent)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "pick image for VW error: ${e.message}")
                }
            }
            else -> {
                AppLogger.w(TAG, "未知互动操作: $action")
            }
        }
        notifyDataChanged() // 触发 VirtualWorldScreen 重组
    }

    /**
     * 虚拟世界互动辅助：更新心情 + 触发 AI 短对话
     * @param newMood 新的心情文本（如"开心"/"满足"/"兴奋"）
     * @param triggerHint 触发 hint，作为 user 消息送给 AI
     */
    private fun updateVwMoodAndChat(newMood: String, triggerHint: String) {
        try {
            val worldManager = findActiveVirtualWorld() ?: return
            val cur = worldManager.state
            worldManager.state = cur.copy(currentMood = newMood)
            // state 的 setter 已自动持久化到 SharedPreferences

            // 异步触发一次 AI 对话（避免阻塞 UI）
            val appCtx = this@MainActivity
            memoryScope.launch {
                try {
                    val sm = settingsManager ?: return@launch
                    val apiUrl = sm.chatApiUrl
                    val apiKey = sm.chatApiKey
                    val model = sm.chatModel
                    if (apiUrl.isBlank() || apiKey.isBlank()) return@launch
                    val pm = com.aicompanion.persona.PersonaManager(appCtx)
                    val persona = pm.getActivePersona()
                    val systemPrompt = buildString {
                        append("你是「${persona.name}」，正在虚拟世界中与用户互动。")
                        if (persona.personality.isNotBlank()) append("性格：${persona.personality}。")
                        if (persona.speechStyle.isNotBlank()) append("说话风格：${persona.speechStyle}。")
                        append("请用 1-2 句话自然地回应，体现当前心情「$newMood」。")
                    }
                    val req = org.json.JSONObject().apply {
                        put("model", model)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply {
                                put("role", "system"); put("content", systemPrompt)
                            })
                            put(org.json.JSONObject().apply {
                                put("role", "user"); put("content", triggerHint)
                            })
                        })
                        put("temperature", sm.llmTemperature)
                        put("max_tokens", 256)
                    }
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val body = req.toString().toRequestBody(mediaType)
                    val rq = okhttp3.Request.Builder()
                        .url(apiUrl)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()
                    val reply = okhttp3.OkHttpClient().newBuilder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                        .newCall(rq).execute().use { resp ->
                            if (!resp.isSuccessful) return@use null
                            val raw = resp.body?.string() ?: return@use null
                            org.json.JSONObject(raw)
                                .optJSONArray("choices")?.optJSONObject(0)
                                ?.optJSONObject("message")?.optString("content")?.trim()
                        } ?: return@launch

                    // 将 AI 回复作为一条故事事件写入虚拟世界
                    val event = com.aicompanion.virtualworld.StoryEvent(
                        virtualDay = worldManager.state.dayCount,
                        virtualHour = worldManager.state.hourOfDay,
                        virtualMinute = worldManager.state.minuteOfHour,
                        content = reply,
                        speakerName = persona.name,
                        eventType = "dialogue",
                        summary = triggerHint,
                    )
                    worldManager.addStoryEvent(event)
                    withContext(Dispatchers.Main) { notifyDataChanged() }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "updateVwMoodAndChat AI: ${e.message}")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "updateVwMoodAndChat: ${e.message}")
        }
    }

    // ===== PhoneCallScreen Compose 桥接方法 =====

    override fun getCallPersonaName(): String = _callPersonaName

    override fun isCallActive(): Boolean = _isCallActive

    override fun getCallStatus(): String = _callStatus

    override fun getCallDurationMs(): Long {
        return if (_isCallActive && _callStartTime > 0) {
            System.currentTimeMillis() - _callStartTime
        } else 0L
    }

    override fun getCallTranscript(): String = _callTranscript

    override fun isCallMuted(): Boolean = _isCallMuted

    override fun isCallSpeakerOn(): Boolean = _isCallSpeakerOn

    override fun getCallWaveformMode(): Int = _callWaveformMode

    override fun hangUp() {
        _isCallActive = false
        _callStatus = "通话结束"
        _callWaveformMode = 0
        // 恢复 AudioManager 状态并退出通话模式
        callAudioManager?.let { am ->
            try {
                am.isSpeakerphoneOn = _callSavedSpeakerphoneOn
                am.isMicrophoneMute = _callSavedMicMuted
                am.mode = AudioManager.MODE_NORMAL
            } catch (e: Exception) {
                AppLogger.w(TAG, "AudioManager restore: ${e.message}")
            }
        }
        try {
            navController?.popBackStack()
        } catch (_: Exception) {}
    }

    override fun toggleCallMute() {
        _isCallMuted = !_isCallMuted
        _callWaveformMode = if (_isCallMuted) 3 else 1 // MUTED or LISTENING
        // 真实控制麦克风静音
        callAudioManager?.let { am ->
            try {
                am.isMicrophoneMute = _isCallMuted
            } catch (e: Exception) {
                AppLogger.w(TAG, "toggleCallMute: ${e.message}")
            }
        }
    }

    override fun toggleCallSpeaker() {
        _isCallSpeakerOn = !_isCallSpeakerOn
        // 真实控制扬声器开关
        callAudioManager?.let { am ->
            try {
                am.isSpeakerphoneOn = _isCallSpeakerOn
            } catch (e: Exception) {
                AppLogger.w(TAG, "toggleCallSpeaker: ${e.message}")
            }
        }
    }

    // ===== DiaryScreen 相关的 AppHost 方法 =====

    /** 获取日记列表 - 从 DiaryManager 读取并转换为 UI 层 DiaryEntry（5秒 TTL 缓存） */
    override fun getDiaryEntries(): List<com.aicompanion.ui.diary.DiaryEntry> {
        // 使用 CacheManager 管理 TTL 缓存
        if (cacheManager.isValid("diary", com.aicompanion.util.CacheManager.DIARY_TTL_MS)) {
            return diaryCache
        }
        return try {
            val manager = com.aicompanion.diary.DiaryManager(this, activePersonaId)
            val entries = manager.getAllDiaries()
            val result = entries.sortedByDescending { it.date }.map { entry ->
                // mood 映射：英文 → 中文
                val moodMap = mapOf(
                    "happy" to "开心", "sad" to "难过", "excited" to "兴奋",
                    "calm" to "平静", "sentimental" to "感性", "normal" to "普通"
                )
                // 解析日期
                val localDate = try {
                    java.time.LocalDate.parse(entry.date)
                } catch (e: Exception) {
                    java.time.LocalDate.now()
                }
                com.aicompanion.ui.diary.DiaryEntry(
                    id = entry.createdAt.hashCode(),
                    date = localDate,
                    mood = moodMap[entry.mood] ?: entry.mood,
                    moodEmoji = entry.moodEmoji,
                    content = entry.content.takeIf { it.isNotBlank() } ?: entry.title,
                    aiReplySummary = entry.title.takeIf { it.isNotBlank() && it != entry.content }
                        ?: "${entry.tags.joinToString("、")} · 好感度${entry.affectionLevel}",
                )
            }
            cacheManager.updateDiary(result)
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "getDiaryEntries: ${e.message}")
            emptyList()
        }
    }

    override fun addDiary(content: String) {
        try {
            val manager = com.aicompanion.diary.DiaryManager(this, activePersonaId)
            val ok = manager.saveUserDiary(content)
            if (ok) {
                Toast.makeText(this, "日记已保存", Toast.LENGTH_SHORT).show()
                AppLogger.i(TAG, "addDiary: 已保存用户日记")
                notifyDataChanged() // 触发 DiaryScreen 重组刷新列表（同时清除缓存）
            } else {
                // 今日已有日记（可能是 LLM 自动生成），提示用户使用编辑功能
                Toast.makeText(this, "今天已有日记，请使用编辑功能修改", Toast.LENGTH_SHORT).show()
                AppLogger.w(TAG, "addDiary: 今日已有日记，保存失败")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "addDiary: ${e.message}")
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 更新已有日记：由 Compose 内联编辑对话框触发 */
    override fun updateDiary(id: Long, content: String) {
        try {
            val manager = com.aicompanion.diary.DiaryManager(this, activePersonaId)
            val ok = manager.updateDiaryContentById(id, content)
            if (ok) {
                Toast.makeText(this, "日记已更新", Toast.LENGTH_SHORT).show()
                AppLogger.i(TAG, "updateDiary: 已更新日记 id=$id")
                notifyDataChanged() // 触发 DiaryScreen 重组刷新列表（同时清除缓存）
            } else {
                Toast.makeText(this, "更新失败：未找到对应日记", Toast.LENGTH_SHORT).show()
                AppLogger.w(TAG, "updateDiary: 未找到日记 id=$id")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "updateDiary: ${e.message}")
            Toast.makeText(this, "更新失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDiaryClick(diaryId: Int) {
        // 现已由 Compose 内联编辑对话框处理点击事件，不再跳转 DiaryActivity
        // 保留方法以满足 AppHost 接口契约，便于后续扩展
        AppLogger.d(TAG, "onDiaryClick: diaryId=$diaryId (handled by Compose inline dialog)")
    }

    /** 删除指定日期的日记 */
    override fun deleteDiary(date: String) {
        try {
            val manager = com.aicompanion.diary.DiaryManager(this, activePersonaId)
            val ok = manager.deleteDiary(date)
            if (ok) {
                Toast.makeText(this, "已删除日记", Toast.LENGTH_SHORT).show()
                AppLogger.i(TAG, "deleteDiary: 已删除 $date")
                notifyDataChanged() // 触发 DiaryScreen 重组刷新列表（同时清除缓存）
            } else {
                Toast.makeText(this, "删除失败：日记不存在", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteDiary error: ${e.message}")
            Toast.makeText(this, "删除失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== SettingsScreen 相关的 AppHost 方法 =====

    /** 获取 API 地址 */
    override fun getApiUrl(): String = settingsManager?.chatApiUrl ?: ""

    /** 获取 API Key */
    override fun getApiKey(): String = settingsManager?.chatApiKey ?: ""

    /** 获取模型名称 */
    override fun getModel(): String = settingsManager?.chatModel ?: "GPT-4o"

    /** 获取温度值 */
    override fun getTemperature(): Float = settingsManager?.llmTemperature ?: 0.7f

    /** 获取最大 Token 值 */
    override fun getMaxTokens(): Int = settingsManager?.llmMaxTokens ?: 4096

    /** TTS 是否启用 */
    override fun isTtsEnabled(): Boolean = settingsManager?.isTTSEnabled ?: true

    /** ASR 是否启用 */
    override fun isAsrEnabled(): Boolean = settingsManager?.voiceRecognitionEnabled ?: true

    /** 获取语速 */
    override fun getSpeechRate(): Float = settingsManager?.ttsRate ?: 1.0f

    /** 获取音色名称 */
    override fun getVoice(): String = settingsManager?.ttsVoiceName ?: "甜美女声"

    /** 保存 API 地址 */
    override fun saveApiUrl(url: String) {
        settingsManager?.chatApiUrl = url
        AppLogger.d(TAG, "saveApiUrl: 已保存")
    }

    /** 保存 API Key */
    override fun saveApiKey(key: String) {
        settingsManager?.chatApiKey = key
        AppLogger.d(TAG, "saveApiKey: 已保存")
    }

    /** 保存模型选择 */
    override fun saveModel(model: String) {
        settingsManager?.chatModel = model
        AppLogger.d(TAG, "saveModel: $model")
    }

    /** 保存温度值 */
    override fun saveTemperature(temperature: Float) {
        settingsManager?.llmTemperature = temperature
        AppLogger.d(TAG, "saveTemperature: $temperature")
    }

    /** 保存最大 Token 值 */
    override fun saveMaxTokens(maxTokens: Int) {
        settingsManager?.llmMaxTokens = maxTokens
        AppLogger.d(TAG, "saveMaxTokens: $maxTokens")
    }

    /** 保存 TTS 开关状态 */
    override fun saveTtsEnabled(enabled: Boolean) {
        settingsManager?.isTTSEnabled = enabled
        AppLogger.d(TAG, "saveTtsEnabled: $enabled")
    }

    /** 保存 ASR 开关状态 */
    override fun saveAsrEnabled(enabled: Boolean) {
        settingsManager?.voiceRecognitionEnabled = enabled
        AppLogger.d(TAG, "saveAsrEnabled: $enabled")
    }

    /** 保存语速 */
    override fun saveSpeechRate(rate: Float) {
        settingsManager?.ttsRate = rate
        AppLogger.d(TAG, "saveSpeechRate: $rate")
    }

    /** 保存音色选择 */
    override fun saveVoice(voice: String) {
        settingsManager?.ttsVoiceName = voice
        AppLogger.d(TAG, "saveVoice: $voice")
    }

    // ===== AchievementScreen 相关的 AppHost 方法 =====

    /** 获取成就列表 - 从 AchievementManager 读取真实数据 */
    override fun getAchievements(): List<com.aicompanion.models.Achievement> {
        return try {
            achievementManager?.getAchievements() ?: emptyList()
        } catch (e: Exception) {
            AppLogger.e(TAG, "getAchievements: ${e.message}")
            emptyList()
        }
    }

    /** 获取个人中心页面数据（5秒 TTL 缓存） */
    override fun getProfileData(): com.aicompanion.ui.profile.ProfileData {
        // 使用 CacheManager 管理 TTL 缓存
        if (cacheManager.isValid("profile", com.aicompanion.util.CacheManager.PROFILE_TTL_MS)) {
            return profileCache ?: com.aicompanion.ui.profile.ProfileData()
        }
        return try {
            // 1. 获取用户昵称（从 NicknameManager 或默认）
            val nickname = try {
                val nicknames = nicknameManager?.getActiveNicknames()
                nicknames?.firstOrNull()?.ifBlank { null } ?: "星辰旅人"
            } catch (e: Exception) { "星辰旅人" }

            // 2. 获取用户 ID
            val userId = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("user_id", "STR_20240601") ?: "STR_20240601"

            // 3. 获取个性签名（从 SharedPreferences 或默认）
            val signature = try {
                getSharedPreferences("companion_settings", MODE_PRIVATE).getString("user_signature", "")
                    ?: ""
            } catch (e: Exception) { "" }

            // 4. 获取 AI 头像路径（使用统一 getAiAvatarPath 方法，含 AvatarManager fallback）
            val avatarPath = getAiAvatarPath() ?: ""

            // 4.1 获取用户头像路径（从 AvatarManager 读取）
            val userAvatarPath = com.aicompanion.util.AvatarManager
                .getUserAvatarPath(this@MainActivity, activePersonaId)
                .takeIf { it.isNotBlank() && java.io.File(it).exists() } ?: ""

            // 5. 获取好感度信息
            val affectionLevel = affectionManager?.affectionLevel ?: 1
            val affectionExp = (affectionLevel % 10) * 100 // 简化计算：当前等级进度
            val affectionMaxExp = 1000

            // 6. 获取聊天天数（从 PersonaStatsManager）
            val chatDays = statsManager?.totalChatDays ?: 0

            // 7. 获取日记数量（通过 DiaryManager 直接查询）
            val diaryCount = try {
                val dm = com.aicompanion.diary.DiaryManager(this@MainActivity, activePersonaId)
                dm.getAllDiaries().size
            } catch (e: Exception) { 0 }

            // 8. 获取签到天数
            val checkInDays = checkInManager.totalCheckIns

            // 9. 计算在线时长（基于相处天数估算，每天平均使用时长）
            val daysTogether = getDaysTogether()
            val onlineHours = daysTogether * 2 // 假设每天平均使用 2 小时

            com.aicompanion.ui.profile.ProfileData(
                userName = nickname,
                userId = userId,
                signature = signature.ifEmpty { "与星尘同行，每一天都是冒险" },
                avatarPath = avatarPath,
                userAvatarPath = userAvatarPath,
                affectionLevel = affectionLevel.coerceIn(1, 99),
                affectionExp = affectionExp.coerceIn(0, affectionMaxExp),
                affectionMaxExp = affectionMaxExp,
                chatDays = chatDays.coerceAtLeast(0),
                diaryCount = diaryCount.coerceAtLeast(0),
                checkInDays = checkInDays.coerceAtLeast(0),
                onlineHours = onlineHours.coerceAtLeast(0),
            ).also {
                cacheManager.updateProfile(it)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getProfileData failed: ${e.message}")
            // 返回默认值，确保 UI 不崩溃
            com.aicompanion.ui.profile.ProfileData()
        }
    }

    /**
     * 退出登录
     *
     * 实现真实登出逻辑：
     * 1. 清除聊天会话状态（停止 AI 回复、清空当前消息）
     * 2. 清除 API 凭据（chatApiKey）
     * 3. 重置 ViewModel 状态
     * 4. 通过 notifyDataChanged() 触发 UI 刷新
     *
     * 注：保留角色档案/聊天历史/日记等用户数据，仅清除会话凭据
     */
    override fun logout() {
        try {
            // 1. 清除 API 凭据（API Key），保留 API URL 和模型选择
            try {
                settingsManager?.chatApiKey = ""
                AppLogger.i(TAG, "logout: 已清除 API Key")
            } catch (e: Exception) {
                AppLogger.e(TAG, "logout clear apiKey: ${e.message}")
            }

            // 2. 重置通话/录音状态
            _isCallActive = false
            try {
                getSystemService(AUDIO_SERVICE)?.let { am ->
                    (am as? android.media.AudioManager)?.mode = android.media.AudioManager.MODE_NORMAL
                }
            } catch (_: Exception) {}

            // 3. 重置好感度缓存等临时状态
            cachedAiName = null
            cachedAiAvatarPath = null

            // 4. 重置消息版本号触发 UI 刷新
            _messageVersion = 0

            // 5. 提示用户并触发 UI 刷新
            Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show()
            AppLogger.i(TAG, "logout: 用户已登出")
            notifyDataChanged()
        } catch (e: Exception) {
            AppLogger.e(TAG, "logout error: ${e.message}")
            Toast.makeText(this, "退出失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 获取应用版本名称（用于关于对话框） */
    override fun getAppVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0)?.versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }
    }

    // ===== Live2D 相关的 AppHost 方法 =====

    private val live2dModelManager by lazy { com.aicompanion.live2d.ModelManager(this@MainActivity) }

    override fun isLive2dEnabled(): Boolean = settingsManager?.live2dEnabled == true

    override fun getLive2DView(): android.view.View? {
        if (!isLive2dEnabled()) return null
        val coord = live2DCoordinator ?: return null
        // Compose 模式下没有 ViewStub，直接创建视图
        // 注意：此处只负责返回视图实例，不触发 loadModel（避免阻塞 AndroidView factory 主线程）
        // 模型加载由 resumeLive2D() 异步触发
        val view = coord.createView(this@MainActivity) ?: return null
        // 设置布局参数：MATCH_PARENT 以便正确测量和渲染
        view.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        return view
    }

    override fun loadLive2DModel() {
        // 异步加载，避免阻塞调用线程（Compose 重组期间）
        lifecycleScope.launch(Dispatchers.Main) {
            live2DCoordinator?.loadModel()
        }
    }

    override fun pauseLive2D() {
        live2DCoordinator?.hideView()
    }

    override fun resumeLive2D() {
        if (!isLive2dEnabled()) return
        // 视图创建与模型加载全部异步化，避免阻塞 Compose 重组
        lifecycleScope.launch(Dispatchers.Main) {
            val view = live2DCoordinator?.createView(this@MainActivity) ?: return@launch
            view.visibility = android.view.View.VISIBLE
            (view as? com.aicompanion.live2d.Live2DWebView)?.resumeRendering()
            // 如果模型还没加载, 异步加载模型
            if (live2DCoordinator?.isModelLoaded != true) {
                live2DCoordinator?.loadModel()
            }
        }
    }

    override fun getLive2DModels(): List<com.aicompanion.models.Live2DModel> {
        return try { live2dModelManager.getAllModels() } catch (_: Exception) { emptyList() }
    }

    override fun getCurrentLive2DModelId(): String {
        return try { live2dModelManager.getCurrentModel().id } catch (_: Exception) { "" }
    }

    override fun setLive2DModel(modelId: String) {
        try {
            live2dModelManager.setActiveModel(modelId)
            // 重新加载模型
            live2DCoordinator?.loadModel()
        } catch (e: Exception) {
            AppLogger.e(TAG, "setLive2DModel: ${e.message}")
        }
    }

    override fun getLive2DScale(): Float = live2DCoordinator?.getScale() ?: 1f

    override fun setLive2DScale(scale: Float) {
        live2DCoordinator?.setScale(scale)
    }

    // ===== AI 预测回复桥接方法 =====

    private val _predictionsState = androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
    override val predictionsState: androidx.compose.runtime.State<List<String>> get() = _predictionsState

    override fun triggerPredictions() {
        chatViewModel.triggerPredictions {
            com.aicompanion.prompt.PromptBuilder.buildIdentity(this@MainActivity, activePersonaId)
        }
    }

    override fun clearPredictions() {
        _predictionsState.value = emptyList()
    }

    /** 由 ChatViewModel.UiEvent.ShowPredictions 调用，更新 Compose 状态 */
    private fun updatePredictions(predictions: List<String>) {
        _predictionsState.value = predictions
    }

    // ===== CheckInScreen 相关的 AppHost 方法 =====

    private val checkInManager by lazy { com.aicompanion.gamify.CheckInManager(this@MainActivity) }

    override fun getCheckInStreak(): Int = checkInManager.currentStreak

    override fun isCheckedInToday(): Boolean = checkInManager.isCheckedInToday()

    override fun getTotalCheckIns(): Int = checkInManager.totalCheckIns

    override fun getCheckedDates(): Set<String> {
        return try {
            checkInManager.getHistory().map { it.date }.toSet()
        } catch (e: Exception) {
            AppLogger.e(TAG, "getCheckedDates: ${e.message}")
            emptySet()
        }
    }

    override fun performCheckIn() {
        try {
            val result = checkInManager.checkIn()
            when (result) {
                is com.aicompanion.gamify.CheckInManager.CheckInResult.Success -> {
                    _messageVersion++ // 触发 Compose 刷新
                    val ach = achievementManager?.updateProgress("checkin", result.streak)
                    if (ach != null) showAchievementUnlock(ach)
                    addPetMessage("📅 签到成功！已连续${result.streak}天", Emotion.HAPPY, Action.TAIL_FLICK)
                    updateAffectionDisplay()
                    // 连续签到15天触发AI发动态
                    if (result.shouldTriggerAiMoment) {
                        checkAiMomentTrigger()
                        Toast.makeText(this@MainActivity,
                            "🎉 连续签到${result.streak}天！AI正在发动态...",
                            Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity,
                            "签到成功！连续${result.streak}天",
                            Toast.LENGTH_SHORT).show()
                    }
                }
                is com.aicompanion.gamify.CheckInManager.CheckInResult.AlreadyCheckedIn -> {
                    Toast.makeText(this@MainActivity, "今天已经签过啦~", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "performCheckIn: ${e.message}")
        }
    }

    // ===== AlbumScreen 相关的 AppHost 方法 =====

    /**
     * 获取纪念相册照片列表（5秒 TTL 缓存）
     * 从 MemorialAlbumManager 读取所有条目，过滤出图片文件存在的条目，
     * 转换为 AlbumPhotoData 供 Compose 层 AlbumScreen 使用
     */
    override fun getAlbumPhotos(): List<com.aicompanion.ui.album.AlbumPhotoData> {
        // 使用 CacheManager 管理 TTL 缓存
        if (cacheManager.isValid("album", com.aicompanion.util.CacheManager.ALBUM_TTL_MS)) {
            return albumCache
        }
        return try {
            val entries = com.aicompanion.album.MemorialAlbumManager.getEntries(this@MainActivity)
                .sortedByDescending { it.createdAt }
                .filter { java.io.File(it.imagePath).exists() }
            val result = entries.mapIndexed { index, entry ->
                com.aicompanion.ui.album.AlbumPhotoData(
                    id = index + 1,
                    date = entry.createdAt.substring(0, 10).replace("-", "."),
                    description = entry.title.ifBlank { entry.caption },
                    imagePath = entry.imagePath,
                )
            }
            cacheManager.updateAlbum(result)
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "getAlbumPhotos: ${e.message}")
            emptyList()
        }
    }
}
