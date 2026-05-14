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
import com.aicompanion.R
import com.aicompanion.affection.AffectionManager
import com.aicompanion.gamify.AchievementManager
import com.aicompanion.interaction.ProactiveInteractionEngine
import com.aicompanion.live2d.Live2DWebView
import com.aicompanion.memory.MemoryManager
import com.aicompanion.models.Action
import com.aicompanion.models.ChatResponse
import com.aicompanion.models.Emotion
import com.aicompanion.network.ApiClient
import com.aicompanion.search.WebSearchEngine
import com.aicompanion.settings.SettingsManager
import com.aicompanion.theme.ThemeManager
import com.aicompanion.voice.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val text: String,
    val time: String,
    val isUser: Boolean,
    val userMood: String = "",
    var feedback: Int = 0,
    val emotion: Emotion = Emotion.NEUTRAL,
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_PICK_IMAGE = 100
    }

    private var live2dView: Live2DWebView? = null
    private var recyclerChat: RecyclerView? = null
    private var etMessage: EditText? = null
    private var btnSend: ImageButton? = null
    private var btnVoice: ImageButton? = null
    private var btnSettings: ImageButton? = null
    private var btnMood: ImageButton? = null
    private var btnMore: ImageButton? = null
    private var tvWeather: TextView? = null
    private var tvDaysLabel: TextView? = null
    private var progressAffection: ProgressBar? = null
    private var tvPetName: TextView? = null
    private var tvStatus: TextView? = null
    private var chipEmotion: com.google.android.material.chip.Chip? = null
    private var chipAction: com.google.android.material.chip.Chip? = null
    private var ivAiAvatarSmall: ImageView? = null

    private var settingsManager: SettingsManager? = null
    private var affectionManager: AffectionManager? = null
    private var achievementManager: AchievementManager? = null
    private var apiClient: ApiClient? = null
    private var chatAdapter: ChatAdapter? = null
    private var voiceManager: VoiceManager? = null
    private var proactiveEngine: ProactiveInteractionEngine? = null
    private var memoryManager: MemoryManager? = null
    private var momentsManager: com.aicompanion.memory.MemorableMomentsManager? = null
    private var systemMonitor: com.aicompanion.services.SystemMonitor? = null
    private var searchEngine: WebSearchEngine? = null
    private var aiActionManager: com.aicompanion.action.AIActionManager? = null

    private val messages = mutableListOf<ChatMessage>()
    private val messageScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var isInForeground = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            initStep("Views") { initViews() }
            initStep("SettingsManager") { settingsManager = SettingsManager(this) }
            initStep("EnsureDirs") { ensureAppDirs() }
            initStep("AffectionManager") { affectionManager = AffectionManager(this) }
            initStep("AchievementManager") { achievementManager = AchievementManager(this) }
            initStep("MemoryManager") { memoryManager = MemoryManager(this) }
            initStep("MomentsManager") { momentsManager = com.aicompanion.memory.MemorableMomentsManager(this) }
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
            initStep("SearchEngine") { searchEngine = WebSearchEngine() }
            initStep("AIActionManager") { aiActionManager = com.aicompanion.action.AIActionManager(this) }
            initStep("VoiceManager") { voiceManager = VoiceManager(this) }
            initStep("ProactiveEngine") { proactiveEngine = ProactiveInteractionEngine(settingsManager!!) }
            initStep("ApiClient") { rebuildApiClient() }
            initStep("ChatAdapter") { initChatAdapter() }
            initStep("LoadAvatar") { loadAiAvatar() }
            initStep("Live2DSettings") { loadLive2DSettings() }
            initStep("Live2DModel") { loadLive2DModel() }
            initStep("ClickListeners") { setupClickListeners() }
            initStep("ApplyTheme") { applyTheme() }
            initStep("UpdateDisplay") { updateAffectionDisplay() }
            initStep("Weather") { updateWeather() }
            initStep("LoadMessages") { loadChatHistory() }
            initStep("Welcome") { loadWelcomeMessage() }
            initStep("Proactive") { scheduleProactiveChat() }
            initStep("DiaryTimer") { scheduleDiaryTimer() }
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

    private fun fatal(step: String, e: Exception) {
        Log.e(TAG, "[FATAL] $step: ${e.javaClass.simpleName}: ${e.message}", e)
        Toast.makeText(this, "严重错误: $step - ${e.message}", Toast.LENGTH_LONG).show()
    }

    private fun initViews() {
        live2dView = findViewById(R.id.live2d_view)
        recyclerChat = findViewById(R.id.recycler_chat)
        etMessage = findViewById(R.id.et_message)
        btnSend = findViewById(R.id.btn_send)
        btnVoice = findViewById(R.id.btn_voice)
        btnSettings = findViewById(R.id.btn_settings)
        btnMood = findViewById(R.id.btn_mood)
        btnMore = findViewById(R.id.btn_more)
        tvWeather = findViewById(R.id.tv_weather)
        tvDaysLabel = findViewById(R.id.tv_days_label)
        progressAffection = findViewById(R.id.progress_affection)
        tvPetName = findViewById(R.id.tv_pet_name)
        tvStatus = findViewById(R.id.tv_status)
        chipEmotion = findViewById(R.id.chip_emotion)
        chipAction = findViewById(R.id.chip_action)
        ivAiAvatarSmall = findViewById(R.id.iv_ai_avatar_small)
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
            apiClient = ApiClient(sm.chatApiUrl, sm.chatApiKey, sm.chatModel)
        }
    }

    private fun initChatAdapter() {
        chatAdapter = ChatAdapter(messages)
        recyclerChat?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        chatAdapter?.onFeedback = { position, isLike ->
            if (position < messages.size) {
                val msg = messages[position]
                if (!msg.isUser) {
                    msg.feedback = if (isLike) 1 else -1
                    saveMessageFeedback(position, if (isLike) 1 else -1)
                    val ach = achievementManager?.updateProgress("feedback", getTotalPositiveFeedback())
                    if (ach != null) showAchievementUnlock(ach)
                    if (isLike) { affectionManager?.addAffection(1); updateAffectionDisplay() }
                }
            }
        }

        chatAdapter?.onDeleteMessage = { position ->
            if (position >= 0 && position < messages.size) {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("删除消息")
                    .setMessage("确定删除这条消息吗？")
                    .setPositiveButton("删除") { _, _ ->
                        messages.removeAt(position)
                        chatAdapter?.notifyDataSetChanged()
                        saveChatHistory()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private fun loadLive2DSettings() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        offsetX = prefs.getFloat("model_offset_x", 0f)
        offsetY = prefs.getFloat("model_offset_y", 0f)
        modelBaseScale = prefs.getFloat("model_scale", 1f)
    }

    private fun loadLive2DModel() {
        val webView = live2dView ?: return

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
            try { webView.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json") } catch (_: Exception) {}
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

        btnVoice?.setOnLongClickListener {
            try {
                voiceManager?.startListening { text ->
                    if (isDestroyed) return@startListening
                    runOnUiThread { etMessage?.setText(text); sendMessage() }
                }
            } catch (_: Exception) {}
            true
        }

        btnVoice?.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_UP -> { try { voiceManager?.stopListening() } catch (_: Exception) {} ; true }
                else -> false
            }
        }

        btnSettings?.setOnClickListener {
            try { startActivity(Intent(this, SettingsActivity::class.java)) } catch (e: Exception) {
                Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
            }
        }

        btnMood?.setOnClickListener { showMoodSelector() }
        btnMore?.setOnClickListener { showFeaturePanel() }
        ivAiAvatarSmall?.setOnClickListener {
            try { startActivity(Intent(this, ProfileActivity::class.java)) } catch (_: Exception) {}
        }

        chatAdapter?.onFeedback = { position, isLike ->
            if (position < messages.size) {
                val msg = messages[position]
                if (!msg.isUser) {
                    msg.feedback = if (isLike) 1 else -1
                    saveMessageFeedback(position, if (isLike) 1 else -1)
                    val ach = achievementManager?.updateProgress("feedback", getTotalPositiveFeedback())
                    if (ach != null) showAchievementUnlock(ach)
                    if (isLike) { affectionManager?.addAffection(1); updateAffectionDisplay() }
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
                FeatureItem(R.drawable.ic_focus, "专注计时", 3),
                FeatureItem(R.drawable.ic_model, "切换皮套", 4),
                FeatureItem(R.drawable.ic_background, "更换背景", 5),
                FeatureItem(R.drawable.ic_log, "运行日志", 6),
                FeatureItem(R.drawable.ic_help, "操作教程", 7),
                FeatureItem(R.drawable.ic_robot, "手机自动化", 8)
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
                            1 -> try { startActivity(Intent(this@MainActivity, AchievementActivity::class.java)) } catch (_: Exception) {}
                            2 -> try { startActivity(Intent(this@MainActivity, DiaryActivity::class.java)) } catch (_: Exception) {}
                            3 -> if (focusActive) cancelFocusTimer() else startFocusTimer()
                            4 -> try { startActivity(Intent(this@MainActivity, ModelManagerActivity::class.java)) } catch (_: Exception) {}
                            5 -> pickBackgroundImage()
                            6 -> showLogViewer()
                            7 -> showTutorial()
                            8 -> showAutoOperationDialog()
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

    private fun sendMessage() {
        val text = etMessage?.text?.toString()?.trim() ?: ""
        if (text.isEmpty()) return
        etMessage?.text?.clear()

        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        messages.add(ChatMessage(text, time, true, currentUserMood))
        chatAdapter?.notifyItemInserted(messages.size - 1)
        recyclerChat?.scrollToPosition(messages.size - 1)

        affectionManager?.addMessage()
        affectionManager?.evaluateUserBehavior(text, currentUserMoodName.let { nm ->
            try { Emotion.valueOf(nm.uppercase()) } catch (_: Exception) { Emotion.NEUTRAL }
        })
        updateAffectionDisplay()

        val chatAch = achievementManager?.updateProgress("chat", messages.count { it.isUser })
        if (chatAch != null) showAchievementUnlock(chatAch)

        triggerMomentsScoringIfNeeded()

        val actionMgr = aiActionManager
        if (actionMgr != null) {
            if (actionMgr.isSearchRequest(text)) {
                handleSearchRequest(text, actionMgr)
                return
            }
        }

        sendToLLM(text)
    }

    private fun handleSearchRequest(text: String, actionMgr: com.aicompanion.action.AIActionManager) {
        setLoading(true)
        addPetMessage("🔍 正在搜索...", Emotion.NEUTRAL, Action.IDLE)

        messageScope.launch {
            try {
                val keywords = withContext(Dispatchers.IO) { actionMgr.extractSearchKeywords(text) }
                val result = withContext(Dispatchers.IO) { actionMgr.searchAndSummarize(keywords) }
                addPetMessage(result, Emotion.HAPPY, Action.EAR_TWITCH)
            } catch (e: Exception) {
                addPetMessage("搜索出错了：${e.message}", Emotion.SAD, Action.IDLE)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun handleAlarmRequest(text: String, actionMgr: com.aicompanion.action.AIActionManager) {
        val info = actionMgr.parseAlarmInfo(text)
        if (info != null) {
            val result = actionMgr.setAlarm(info)
            addPetMessage(result, Emotion.HAPPY, Action.TAIL_FLICK)
        } else {
            addPetMessage("没听懂要定几点的闹钟呢😅 试试说「定个10点的闹钟」", Emotion.SAD, Action.IDLE)
        }
    }

    private fun handleScheduleRequest(text: String, actionMgr: com.aicompanion.action.AIActionManager) {
        val info = actionMgr.parseScheduleInfo(text)
        if (info != null) {
            val result = actionMgr.addSchedule(info)
            addPetMessage(result, Emotion.HAPPY, Action.TAIL_FLICK)
        } else {
            addPetMessage("没听懂要安排什么日程呢😅 试试说「明天下午3点开会」", Emotion.SAD, Action.IDLE)
        }
    }

    private fun trySetAlarmFromMessage(text: String, actionMgr: com.aicompanion.action.AIActionManager) {
        val info = actionMgr.parseAlarmInfo(text)
        if (info != null) {
            actionMgr.setAlarm(info)
        }
    }

    private fun trySetScheduleFromMessage(text: String, actionMgr: com.aicompanion.action.AIActionManager) {
        val info = actionMgr.parseScheduleInfo(text)
        if (info != null) {
            actionMgr.addSchedule(info)
        }
    }

    private var momentsScoreCounter = 0
    private fun triggerMomentsScoringIfNeeded() {
        momentsScoreCounter++
        if (momentsScoreCounter % 20 == 0) {
            triggerMomentsScoring()
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
        val prefs = getSharedPreferences("avatar_data", MODE_PRIVATE)
        val aiPath = prefs.getString("ai_avatar", "")
        if (aiPath?.isNotEmpty() == true) {
            val file = File(aiPath)
            if (file.exists()) {
                ivAiAvatarSmall?.setImageBitmap(android.graphics.BitmapFactory.decodeFile(aiPath))
            }
        }
    }

    private fun sendToLLM(message: String) {
        val client = apiClient
        val sm = settingsManager
        if (client == null || sm == null) {
            addPetMessage("请先在设置中配置 API 哦~", Emotion.NEUTRAL, Action.IDLE)
            return
        }

        setLoading(true)
        chatAdapter?.setTypingIndicator(true)
        recyclerChat?.scrollToPosition(messages.size - 1)

        val systemContext = buildSystemContext(message)

        messageScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val persona = getPersonaInfo()
                    val memories = memoryManager?.getLocalMemories()?.map { it.fact } ?: emptyList()
                    val history = messages.takeLast(10).filter { it.text.length < 500 }.map { it.isUser to it.text }
                    client.sendChat(
                        sm.userId, message, persona.first, persona.second,
                        currentUserMoodName, "idle", memories, "", sm.offlineMode, systemContext, history
                    )
                }

                chatAdapter?.setTypingIndicator(false)

                if (response != null) {
                    if (response.errorMessage != null) {
                        chatAdapter?.setTypingIndicator(false)
                        addPetMessage("呜...${response.errorMessage}", Emotion.SAD, Action.IDLE)
                    } else {
                        addPetMessage(response.text, response.emotion, response.action)
                        if (sm.isTTSEnabled && response.audioUrl != null) {
                            voiceManager?.playSpeech(response.audioUrl, response.emotion)
                        } else if (sm.isTTSEnabled) {
                            voiceManager?.speak(response.text, response.emotion)
                        }
                        updatePetDisplay(response)

                        val actionMgr = aiActionManager
                        if (actionMgr != null && actionMgr.isAlarmRequest(message)) {
                            trySetAlarmFromMessage(message, actionMgr)
                        }
                        if (actionMgr != null && actionMgr.isScheduleRequest(message)) {
                            trySetScheduleFromMessage(message, actionMgr)
                        }
                    }
                } else {
                    addPetMessage("呜...连接不上AI，请检查API设置", Emotion.SAD, Action.IDLE)
                }
            } catch (e: Exception) {
                chatAdapter?.setTypingIndicator(false)
                logHistory.add("[ERROR] ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())} sendToLLM: ${e.message}")
                addPetMessage("出错了: ${e.message}", Emotion.SAD, Action.IDLE)
                Log.e(TAG, "sendToLLM error: ${e.message}", e)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun addPetMessage(text: String, emotion: Emotion, action: Action) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val cleanText = text.replace(Regex("\\[\\[emotion:\\w+\\]\\]", RegexOption.IGNORE_CASE), "").trim()
        messages.add(ChatMessage(cleanText, time, false, emotion = emotion, timestamp = System.currentTimeMillis()))
        chatAdapter?.notifyItemInserted(messages.size - 1)
        recyclerChat?.scrollToPosition(messages.size - 1)
        saveChatHistory()

        live2dView?.setEmotion(emotion)
        live2dView?.setAction(action)

        if (!isInForeground) {
            val aiName = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString("ai_name", "星尘") ?: "星尘"
            systemMonitor?.showAiMessageNotification(aiName, cleanText)
        }
    }

    private fun getPersonaInfo(): Pair<String, String> {
        val personaPrefs = getSharedPreferences("persona_data", MODE_PRIVATE)
        val appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        val name = personaPrefs.getString("persona_name", null)
            ?: appPrefs.getString("ai_name", "星尘") ?: "星尘"

        val userCall = personaPrefs.getString("user_nickname", null)
            ?: appPrefs.getString("user_call_name", "") ?: ""

        val fullPrompt = buildString {
            val promptFromPersona = buildString {
                val desc = personaPrefs.getString("persona_desc", "") ?: ""
                val personality = personaPrefs.getString("persona_personality", "") ?: ""
                val speechStyle = personaPrefs.getString("persona_speech_style", "") ?: ""
                val catchphrases = personaPrefs.getString("persona_catchphrases", "") ?: ""
                val appearance = personaPrefs.getString("persona_appearance", "") ?: ""
                val preferences = personaPrefs.getString("persona_preferences", "") ?: ""
                val worldSetting = personaPrefs.getString("world_setting", "") ?: ""
                val worldRelationship = personaPrefs.getString("world_relationship", "") ?: ""
                val worldRules = personaPrefs.getString("world_rules", "") ?: ""

                append("你是「$name」。")
                if (desc.isNotBlank()) append("\n简介：$desc")
                if (appearance.isNotBlank()) append("\n外貌：$appearance")
                if (personality.isNotBlank()) append("\n性格：$personality")
                if (speechStyle.isNotBlank()) append("\n说话风格：$speechStyle")
                if (catchphrases.isNotBlank()) append("\n常用口头禅：$catchphrases")
                if (preferences.isNotBlank()) append("\n喜好：$preferences")
                if (worldSetting.isNotBlank()) append("\n世界观设定：$worldSetting")
                if (worldRelationship.isNotBlank()) append("\n你和用户的关系：$worldRelationship")
                if (worldRules.isNotBlank()) append("\n规则：$worldRules")
            }
            if (promptFromPersona.isNotBlank()) {
                append(promptFromPersona)
            } else {
                append("你是「$name」，一个可爱的AI桌宠。")
                val oldPrompt = appPrefs.getString("ai_prompt", "") ?: ""
                if (oldPrompt.isNotBlank()) append("\n$oldPrompt")
            }
            if (userCall.isNotBlank()) append("\n你称呼用户为「$userCall」。")
            val style = settingsManager?.languageStyle?.name?.lowercase() ?: "normal"
            when (style) {
                "tsundere" -> append("\n你是傲娇性格，嘴上不饶人但内心关心用户。")
                "cute" -> append("\n你说话非常可爱，经常用颜文字和拟声词。")
            }
        }
        return Pair(name, fullPrompt)
    }

    private fun updatePetDisplay(response: ChatResponse) {
        chipEmotion?.text = response.emotion.name.lowercase()
        chipAction?.text = response.action.name.lowercase()
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
            findViewById<ImageView>(R.id.iv_chat_background)?.let { iv ->
                if (!bgPath.isNullOrEmpty()) {
                    val file = File(bgPath)
                    if (file.exists()) {
                        val bmp = BitmapFactory.decodeFile(bgPath)
                        iv.setImageBitmap(bmp)
                        iv.alpha = 0.3f
                    }
                }
            }

            ThemeManager.applyTheme(this)
        } catch (e: Exception) {
            Log.e(TAG, "applyTheme: ${e.message}")
        }
    }

    private fun updateAffectionDisplay() {
        val am = affectionManager ?: return
        tvDaysLabel?.text = "第${am.getDaysSinceFirstUse()}天"
        progressAffection?.progress = am.affectionLevel
        tvPetName?.text = "✨ ${getPersonaInfo().first}"
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
    }

    private fun getYesterday(): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    private fun showMoodSelector() {
        val moods = arrayOf(
            "😊 开心" to "HAPPY", "😢 难过" to "SAD", "😠 生气" to "ANGRY",
            "😳 傲娇" to "TSUNDERE", "😲 惊讶" to "SURPRISED", "😐 平静" to "NEUTRAL"
        )
        val names = moods.map { it.first }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("选择你的心情")
            .setItems(names) { _, which ->
                val (emoji, moodName) = moods[which]
                currentUserMood = emoji
                currentUserMoodName = moodName
                btnMood?.contentDescription = emoji
                Toast.makeText(this, "心情: $emoji", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showAchievementUnlock(achievement: com.aicompanion.models.Achievement) {
        Toast.makeText(this, "🏆 成就解锁: ${achievement.title}", Toast.LENGTH_LONG).show()
    }

    private fun buildSystemContext(message: String): String {
        val lower = message.lowercase()
        val sb = StringBuilder()

        val needTime = listOf("几点", "什么时间", "时间", "日期", "今天几号", "星期", "几点钟", "now time", "what time").any { lower.contains(it) }
        val needBattery = listOf(
            "电量", "电池", "电量百分比", "还有多少电", "还剩多少电", "电量剩余",
            "手机电量", "电池电量", "充", "充电", "power", "battery"
        ).any { lower.contains(it) } || lower.matches(Regex(".*\\b电.*\\b.*"))

        val isAlarmRequest = listOf(
            "闹钟", "定闹", "设闹", "订闹", "提醒我",
            "分钟后提醒", "小时后提醒",
            "set alarm", "alarm"
        ).any { lower.contains(it) }

        val isScheduleRequest = listOf(
            "安排", "日程", "记一下", "记着", "记住", "提醒我",
            "明天", "后天", "下周", "下个月", "周一", "周二", "周三",
            "周四", "周五", "周六", "周日", "星期",
            "上午", "下午", "晚上"
        ).count { lower.contains(it) } >= 2

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
            } catch (_: Exception) {}
        }

        if (isAlarmRequest) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("[系统信息] 当前时间：$currentTimeStr。用户想要设置闹钟，请用轻松自然的语气回复，我会帮你设置好闹钟。")
        }

        if (isScheduleRequest) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("[系统信息] 当前时间：$currentTimeStr。用户想要安排日程，请用轻松自然的语气回复，我会帮你记下来。")
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
        } catch (_: Exception) {}
        return -1
    }

    private fun showLogViewer() {
        val currentLog = live2dView?.getLog() ?: "无日志"
        val historyLog = logHistory.joinToString("\n").takeLast(2000)
        val fullLog = buildString {
            append("=== Live2D 日志 ===\n")
            append(currentLog.takeLast(3000))
            append("\n\n=== 历史错误 ===\n")
            append(historyLog)
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
                    try { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {}
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
        try { startActivityForResult(intent, REQUEST_PICK_IMAGE) } catch (_: Exception) {}
    }

    private fun startFocusTimer() {
        focusActive = true
        focusSecondsLeft = 25 * 60
        focusEndTime = System.currentTimeMillis() + focusSecondsLeft * 1000L
        updateFocusDisplay()
        addPetMessage("🍅 专注模式启动！25分钟，我会陪着你~", Emotion.HAPPY, Action.STRETCH)
        focusRunnable = object : Runnable {
            override fun run() {
                if (!focusActive) return
                focusSecondsLeft = ((focusEndTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                updateFocusDisplay()
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
        addPetMessage("🎉 专注完成！你真棒！好感+3", Emotion.HAPPY, Action.TAIL_FLICK)
        tvStatus?.text = "在线"
    }

    private fun cancelFocusTimer() {
        focusActive = false
        focusRunnable?.let { handler.removeCallbacks(it) }
        tvStatus?.text = "在线"
        addPetMessage("专注取消了，没关系，下次继续~", Emotion.NEUTRAL, Action.IDLE)
    }

    private fun scheduleDiaryTimer() {
        val sm = settingsManager ?: return
        val mode = sm.diaryTriggerMode
        if (mode == com.aicompanion.settings.DiaryTriggerMode.MANUAL) return

        val intervalMs = when (mode) {
            com.aicompanion.settings.DiaryTriggerMode.HOURLY -> 60 * 60 * 1000L
            com.aicompanion.settings.DiaryTriggerMode.TWO_HOURS -> 2 * 60 * 60 * 1000L
            com.aicompanion.settings.DiaryTriggerMode.MESSAGES_50 -> 30 * 1000L
            com.aicompanion.settings.DiaryTriggerMode.DAILY_10PM -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 22)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis - System.currentTimeMillis()
            }
            else -> return
        }

        handler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            checkDiaryTimerTrigger()
            if (mode != com.aicompanion.settings.DiaryTriggerMode.DAILY_10PM) {
                scheduleDiaryTimer()
            }
        }, intervalMs)
    }

    private fun checkDiaryTimerTrigger() {
        val sm = settingsManager ?: return
        val mm = memoryManager ?: return
        val dm = com.aicompanion.diary.DiaryManager(this)
        val am = affectionManager ?: return

        when (sm.diaryTriggerMode) {
            com.aicompanion.settings.DiaryTriggerMode.MESSAGES_50 -> {
                if (messages.count { it.isUser } % 50 == 0 && messages.count { it.isUser } > 0) {
                    autoTriggerDiary(dm, am)
                }
            }
            com.aicompanion.settings.DiaryTriggerMode.HOURLY,
            com.aicompanion.settings.DiaryTriggerMode.TWO_HOURS -> {
                autoTriggerDiary(dm, am)
            }
            com.aicompanion.settings.DiaryTriggerMode.DAILY_10PM -> {
                autoTriggerDiary(dm, am)
            }
            else -> {}
        }
    }

    private fun autoTriggerDiary(dm: com.aicompanion.diary.DiaryManager, am: AffectionManager) {
        val client = apiClient ?: return
        val sm = settingsManager ?: return
        if (sm.chatApiUrl.isBlank()) return

        messageScope.launch {
            try {
                val persona = getPersonaInfo()
                val chatTexts = messages.map { it.text }
                val combined = chatTexts.joinToString(" | ")
                val localMood = analyzeLocalMood(combined)

                val llmContent = withContext(Dispatchers.IO) {
                    val existing = dm.getDiaryByDate(
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    )
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
                        isUpdate = existing != null
                    )
                }

                if (llmContent != null && llmContent.isNotBlank()) {
                    val existing = dm.getDiaryByDate(
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    )
                    if (existing != null) {
                        dm.appendLlmDiaryUpdate(llmContent, chatTexts, am.affectionLevel)
                    } else {
                        dm.saveLlmDiary(llmContent, chatTexts, am.affectionLevel)
                    }
                    Toast.makeText(this@MainActivity, "📔 日记已更新", Toast.LENGTH_SHORT).show()
                } else {
                    dm.updateOrGenerateDailyDiary(chatTexts, am.affectionLevel)
                    Toast.makeText(this@MainActivity, "📔 日记已生成（本地模式）", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.d(TAG, "LLM diary failed, falling back: ${e.message}")
                val chatTexts = messages.map { it.text }
                dm.updateOrGenerateDailyDiary(chatTexts, am.affectionLevel)
                Toast.makeText(this@MainActivity, "📔 日记已生成（本地模式）", Toast.LENGTH_SHORT).show()
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

    private fun updateFocusDisplay() {
        if (!focusActive) return
        val min = focusSecondsLeft / 60
        val sec = focusSecondsLeft % 60
        tvStatus?.text = "🍅 %02d:%02d".format(min, sec)
    }

    private fun triggerProactiveChat() {
        if (isDestroyed || isFinishing) return
        val client = apiClient ?: return
        val sm = settingsManager ?: return

        messageScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    if (sm.chatApiUrl.isNotBlank()) {
                        client.generateNagContent(getPersonaInfo().first, getPersonaInfo().second)
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

    private fun triggerBatteryAlert(percentage: Int) {
        val client = apiClient ?: return
        val sm = settingsManager ?: return
        if (sm.chatApiUrl.isBlank()) return

        messageScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    client.generateNagContent(
                        getPersonaInfo().first, getPersonaInfo().second,
                        systemAlert = "主人的手机电量只剩 $percentage% 了！请提醒主人及时充电，语气要关心和温柔。"
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
            val name = getPersonaInfo().first
            addPetMessage("你好呀！我是${name}，你的AI伙伴~", Emotion.HAPPY, Action.TAIL_FLICK)
        }
    }

    private fun loadChatHistory() {
        try {
            val prefs = getSharedPreferences("chat_history", MODE_PRIVATE)
            val json = prefs.getString("messages", "[]") ?: "[]"
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                messages.add(ChatMessage(
                    text = obj.optString("text", ""),
                    time = obj.optString("time", ""),
                    isUser = obj.optBoolean("isUser", false),
                    userMood = obj.optString("userMood", ""),
                    feedback = obj.optInt("feedback", 0)
                ))
            }
            chatAdapter?.notifyDataSetChanged()
            if (messages.isNotEmpty()) recyclerChat?.scrollToPosition(messages.size - 1)
        } catch (e: Exception) {
            Log.e(TAG, "loadChatHistory: ${e.message}")
        }
    }

    private fun saveChatHistory() {
        try {
            val arr = org.json.JSONArray()
            messages.takeLast(100).forEach { msg ->
                arr.put(org.json.JSONObject().apply {
                    put("text", msg.text)
                    put("time", msg.time)
                    put("isUser", msg.isUser)
                    put("userMood", msg.userMood)
                    put("feedback", msg.feedback)
                })
            }
            getSharedPreferences("chat_history", MODE_PRIVATE).edit().putString("messages", arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "saveChatHistory: ${e.message}")
        }
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
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
        if (isFinishing || isDestroyed) return

        loadLive2DSettings()
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

        rebuildApiClient()
        updateWeather()
        updateAffectionDisplay()
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
    }

    override fun onDestroy() {
        focusRunnable?.let { handler.removeCallbacks(it) }
        proactiveRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable?.let { handler.removeCallbacks(it) }
        messageScope.cancel()
        systemMonitor?.stopMonitoring()
        voiceManager?.cleanup()
        live2dView?.cleanup()
        super.onDestroy()
    }
}
