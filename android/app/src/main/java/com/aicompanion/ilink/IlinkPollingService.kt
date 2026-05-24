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
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.*
import java.io.File

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
        createNotificationChannel()
        AppLogger.i(TAG, "=== IlinkPollingService onCreate ===")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.i(TAG, "=== onStartCommand: action=${intent?.action}, isBound=${authManager.isBound}, isRunning=$isRunning ===")

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
        AppLogger.i(TAG, "=== Service started: persona=${activePersona.id}(${activePersona.name}), botToken=${authManager.botToken.take(20)}..., baseUrl=${authManager.baseUrl} ===")

        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        pollingJob = serviceScope.launch {
            AppLogger.i(TAG, "=== Polling loop started ===")
            while (isActive && isRunning && authManager.isBound) {
                try {
                    pollCount++
                    val currentBuf = updatesBuf
                    AppLogger.d(TAG, "[Poll #$pollCount] calling getUpdates, buf=${currentBuf.take(20)}, token=${authManager.botToken.take(15)}...")

                    val result = IlinkApi.getUpdates(
                        authManager.botToken,
                        authManager.baseUrl,
                        currentBuf
                    )

                    if (result != null) {
                        failCount = 0
                        AppLogger.d(TAG, "[Poll #$pollCount] result: msgs=${result.messages.size}, newBuf=${result.getUpdatesBuf.take(20)}, expired=${result.sessionExpired}")

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
                            AppLogger.i(TAG, "[Poll #$pollCount] msg: type=${msg.messageType}, state=${msg.messageState}, from=${msg.fromUserId}, ctxToken=${msg.contextToken.take(15)}, textLen=${msg.textContent.length}, items=${msg.itemList.size}")
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
                    AppLogger.i(TAG, "Polling cancelled")
                    break
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Polling error: ${e.javaClass.simpleName}: ${e.message}")
                    failCount++
                    delay(5000)
                }
            }

            AppLogger.i(TAG, "=== Polling loop ended ===")
            withContext(Dispatchers.Main) {
                stopPolling()
                stopSelf()
            }
        }
    }

    private suspend fun handleMessage(msg: IlinkMessage) {
        AppLogger.i(TAG, "handleMessage: type=${msg.messageType}, from=${msg.fromUserId}, to=${msg.toUserId}, text='${msg.textContent.take(50)}', items=${msg.itemList.size}")

        if (msg.messageType != 1) {
            AppLogger.d(TAG, "Skip: messageType=${msg.messageType} is not USER(1)")
            return
        }

        var userText = msg.textContent.trim()

        if (userText.isBlank()) {
            for (item in msg.itemList) {
                AppLogger.d(TAG, "  item: type=${item.type}, text='${item.text.take(30)}', voiceText='${item.voiceText.take(30)}'")
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

        if (userText.isBlank()) {
            AppLogger.w(TAG, "Skip: all text fields are empty, from=${msg.fromUserId}")
            return
        }

        AppLogger.i(TAG, ">>> Processing message from ${msg.fromUserId}: '$userText'")
        handleTextMessage(msg.fromUserId, msg.contextToken, userText)
    }

    private suspend fun handleTextMessage(fromUserId: String, contextToken: String, userText: String) {
        AppLogger.i(TAG, "handleTextMessage: from=$fromUserId, ctxToken=${contextToken.take(15)}, text='$userText'")

        withContext(Dispatchers.Main) {
            updateNotification("回复中: ${userText.take(20)}...")
        }

        sendTypingIndicator(contextToken)

        AppLogger.i(TAG, "Generating AI reply for: '$userText'")
        val reply = generateAiReply(userText)
        AppLogger.i(TAG, "AI reply generated: len=${reply.length}, preview='${reply.take(80)}'")

        if (reply.isBlank()) {
            AppLogger.e(TAG, "AI reply is blank! Skipping send.")
            return
        }

        AppLogger.i(TAG, "Sending reply to $fromUserId, ctxToken=${contextToken.take(15)}")
        val sent = IlinkApi.sendMessage(
            authManager.botToken,
            authManager.baseUrl,
            fromUserId,
            contextToken,
            reply
        )
        AppLogger.i(TAG, "<<< Reply sent: $sent")

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
                AppLogger.d(TAG, "addTurn: user='${userText.take(30)}', ai='${reply.take(30)}'")

                serviceScope.launch {
                    try {
                        val client = buildApiClient()
                        if (client != null) {
                            contextManager?.evaluateAndUpdateMemory(client)
                            AppLogger.d(TAG, "evaluateAndUpdateMemory done")
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
                AppLogger.d(TAG, "Fetching typing ticket...")
                val config = IlinkApi.getConfig(
                    authManager.botToken,
                    authManager.baseUrl,
                    authManager.ilinkUserId,
                    contextToken
                )
                if (config != null && config.typingTicket.isNotBlank()) {
                    cachedTypingTicket = config.typingTicket
                    AppLogger.d(TAG, "Got typing ticket: ${cachedTypingTicket.take(20)}...")
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
        } catch (e: Exception) {
            AppLogger.d(TAG, "sendTyping failed: ${e.message}")
        }
    }

    private suspend fun sendTtsVoiceToWechat(toUserId: String, contextToken: String, text: String) {
        try {
            val engineMode = settingsManager.ttsEngineMode
            if (engineMode == com.aicompanion.voice.TtsManager.ENGINE_LOCAL) {
                AppLogger.d(TAG, "TTS engine is LOCAL, skip voice message")
                return
            }

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

            if (voiceSent) {
                AppLogger.i(TAG, "Voice message sent to wechat, duration=${durationMs}ms, engine=$engineMode")
            } else {
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

    private suspend fun generateAiReply(userText: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val activePersona = personaManager.getActivePersona()
                val pName = activePersona.name
                AppLogger.d(TAG, "generateAiReply: persona=${activePersona.id}($pName)")

                val ctxHistory = contextManager?.getRecentTurnsAsPairs() ?: emptyList()
                AppLogger.d(TAG, "generateAiReply: ctxHistory=${ctxHistory.size} turns")

                val apiUrl = settingsManager.chatApiUrl
                val apiKey = settingsManager.chatApiKey
                val model = settingsManager.chatModel
                AppLogger.d(TAG, "generateAiReply: apiUrl=$apiUrl, model=$model, apiKeyLen=${apiKey.length}")

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

                val pPrompt = buildString {
                    append("你是$pName，一个AI伴侣角色。用户通过微信给你发消息，请自然地回复。")
                    if (activePersona.personality.isNotBlank()) {
                        append("\n你的性格：${activePersona.personality}")
                    }
                    if (activePersona.prompt.isNotBlank()) {
                        append("\n${activePersona.prompt}")
                    }
                }

                AppLogger.d(TAG, "generateAiReply: calling sendChat...")
                val response = client.sendChat(
                    userId = "wechat_user",
                    message = userText,
                    personaName = pName,
                    personaPrompt = pPrompt,
                    emotion = "NEUTRAL",
                    action = "IDLE",
                    memories = emptyList(),
                    appCategory = "wechat_ilink",
                    chatHistory = ctxHistory
                )

                val rawText = response?.text ?: ""
                AppLogger.d(TAG, "generateAiReply: raw response len=${rawText.length}, preview='${rawText.take(80)}'")

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

        AppLogger.i(TAG, "Wechat diary trigger: mode=$mode, turns=$totalTurns")
        serviceScope.launch {
            try {
                val client = buildApiClient() ?: return@launch
                val poolBlock = ctxMgr.memoryPool.getPoolBlock()
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
                    AppLogger.i(TAG, "Wechat diary generated successfully")
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
        AppLogger.i(TAG, "Polling stopped, total polls=$pollCount")
    }

    override fun onDestroy() {
        stopPolling()
        serviceScope.cancel()
        super.onDestroy()
        AppLogger.i(TAG, "=== IlinkPollingService destroyed ===")
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
