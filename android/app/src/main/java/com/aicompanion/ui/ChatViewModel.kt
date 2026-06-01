package com.aicompanion.ui

import android.app.Application
import android.util.Log
import com.aicompanion.util.AppLogger
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicompanion.emotion.EmotionParams
import com.aicompanion.humanizer.Humanizer
import com.aicompanion.memory.ContextManager
import com.aicompanion.models.Action
import com.aicompanion.models.ChatResponse
import com.aicompanion.models.Emotion
import com.aicompanion.network.ApiClient
import com.aicompanion.predict.ChatPredictor
import com.aicompanion.rag.PersonaRagManager
import com.aicompanion.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val MAX_MESSAGES = 500
    }

    sealed class UiEvent {
        data class ScrollToPosition(val position: Int) : UiEvent()
        data class ShowToast(val message: String) : UiEvent()
        data class SetTypingIndicator(val isTyping: Boolean) : UiEvent()
        data class SetLoading(val isLoading: Boolean) : UiEvent()
        data class NotifyItemInserted(val position: Int) : UiEvent()
        data class NotifyItemRangeInserted(val positionStart: Int, val itemCount: Int) : UiEvent()
        data class NotifyItemRangeRemoved(val positionStart: Int, val itemCount: Int) : UiEvent()
        data class NotifyItemRemoved(val position: Int) : UiEvent()
        data class NotifyItemChanged(val position: Int, val payload: Any? = null) : UiEvent()
        data class OnPetMessageAdded(
            val msg: ChatMessage,
            val position: Int,
            val emotion: Emotion,
            val action: Action
        ) : UiEvent()
        data class UpdatePetDisplay(val response: ChatResponse) : UiEvent()
        data class TriggerTtsAndPlay(
            val text: String,
            val emotion: Emotion,
            val message: ChatMessage
        ) : UiEvent()
        data class ShowPredictions(val predictions: List<String>) : UiEvent()
        data class TryAttachVirtualWorldImage(val message: String) : UiEvent()
        data class CheckTurnsDiaryTrigger(val message: String) : UiEvent()
        data class CheckNewSession(val poolChars: Int) : UiEvent()
        data class AddGeneratedImage(val imagePath: String) : UiEvent()
    }

    private val messagesLock = Any()
    val messages = mutableListOf<ChatMessage>()

    fun getMessagesSnapshot(): List<ChatMessage> = synchronized(messagesLock) { messages.toList() }

    private val memoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val humanizer = Humanizer()
    private var chatSaveJob: kotlinx.coroutines.Job? = null

    var chatStorage: com.aicompanion.storage.ChatHistoryStorage? = null
    var contextManager: ContextManager? = null
    var apiClient: ApiClient? = null
    var settingsManager: SettingsManager? = null
    var chatPredictor: ChatPredictor? = null
    var nicknameManager: NicknameManager? = null
    var personaRagManager: PersonaRagManager? = null
    var aiActionManager: com.aicompanion.action.AIActionManager? = null
    var statsManager: com.aicompanion.stats.PersonaStatsManager? = null
    var emotionAnalyzer: com.aicompanion.emotion.EmotionAnalyzer? = null
    var emotionGuardian: com.aicompanion.emotion.EmotionGuardian? = null
    var activePersonaId: String = "default"
    var buildIdentity: (() -> com.aicompanion.prompt.IdentityBlock)? = null

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 64)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private fun emitEvent(event: UiEvent) {
        _uiEvents.tryEmit(event)
    }

    fun addMessage(msg: ChatMessage) {
        synchronized(messagesLock) {
            messages.add(msg)
            if (messages.size > MAX_MESSAGES) {
                messages.removeAt(0)
            }
        }
    }

    fun addPetMessageInternal(text: String, emotion: Emotion, action: Action): ChatMessage? {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val cleanText = text.replace(Regex("\\[\\[emotion:\\w+\\]\\]", RegexOption.IGNORE_CASE), "").trim()
        if (cleanText.isBlank()) return null
        //AppLogger.d(TAG, "addPetMessage: AI回复 '${cleanText.take(50)}', emotion=${emotion.name}")
        val msg = ChatMessage(
            text = cleanText,
            time = time,
            isUser = false,
            emotion = emotion,
            timestamp = System.currentTimeMillis()
        )
        addMessage(msg)
        saveChatHistory()
        saveMessageToFile(msg)
        return msg
    }

    fun getChatPrefsName(): String {
        return "chat_history_$activePersonaId"
    }

    fun saveChatHistory() {
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

    fun doSaveChatHistory() {
        memoryScope.launch(Dispatchers.IO) {
            try {
                val snapshot = synchronized(messagesLock) { messages.takeLast(100) }
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
                        if (msg.stickerPath != null) put("stickerPath", msg.stickerPath)
                        if (msg.generatedImagePath != null) put("generatedImagePath", msg.generatedImagePath)
                        put("imageUrls", org.json.JSONArray(msg.imageUrls ?: emptyList<String>()).toString())
                        if (msg.audioPath != null) put("audioPath", msg.audioPath)
                        if (msg.audioUrl != null) put("audioUrl", msg.audioUrl)
                    })
                }
                val json = arr.toString()
                getApplication<Application>().getSharedPreferences(
                    getChatPrefsName(), android.content.Context.MODE_PRIVATE
                ).edit().putString("messages", json).apply()
            } catch (e: OutOfMemoryError) {
                try {
                    val arr = org.json.JSONArray()
                    synchronized(messagesLock) { messages.takeLast(20) }.forEach { msg ->
                        arr.put(org.json.JSONObject().apply {
                            put("id", msg.id)
                            put("text", msg.text)
                            put("time", msg.time)
                            put("isUser", msg.isUser)
                        })
                    }
                    getApplication<Application>().getSharedPreferences(
                        getChatPrefsName(), android.content.Context.MODE_PRIVATE
                    ).edit().putString("messages", arr.toString()).apply()
                } catch (e2: Exception) {
                    com.aicompanion.util.AppLogger.e(TAG, "saveChatHistory OOM恢复失败: ${e2.message}", e2)
                }
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e(TAG, "saveChatHistory失败: ${e.message}", e)
            }
        }
    }

    fun loadChatHistory(): List<ChatMessage> {
        val storage = chatStorage ?: return emptyList()
        try {
            val stored = storage.getRecentMessages("persona", activePersonaId, 200)
            if (stored.isNotEmpty()) {
                val result = stored.map { s ->
                    ChatMessage(
                        id = s.id, text = s.text, time = s.time, isUser = s.isUser,
                        userMood = s.userMood, feedback = s.feedback,
                        emotion = try { Emotion.valueOf(s.emotion) } catch (_: Exception) { Emotion.NEUTRAL },
                        timestamp = s.timestamp, isFavorited = s.isFavorited,
                        reactionEmoji = s.reactionEmoji, stickerPath = s.stickerPath,
                        generatedImagePath = s.generatedImagePath,
                        audioPath = s.audioPath, audioUrl = s.audioUrl,
                        imageUrls = s.imageUrls
                    )
                }
                return result
            }

            val prefs = getApplication<Application>().getSharedPreferences(
                getChatPrefsName(), android.content.Context.MODE_PRIVATE
            )
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
                    reactionEmoji = obj.optString("reactionEmoji", ""),
                    stickerPath = obj.optString("stickerPath", "").ifBlank { null },
                    generatedImagePath = obj.optString("generatedImagePath", "").ifBlank { null },
                    imageUrls = try {
                        val arr = obj.optString("imageUrls", "")
                        if (arr.isNotBlank()) org.json.JSONArray(arr).let { ja -> (0 until ja.length()).map { ja.getString(it) } }
                        else emptyList()
                    } catch (_: Exception) { emptyList() },
                    audioPath = obj.optString("audioPath", "").ifBlank { null },
                    audioUrl = obj.optString("audioUrl", "").ifBlank { null }
                ))
            }

            if (result.isNotEmpty()) {
                memoryScope.launch {
                    try {
                        storage.migrateFromSharedPreferences(getChatPrefsName(), "persona", activePersonaId)
                    } catch (_: Exception) {}
                }
            }

            return result
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "loadChatHistory失败: ${e.message}", e)
            return emptyList()
        }
    }

    fun applyLoadedMessages(loaded: List<ChatMessage>) {
        synchronized(messagesLock) {
            messages.clear()
            messages.addAll(loaded)
            while (messages.size > MAX_MESSAGES) messages.removeAt(0)
        }
    }

    fun saveMessageToFile(msg: ChatMessage) {
        memoryScope.launch {
            try {
                chatStorage?.addMessage("persona", activePersonaId, com.aicompanion.storage.StoredMessage(
                    id = msg.id, text = msg.text, time = msg.time, isUser = msg.isUser,
                    userMood = msg.userMood, feedback = msg.feedback,
                    emotion = msg.emotion.name, timestamp = msg.timestamp,
                    isFavorited = msg.isFavorited, reactionEmoji = msg.reactionEmoji,
                    stickerPath = msg.stickerPath,
                    generatedImagePath = msg.generatedImagePath,
                    audioPath = msg.audioPath, audioUrl = msg.audioUrl,
                    imageUrls = msg.imageUrls ?: emptyList()
                ))
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e(TAG, "saveMessageToFile: ${e.message}")
            }
        }
    }

    private var memorySearchCache: com.aicompanion.rag.EmbeddingSearchCache? = null

    suspend fun searchMemory(query: String, topK: Int): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()

        val poolEntries = contextManager?.memoryPool?.getAll() ?: emptyList()
        if (poolEntries.isNotEmpty()) {
            try {
                val cache = memorySearchCache ?: com.aicompanion.rag.EmbeddingSearchCache(
                    getApplication<Application>(), "memory_pool"
                ).also { memorySearchCache = it }

                val entries = poolEntries.mapIndexed { i, e -> i.toString() to e.content }
                cache.buildIndex(entries)

                val results = cache.search(query, topK, 0.1f)
                if (results.isNotEmpty()) {
                    sb.appendLine("[短期记忆池]")
                    results.forEach { r ->
                        sb.appendLine("- ${r.text}")
                    }
                }
            } catch (e: Exception) {
                val poolTexts = poolEntries.map { it.content }
                val embedder = com.aicompanion.rag.TfidfEmbedder()
                embedder.buildVocabulary(poolTexts)
                val queryVec = embedder.embedSingleSync(query)
                val vecs = embedder.embedSync(poolTexts)
                val scored = vecs.mapIndexed { i, v -> i to com.aicompanion.rag.VectorMath.cosineSimilarity(queryVec, v) }
                    .sortedByDescending { it.second }
                    .filter { it.second > 0.1f }
                if (scored.isNotEmpty()) {
                    sb.appendLine("[短期记忆池]")
                    scored.take(topK).forEach { (i, _) ->
                        sb.appendLine("- ${poolEntries[i].content}")
                    }
                }
            }
        }

        if (sb.isEmpty()) {
            return@withContext "未找到与「$query」相关的短期记忆。如需查找更早的记录，请使用search_diary工具。"
        }
        //AppLogger.d(TAG, "searchMemory: '$query' -> ${sb.length} chars")
        sb.toString().trimEnd()
    }

    suspend fun searchDiary(query: String, topK: Int): String = withContext(Dispatchers.IO) {
        try {
            val dm = com.aicompanion.diary.DiaryManager(getApplication<Application>(), activePersonaId)
            val diaryResults = dm.searchDiariesRag(query, topK)
            if (diaryResults.isEmpty()) {
                return@withContext "未找到与「$query」相关的日记记录。"
            }
            val sb = StringBuilder()
            sb.appendLine("[日记记录]")
            diaryResults.forEach { entry ->
                sb.appendLine("📅 ${entry.date} ${entry.title} ${entry.moodEmoji}")
                sb.appendLine(entry.content.take(300))
                sb.appendLine()
            }
            //AppLogger.d(TAG, "searchDiary: '$query' -> ${diaryResults.size} results, ${sb.length} chars")
            sb.toString().trimEnd()
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "searchDiary failed: ${e.message}")
            "日记搜索出错: ${e.message}"
        }
    }

    private fun buildNicknameContext(): String {
        val app = getApplication<Application>()
        val userCall = app.getSharedPreferences("persona_data_$activePersonaId", android.content.Context.MODE_PRIVATE)
            .getString("user_nickname", null)
            ?: app.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                .getString("user_call_name", "") ?: ""

        return if (userCall.isBlank()) {
            val discovered = nicknameManager?.getActiveNicknames() ?: emptyList()
            if (discovered.isEmpty() && messages.size >= 4) {
                "\n\n【提示】你还没有给用户设定称呼。如果你觉得通过聊天已经对用户有了一定了解，可以调用 summarize_nicknames 工具为主人总结出几个合适的称呼。"
            } else {
                ""
            }
        } else {
            ""
        }
    }

    fun sendToLLM(
        message: String,
        imageUrls: List<String> = emptyList(),
        currentUserMoodName: String,
        buildSystemContext: (String) -> String,
        getPersonaInfo: suspend (String) -> Pair<String, String>,
        isFinishing: () -> Boolean,
        isDestroyed: () -> Boolean
    ) {
        AppLogger.w(TAG, "sendToLLM: 开始处理消息 '${message.take(50)}'")

        if (com.aicompanion.safety.ContentSafetyFilter.shouldBlock(getApplication<Application>(), message)) {
            val refusalText = com.aicompanion.safety.ContentSafetyFilter.getRefusalResponse()
            val msg = addPetMessageInternal(refusalText, Emotion.NEUTRAL, Action.IDLE)
            if (msg != null) {
                emitEvent(UiEvent.OnPetMessageAdded(msg, messages.size - 1, Emotion.NEUTRAL, Action.IDLE))
            }
            return
        }

        val client = apiClient
        val sm = settingsManager
        if (client == null || sm == null) {
            com.aicompanion.util.AppLogger.e(TAG, "sendToLLM: apiClient=${client != null}, settingsManager=${sm != null}")
            val msg = addPetMessageInternal("请先在设置中配置 API 哦~", Emotion.NEUTRAL, Action.IDLE)
            if (msg != null) {
                emitEvent(UiEvent.OnPetMessageAdded(msg, messages.size - 1, Emotion.NEUTRAL, Action.IDLE))
            }
            return
        }

        AppLogger.w(TAG, "sendToLLM: apiClient已就绪, url=${sm.chatApiUrl.take(30)}, model=${sm.chatModel}")

        emitEvent(UiEvent.SetLoading(true))
        emitEvent(UiEvent.SetTypingIndicator(true))
        emitEvent(UiEvent.ScrollToPosition(messages.size - 1))

        val systemContext = buildSystemContext(message)
        val actionMgr = aiActionManager

        viewModelScope.launch {
            try {
                AppLogger.w(TAG, "sendToLLM: 协程启动，开始获取角色信息")
                val persona = getPersonaInfo(message)
                AppLogger.w(TAG, "sendToLLM: 角色信息获取完成, name=${persona.first}")
                val memories = emptyList<String>()
                val (_, history, tools) = withContext(Dispatchers.IO) {
                    val ctxH = contextManager?.getRecentTurnsAsPairs() ?: emptyList()
                    val h = if (ctxH.isNotEmpty()) ctxH
                              else messages.takeLast(settingsManager?.contextTurns ?: 10).filter { it.text.length < 500 }.map { msg ->
                                  val textWithImages = if (msg.imageUrls.isNotEmpty() && msg.isUser) {
                                      msg.text + "\n[图片已发送]"
                                  } else {
                                      msg.text
                                  }
                                  msg.isUser to textWithImages
                              }
                    val t = actionMgr?.getToolDefinitions() ?: emptyList()
                    Triple(ctxH, h, t)
                }
                AppLogger.w(TAG, "sendToLLM: history=${history.size}条, tools=${tools.size}个")

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
                        AppLogger.w(TAG, "Emotion analysis: tempOffset=${emotionParams.temperatureOffset}, " +
                            "pitchOffset=${emotionParams.ttsPitchOffset}, rateOffset=${emotionParams.ttsRateOffset}, " +
                            "intensity=${emotionParams.emotionIntensity}")
                        emotionGuardian?.recordEmotion(emotionParams.ttsEmotion, emotionParams.emotionIntensity)
                    } catch (e: Exception) {
                        com.aicompanion.util.AppLogger.w(TAG, "Emotion analysis failed, using defaults: ${e.message}")
                    }
                }

                val nicknameContext = buildNicknameContext()

                val overrideTemp = if (sm.emotionAnalysisEnabled) emotionParams.applyToTemperature(sm.llmTemperature) else null
                val overrideTopP = if (sm.emotionAnalysisEnabled) emotionParams.applyToTopP(sm.llmTopP) else null

                val response = if (tools.isNotEmpty()) {
                    AppLogger.w(TAG, "sendToLLM: 调用sendChatWithToolLoop")
                    withContext(Dispatchers.IO) {
                        client.sendChatWithToolLoop(
                            sm.userId, message, persona.first, persona.second + nicknameContext,
                            currentUserMoodName, "idle", memories, history, systemContext, tools,
                            toolExecutor = { name, args ->
                                if (name == "generate_image") {
                                    com.aicompanion.AppContainer.setImagePluginWorldId("")
                                    com.aicompanion.AppContainer.setAssociatedEventId(null)
                                }
                                actionMgr!!.executeTool(name, args)
                            },
                            imageUrls = imageUrls,
                            overrideTemperature = overrideTemp,
                            overrideTopP = overrideTopP
                        )
                    }
                } else {
                    AppLogger.w(TAG, "sendToLLM: 调用sendChat")
                    withContext(Dispatchers.IO) {
                        client.sendChat(
                            sm.userId, message, persona.first, persona.second,
                            currentUserMoodName, "idle", memories, "", systemContext, history,
                            imageUrls = imageUrls,
                            overrideTemperature = overrideTemp,
                            overrideTopP = overrideTopP
                        )
                    }
                }

                AppLogger.w(TAG, "sendToLLM: API响应=${response != null}, errorMsg=${response?.errorMessage}")

                emitEvent(UiEvent.SetTypingIndicator(false))

                if (response != null) {
                    if (response.errorMessage != null) {
                        val msg = addPetMessageInternal("呜...${response.errorMessage}", Emotion.SAD, Action.IDLE)
                        if (msg != null) {
                            emitEvent(UiEvent.OnPetMessageAdded(msg, messages.size - 1, Emotion.SAD, Action.IDLE))
                        }
                    } else {
                        val rawText = response.text
                        val isComplex = humanizer.isComplexQuestion(message)
                        val chunks = humanizer.humanize(rawText, isComplex)

                        if (chunks.isEmpty()) {
                            if (rawText.isNotBlank()) {
                                val msg = addPetMessageInternal(rawText, response.emotion, response.action)
                                if (msg != null) {
                                    emitEvent(UiEvent.OnPetMessageAdded(msg, messages.size - 1, response.emotion, response.action))
                                    if (sm.isTTSEnabled) {
                                        val ttsMsg = messages.lastOrNull { !it.isUser }
                                        if (ttsMsg != null) emitEvent(UiEvent.TriggerTtsAndPlay(rawText, response.emotion, ttsMsg))
                                    }
                                }
                            } else {
                                com.aicompanion.util.AppLogger.w(TAG, "sendToLLM: API响应成功但回复内容为空")
                                val msg = addPetMessageInternal("嗯...我好像走神了，能再说一次吗？", Emotion.NEUTRAL, Action.IDLE)
                                if (msg != null) {
                                    emitEvent(UiEvent.OnPetMessageAdded(msg, messages.size - 1, Emotion.NEUTRAL, Action.IDLE))
                                }
                            }
                        } else {
                            for (i in chunks.indices) {
                                val chunk = chunks[i]
                                if (chunk.text.isBlank()) continue
                                if (i > 0) delay(chunk.delayMs)
                                val emot = if (chunk.isThinking) Emotion.NEUTRAL else response.emotion
                                val act = if (chunk.isThinking) Action.IDLE else response.action
                                val msg = addPetMessageInternal(chunk.text, emot, act)
                                if (msg != null) {
                                    emitEvent(UiEvent.OnPetMessageAdded(msg, messages.size - 1, emot, act))
                                    if (i == 0 && sm.isTTSEnabled) {
                                        val firstMsg = messages.lastOrNull { !it.isUser }
                                        if (firstMsg != null) {
                                            emitEvent(UiEvent.TriggerTtsAndPlay(rawText, response.emotion, firstMsg))
                                        }
                                    }
                                }
                            }
                        }

                        emitEvent(UiEvent.UpdatePetDisplay(response))
                        emitEvent(UiEvent.TryAttachVirtualWorldImage(message))

                        val genImagePaths = com.aicompanion.plugin.GenerateImagePlugin.consumeGeneratedImagePaths()
                        genImagePaths.forEach { path ->
                            emitEvent(UiEvent.AddGeneratedImage(path))
                        }

                        if (emotionGuardian?.shouldSendCare() == true) {
                            val careMsg = emotionGuardian?.getCareMessage() ?: ""
                            if (careMsg.isNotBlank()) {
                                emotionGuardian?.markCareSent()
                                val careChatMsg = addPetMessageInternal(careMsg, Emotion.HAPPY, Action.IDLE)
                                if (careChatMsg != null) {
                                    emitEvent(UiEvent.OnPetMessageAdded(careChatMsg, messages.size - 1, Emotion.HAPPY, Action.IDLE))
                                }
                            }
                        }

                        contextManager?.addTurn(message, rawText)

                        triggerPredictionsInternal()
                        emitEvent(UiEvent.CheckTurnsDiaryTrigger(message))

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
                            emitEvent(UiEvent.CheckNewSession(poolChars))
                        }
                    }
                } else {
                    val msg = addPetMessageInternal("呜...连接不上AI，请检查API设置", Emotion.SAD, Action.IDLE)
                    if (msg != null) {
                        emitEvent(UiEvent.OnPetMessageAdded(msg, messages.size - 1, Emotion.SAD, Action.IDLE))
                    }
                }
            } catch (e: Exception) {
                emitEvent(UiEvent.SetTypingIndicator(false))
                com.aicompanion.util.AppLogger.e(TAG, "sendToLLm error: ${e.javaClass.simpleName}: ${e.message}", e)
                if (!isFinishing() && !isDestroyed()) {
                    val msg = addPetMessageInternal("出错了: ${e.message}", Emotion.SAD, Action.IDLE)
                    if (msg != null) {
                        emitEvent(UiEvent.OnPetMessageAdded(msg, messages.size - 1, Emotion.SAD, Action.IDLE))
                    }
                }
                Log.e(TAG, "sendToLLM error: ${e.message}", e)
            } finally {
                if (!isFinishing() && !isDestroyed()) {
                    emitEvent(UiEvent.SetLoading(false))
                }
            }
        }
    }

    private fun triggerPredictionsInternal() {
        val identityProvider = buildIdentity ?: return
        triggerPredictions(identityProvider)
    }

    fun triggerPredictions(
        buildIdentity: () -> com.aicompanion.prompt.IdentityBlock
    ) {
        val predictor = chatPredictor ?: return
        val recent = messages.takeLast(10).map { msg ->
            if (msg.isUser) "user" to msg.text else "ai" to msg.text
        }
        if (recent.isEmpty()) return
        val identity = buildIdentity()
        viewModelScope.launch {
            val predictions = predictor.predictPrivateChat(
                recentMessages = recent,
                personaName = identity.name,
                personaPersonality = identity.personality
            )
            emitEvent(UiEvent.ShowPredictions(predictions))
        }
    }

    fun cancelSaveJob() {
        chatSaveJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        chatSaveJob?.cancel()
        try { doSaveChatHistory() } catch (_: Exception) {}
        memoryScope.cancel()
    }
}
