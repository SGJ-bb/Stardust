package com.aicompanion.ilink

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.aicompanion.R
import com.aicompanion.memory.ContextManager
import com.aicompanion.network.ApiClient
import com.aicompanion.persona.PersonaManager
import com.aicompanion.settings.SettingsManager
import com.aicompanion.settings.DiaryTriggerMode
import com.aicompanion.diary.DiaryManager
import com.aicompanion.ui.MainActivity
import com.aicompanion.storage.ChatHistoryStorage
import com.aicompanion.storage.StoredMessage
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class IlinkPollingService : Service() {

    companion object {
        private const val TAG = "IlinkPolling"
        private const val CHANNEL_ID = "ilink_polling_channel"
        private const val NOTIFICATION_ID = 2001
        private const val POLL_INTERVAL_MS = 2000L

        const val ACTION_START = "com.aicompanion.ilink.START"
        const val ACTION_STOP = "com.aicompanion.ilink.STOP"

        fun start(context: Context) {
            val intent = Intent(context, IlinkPollingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, IlinkPollingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private var isRunning = false

    private lateinit var authManager: IlinkAuthManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var personaManager: PersonaManager
    private lateinit var chatHistoryStorage: ChatHistoryStorage
    private var contextManager: ContextManager? = null

    private var updatesBuf = ""
    private var failCount = 0
    private val maxFails = 10
    private var cachedTypingTicket = ""
    private var pollCount = 0

    override fun onCreate() {
        super.onCreate()
        authManager = IlinkAuthManager(this)
        settingsManager = SettingsManager(this)
        personaManager = PersonaManager(this)
        chatHistoryStorage = ChatHistoryStorage(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPolling()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!authManager.isBound) {
            AppLogger.w(TAG, "Not bound, stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        if (isRunning) return START_STICKY

        val notification = buildNotification("微信消息监听中")
        startForeground(NOTIFICATION_ID, notification)
        isRunning = true
        failCount = 0
        pollCount = 0

        personaManager.load()
        val activePersona = personaManager.getActivePersona()
        contextManager = ContextManager(this, activePersona.id)

        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        pollingJob = serviceScope.launch {
            while (isActive && isRunning && authManager.isBound) {
                try {
                    pollCount++
                    val currentBuf = updatesBuf

                    val result = IlinkApi.getUpdates(
                        authManager.botToken,
                        authManager.baseUrl,
                        currentBuf
                    )

                    if (result != null) {
                        failCount = 0

                        if (result.sessionExpired) {
                            AppLogger.e(TAG, "Session expired, need re-login")
                            withContext(Dispatchers.Main) {
                                updateNotification("微信会话已过期，请重新绑定")
                            }
                            break
                        }

                        if (result.getUpdatesBuf.isNotBlank()) {
                            updatesBuf = result.getUpdatesBuf
                        }

                        for (msg in result.messages) {
                            handleMessage(msg)
                        }
                    } else {
                        failCount++
                        AppLogger.w(TAG, "[Poll #$pollCount] result is NULL, failCount=$failCount/$maxFails")
                        if (failCount >= maxFails) {
                            AppLogger.e(TAG, "Too many failures, stopping")
                            withContext(Dispatchers.Main) {
                                updateNotification("微信连接异常，请重新绑定")
                            }
                            break
                        }
                    }

                    delay(POLL_INTERVAL_MS)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Polling error: ${e.javaClass.simpleName}: ${e.message}")
                    failCount++
                    delay(5000)
                }
            }

            withContext(Dispatchers.Main) {
                stopPolling()
                stopSelf()
            }
        }
    }

    private suspend fun handleMessage(msg: IlinkMessage) {
        if (msg.messageType != 1) return

        var userText = msg.textContent.trim()
        var imageUrls = listOf<String>()

        // 提取图片URL
        for (item in msg.itemList) {
            if (item.type == 2 && item.imageUrl.isNotBlank()) {
                imageUrls = imageUrls + item.imageUrl
            }
        }

        if (userText.isBlank()) {
            for (item in msg.itemList) {
                if (item.type == 1 && item.text.isNotBlank()) {
                    userText = item.text.trim()
                    break
                }
                if (item.type == 3 && item.voiceText.isNotBlank()) {
                    userText = item.voiceText.trim()
                    break
                }
            }
        }

        // 图片消息无文字时，用[图片]占位
        if (userText.isBlank() && imageUrls.isNotEmpty()) {
            userText = "[图片]"
        }

        if (userText.isBlank() && imageUrls.isEmpty()) {
            AppLogger.w(TAG, "Skip: all text fields are empty, from=${msg.fromUserId}")
            return
        }

        handleTextMessage(msg.fromUserId, msg.contextToken, userText, imageUrls)
    }

    private suspend fun handleTextMessage(fromUserId: String, contextToken: String, userText: String, imageUrls: List<String> = emptyList()) {
        withContext(Dispatchers.Main) {
            updateNotification("回复中: ${userText.take(20)}...")
        }

        // 保存用户消息到微信聊天记录
        val activePersona = personaManager.getActivePersona()
        val wechatScopeId = activePersona.id
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()
        val userMsg = StoredMessage(
            id = UUID.randomUUID().toString(),
            text = userText,
            time = timeFmt.format(Date(now)),
            isUser = true,
            timestamp = now,
            senderName = "微信用户",
            senderPersonaId = wechatScopeId,
            imageUrls = imageUrls
        )
        chatHistoryStorage.addMessage("wechat", wechatScopeId, userMsg)

        sendTypingIndicator(contextToken)

        var reply = generateAiReply(userText, fromUserId, contextToken, imageUrls)

        val emojiList = extractEmojiMarkers(reply)
        reply = reply.replace(Regex("\\[\\[emoji:[^\\]]+\\]\\]", RegexOption.IGNORE_CASE), "").trim()

        if (reply.isBlank()) {
            AppLogger.e(TAG, "AI reply is blank! Skipping send.")
            return
        }

        val sent = IlinkApi.sendMessage(
            authManager.botToken,
            authManager.baseUrl,
            fromUserId,
            contextToken,
            reply
        )

        // 保存AI回复到微信聊天记录
        val replyTime = System.currentTimeMillis()
        val aiMsg = StoredMessage(
            id = UUID.randomUUID().toString(),
            text = if (sent) reply else "$reply [发送失败]",
            time = timeFmt.format(Date(replyTime)),
            isUser = false,
            timestamp = replyTime,
            senderName = activePersona.name,
            senderPersonaId = wechatScopeId
        )
        chatHistoryStorage.addMessage("wechat", wechatScopeId, aiMsg)

        if (sent && emojiList.isNotEmpty()) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    sendStickerToWechat(fromUserId, contextToken, emojiList.first())
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Sticker send failed: ${e.message}")
                }
            }
        }

        if (sent && settingsManager.isTTSEnabled) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    sendTtsVoiceToWechat(fromUserId, contextToken, reply)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "TTS voice to wechat failed: ${e.message}")
                }
            }
        }

        withContext(Dispatchers.Main) {
            updateNotification(if (sent) "已回复: ${reply.take(15)}..." else "回复发送失败")
        }

        if (sent) {
            try {
                contextManager?.addTurn(userText, reply)

                serviceScope.launch {
                    try {
                        val client = buildApiClient()
                        if (client != null) {
                            contextManager?.evaluateAndUpdateMemory(client)
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "evaluateAndUpdateMemory error: ${e.message}")
                    }
                }

                checkWechatDiaryTrigger()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Post-turn processing error: ${e.message}")
            }
        }
    }

    private suspend fun sendTypingIndicator(contextToken: String) {
        try {
            if (cachedTypingTicket.isBlank()) {
                val config = IlinkApi.getConfig(
                    authManager.botToken,
                    authManager.baseUrl,
                    authManager.ilinkUserId,
                    contextToken
                )
                if (config != null && config.typingTicket.isNotBlank()) {
                    cachedTypingTicket = config.typingTicket
                } else {
                    AppLogger.w(TAG, "getConfig returned no typing ticket")
                }
            }

            if (cachedTypingTicket.isNotBlank()) {
                IlinkApi.sendTyping(
                    authManager.botToken,
                    authManager.baseUrl,
                    authManager.ilinkUserId,
                    cachedTypingTicket
                )
            }
        } catch (_: Exception) {}
    }

    private suspend fun sendTtsVoiceToWechat(toUserId: String, contextToken: String, text: String) {
        try {
            val engineMode = settingsManager.ttsEngineMode
            if (engineMode == com.aicompanion.voice.TtsManager.ENGINE_LOCAL) return

            val ttsManager = com.aicompanion.voice.TtsManager(this)
            val result = ttsManager.synthesize(text)

            if (!result.success) {
                AppLogger.w(TAG, "TTS synthesis for wechat failed: ${result.error}")
                return
            }

            val audioPath = result.audioPath
            val audioUrl = result.audioUrl
            if (audioPath.isNullOrBlank() && audioUrl.isNullOrBlank()) {
                AppLogger.w(TAG, "TTS produced no audio output (engine=$engineMode)")
                return
            }

            val durationMs = if (!audioPath.isNullOrBlank()) {
                val audioFile = File(audioPath)
                if (!audioFile.exists()) {
                    AppLogger.w(TAG, "TTS audio file not found: $audioPath")
                    return
                }
                estimateAudioDurationMs(audioFile)
            } else {
                (text.length * 150).coerceIn(1000, 60000)
            }

            val voiceSent = IlinkApi.sendVoiceMessage(
                authManager.botToken,
                authManager.baseUrl,
                toUserId,
                contextToken,
                audioPath ?: audioUrl ?: "",
                durationMs,
                text.take(200)
            )

            if (!voiceSent) {
                AppLogger.w(TAG, "Voice message send failed, text-only reply was already sent")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendTtsVoiceToWechat error: ${e.message}")
        }
    }

    private fun estimateAudioDurationMs(audioFile: File): Int {
        val fileSizeBytes = audioFile.length()
        val bitrateBps = 48000
        return ((fileSizeBytes * 8.0 / bitrateBps) * 1000).toInt().coerceIn(1000, 60000)
    }

    /**
     * 微信回复生成 —— 与主聊天管线（ChatViewModel.prepareRequest/callApi +
     * MainActivity.getPersonaInfo/buildSystemContext）完全对齐的镜像实现：
     * 内容安全过滤 → 人格全量prompt(昵称/RAG/上下文/世界书/主体性/coreRules)
     * → 系统上下文(时间/电量) → 技能库索引 → 情绪分析动态温度/TopP → 工具循环。
     * 两边改动需保持同步。
     */
    private suspend fun generateAiReply(
        userText: String,
        fromUserId: String,
        contextToken: String,
        imageUrls: List<String> = emptyList()
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val activePersona = personaManager.getActivePersona()
                val pName = activePersona.name

                // 内容安全过滤（与主聊天 validateInput 一致）
                if (com.aicompanion.safety.ContentSafetyFilter.shouldBlock(this@IlinkPollingService, userText)) {
                    return@withContext com.aicompanion.safety.ContentSafetyFilter.getRefusalResponse()
                }

                // 历史回退：ContextManager 的轮次只存内存，服务重启（被杀/会话过期/连续失败停止）后归零。
                // 主聊天有"消息快照回退"兜底（ChatViewModel.prepareRequest），微信侧此前没有，
                // 导致服务重启后微信回复完全失忆、风格连续性与 app 内不一致。此处与其对齐。
                // 注：当前这条用户消息在 handleTextMessage 里已先入库，丢掉末尾一条避免重复。
                var ctxHistory = contextManager?.getRecentTurnsAsPairs() ?: emptyList()
                if (ctxHistory.isEmpty()) {
                    val snapshot = chatHistoryStorage.getRecentMessages("wechat", activePersona.id, 40)
                    val historyBase = if (snapshot.lastOrNull()?.isUser == true) snapshot.dropLast(1) else snapshot
                    ctxHistory = historyBase.takeLast(settingsManager.contextTurns)
                        .filter { it.text.length < 500 }
                        .map { it.isUser to it.text }
                    AppLogger.w(TAG, "[MemoryDiag] 微信侧 ContextManager 轮次为空，回退到持久化记录 ${ctxHistory.size}条")
                }

                val apiUrl = settingsManager.chatApiUrl
                val apiKey = settingsManager.chatApiKey
                val model = settingsManager.chatModel

                if (apiUrl.isBlank()) {
                    AppLogger.e(TAG, "generateAiReply: chatApiUrl is empty!")
                    return@withContext ""
                }
                if (apiKey.isBlank()) {
                    AppLogger.e(TAG, "generateAiReply: chatApiKey is empty!")
                    return@withContext ""
                }

                val client = ApiClient(
                    apiUrl, apiKey,
                    model, settingsManager.llmTemperature,
                    settingsManager.llmTopP, settingsManager.llmFrequencyPenalty,
                    settingsManager.llmPresencePenalty, settingsManager.llmMaxTokens,
                    settingsManager.apiProvider
                )

                val identity = com.aicompanion.prompt.PromptBuilder.buildIdentity(this@IlinkPollingService, activePersona.id)
                val ctxBlock = contextManager?.getContextBlock() ?: ""

                // 主体性状态（情绪/驱动力/疲劳/风格要求）：持久化在 subjectivity_<personaId>，
                // 这里只读不写。主聊天每轮都注入该块，微信侧丢失会导致风格不一致。
                val subjectivityBlock = try {
                    com.aicompanion.emotion.SubjectivityEngine(this@IlinkPollingService, activePersona.id)
                        .getStatePromptBlock()
                } catch (e: Exception) {
                    AppLogger.w(TAG, "subjectivityBlock error: ${e.message}")
                    null
                }

                // ===== 人格全量 prompt（镜像 MainActivity.getPersonaInfo）=====
                val pPrompt = buildString {
                    append(com.aicompanion.prompt.PromptBuilder.buildPersonaFull(identity))

                    // 称呼（与主聊天一致：优先人格里设定的昵称，其次历史称呼，最后提示用工具取名）
                    if (identity.userNickname.isNotBlank()) {
                        append("\n叫用户「${identity.userNickname}」。")
                    } else {
                        val discovered = try {
                            com.aicompanion.ui.NicknameManager(this@IlinkPollingService, activePersona.id)
                                .getActiveNicknames()
                        } catch (_: Exception) { emptyList() }
                        if (discovered.isNotEmpty()) {
                            append("\n可以叫用户：${discovered.joinToString("、") { "「$it」"}}。")
                        } else if (ctxHistory.size >= 4) {
                            append("\n可以用summarize_nicknames工具给用户取称呼。")
                        }
                    }

                    // 人格 RAG（与主聊天一致：auto 模式按用户消息检索相关设定）
                    if (com.aicompanion.rag.RagConfig.personaRagEnabled && com.aicompanion.rag.RagConfig.ragMode == "auto") {
                        try {
                            ensurePersonaIndex(activePersona.id)
                            val ragChunks = getPersonaRagManager(activePersona.id).retrieveSync(userText, 3)
                            if (ragChunks.isNotEmpty()) {
                                append("\n\n[相关设定]")
                                for (chunk in ragChunks) {
                                    append("\n$chunk")
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "personaRag: ${e.message}")
                        }
                    }

                    // 长期记忆（与主聊天一致：单独 [上下文] 块，不混入 memories）
                    if (ctxBlock.isNotBlank()) {
                        append("\n\n[上下文]\n$ctxBlock")
                    }

                    // 世界书注入：按用户消息关键词触发（与主聊天一致）
                    val worldInfoBlock = com.aicompanion.prompt.PromptBuilder.buildWorldInfoBlock(
                        this@IlinkPollingService, activePersona.id, userText
                    )
                    if (worldInfoBlock.isNotBlank()) {
                        append(worldInfoBlock)
                    }

                    // 主体性状态注入（与主聊天 getPersonaInfo 位置一致）
                    if (!subjectivityBlock.isNullOrBlank()) {
                        append(subjectivityBlock)
                    }

                    append(com.aicompanion.prompt.PromptBuilder.getCoreRules(this@IlinkPollingService))
                    append("\n- 有强烈情绪时用send_sticker工具发表情包。用户让你发表情包时必须调用send_sticker工具，不要只用文字描述。可以反问和主动关心。保持角色不跳戏。")
                    append("\n- 当用户提到过去的事情、问你记不记得什么、或需要回忆历史经历时，主动使用search_diary工具搜索日记。")

                    // 微信专属回复规则（emoji 标记等）
                    append("\n\n[微信回复规则]")
                    append("\n1. 用自然、口语化的方式回复，像真实朋友聊天一样。")
                    append("\n2. 适当使用emoji表情来增强表达，但不要过度使用。")
                    append("\n3. 如果想发送表情包，也可以在回复末尾添加 [[emoji:表情包名称]] 标记，例如 [[emoji:开心]]、[[emoji:拥抱]]、[[emoji:比心]]、[[emoji:委屈]]、[[emoji:生气]]、[[emoji:卖萌]]、[[emoji:偷笑]]、[[emoji:吐槽]]、[[emoji:哭泣]]、[[emoji:慌张]]、[[emoji:偷听]]、[[emoji:偷瞄]]、[[emoji:调侃]]、[[emoji:邪恶的笑]]、[[emoji:骂人]]、[[emoji:鬼迷日眼的笑]] 等。每次最多发送一个表情包。优先使用send_sticker工具。")
                    append("\n4. 回复要简洁，避免过长的段落。")
                }

                // ===== 系统上下文（镜像 MainActivity.buildSystemContext：时间/电量按需注入）=====
                // 注：虚拟世界摘要依赖 app 内的世界会话，微信侧无活跃会话，不注入。
                val systemContext = buildWechatSystemContext(userText)

                // 技能库清单注入（与主聊天 callApi 一致：渐进式披露）
                var systemContextWithSkills = systemContext
                if (settingsManager.skillEnabled) {
                    try {
                        val index = com.aicompanion.skill.SkillStore(this@IlinkPollingService).buildSkillIndexPrompt()
                        if (index.isNotBlank()) systemContextWithSkills = "$systemContextWithSkills\n\n$index"
                    } catch (_: Exception) {
                    }
                }

                // ===== 情绪分析（与主聊天 prepareRequest 一致：动态温度/TopP/心情注入）=====
                var emotionParams: com.aicompanion.emotion.EmotionParams? = null
                if (settingsManager.emotionAnalysisEnabled) {
                    try {
                        val analyzer = emotionAnalyzer
                            ?: com.aicompanion.emotion.EmotionAnalyzer(client).also { emotionAnalyzer = it }
                        emotionParams = analyzer.analyzeEmotion(
                            personaName = pName,
                            personaPrompt = pPrompt,
                            userMessage = userText,
                            chatHistory = ctxHistory,
                            currentEmotion = "neutral"
                        )
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "情绪分析失败,使用默认值: ${e.message}")
                    }
                }
                val overrideTemp = emotionParams?.applyToTemperature(settingsManager.llmTemperature)
                val overrideTopP = emotionParams?.applyToTopP(settingsManager.llmTopP)
                val moodEmotion = emotionParams?.ttsEmotion ?: "neutral"

                // ===== 调用（与主聊天 callApi 一致：有工具走工具循环，无工具走 sendChat）=====
                val tools = com.aicompanion.plugin.PluginRegistry.getEnabledDefinitions()

                val response = if (tools.isNotEmpty()) {
                    client.sendChatWithToolLoop(
                        userId = "wechat_user",
                        message = userText,
                        personaName = pName,
                        personaPrompt = pPrompt,
                        emotion = moodEmotion,
                        action = "idle",
                        memories = emptyList(),
                        chatHistory = ctxHistory,
                        systemContext = systemContextWithSkills,
                        tools = tools,
                        toolExecutor = { name, args ->
                            executeWechatTool(name, args, fromUserId, contextToken)
                        },
                        imageUrls = imageUrls,
                        overrideTemperature = overrideTemp,
                        overrideTopP = overrideTopP,
                        maxIterations = settingsManager.aiToolLoopMaxRounds
                    )
                } else {
                    client.sendChat(
                        userId = "wechat_user",
                        message = userText,
                        personaName = pName,
                        personaPrompt = pPrompt,
                        emotion = moodEmotion,
                        action = "idle",
                        memories = emptyList(),
                        appCategory = "",
                        systemContext = systemContextWithSkills,
                        chatHistory = ctxHistory,
                        imageUrls = imageUrls,
                        overrideTemperature = overrideTemp,
                        overrideTopP = overrideTopP
                    )
                }

                val rawText = response?.text ?: ""

                val cleaned = rawText.replace(Regex("\\[\\[emotion:\\w+\\]\\]", RegexOption.IGNORE_CASE), "").trim()
                if (cleaned.isBlank()) {
                    AppLogger.w(TAG, "generateAiReply: cleaned reply is blank!")
                }
                cleaned
            } catch (e: Exception) {
                AppLogger.e(TAG, "generateAiReply ERROR: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                ""
            }
        }
    }

    /** 情绪分析器复用（避免每条消息新建实例） */
    private var emotionAnalyzer: com.aicompanion.emotion.EmotionAnalyzer? = null
    private var personaRagManagerRef: com.aicompanion.rag.PersonaRagManager? = null
    private var personaRagPersonaId: String? = null

    private fun getPersonaRagManager(personaId: String): com.aicompanion.rag.PersonaRagManager {
        if (personaRagManagerRef == null || personaRagPersonaId != personaId) {
            personaRagManagerRef = com.aicompanion.rag.PersonaRagManager(this, personaId)
            personaRagPersonaId = personaId
        }
        return personaRagManagerRef!!
    }

    /** 人格 RAG 索引按需重建（镜像 MainActivity.checkAndRebuildPersonaIndex/buildPersonaFields） */
    private suspend fun ensurePersonaIndex(personaId: String) {
        val rag = getPersonaRagManager(personaId)
        val fields = buildPersonaFields(personaId)
        val newHash = fields.values.joinToString("|").hashCode().toString()
        if (rag.currentHash() == newHash) return
        rag.buildIndex(fields)
        AppLogger.i(TAG, "微信侧人格RAG索引已重建: ${rag.getChunkCount()}分块")
    }

    private fun buildPersonaFields(personaId: String): Map<String, String> {
        val personaPrefs = getSharedPreferences("persona_data_$personaId", Context.MODE_PRIVATE)
        val name = personaPrefs.getString("persona_name", null)
            ?: getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("ai_name", "星尘")
            ?: "星尘"
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

    /** 系统上下文（镜像 MainActivity.buildSystemContext 的时间/电量部分） */
    private fun buildWechatSystemContext(message: String): String {
        val lower = message.lowercase()
        val sb = StringBuilder()

        val needTime = listOf("几点", "什么时间", "时间", "日期", "今天几号", "星期", "几点钟", "now time", "what time")
            .any { lower.contains(it) }
        val needBattery = listOf(
            "电量", "电池", "电量百分比", "还有多少电", "还剩多少电", "电量剩余",
            "手机电量", "电池电量", "充", "充电", "power", "battery"
        ).any { lower.contains(it) } || lower.contains("电")

        if (needTime) {
            val currentTimeStr = SimpleDateFormat("yyyy年M月d日 EEEE HH:mm", Locale.getDefault()).format(Date())
            sb.append("[系统信息] 当前时间：$currentTimeStr")
        }

        if (needBattery) {
            try {
                val batteryIntent = applicationContext.registerReceiver(
                    null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
                )
                val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    val percentage = (level * 100) / scale
                    val bm = getSystemService(android.os.BatteryManager::class.java)
                    val isCharging = bm?.isCharging == true
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append("[系统信息] 当前手机电量：${percentage}%${if (isCharging) "（充电中）" else ""}")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "buildWechatSystemContext: ${e.message}")
            }
        }
        return sb.toString()
    }

    /**
     * 微信侧工具执行器（与主聊天 AIActionManager.executeTool 同源：走 PluginRegistry）。
     * send_sticker 特殊处理：表情包图片直接发到微信，不经全局 onStickerSent 回调，
     * 避免与主聊天互相抢占回调。
     */
    private suspend fun executeWechatTool(
        name: String,
        arguments: String,
        fromUserId: String,
        contextToken: String
    ): String {
        // 与主聊天 ChatViewModel.callApi 的 toolExecutor 一致：生成图片时重置关联状态
        if (name == "generate_image") {
            com.aicompanion.AppContainer.setImagePluginWorldId("")
            com.aicompanion.AppContainer.setAssociatedEventId(null)
        }

        if (name == "send_sticker") {
            return try {
                val emotion = org.json.JSONObject(arguments).optString("emotion", "")
                if (emotion.isBlank()) {
                    "请提供要表达的情感"
                } else {
                    val stickerManager = com.aicompanion.sticker.StickerManager(this).also { it.loadStickers() }
                    val stickers = stickerManager.searchStickersByKeyword(emotion)
                    if (stickers.isEmpty()) {
                        "没有找到匹配「$emotion」的表情包，用文字表达吧～"
                    } else {
                        val sticker = stickers.random()
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                sendStickerToWechat(fromUserId, contextToken, emotion)
                            } catch (e: Exception) {
                                AppLogger.w(TAG, "微信表情包发送失败: ${e.message}")
                            }
                        }
                        "已发送表情包：${sticker.description.ifBlank { sticker.emotion }}"
                    }
                }
            } catch (e: Exception) {
                "工具执行失败: ${e.message}"
            }
        }

        return try {
            com.aicompanion.plugin.PluginRegistry.executePlugin(name, arguments)
        } catch (e: Exception) {
            AppLogger.e(TAG, "executeWechatTool $name failed: ${e.message}")
            "工具执行失败: ${e.message}"
        }
    }

    private fun extractEmojiMarkers(text: String): List<String> {
        val regex = Regex("\\[\\[emoji:([^\\]]+)\\]\\]", RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { it.groupValues[1].trim() }.toList()
    }

    private suspend fun sendStickerToWechat(toUserId: String, contextToken: String, emojiName: String) {
        try {
            AppLogger.i(TAG, "sendStickerToWechat: emojiName=$emojiName")
            val stickerManager = com.aicompanion.sticker.StickerManager(this)
            stickerManager.loadStickers()
            val stickers = stickerManager.searchStickersByKeyword(emojiName)
            AppLogger.i(TAG, "sendStickerToWechat: found ${stickers.size} stickers for '$emojiName'")
            if (stickers.isEmpty()) {
                AppLogger.w(TAG, "sendStickerToWechat: no sticker found for '$emojiName'")
                return
            }

            val sticker = stickers.first()
            val stickerFile = File(sticker.filePath)
            if (!stickerFile.exists()) {
                AppLogger.w(TAG, "sendStickerToWechat: sticker file not found: ${sticker.filePath}")
                return
            }

            val imageBytes = stickerFile.readBytes()
            AppLogger.i(TAG, "sendStickerToWechat: sticker file size=${imageBytes.size} bytes")

            val cdnUrl = IlinkApi.uploadImageToCdn(
                authManager.botToken,
                authManager.baseUrl,
                imageBytes,
                stickerFile.name
            )

            if (cdnUrl != null) {
                val sent = IlinkApi.sendImageMessage(
                    authManager.botToken,
                    authManager.baseUrl,
                    toUserId,
                    contextToken,
                    cdnUrl
                )
                if (sent) {
                    AppLogger.i(TAG, "sendStickerToWechat: sticker image sent via CDN, emoji=$emojiName")
                } else {
                    AppLogger.w(TAG, "sendStickerToWechat: sendImageMessage failed for CDN url")
                }
            } else {
                AppLogger.w(TAG, "sendStickerToWechat: CDN upload failed, trying direct image send")
                val sent = IlinkApi.sendImageMessage(
                    authManager.botToken,
                    authManager.baseUrl,
                    toUserId,
                    contextToken,
                    stickerFile.absolutePath
                )
                if (!sent) {
                    AppLogger.w(TAG, "sendStickerToWechat: direct image send also failed for '$emojiName'")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendStickerToWechat error: ${e.message}")
        }
    }

    private fun buildApiClient(): ApiClient? {
        val apiUrl = settingsManager.chatApiUrl
        val apiKey = settingsManager.chatApiKey
        val model = settingsManager.chatModel
        if (apiUrl.isBlank() || apiKey.isBlank()) return null
        return ApiClient(
            apiUrl, apiKey,
            model, settingsManager.llmTemperature,
            settingsManager.llmTopP, settingsManager.llmFrequencyPenalty,
            settingsManager.llmPresencePenalty, settingsManager.llmMaxTokens,
            settingsManager.apiProvider
        )
    }

    private fun checkWechatDiaryTrigger() {
        val mode = settingsManager.diaryTriggerMode
        if (mode == DiaryTriggerMode.MANUAL) return

        val activePersona = personaManager.getActivePersona()
        val dm = DiaryManager(this, activePersona.id)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val todayDiary = dm.getDiaryByDate(today)
        if (todayDiary != null) return

        val ctxMgr = contextManager ?: return
        val totalTurns = ctxMgr.sessionManager.currentTurnCount
        if (totalTurns < 5) return

        when (mode) {
            DiaryTriggerMode.MSG_50 -> {
                if (totalTurns % 50 != 0) return
            }
            DiaryTriggerMode.HOURLY, DiaryTriggerMode.EVERY_2H, DiaryTriggerMode.DAILY_10PM -> {
                val prefs = getSharedPreferences("wechat_diary_${activePersona.id}", Context.MODE_PRIVATE)
                val lastTrigger = prefs.getLong("last_wechat_diary_ms", 0)
                val intervalMs = when (mode) {
                    DiaryTriggerMode.HOURLY -> 60 * 60 * 1000L
                    DiaryTriggerMode.EVERY_2H -> 2 * 60 * 60 * 1000L
                    DiaryTriggerMode.DAILY_10PM -> 24 * 60 * 60 * 1000L
                    else -> return
                }
                if (System.currentTimeMillis() - lastTrigger < intervalMs) return
                prefs.edit().putLong("last_wechat_diary_ms", System.currentTimeMillis()).apply()
            }
            else -> return
        }

        serviceScope.launch {
            try {
                val client = buildApiClient() ?: return@launch
                val recentTurns = ctxMgr.getRecentTurnsText()
                if (recentTurns.isBlank()) return@launch

                val persona = activePersona
                val llmContent = withContext(Dispatchers.IO) {
                    client.generateDiaryContent(
                        recentTurns.split("\n"),
                        persona.name,
                        persona.personality,
                        "calm",
                        "😊",
                        0,
                        isUpdate = false
                    )
                }

                if (llmContent != null && llmContent.isNotBlank()) {
                    dm.saveLlmDiary(llmContent, recentTurns.split("\n"), 0)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Wechat diary generation error: ${e.message}")
            }
        }
    }

    private fun stopPolling() {
        isRunning = false
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onDestroy() {
        stopPolling()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "微信消息监听",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "监听微信iLink消息"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("星尘·微信连接")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_phone_call)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("星尘·微信连接")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_phone_call)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) {}
    }
}
