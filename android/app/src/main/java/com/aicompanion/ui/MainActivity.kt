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
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import android.widget.HorizontalScrollView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val time: String,
    val isUser: Boolean,
    val userMood: String = "",
    var feedback: Int = 0,
    val emotion: Emotion = Emotion.NEUTRAL,
    val timestamp: Long = System.currentTimeMillis(),
    var isPartial: Boolean = false,
    var isFavorited: Boolean = false,
    var reactionEmoji: String = "",
    val stickerPath: String? = null,
    var audioPath: String? = null,
    var audioUrl: String? = null
)

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_PICK_IMAGE = 100
        private const val REQUEST_STICKER_PICK = 4001
        private const val REQUEST_IMAGE_UPLOAD = 4002
    }

    private var live2dView: Live2DWebView? = null
    private var recyclerChat: RecyclerView? = null
    private var etMessage: EditText? = null
    private var btnSend: ImageButton? = null
    private var btnVoice: ImageButton? = null
    private var isVoiceRecording = false
    private var voiceWaveformOverlay: View? = null
    private var btnSettings: ImageButton? = null
    private var btnStickerChat: ImageButton? = null
    private var btnImageUpload: ImageButton? = null
    private var btnMore: ImageButton? = null
    private var tvWeather: TextView? = null
    private var tvDaysLabel: TextView? = null
    private var progressAffection: ProgressBar? = null
    private var tvPetName: TextView? = null
    private var ivAiAvatarSmall: ImageView? = null

    private var settingsManager: SettingsManager? = null
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
    private var favoriteManager: FavoriteManager? = null
    private var nicknameManager: NicknameManager? = null
    private var chatBubblePopup: ChatBubblePopup? = null
    private var cachedAiName: String? = null
    private var cachedAiAvatarPath: String? = null
    private var emotionAnalyzer: com.aicompanion.emotion.EmotionAnalyzer? = null

    private var scrollPredictions: HorizontalScrollView? = null
    private var layoutPredictions: LinearLayout? = null
    private var chatPredictor: ChatPredictor? = null

    private val messages = mutableListOf<ChatMessage>()
    private val messageScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val memoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chatStorage by lazy { com.aicompanion.storage.ChatHistoryStorage(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var isInForeground = false
    private var quotedMessage: ChatMessage? = null

    private var offsetX = 0f
    private var offsetY = 0f
    private var modelBaseScale = 1f
    private var modelNaturalW = 0f
    private var modelNaturalH = 0f
    private var lastLoadedModelPath: String? = null

    private var focusActive = false
    private var focusSecondsLeft = 0
    private var focusEndTime = 0L
    private var focusRunnable: Runnable? = null
    private var proactiveRunnable: Runnable? = null
    private var virtualWorldRunnable: Runnable? = null
    private var diaryRunnable: Runnable? = null

    private var currentUserMood = ""
    private var currentUserMoodName = ""
    private var isModelLoaded = false
    private val logHistory = Collections.synchronizedList(mutableListOf<String>())

    private var longPressPending = false
    private var dragActive = false
    private var touchDownRawX = 0f
    private var touchDownRawY = 0f
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var longPressRunnable: Runnable? = null

    private val activePersonaId: String by lazy {
        val raw = intent?.getStringExtra("persona_id")
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("active_persona_id", "default")
            ?: "default"
        if (raw.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) raw else "default"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
        setContentView(R.layout.activity_main)

        try {
            initStep("Views") { initViews() }
            initStep("SettingsManager") { settingsManager = SettingsManager(this) }
            initStep("EnsureDirs") { ensureAppDirs() }
            initStep("AffectionManager") { affectionManager = AffectionManager(this, activePersonaId) }
            initStep("StatsManager") { statsManager = com.aicompanion.stats.PersonaStatsManager(this, activePersonaId) }
            initStep("AchievementManager") { achievementManager = AchievementManager(this, activePersonaId) }
            initStep("MomentsManager") { momentsManager = com.aicompanion.memory.MemorableMomentsManager(this, activePersonaId) }
            initStep("SystemMonitor") {
                val monitor = com.aicompanion.services.SystemMonitor(this)
                monitor.startMonitoring()
                monitor.onBatteryLow = { percentage ->
                    if (!isFinishing && !isDestroyed) {
                        isInForeground = false
                        triggerBatteryAlert(percentage)
                    }
                }
                systemMonitor = monitor
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
                        addStickerMessage("ai", imagePath)
                    }
                }
            }
            initStep("ContextManager") { contextManager = ContextManager(this, activePersonaId) }
            initStep("PersonaRag") { personaRagManager = PersonaRagManager(this, activePersonaId) }
            initStep("WireNickname") {
                val personaPrefs = getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE)
                val appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                contextManager?.userNickname = personaPrefs.getString("user_nickname", null)
                    ?: appPrefs.getString("user_call_name", "")
                    ?: "用户"
            }
            initStep("FavoriteManager") { favoriteManager = FavoriteManager(this, activePersonaId) }
            initStep("NicknameManager") { nicknameManager = NicknameManager(this) }
            initStep("ChatBubblePopup") { chatBubblePopup = ChatBubblePopup(this) }
            initStep("VoiceManager") { voiceManager = VoiceManager(this) }
            initStep("TtsManager") { ttsManager = com.aicompanion.voice.TtsManager(this) }
            initStep("ProactiveEngine") { proactiveEngine = ProactiveInteractionEngine(settingsManager!!) }
            initStep("ApiClient") { rebuildApiClient() }
            initStep("PersonaCompress") { initPersonaCompression() }
            initStep("ChatAdapter") { initChatAdapter() }
            initStep("LoadAvatar") { loadAiAvatar() }
            initStep("Live2DSettings") { if (settingsManager?.live2dEnabled == true) { loadLive2DSettings() } else { hideLive2DView() } }
            initStep("Live2DModel") { if (settingsManager?.live2dEnabled == true) loadLive2DModel() }
            initStep("ClickListeners") { setupClickListeners() }
            initStep("ApplyTheme") { applyTheme() }
            initStep("UpdateDisplay") { updateAffectionDisplay() }
            initStep("Weather") { updateWeather() }
            initStep("LoadMessages") { loadChatHistory() }
            initStep("Welcome") { loadWelcomeMessage() }
            initStep("Proactive") { scheduleProactiveChat() }
            initStep("VirtualWorld") { scheduleVirtualWorldTick() }
            initStep("DiaryTimer") { scheduleDiaryTimer() }
            initStep("BatteryOptimization") { requestBatteryOptimization() }
            initStep("EntranceAnim") { }

            messageScope.launch(Dispatchers.IO) {
                try {
                    com.aicompanion.prompt.PromptBuilder.buildIdentity(this@MainActivity, activePersonaId)
                } catch (e: Exception) { com.aicompanion.util.AppLogger.e(TAG, "buildIdentity预热失败: ${e.message}", e) }
            }
        } catch (e: Exception) {
            fatal("onCreate", e)
        }
    }

    private fun initStep(name: String, block: () -> Unit) {
        try { block() } catch (e: Exception) {
            Log.e(TAG, "[INIT FAIL] $name: ${e.javaClass.simpleName}: ${e.message}", e)
            Toast.makeText(this, "初始化失败: $name - ${e.message}", Toast.LENGTH_LONG).show()
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

        if (personaText.length > 500 && personaRagManager != null) {
            messageScope.launch(Dispatchers.IO) {
                personaRagManager?.buildIndex(buildPersonaFields())
            }
        }
    }

    private fun buildFullPersonaText(): String {
        val personaId = intent.getStringExtra("persona_id")
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("active_persona_id", "default")
            ?: "default"
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
        val personaId = intent.getStringExtra("persona_id")
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("active_persona_id", "default")
            ?: "default"
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
        live2dView = findViewById(R.id.live2d_view)
        recyclerChat = findViewById(R.id.recycler_chat)
        etMessage = findViewById(R.id.et_message)
        btnSend = findViewById(R.id.btn_send)
        btnVoice = findViewById(R.id.btn_voice)
        btnSettings = findViewById(R.id.btn_settings)
        btnStickerChat = findViewById(R.id.btn_sticker_chat)
    btnImageUpload = findViewById(R.id.btn_image_upload)
    btnMore = findViewById(R.id.btn_more)

        findViewById<View>(R.id.btn_phone_call)?.setOnClickListener {
            try {
                val intent = Intent(this, com.aicompanion.ui.PhoneCallActivity::class.java)
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_PERSONA_ID, activePersonaId)
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_PERSONA_NAME, cachedAiName ?: "星尘")
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_SCOPE, "persona")
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_SCOPE_ID, activePersonaId)
                startActivity(intent)
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e(TAG, "phoneCall: ${e.message}")
            }
        }
        tvWeather = findViewById(R.id.tv_weather)
        tvDaysLabel = findViewById(R.id.tv_days_label)
        progressAffection = findViewById(R.id.progress_affection)
        tvPetName = findViewById(R.id.tv_pet_name)
        ivAiAvatarSmall = findViewById(R.id.iv_ai_avatar_small)
        scrollPredictions = findViewById(R.id.scroll_predictions)
        layoutPredictions = findViewById(R.id.layout_predictions)
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

    private fun rebuildApiClient() {
        val sm = settingsManager ?: return
        if (sm.chatApiUrl.isNotBlank()) {
            apiClient = ApiClient(sm.chatApiUrl, sm.chatApiKey, sm.chatModel,
                sm.llmTemperature, sm.llmTopP, sm.llmFrequencyPenalty, sm.llmPresencePenalty, sm.llmMaxTokens,
                sm.apiProvider)
        }
        chatPredictor = ChatPredictor(this, sm)
    }

    private fun initChatAdapter() {
        chatAdapter = ChatAdapter(messages)
        chatAdapter?.cacheSkinSettings(this)

        val personaId = intent.getStringExtra("persona_id")
        if (!personaId.isNullOrEmpty()) {
            val pm = com.aicompanion.persona.PersonaManager(this)
            pm.load()
            val persona = pm.getPersona(personaId)
            if (persona != null && persona.avatarPath.isNotBlank()) {
                chatAdapter?.aiAvatarOverride = persona.avatarPath
            }
        }

        recyclerChat?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
            itemAnimator = null
            setItemViewCacheSize(4)
            setHasFixedSize(true)
            isNestedScrollingEnabled = true
            recycledViewPool.setMaxRecycledViews(0, 8)
            recycledViewPool.setMaxRecycledViews(1, 8)
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
                favoriteManager?.removeFavorite(msg.id)
                messages.removeAt(position)
                chatAdapter?.notifyItemRemoved(position)
                saveChatHistory()
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

    private fun loadLive2DSettings() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        offsetX = prefs.getFloat("model_offset_x", 0f)
        offsetY = prefs.getFloat("model_offset_y", 0f)
        modelBaseScale = prefs.getFloat("model_scale", 1f)
    }

    private fun hideLive2DView() {
        live2dView?.visibility = View.GONE
    }

    private fun loadLive2DModel() {
        val webView = live2dView ?: return
        webView.visibility = View.VISIBLE

        webView.setOnModelInfo { width, height, baseScale ->
            modelNaturalW = width
            modelNaturalH = height
        }

        webView.setOnModelLoaded { success ->
            if (isDestroyed) return@setOnModelLoaded
            runOnUiThread {
                if (!success) {
                    val failedLog = live2dView?.getLog() ?: ""
                    logHistory.add("[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] 模型加载失败:")
                    failedLog.lines().takeLast(20).forEach { logHistory.add("  $it") }

                    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    val currentPath = prefs.getString("active_model_path", "")
                    if (!currentPath.isNullOrEmpty()) {
                        Log.w(TAG, "Custom model failed, falling back to default")
                        prefs.edit().remove("active_model_path").apply()
                        lastLoadedModelPath = null
                        live2dView?.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json")
                    } else {
                        Toast.makeText(this@MainActivity, "皮套加载失败", Toast.LENGTH_LONG).show()
                    }
                } else {
                    live2dView?.translationX = offsetX
                    live2dView?.translationY = offsetY
                    val scale = getSharedPreferences("app_prefs", MODE_PRIVATE).getFloat("model_scale", 1f)
                    live2dView?.setModelScale(scale.coerceIn(0.3f, 3.0f))
                    isModelLoaded = true
                }
            }
        }

        setupLive2DTouch()

        try {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val customModelPath = prefs.getString("active_model_path", "")
            if (!customModelPath.isNullOrEmpty() && !customModelPath.startsWith("file:///android_asset/")) {
                val file = File(customModelPath)
                if (file.exists() && file.isFile) {
                    lastLoadedModelPath = customModelPath
                    webView.loadLive2DModelFromPath(customModelPath)
                } else {
                    prefs.edit().remove("active_model_path").apply()
                    lastLoadedModelPath = null
                    webView.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json")
                }
            } else {
                lastLoadedModelPath = null
                webView.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json")
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadLive2DModel failed: ${e.message}", e)
            try { webView.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json") } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "loadLive2DModel: ${e.message}") }
        }
    }

    private fun setupLive2DTouch() {
        val view = live2dView ?: return
        view.touchHandler = lambda@{ event ->
            if (!isModelLoaded) return@lambda false
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    touchDownRawX = event.rawX
                    touchDownRawY = event.rawY
                    longPressPending = true
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    longPressRunnable = Runnable {
                        if (longPressPending) {
                            dragActive = true
                            longPressPending = false
                            lastTouchRawX = touchDownRawX
                            lastTouchRawY = touchDownRawY
                            live2dView?.alpha = 0.85f
                        }
                    }
                    longPressRunnable?.let { handler.postDelayed(it, 300) }
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (dragActive) {
                        val dx = event.rawX - lastTouchRawX
                        val dy = event.rawY - lastTouchRawY
                        live2dView?.translationX = (live2dView?.translationX ?: 0f) + dx
                        live2dView?.translationY = (live2dView?.translationY ?: 0f) + dy
                        lastTouchRawX = event.rawX
                        lastTouchRawY = event.rawY
                        return@lambda true
                    }
                    if (longPressPending) {
                        val dx = Math.abs(event.x - touchDownX)
                        val dy = Math.abs(event.y - touchDownY)
                        if (dx > 10 || dy > 10) {
                            longPressRunnable?.let { handler.removeCallbacks(it) }
                            longPressPending = false
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    if (dragActive) {
                        dragActive = false
                        live2dView?.alpha = 0.9f
                        offsetX = live2dView?.translationX ?: 0f
                        offsetY = live2dView?.translationY ?: 0f
                        getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                            .putFloat("model_offset_x", offsetX)
                            .putFloat("model_offset_y", offsetY)
                            .apply()
                        return@lambda true
                    }
                    if (longPressPending) {
                        longPressPending = false
                        live2dView?.tapModel(event.x, event.y)
                        return@lambda true
                    }
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    longPressPending = false
                    if (dragActive) {
                        dragActive = false
                        live2dView?.alpha = 0.9f
                        offsetX = live2dView?.translationX ?: 0f
                        offsetY = live2dView?.translationY ?: 0f
                        getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                            .putFloat("model_offset_x", offsetX)
                            .putFloat("model_offset_y", offsetY)
                            .apply()
                    }
                    true
                }
                else -> false
            }
        }
    }

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
            try { startActivity(Intent(this, SettingsActivity::class.java)) } catch (e: Exception) {
                Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btn_moments)?.setOnClickListener {
            startActivity(Intent(this, com.aicompanion.moments.MomentsActivity::class.java))
        }

        findViewById<View>(R.id.btn_diary)?.setOnClickListener {
            startActivity(Intent(this, com.aicompanion.ui.DiaryActivity::class.java))
        }

        btnStickerChat?.let { btn ->
            btn.setOnClickListener {
                try {
                    startActivityForResult(
                        Intent(this, com.aicompanion.sticker.StickerActivity::class.java),
                        REQUEST_STICKER_PICK
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开表情包", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnImageUpload?.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_IMAGE_UPLOAD)
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
    }

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
                            3 -> if (focusActive) cancelFocusTimer() else startFocusTimer()
                            4 -> try { startActivity(Intent(this@MainActivity, ModelManagerActivity::class.java)) } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "showFeaturePanel: ${e.message}") }
                            5 -> pickBackgroundImage()
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
                            99 -> {
                                android.app.AlertDialog.Builder(this@MainActivity)
                                    .setTitle("清空聊天记录")
                                    .setMessage("确定要清空所有聊天记录吗？此操作不可撤销。")
                                    .setPositiveButton("清空") { _, _ ->
                                        val oldSize = messages.size
                                        messages.clear()
                                        chatAdapter?.notifyItemRangeRemoved(0, oldSize)
                                        saveChatHistory()
                                        Toast.makeText(this@MainActivity, "聊天记录已清空", Toast.LENGTH_SHORT).show()
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
        isVoiceRecording = true
        btnVoice?.alpha = 0.5f
        btnVoice?.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        showVoiceWaveformOverlay()

        try {
            val asrManager = com.aicompanion.voice.LocalAsrManager(this)
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
                    asrManager.cleanup()
                }
                override fun onError(error: String) {
                    runOnUiThread {
                        stopVoiceRecording()
                        etMessage?.setHint("输入消息...")
                        android.widget.Toast.makeText(this@MainActivity, "语音识别: $error", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    asrManager.cleanup()
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
            com.aicompanion.util.AppLogger.e(TAG, "语音识别启动失败: ${e.message}", e)
        }
    }

    private fun stopVoiceRecording() {
        isVoiceRecording = false
        btnVoice?.alpha = 1.0f
        btnVoice?.backgroundTintList = null
        hideVoiceWaveformOverlay()
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
        com.aicompanion.util.AppLogger.d(TAG, "sendMessage: 用户发送 '${text.take(50)}'")
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
        messages.add(ChatMessage(text = displayText, time = time, isUser = true, userMood = currentUserMood))
        chatAdapter?.notifyItemInserted(messages.size - 1)
        recyclerChat?.scrollToPosition(messages.size - 1)
        statsManager?.recordUserMessage(text)
        saveChatHistory()
        saveMessageToFile(messages.last())

        affectionManager?.addMessage()
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
        val personaId = intent.getStringExtra("persona_id")
        var avatarPath: String? = null

        if (!personaId.isNullOrEmpty()) {
            val pm = com.aicompanion.persona.PersonaManager(this)
            pm.load()
            val persona = pm.getPersona(personaId)
            if (persona != null && persona.avatarPath.isNotBlank()) {
                avatarPath = persona.avatarPath
            }
        }

        if (avatarPath.isNullOrBlank()) {
            val prefs = getSharedPreferences("avatar_data", MODE_PRIVATE)
            avatarPath = prefs.getString("ai_avatar", "")
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

    private fun sendToLLM(message: String) {
        com.aicompanion.util.AppLogger.d(TAG, "sendToLLM: 开始处理消息 '${message.take(50)}'")

        if (com.aicompanion.safety.ContentSafetyFilter.shouldBlock(this, message)) {
            addPetMessage(com.aicompanion.safety.ContentSafetyFilter.getRefusalResponse(), com.aicompanion.models.Emotion.NEUTRAL, com.aicompanion.models.Action.IDLE)
            return
        }

        val client = apiClient
        val sm = settingsManager
        if (client == null || sm == null) {
            com.aicompanion.util.AppLogger.e(TAG, "sendToLLM: apiClient=${client != null}, settingsManager=${sm != null}")
            addPetMessage("请先在设置中配置 API 哦~", Emotion.NEUTRAL, Action.IDLE)
            return
        }

        com.aicompanion.util.AppLogger.d(TAG, "sendToLLM: apiClient已就绪, url=${sm.chatApiUrl.take(30)}, model=${sm.chatModel}")

        setLoading(true)
        chatAdapter?.setTypingIndicator(true)
        recyclerChat?.scrollToPosition(messages.size - 1)

        val systemContext = buildSystemContext(message)
        val actionMgr = aiActionManager

        messageScope.launch {
            try {
                com.aicompanion.util.AppLogger.d(TAG, "sendToLLM: 协程启动，开始获取角色信息")
                val persona = getPersonaInfo(message)
                com.aicompanion.util.AppLogger.d(TAG, "sendToLLM: 角色信息获取完成, name=${persona.first}")
                val memories = emptyList<String>()
                val ctxHistory = contextManager?.getRecentTurnsAsPairs() ?: emptyList()
                val history = if (ctxHistory.isNotEmpty()) ctxHistory
                              else messages.takeLast(settingsManager?.contextTurns ?: 10).filter { it.text.length < 500 }.map { it.isUser to it.text }
                val tools = actionMgr?.getToolDefinitions() ?: emptyList()
                com.aicompanion.util.AppLogger.d(TAG, "sendToLLM: history=${history.size}条, tools=${tools.size}个")

                var emotionParams = EmotionParams()
                if (sm.emotionAnalysisEnabled && client.chatApiUrl.isNotBlank()) {
                    try {
                        val analyzer = emotionAnalyzer ?: com.aicompanion.emotion.EmotionAnalyzer(client).also { emotionAnalyzer = it }
                        emotionParams = withContext(Dispatchers.IO) {
                            analyzer.analyzeEmotion(
                                personaName = persona.first,
                                personaPrompt = persona.second,
                                userMessage = message,
                                chatHistory = history,
                                currentEmotion = currentUserMoodName
                            )
                        }
                        com.aicompanion.util.AppLogger.d(TAG, "Emotion analysis: tempOffset=${emotionParams.temperatureOffset}, " +
                            "pitchOffset=${emotionParams.ttsPitchOffset}, rateOffset=${emotionParams.ttsRateOffset}, " +
                            "intensity=${emotionParams.emotionIntensity}")
                    } catch (e: Exception) {
                        com.aicompanion.util.AppLogger.w(TAG, "Emotion analysis failed, using defaults: ${e.message}")
                    }
                }

                val userCall = getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE)
                    .getString("user_nickname", null)
                    ?: getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .getString("user_call_name", "") ?: ""

                val nicknameContext = if (userCall.isBlank()) {
                    val discovered = nicknameManager?.getActiveNicknames() ?: emptyList()
                    if (discovered.isEmpty() && messages.size >= 4) {
                        "\n\n【提示】你还没有给用户设定称呼。如果你觉得通过聊天已经对用户有了一定了解，可以调用 summarize_nicknames 工具为主人总结出几个合适的称呼。"
                    } else {
                        ""
                    }
                } else {
                    ""
                }

                val overrideTemp = if (sm.emotionAnalysisEnabled) emotionParams.applyToTemperature(sm.llmTemperature) else null
                val overrideTopP = if (sm.emotionAnalysisEnabled) emotionParams.applyToTopP(sm.llmTopP) else null

                val response = if (tools.isNotEmpty()) {
                    com.aicompanion.util.AppLogger.d(TAG, "sendToLLM: 调用sendChatWithToolLoop")
                    withContext(Dispatchers.IO) {
                        client.sendChatWithToolLoop(
                            sm.userId, message, persona.first, persona.second + nicknameContext,
                            currentUserMoodName, "idle", memories, history, systemContext, tools
                        ) { name, args -> actionMgr!!.executeTool(name, args) }
                    }
                } else {
                    com.aicompanion.util.AppLogger.d(TAG, "sendToLLM: 调用sendChat")
                    withContext(Dispatchers.IO) {
                        client.sendChat(
                            sm.userId, message, persona.first, persona.second,
                            currentUserMoodName, "idle", memories, "", systemContext, history,
                            overrideTemperature = overrideTemp,
                            overrideTopP = overrideTopP
                        )
                    }
                }

                com.aicompanion.util.AppLogger.d(TAG, "sendToLLM: API响应=${response != null}, errorMsg=${response?.errorMessage}")

                chatAdapter?.setTypingIndicator(false)

                if (response != null) {
                    if (response.errorMessage != null) {
                        addPetMessage("呜...${response.errorMessage}", Emotion.SAD, Action.IDLE)
                    } else {
                        val rawText = response.text
                        val isComplex = humanizer.isComplexQuestion(message)
                        val chunks = humanizer.humanize(rawText, isComplex)

                        if (chunks.isEmpty()) {
                            if (rawText.isNotBlank()) {
                                addPetMessage(rawText, response.emotion, response.action)
                                if (sm.isTTSEnabled) {
                                    val msg = messages.lastOrNull { !it.isUser }
                                    if (msg != null) triggerTtsAndPlay(rawText, response.emotion, msg)
                                }
                            } else {
                                com.aicompanion.util.AppLogger.w(TAG, "sendToLLM: API响应成功但回复内容为空")
                                addPetMessage("嗯...我好像走神了，能再说一次吗？", Emotion.NEUTRAL, Action.IDLE)
                            }
                        } else {
                            for (i in chunks.indices) {
                                val chunk = chunks[i]
                                if (chunk.text.isBlank()) continue
                                if (i > 0) delay(chunk.delayMs)
                                val emot = if (chunk.isThinking) Emotion.NEUTRAL else response.emotion
                                val act = if (chunk.isThinking) Action.IDLE else response.action
                                addPetMessage(chunk.text, emot, act)
                                if (i == 0 && sm.isTTSEnabled) {
                                    val firstMsg = messages.lastOrNull { !it.isUser }
                                    if (firstMsg != null) {
                                        triggerTtsAndPlay(rawText, response.emotion, firstMsg)
                                    }
                                }
                            }
                        }

                        updatePetDisplay(response)

                        tryAttachVirtualWorldImage(message)

                        contextManager?.addTurn(message, rawText)

                        triggerPredictions()
                        checkTurnsDiaryTrigger()

                        memoryScope.launch {
                            try {
                                contextManager?.evaluateAndUpdateMemory(client)
                            } catch (e: Exception) {
                                com.aicompanion.util.AppLogger.e(TAG, "evaluateAndUpdateMemory: ${e.message}")
                            }
                        }

                        if (contextManager?.needsCompression() == true) {
                            memoryScope.launch {
                                contextManager?.compress()
                            }
                        }

                        val needNewSession = contextManager?.needsNewSession() == true
                        if (needNewSession) {
                            val poolChars = contextManager?.memoryPool?.getPoolCharCount() ?: 0
                            addPetMessage(
                                "📝 记忆池已达${poolChars}字！建议开启新会话以压缩记忆并生成日记。\n点击功能面板 → 新会话 来继续",
                                Emotion.NEUTRAL, Action.IDLE
                            )
                        }
                    }
                } else {
                    addPetMessage("呜...连接不上AI，请检查API设置", Emotion.SAD, Action.IDLE)
                }
            } catch (e: Exception) {
                chatAdapter?.setTypingIndicator(false)
                com.aicompanion.util.AppLogger.e(TAG, "sendToLLm error: ${e.javaClass.simpleName}: ${e.message}", e)
                if (!isFinishing && !isDestroyed) {
                    addPetMessage("出错了: ${e.message}", Emotion.SAD, Action.IDLE)
                }
                Log.e(TAG, "sendToLLM error: ${e.message}", e)
            } finally {
                if (!isFinishing && !isDestroyed) {
                    setLoading(false)
                }
            }
        }
    }

    private fun triggerPredictions() {
        val predictor = chatPredictor ?: return
        val recent = messages.takeLast(10).map { msg ->
            if (msg.isUser) "user" to msg.text else "ai" to msg.text
        }
        if (recent.isEmpty()) return
        val personaId = intent.getStringExtra("persona_id")
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("active_persona_id", "default")
            ?: "default"
        val identity = com.aicompanion.prompt.PromptBuilder.buildIdentity(this, personaId)
        messageScope.launch {
            val predictions = predictor.predictPrivateChat(
                recentMessages = recent,
                personaName = identity.name,
                personaPersonality = identity.personality
            )
            withContext(Dispatchers.Main) {
                showPredictions(predictions)
            }
        }
    }

    private fun showPredictions(predictions: List<String>) {
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
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val cleanText = text.replace(Regex("\\[\\[emotion:\\w+\\]\\]", RegexOption.IGNORE_CASE), "").trim()
        if (cleanText.isBlank()) return
        com.aicompanion.util.AppLogger.d(TAG, "addPetMessage: AI回复 '${cleanText.take(50)}', emotion=${emotion.name}")
        messages.add(ChatMessage(text = cleanText, time = time, isUser = false, emotion = emotion, timestamp = System.currentTimeMillis()))
        chatAdapter?.notifyItemInserted(messages.size - 1)
        recyclerChat?.scrollToPosition(messages.size - 1)
        saveChatHistory()
        saveMessageToFile(messages.last())
        statsManager?.recordAiMessage(cleanText)
        statsManager?.recordEmotion(emotion.name)

        live2dView?.setEmotion(emotion)
        live2dView?.setAction(action)
        if (settingsManager?.live2dEnabled != true) {
            live2dView?.visibility = View.GONE
        }

        val aiName = cachedAiName ?: getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("ai_name", "星尘").also { cachedAiName = it } ?: "星尘"
        val avatarPath = cachedAiAvatarPath ?: getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE)
            .getString("persona_avatar_path", "").also { cachedAiAvatarPath = it } ?: ""

        chatBubblePopup?.show(aiName, cleanText, avatarPath.ifBlank { null })

        if (!hasWindowFocus()) {
            systemMonitor?.showAiMessageNotification(aiName, cleanText)
        }
    }

    private fun triggerTtsAndPlay(text: String, emotion: Emotion, message: ChatMessage) {
        val tm = ttsManager ?: return
        val engineMode = tm.engineMode

        if (engineMode == com.aicompanion.voice.TtsManager.ENGINE_LOCAL ||
            (engineMode == com.aicompanion.voice.TtsManager.ENGINE_AUTO && !tm.isCloudConfigured)) {
            voiceManager?.speak(text, emotion)
            return
        }

        memoryScope.launch {
            try {
                val result = tm.synthesize(text, emotion)
                if (result.success && (result.audioPath != null || result.audioUrl != null)) {
                    message.audioPath = result.audioPath
                    message.audioUrl = result.audioUrl
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            val idx = messages.indexOf(message)
                            if (idx >= 0) chatAdapter?.notifyItemChanged(idx, "audio")
                            tm.playAudio(result.audioPath, result.audioUrl)
                        }
                    }
                } else if (!result.success) {
                    com.aicompanion.util.AppLogger.w(TAG, "TTS失败(${engineMode})，回退本地: ${result.error}")
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            voiceManager?.speak(text, emotion)
                        }
                    }
                }
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e(TAG, "triggerTtsAndPlay: ${e.message}")
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        voiceManager?.speak(text, emotion)
                    }
                }
            }
        }
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
        messages.add(msg)
        chatAdapter?.notifyItemInserted(messages.size - 1)
        recyclerChat?.scrollToPosition(messages.size - 1)
        saveChatHistory()
        if (sender == "user") statsManager?.recordStickerSent() else statsManager?.recordStickerReceived()
        if (sender == "user") {
            sendToLLM("[用户发送了一个表情包: $stickerName]")
        }
    }

    private suspend fun getPersonaInfo(query: String = ""): Pair<String, String> {
        val personaId = intent.getStringExtra("persona_id")
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("active_persona_id", "default")
            ?: "default"

        val identity = com.aicompanion.prompt.PromptBuilder.buildIdentity(this, personaId)

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

            if (query.isNotBlank() && RagConfig.personaRagEnabled) {
                checkAndRebuildPersonaIndex()
                if (personaRagManager?.isReady() == true) {
                    try {
                        val ragChunks = withContext(Dispatchers.IO) {
                            personaRagManager!!.retrieveSync(query, 3)
                        }
                        if (ragChunks.isNotEmpty()) {
                            append("\n\n[相关设定]")
                            for (chunk in ragChunks) {
                                append("\n$chunk")
                            }
                        }
                    } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "getPersonaInfo: ${e.message}") }
                }
            }

            val style = settingsManager?.languageStyle?.name?.lowercase() ?: "normal"
            when (style) {
                "tsundere" -> append("\n傲娇，嘴硬心软。")
                "cute" -> append("\n可爱，用颜文字和拟声词。")
            }

            val ctxBlock = contextManager?.getContextBlock() ?: ""
            if (ctxBlock.isNotBlank()) {
                append("\n\n[上下文]\n$ctxBlock")
            }

            append(com.aicompanion.prompt.PromptBuilder.getCoreRules(this@MainActivity))
            append("\n- 有强烈情绪时用send_sticker。可以反问和主动关心。保持角色不跳戏。")
            append("\n- 当用户提到过去的事情、问你记不记得什么、或需要回忆历史经历时，主动使用search_diary工具搜索日记。")
        }
        return Pair(identity.name, fullPrompt)
    }

    private fun checkAndRebuildPersonaIndex() {
        val rag = personaRagManager ?: return
        val fields = buildPersonaFields()
        val newHash = fields.values.joinToString("|").hashCode().toString()
        if (rag.currentHash() == newHash) return
        com.aicompanion.util.AppLogger.d(TAG, "checkAndRebuildPersonaIndex: hash changed, rebuilding")
        messageScope.launch {
            rag.buildIndex(fields)
        }
    }

    private fun searchMemory(query: String, topK: Int): String {
        val sb = StringBuilder()

        val poolEntries = contextManager?.memoryPool?.getAll() ?: emptyList()
        if (poolEntries.isNotEmpty()) {
            val poolTexts = poolEntries.map { it.content }
            val embedder = com.aicompanion.rag.TfidfEmbedder()
            embedder.buildVocabulary(poolTexts)
            val queryVec = embedder.embedSingleSync(query)
            val vecs = embedder.embedSync(poolTexts)
            val scored = vecs.mapIndexed { i, v -> i to cosineSim(queryVec, v) }
                .sortedByDescending { it.second }
                .filter { it.second > 0.1f }
            if (scored.isNotEmpty()) {
                sb.appendLine("[短期记忆池]")
                scored.take(topK).forEach { (i, _) ->
                    sb.appendLine("- ${poolEntries[i].content}")
                }
            }
        }

        if (sb.isEmpty()) {
            return "未找到与「$query」相关的短期记忆。如需查找更早的记录，请使用search_diary工具。"
        }
        com.aicompanion.util.AppLogger.d(TAG, "searchMemory: '$query' -> ${sb.length} chars")
        return sb.toString().trimEnd()
    }

    private fun searchDiary(query: String, topK: Int): String {
        try {
            val dm = com.aicompanion.diary.DiaryManager(this, activePersonaId)
            val diaryResults = dm.searchDiariesRag(query, topK)
            if (diaryResults.isEmpty()) {
                return "未找到与「$query」相关的日记记录。"
            }
            val sb = StringBuilder()
            sb.appendLine("[日记记录]")
            diaryResults.forEach { entry ->
                sb.appendLine("📅 ${entry.date} ${entry.title} ${entry.moodEmoji}")
                sb.appendLine(entry.content.take(300))
                sb.appendLine()
            }
            com.aicompanion.util.AppLogger.d(TAG, "searchDiary: '$query' -> ${diaryResults.size} results, ${sb.length} chars")
            return sb.toString().trimEnd()
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "searchDiary failed: ${e.message}")
            return "日记搜索出错: ${e.message}"
        }
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
        try {
            val scheme = ThemeManager.getCurrentScheme(this)
            val primaryDark = safeParseColor(scheme.primaryColorDark, 0xFF1a1a2e.toInt())
            val accentColor = safeParseColor(scheme.accentColor, 0xFFaabbdd.toInt())

            findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)?.setBackgroundColor(primaryDark)
            findViewById<LinearLayout>(R.id.layout_input)?.setBackgroundColor(primaryDark)

            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val bgPath = prefs.getString("chat_background", "")
            if (!bgPath.isNullOrEmpty()) {
                val path = bgPath
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val file = File(path)
                        if (!file.exists()) return@launch
                        val bgOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(path, bgOptions)
                        bgOptions.inSampleSize = calculateSampleSize(bgOptions.outWidth, bgOptions.outHeight, 1080, 1920)
                        bgOptions.inJustDecodeBounds = false
                        val bmp = BitmapFactory.decodeFile(path, bgOptions) ?: return@launch
                        withContext(Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) {
                                findViewById<ImageView>(R.id.iv_chat_background)?.setImageBitmap(bmp)
                                findViewById<ImageView>(R.id.iv_chat_background)?.alpha = 0.3f
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            ThemeManager.applyTheme(this)
            chatAdapter?.cacheSkinSettings(this)
        } catch (e: Exception) {
            Log.e(TAG, "applyTheme: ${e.message}")
        }
    }

    private fun updateAffectionDisplay() {
        val am = affectionManager ?: return
        tvDaysLabel?.text = "第${am.getDaysSinceFirstUse()}天"
        progressAffection?.progress = am.affectionLevel
        val personaPrefs = getSharedPreferences("persona_data_$activePersonaId", MODE_PRIVATE)
        val name = personaPrefs.getString("persona_name", null)
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("ai_name", "星尘")
            ?: "星尘"
        tvPetName?.text = "✨ $name"
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
                    com.aicompanion.util.AppLogger.d(TAG, "User personality summarized: affection=${am.affectionLevel}")
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
                    com.aicompanion.util.AppLogger.d(TAG, "Personality evolved: affection=${am.affectionLevel}")
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
    }

    private fun performCheckIn() {
        val am = affectionManager ?: return
        val ach = achievementManager ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val prefs = getSharedPreferences("checkin_data", MODE_PRIVATE)
        val lastDate = prefs.getString("last_checkin", "")
        val streak = prefs.getInt("streak", 0)

        if (lastDate == today) {
            Toast.makeText(this, "今天已经签到过了~", Toast.LENGTH_SHORT).show()
            return
        }

        val newStreak = if (lastDate == getYesterday()) streak + 1 else 1
        prefs.edit().putString("last_checkin", today).putInt("streak", newStreak).apply()

        am.addAffection(2, "签到")
        val checkinAch = ach.updateProgress("checkin", newStreak)
        if (checkinAch != null) showAchievementUnlock(checkinAch)

        addPetMessage("📅 签到成功！连续${newStreak}天~好感+2", Emotion.HAPPY, Action.TAIL_FLICK)
        updateAffectionDisplay()
        checkAiMomentTrigger()
    }

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
        val globalVw = com.aicompanion.virtualworld.VirtualWorldManager(this, "")
        if (globalVw.isEnabled) return globalVw

        try {
            val gcManager = com.aicompanion.groupchat.GroupChatManager(this)
            gcManager.load()
            for (group in gcManager.getAllGroups()) {
                val groupVw = com.aicompanion.virtualworld.VirtualWorldManager(this, group.id)
                if (groupVw.isEnabled) return groupVw
            }
        } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "findActiveVirtualWorld: ${e.message}") }

        return null
    }

    private fun buildSystemContext(message: String): String {
        val lower = message.lowercase()
        val sb = StringBuilder()

        val needTime = listOf("几点", "什么时间", "时间", "日期", "今天几号", "星期", "几点钟", "now time", "what time").any { lower.contains(it) }
        val needBattery = listOf(
            "电量", "电池", "电量百分比", "还有多少电", "还剩多少电", "电量剩余",
            "手机电量", "电池电量", "充", "充电", "power", "battery"
        ).any { lower.contains(it) } || lower.matches(Regex(".*\\b电.*\\b.*"))

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
        val live2dLog = live2dView?.getLog()?.takeLast(1000) ?: ""
        val historyLog = logHistory.joinToString("\n").takeLast(500)

        val errorLogs = allLogs.lines().filter { it.contains("E/") }.joinToString("\n")
        val warnLogs = allLogs.lines().filter { it.contains("W/") }.joinToString("\n")

        val fullLog = buildString {
            if (errorLogs.isNotBlank()) {
                append("=== ❌ 错误日志 ===\n\n")
                append(errorLogs)
                append("\n\n")
            }
            if (warnLogs.isNotBlank()) {
                append("=== ⚠️ 警告日志 ===\n\n")
                append(warnLogs)
                append("\n\n")
            }
            append("=== 📋 全部日志 (最近300条) ===\n\n")
            append(if (allLogs.isNotBlank()) allLogs else "暂无日志\n")
            if (live2dLog.isNotBlank()) {
                append("\n\n=== Live2D 日志 ===\n")
                append(live2dLog)
            }
            if (historyLog.isNotBlank()) {
                append("\n\n=== 历史 ===\n")
                append(historyLog)
            }
        }

        val scrollView = android.widget.ScrollView(this).apply {
            val textView = android.widget.TextView(this@MainActivity).apply {
                text = fullLog
                textSize = 11f
                setPadding(32, 24, 32, 24)
                setTextIsSelectable(true)
            }
            addView(textView)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("📋 运行日志")
            .setView(scrollView)
            .setPositiveButton("复制全部") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("日志", fullLog))
                Toast.makeText(this@MainActivity, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showTutorial() {
        if (isFinishing || isDestroyed) return
        val steps = listOf(
            "👋 欢迎使用星尘 AI 桌宠！" to "我是你的专属AI伙伴，可以陪你聊天、记录心情和日记。\n\n点击「下一步」了解基本操作~",
            "💬 聊天与表情" to "在底部输入框发消息和我聊天~\n\n点击 😊 按钮选择你的心情，我会根据你的情绪回复哦！",
            "⚙️ API 配置" to "点击右上角 ⚙ 设置按钮，配置你的 AI API。\n\n选择厂商后会自动填充地址和模型，只需填入 API 密钥即可~",
            "🎮 更多功能" to "• 📅 签到 — 每日打卡领好感\n• 🏆 成就 — 解锁各种有趣成就\n• 📔 日记 — 自动生成每日日记\n• 🍅 专注 — 番茄钟计时",
            "🐾 Live2D 互动" to "• 点击 ⋮ 按钮打开功能面板\n• 在设置中可以调整模型大小\n• 悬浮窗模式让我在桌面陪伴你~"
        )
        var step = 0
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(steps[step].first)
            .setMessage(steps[step].second)
            .setPositiveButton("下一步") { _, _ -> }
            .setNegativeButton("跳过", null)
            .create()
        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            step++
            if (step < steps.size) {
                dialog.setTitle(steps[step].first)
                dialog.setMessage(steps[step].second)
                if (step == steps.size - 1) dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).text = "完成"
            } else {
                dialog.dismiss()
            }
        }
    }

    private fun showAutoOperationDialog() {
        if (isFinishing || isDestroyed) return

        val sm = settingsManager ?: return
        if (sm.chatApiUrl.isBlank()) {
            Toast.makeText(this, "请先在设置中配置API", Toast.LENGTH_SHORT).show()
            return
        }

        if (!com.aicompanion.screen.AutoOperator.isServiceReady()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("需要无障碍权限")
                .setMessage("手机自动化需要开启「星尘AI」的无障碍服务才能操作手机。\n\n请前往：系统设置 → 无障碍 → 已安装应用 → 星尘AI → 开启服务\n\n开启后重新进入此功能即可使用。")
                .setPositiveButton("去开启") { _, _ ->
                    try { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "showAutoOperationDialog: ${e.message}") }
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        val input = android.widget.EditText(this).apply {
            hint = "例：帮我把音量调到最大 / 打开微信找张三"
            setTextColor(0xFFe8e8f0.toInt())
            setHintTextColor(0xFF667788.toInt())
            setBackgroundResource(android.R.color.transparent)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(0xFF0f0c29.toInt())
            addView(input)
        }

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("🤖 手机自动化")
            .setView(container)
            .setPositiveButton("开始执行", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val request = input.text.toString().trim()
                if (request.isEmpty()) return@setOnClickListener
                dialog.dismiss()
                executeAutoOperation(request)
            }
        }

        dialog.show()
    }

    private fun executeAutoOperation(userRequest: String) {
        if (isFinishing || isDestroyed) return

        Toast.makeText(this, "🤖 正在分析指令...", Toast.LENGTH_LONG).show()
        addPetMessage("主人让我帮你操作手机：$userRequest", com.aicompanion.models.Emotion.HAPPY, com.aicompanion.models.Action.IDLE)

        messageScope.launch {
            try {
                val screenInfo = withContext(Dispatchers.IO) {
                    com.aicompanion.screen.AutoOperator.formatScreenForLLM()
                }

                val llmResult = withContext(Dispatchers.IO) {
                    apiClient?.analyzeAutoOperation(userRequest, screenInfo)
                }

                if (llmResult.isNullOrBlank() || llmResult == "[]") {
                    addPetMessage("抱歉，我没法理解这个操作该怎么办😅", com.aicompanion.models.Emotion.SAD, com.aicompanion.models.Action.IDLE)
                    return@launch
                }

                val actions = com.aicompanion.screen.AutoOperator.parseActionsFromLLM(llmResult)
                if (actions.isEmpty()) {
                    addPetMessage("抱歉，我没法理解这个操作该怎么办😅", com.aicompanion.models.Emotion.SAD, com.aicompanion.models.Action.IDLE)
                    return@launch
                }

                addPetMessage("明白了！正在执行${actions.size}个步骤...", com.aicompanion.models.Emotion.TSUNDERE, com.aicompanion.models.Action.EAR_TWITCH)

                var successCount = 0
                for ((i, action) in actions.withIndex()) {
                    if (isFinishing || isDestroyed) return@launch

                    val stepDesc = when (action.type) {
                        "click" -> "点击「${action.text}」"
                        "back" -> "返回"
                        "home" -> "回到桌面"
                        "scroll" -> "滚动"
                        "wait" -> "等待${action.durationMs}ms"
                        "notifications" -> "打开通知栏"
                        "recents" -> "打开最近任务"
                        else -> action.type
                    }

                    val stepResult = withContext(Dispatchers.IO) {
                        com.aicompanion.screen.AutoOperator.executeAction(action)
                    }

                    if (stepResult) successCount++

                    withContext(Dispatchers.IO) { Thread.sleep(800) }
                }

                val reportStr = buildString {
                    append("完成！${successCount}/${actions.size}个步骤执行成功")
                    if (successCount == actions.size) {
                        append("，搞定啦~")
                    } else {
                        append("，有几个步骤没成功，可能界面不太一样")
                    }
                }
                addPetMessage(reportStr, com.aicompanion.models.Emotion.HAPPY, com.aicompanion.models.Action.IDLE)

            } catch (e: Exception) {
                Log.e(TAG, "AutoOperation failed: ${e.message}")
                addPetMessage("操作失败了：${e.message}", com.aicompanion.models.Emotion.SAD, com.aicompanion.models.Action.IDLE)
            }
        }
    }

    private fun pickBackgroundImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try { startActivityForResult(intent, REQUEST_PICK_IMAGE) } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "pickBackgroundImage: ${e.message}") }
    }

    private fun startFocusTimer() {
        focusActive = true
        focusSecondsLeft = 25 * 60
        focusEndTime = System.currentTimeMillis() + focusSecondsLeft * 1000L
        addPetMessage("🍅 专注模式启动！25分钟，我会陪着你~", Emotion.HAPPY, Action.STRETCH)
        focusRunnable = object : Runnable {
            override fun run() {
                if (!focusActive) return
                focusSecondsLeft = ((focusEndTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                if (focusSecondsLeft <= 0) completeFocusSession()
                else handler.postDelayed(this, 1000)
            }
        }
        focusRunnable?.let { handler.postDelayed(it, 1000) }
    }

    private fun completeFocusSession() {
        focusActive = false
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val pomodoros = prefs.getInt("total_pomodoros", 0) + 1
        prefs.edit().putInt("total_pomodoros", pomodoros).apply()
        val ach = achievementManager?.updateProgress("pomodoro", pomodoros)
        if (ach != null) showAchievementUnlock(ach)
        affectionManager?.addAffection(3, "专注完成")
        updateAffectionDisplay()
        checkAiMomentTrigger()
        addPetMessage("🎉 专注完成！你真棒！好感+3", Emotion.HAPPY, Action.TAIL_FLICK)
    }

    private fun cancelFocusTimer() {
        focusActive = false
        focusRunnable?.let { handler.removeCallbacks(it) }
        addPetMessage("专注取消了，没关系，下次继续~", Emotion.NEUTRAL, Action.IDLE)
    }

    private fun scheduleDiaryTimer() {
        val sm = settingsManager ?: return
        val mode = sm.diaryTriggerMode
        if (mode == com.aicompanion.settings.DiaryTriggerMode.MANUAL) return
        if (mode == com.aicompanion.settings.DiaryTriggerMode.MSG_50) return

        val delayMs = when (mode) {
            com.aicompanion.settings.DiaryTriggerMode.HOURLY -> 60 * 60 * 1000L
            com.aicompanion.settings.DiaryTriggerMode.EVERY_2H -> 2 * 60 * 60 * 1000L
            else -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 22)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis - System.currentTimeMillis()
            }
        }

        diaryRunnable = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            val dm = com.aicompanion.diary.DiaryManager(this, activePersonaId)
            val am = affectionManager ?: return@Runnable
            val todayDiary = dm.getDiaryByDate(
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            )
            if (todayDiary == null && messages.count { it.isUser } >= 5) {
                autoTriggerDiary(dm, am)
            }
            scheduleDiaryTimer()
        }
        handler.postDelayed(diaryRunnable!!, delayMs)
    }

    private fun checkTurnsDiaryTrigger() {
        val sm = settingsManager ?: return
        val mode = sm.diaryTriggerMode
        if (mode != com.aicompanion.settings.DiaryTriggerMode.MSG_50) return

        val ctxMgr = contextManager ?: return
        val totalTurns = ctxMgr.sessionManager.currentTurnCount
        if (totalTurns < 50) return

        val prefs = getSharedPreferences("diary_trigger_$activePersonaId", MODE_PRIVATE)
        val lastTriggeredTurns = prefs.getInt("last_diary_turns_trigger", 0)
        if (totalTurns - lastTriggeredTurns < 50) return

        val dm = com.aicompanion.diary.DiaryManager(this, activePersonaId)
        val am = affectionManager ?: return
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val todayDiary = dm.getDiaryByDate(today)
        if (todayDiary != null) return

        if (messages.count { it.isUser } < 5) return

        prefs.edit().putInt("last_diary_turns_trigger", totalTurns).apply()
        autoTriggerDiary(dm, am)
    }

    private fun triggerManualDiary() {
        val dm = com.aicompanion.diary.DiaryManager(this, activePersonaId)
        val am = affectionManager ?: return
        setLoading(true)
        addPetMessage("正在为你写今天的日记...", Emotion.NEUTRAL, Action.IDLE)

        val task = {
            autoTriggerDiary(dm, am)
            messageScope.launch {
                kotlinx.coroutines.delay(3000)
                setLoading(false)
            }
        }
        task.invoke()
    }

    private var diaryWriting = false

    private fun autoTriggerDiary(dm: com.aicompanion.diary.DiaryManager, am: AffectionManager) {
        val client = apiClient ?: return
        val sm = settingsManager ?: return
        if (sm.chatApiUrl.isBlank()) return
        if (diaryWriting) return

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val existingDiary = dm.getDiaryByDate(today)
        if (existingDiary != null && dm.getTodayDiaryAppendCount() >= 3) return
        if (!dm.canUpdateDiary()) return

        diaryWriting = true
        dm.markDiaryUpdated()

        messageScope.launch {
            try {
                val persona = getPersonaInfo()
                val chatTexts = messages.map { it.text }
                if (chatTexts.isEmpty()) return@launch

                val combined = chatTexts.joinToString(" | ")

                val poolBlock = contextManager?.memoryPool?.getPoolBlock() ?: ""
                val diaryContext = if (poolBlock.isNotBlank()) {
                    "今天记忆池内容:\n$poolBlock\n\n聊天记录:\n$combined"
                } else {
                    combined
                }

                val localMood = analyzeLocalMood(diaryContext)

                val llmContent = withContext(Dispatchers.IO) {
                    client.generateDiaryContent(
                        chatTexts, persona.first, persona.second, localMood,
                        when (localMood) {
                            "happy" -> "🥰"
                            "sad" -> "😢"
                            "excited" -> "🤩"
                            "calm" -> "😌"
                            "sentimental" -> "🌙"
                            else -> "😊"
                        },
                        am.affectionLevel,
                        isUpdate = existingDiary != null
                    )
                }

                if (llmContent != null && llmContent.isNotBlank()) {
                    val currentExisting = dm.getDiaryByDate(today)
                    if (currentExisting != null) {
                        if (dm.getTodayDiaryAppendCount() < 3) {
                            dm.appendLlmDiaryUpdate(llmContent, chatTexts, am.affectionLevel)
                        }
                    } else {
                        dm.saveLlmDiary(llmContent, chatTexts, am.affectionLevel)
                    }
                    Toast.makeText(this@MainActivity, "📔 日记已更新", Toast.LENGTH_SHORT).show()
                } else {
                    dm.updateOrGenerateDailyDiary(chatTexts, am.affectionLevel)
                    Toast.makeText(this@MainActivity, "📔 日记已生成（本地模式）", Toast.LENGTH_SHORT).show()
                }

                val sm = settingsManager
                if (sm != null && sm.userPersonalityDef.isBlank()) {
                    try {
                        val summaryResult = client.summarizeUserPersonality(
                            personaName = persona.first,
                            recentChatSummary = chatTexts.takeLast(30).joinToString("\n"),
                            currentSummary = sm.getAiSummarizedPersonality(activePersonaId),
                            affectionLevel = am.affectionLevel
                        )
                        if (!summaryResult.isNullOrBlank()) {
                            sm.setAiSummarizedPersonality(activePersonaId, summaryResult)
                            com.aicompanion.prompt.PromptBuilder.invalidateCache()
                        }
                    } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "autoTriggerDiary: ${e.message}") }
                }
            } catch (e: Exception) {
                Log.d(TAG, "LLM diary failed, falling back: ${e.message}")
                val chatTexts = messages.map { it.text }
                dm.updateOrGenerateDailyDiary(chatTexts, am.affectionLevel)
                Toast.makeText(this@MainActivity, "📔 日记已生成（本地模式）", Toast.LENGTH_SHORT).show()
            } finally {
                diaryWriting = false
            }
        }
    }

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
                messages.clear()
                chatAdapter?.notifyItemRangeRemoved(0, oldSize)
                saveChatHistory()
                addPetMessage("✨ 新会话已开启！我已保留了核心记忆并生成了日记~", Emotion.HAPPY, Action.TAIL_FLICK)
            } catch (e: Exception) {
                Log.e(TAG, "createNewSession: ${e.message}")
                addPetMessage("开启新会话失败: ${e.message}", Emotion.SAD, Action.IDLE)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun analyzeLocalMood(text: String): String {
        val lower = text.lowercase()
        val happyWords = listOf("哈哈", "开心", "喜欢", "太好了", "棒", "nice", "love", "good", "可爱")
        val sadWords = listOf("难过", "伤心", "哭", "不好", "烦", "生气", "sad", "bad", "讨厌")
        val excitedWords = listOf("厉害", "冲", "加油", "go", "yes", "完美", "了不起", "冲啊")
        val calmWords = listOf("安静", "舒服", "平静", "放松", "休息", "calm", "peace", "冥想")
        val sentimentalWords = listOf("回忆", "想念", "记得", "曾经", "星空", "月光", "诗", "夜晚")
        val scores = mapOf(
            "happy" to happyWords.count { lower.contains(it) },
            "sad" to sadWords.count { lower.contains(it) },
            "excited" to excitedWords.count { lower.contains(it) },
            "calm" to calmWords.count { lower.contains(it) },
            "sentimental" to sentimentalWords.count { lower.contains(it) }
        )
        val max = scores.maxByOrNull { it.value }
        return if (max != null && max.value > 0) max.key else "normal"
    }

    private fun triggerProactiveChat() {
        if (isDestroyed || isFinishing) return
        if (isInForeground) return
        val client = apiClient ?: return
        val sm = settingsManager ?: return

        val chatHistory = messages.takeLast(sm.contextTurns).map { msg ->
            Pair(msg.isUser, msg.text)
        }

        messageScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    if (sm.chatApiUrl.isNotBlank()) {
                        val memCtx = contextManager?.memoryPool?.getPoolBlock()
                        val persona = getPersonaInfo()
                        client.generateNagContent(persona.first, persona.second, memoryContext = memCtx, chatHistory = chatHistory)
                    } else null
                }
                if (response != null && response.text.isNotBlank() && response.errorMessage == null) {
                    addPetMessage(response.text, response.emotion, response.action)
                    updatePetDisplay(response)
                } else {
                    val fallback = proactiveEngine?.getIdlePhrase()
                    if (fallback != null && !isDestroyed) {
                        addPetMessage(fallback.first, fallback.second, fallback.third)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "LLM proactive chat failed: ${e.message}")
                val fallback = proactiveEngine?.getIdlePhrase()
                if (fallback != null && !isDestroyed) {
                    addPetMessage(fallback.first, fallback.second, fallback.third)
                }
            }
        }
    }

    private fun scheduleProactiveChat() {
        val sm = settingsManager ?: return
        if (!sm.isNagEnabled) return
        proactiveRunnable = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            if (proactiveEngine?.shouldTriggerInteraction(sm.nagFrequency.name.lowercase()) == true) {
                triggerProactiveChat()
            }
            scheduleProactiveChat()
        }
        handler.postDelayed(proactiveRunnable!!, 120000L)
    }

    private fun scheduleVirtualWorldTick() {
        virtualWorldRunnable = Runnable {
            if (isFinishing || isDestroyed) return@Runnable

            val worldsToTick = mutableListOf<com.aicompanion.virtualworld.VirtualWorldManager>()

            val globalVw = com.aicompanion.virtualworld.VirtualWorldManager(this, "")
            if (globalVw.isEnabled && globalVw.isRunning) {
                worldsToTick.add(globalVw)
            }

            try {
                val gcManager = com.aicompanion.groupchat.GroupChatManager(this)
                gcManager.load()
                for (group in gcManager.getAllGroups()) {
                    val groupVw = com.aicompanion.virtualworld.VirtualWorldManager(this, group.id)
                    if (groupVw.isEnabled && groupVw.isRunning) {
                        worldsToTick.add(groupVw)
                    }
                }
            } catch (e: Exception) { com.aicompanion.util.AppLogger.e("MainActivity", "scheduleVirtualWorldTick: ${e.message}") }

            for (vwManager in worldsToTick) {
                if (vwManager.shouldTick()) {
                    messageScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                vwManager.runSimulationTick()
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Virtual world tick failed: ${e.message}")
                        }
                    }
                }
            }

            handler.postDelayed(virtualWorldRunnable!!, 60000L)
        }
        handler.postDelayed(virtualWorldRunnable!!, 60000L)
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
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("first_launch", true)) {
            prefs.edit().putBoolean("first_launch", false).apply()
            showTutorial()
            messageScope.launch {
                val name = getPersonaInfo().first
                addPetMessage("你好呀！我是${name}，你的AI伙伴~", Emotion.HAPPY, Action.TAIL_FLICK)
            }
        }
    }

    private fun getChatPrefsName(): String {
        val personaId = intent.getStringExtra("persona_id") ?: "default"
        return "chat_history_$personaId"
    }

    private fun loadChatHistory() {
        try {
            val stored = chatStorage.getRecentMessages("persona", activePersonaId, 200)
            if (stored.isNotEmpty()) {
                val result = stored.map { s ->
                    ChatMessage(
                        id = s.id, text = s.text, time = s.time, isUser = s.isUser,
                        userMood = s.userMood, feedback = s.feedback,
                        emotion = try { Emotion.valueOf(s.emotion) } catch (_: Exception) { Emotion.NEUTRAL },
                        timestamp = s.timestamp, isFavorited = s.isFavorited,
                        reactionEmoji = s.reactionEmoji, stickerPath = s.stickerPath,
                        audioPath = s.audioPath, audioUrl = s.audioUrl
                    )
                }
                messages.clear()
                messages.addAll(result)
                chatAdapter?.notifyItemRangeInserted(0, messages.size)
                if (messages.isNotEmpty()) recyclerChat?.scrollToPosition(messages.size - 1)
                com.aicompanion.util.AppLogger.d(TAG, "loadChatHistory: 从文件加载了${result.size}条消息")
                return
            }

            val prefs = getSharedPreferences(getChatPrefsName(), MODE_PRIVATE)
            val json = prefs.getString("messages", "[]") ?: "[]"
            val arr = org.json.JSONArray(json)
            val result = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(ChatMessage(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    text = obj.optString("text", ""),
                    time = obj.optString("time", ""),
                    isUser = obj.optBoolean("isUser", false),
                    userMood = obj.optString("userMood", ""),
                    feedback = obj.optInt("feedback", 0),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    isFavorited = obj.optBoolean("isFavorited", false),
                    reactionEmoji = obj.optString("reactionEmoji", "")
                ))
            }
            messages.clear()
            messages.addAll(result)
            chatAdapter?.notifyItemRangeInserted(0, messages.size)
            if (messages.isNotEmpty()) recyclerChat?.scrollToPosition(messages.size - 1)
            com.aicompanion.util.AppLogger.d(TAG, "loadChatHistory: 从SP加载了${result.size}条消息")

            if (result.isNotEmpty()) {
                memoryScope.launch {
                    try {
                        val migrated = chatStorage.migrateFromSharedPreferences(getChatPrefsName(), "persona", activePersonaId)
                        com.aicompanion.util.AppLogger.i(TAG, "loadChatHistory: 迁移了${migrated}条消息到文件存储")
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "loadChatHistory失败: ${e.message}", e)
        }
    }

    private var chatSaveJob: kotlinx.coroutines.Job? = null

    private fun saveChatHistory() {
        chatSaveJob?.cancel()
        chatSaveJob = memoryScope.launch(Dispatchers.IO) {
            delay(500)
            try {
                doSaveChatHistory()
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e(TAG, "saveChatHistory: ${e.message}")
            }
        }
    }

    private fun doSaveChatHistory() {
        try {
            val snapshot = messages.takeLast(100)
            val arr = org.json.JSONArray()
            snapshot.forEach { msg ->
                arr.put(org.json.JSONObject().apply {
                    put("id", msg.id)
                    put("text", msg.text)
                    put("time", msg.time)
                    put("isUser", msg.isUser)
                    put("userMood", msg.userMood)
                    put("feedback", msg.feedback)
                    put("timestamp", msg.timestamp)
                    put("isFavorited", msg.isFavorited)
                    put("reactionEmoji", msg.reactionEmoji)
                })
            }
            val json = arr.toString()
            getSharedPreferences(getChatPrefsName(), MODE_PRIVATE).edit()
                .putString("messages", json).apply()
        } catch (e: OutOfMemoryError) {
            try {
                val arr = org.json.JSONArray()
                messages.takeLast(20).forEach { msg ->
                    arr.put(org.json.JSONObject().apply {
                        put("id", msg.id)
                        put("text", msg.text)
                        put("time", msg.time)
                        put("isUser", msg.isUser)
                    })
                }
                getSharedPreferences(getChatPrefsName(), MODE_PRIVATE).edit()
                    .putString("messages", arr.toString()).apply()
            } catch (e2: Exception) {
                com.aicompanion.util.AppLogger.e(TAG, "saveChatHistory OOM恢复失败: ${e2.message}", e2)
            }
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "saveChatHistory失败: ${e.message}", e)
        }
    }

    private fun saveMessageToFile(msg: ChatMessage) {
        memoryScope.launch {
            try {
                chatStorage.addMessage("persona", activePersonaId, com.aicompanion.storage.StoredMessage(
                    id = msg.id, text = msg.text, time = msg.time, isUser = msg.isUser,
                    userMood = msg.userMood, feedback = msg.feedback,
                    emotion = msg.emotion.name, timestamp = msg.timestamp,
                    isFavorited = msg.isFavorited, reactionEmoji = msg.reactionEmoji,
                    stickerPath = msg.stickerPath,
                    audioPath = msg.audioPath, audioUrl = msg.audioUrl
                ))
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e(TAG, "saveMessageToFile: ${e.message}")
            }
        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val file = File(filesDir, "chat_bg_${System.currentTimeMillis()}.jpg")
                    contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                        .putString("chat_background", file.absolutePath).apply()
                    applyTheme()
                } catch (e: Exception) {
                    Toast.makeText(this, "设置背景失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
        if (requestCode == REQUEST_STICKER_PICK && resultCode == Activity.RESULT_OK && data != null) {
            val stickerPath = data.getStringExtra("sticker_path")
            if (!stickerPath.isNullOrEmpty()) {
                addStickerMessage("user", stickerPath)
            }
        }
        if (requestCode == REQUEST_IMAGE_UPLOAD && resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data ?: return
            try {
                val dir = File(filesDir, "chat_images")
                dir.mkdirs()
                val fileName = "img_${System.currentTimeMillis()}.jpg"
                val destFile = File(dir, fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                addStickerMessage("user", destFile.absolutePath)
                sendToLLM("[用户发送了一张图片]")
            } catch (e: Exception) {
                Toast.makeText(this, "图片上传失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
        if (isFinishing || isDestroyed) return

        chatAdapter?.cacheSkinSettings(this)

        if (settingsManager?.live2dEnabled == true) {
            loadLive2DSettings()
            recyclerChat?.setWillNotDraw(true)
            val view = live2dView
            if (view != null && !view.isDestroyed) {
                view.post {
                    view.translationX = offsetX
                    view.translationY = offsetY
                    view.setModelScale(modelBaseScale.coerceIn(0.3f, 3.0f))
                }
            }
            val currentModelPath = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("active_model_path", "")
            if (currentModelPath != lastLoadedModelPath) {
                loadLive2DModel()
            }
        } else {
            hideLive2DView()
            recyclerChat?.setWillNotDraw(false)
        }

        rebuildApiClient()
        updateWeather()
        updateAffectionDisplay()

        if (proactiveRunnable != null) scheduleProactiveChat()
        if (virtualWorldRunnable != null) scheduleVirtualWorldTick()
        if (diaryRunnable != null) scheduleDiaryTimer()
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
        focusRunnable?.let { handler.removeCallbacks(it) }
        proactiveRunnable?.let { handler.removeCallbacks(it) }
        virtualWorldRunnable?.let { handler.removeCallbacks(it) }
        diaryRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        focusRunnable?.let { handler.removeCallbacks(it) }
        proactiveRunnable?.let { handler.removeCallbacks(it) }
        virtualWorldRunnable?.let { handler.removeCallbacks(it) }
        diaryRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable?.let { handler.removeCallbacks(it) }
        chatSaveJob?.cancel()
        try { doSaveChatHistory() } catch (_: Exception) {}
        messageScope.cancel()
        systemMonitor?.stopMonitoring()
        voiceManager?.cleanup()
        ttsManager?.cleanup()
        live2dView?.cleanup()
        super.onDestroy()
    }
}
