package com.aicompanion.ui

import android.app.Application
import android.util.Log
import com.aicompanion.config.AppConfig
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

/**
 * Chat请求数据类,封装所有请求参数
 */
data class ChatRequest(
    val message: String,
    val imageUrls: List<String>,
    val personaName: String,
    val personaPrompt: String,
    val currentUserMoodName: String,
    val memories: List<String>,
    val history: List<Pair<Boolean, String>>,
    val systemContext: String,
    val tools: List<com.aicompanion.models.ToolDefinition>,
    val emotionParams: EmotionParams,
    val overrideTemperature: Float?,
    val overrideTopP: Float?,
    val nicknameContext: String
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
        private val MAX_MESSAGES = AppConfig.CHAT_MAX_MESSAGES
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
        /** 流式回复：某个气泡内容在增长，刷新该条（不新增消息） */
        data class OnStreamDelta(val position: Int) : UiEvent()
        /** 流式回复完成：收尾（统计/情绪/通知），消息已存在，不重复插入 */
        data class OnStreamCompleted(
            val position: Int,
            val msg: ChatMessage,
            val emotion: Emotion,
            val action: Action
        ) : UiEvent()
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
    private val _messages = mutableListOf<ChatMessage>()

    // 只暴露不可变副本
    val messages: List<ChatMessage> get() = synchronized(messagesLock) { _messages.toList() }

    fun getMessagesSnapshot(): List<ChatMessage> = synchronized(messagesLock) { _messages.toList() }

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
    var subjectivityEngine: com.aicompanion.emotion.SubjectivityEngine? = null
    var activePersonaId: String = "default"
    var buildIdentity: (() -> com.aicompanion.prompt.IdentityBlock)? = null

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 256)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private fun emitEvent(event: UiEvent) {
        if (!_uiEvents.tryEmit(event)) {
            AppLogger.e(TAG, "[Chat-Event] UI事件缓冲区溢出,事件丢失: $event")
        }
    }

    fun addMessage(msg: ChatMessage) {
        synchronized(messagesLock) {
            _messages.add(msg)
            if (_messages.size > MAX_MESSAGES) {
                _messages.removeAt(0)
            }
        }
    }

    fun removeMessage(position: Int) {
        synchronized(messagesLock) {
            if (position in _messages.indices) {
                _messages.removeAt(position)
            }
        }
    }

    fun clearMessages() {
        synchronized(messagesLock) {
            _messages.clear()
        }
    }

    fun getMessageCount(): Int = synchronized(messagesLock) { _messages.size }

    fun getLastPetMessage(): ChatMessage? = synchronized(messagesLock) {
        _messages.lastOrNull { !it.isUser }
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
                com.aicompanion.util.AppLogger.e(TAG, "[Chat-History] 保存聊天历史失败(异步): ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    fun doSaveChatHistory() {
        memoryScope.launch(Dispatchers.IO) {
            try {
                val snapshot = synchronized(messagesLock) { _messages.takeLast(100) }
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
                    synchronized(messagesLock) { _messages.takeLast(20) }.forEach { msg ->
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
                    com.aicompanion.util.AppLogger.e(TAG, "[Chat-History] 保存聊天历史OOM,精简后重试仍失败: ${e2.javaClass.simpleName}: ${e2.message}", e2)
                }
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e(TAG, "[Chat-History] 保存聊天历史失败: ${e.javaClass.simpleName}: ${e.message} | 消息数=${messages.size}", e)
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
                        val arrStr = obj.optString("imageUrls", "")
                        if (arrStr.isNotBlank()) org.json.JSONArray(arrStr).let { ja -> (0 until ja.length()).map { ja.getString(it) } }
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
                    } catch (e: Exception) {
                        com.aicompanion.util.AppLogger.w(TAG, "[Chat-Migrate] 从SharedPreferences迁移聊天历史失败: ${e.javaClass.simpleName}: ${e.message}")
                    }
                }
            }

            return result
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "[Chat-History] 加载聊天历史失败: ${e.javaClass.simpleName}: ${e.message} | personaId=$activePersonaId", e)
            return emptyList()
        }
    }

    fun applyLoadedMessages(loaded: List<ChatMessage>) {
        synchronized(messagesLock) {
            _messages.clear()
            _messages.addAll(loaded)
            while (_messages.size > MAX_MESSAGES) _messages.removeAt(0)
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
                com.aicompanion.util.AppLogger.e(TAG, "[Chat-File] 保存单条消息到文件失败: ${e.javaClass.simpleName}: ${e.message} | msgId=${msg.id}")
            }
        }
    }

    private var memorySearchCache: com.aicompanion.rag.EmbeddingSearchCache? = null

    suspend fun searchMemory(query: String, topK: Int): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()

        // 1. 搜索角色 RAG 知识库(角色设定、世界观等)
        try {
            val personaRagManager = com.aicompanion.AppContainer.personaRagManager
            if (com.aicompanion.rag.RagConfig.personaRagEnabled && personaRagManager.isReady()) {
                // 使用异步 retrieve 支持所有嵌入模式(包括云端嵌入)
                val ragChunks = personaRagManager.retrieve(query, topK)
                if (ragChunks.isNotEmpty()) {
                    sb.appendLine("[角色知识库]")
                    ragChunks.forEach { chunk ->
                        sb.appendLine("- $chunk")
                    }
                }
            }
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e(TAG, "[Chat-RAG] searchMemory检索角色知识库失败: ${e.javaClass.simpleName}: ${e.message} | query='${query.take(30)}'")
        }

        // 2. 收集候选：当前场景池 + 跨场景共享记忆 + 已巩固/语义层
        //    原先只查"当前场景池"，而那个池子经常只有一两条（换人设、换群聊后更少），
        //    于是出现"明明记得却说没找到"。
        val candidates = LinkedHashMap<String, String>()
        fun addCandidate(id: String, content: String) {
            if (content.isNotBlank()) candidates[id.ifBlank { content.take(24) }] = content
        }
        runCatching { contextManager?.memoryPool?.getAll()?.forEach { addCandidate(it.id, it.content) } }
        runCatching { contextManager?.globalMemoryPool?.snapshot()?.forEach { addCandidate(it.id, it.content) } }
        runCatching {
            contextManager?.memoryPool?.allForTiering()
                ?.filter { !it.archived && it.tier != com.aicompanion.memory.MemoryTier.EPISODIC }
                ?.forEach { addCandidate(it.id, it.content) }
        }
        val poolEntries = candidates.values.toList()

        if (poolEntries.isNotEmpty()) {
            var hits: List<String> = emptyList()
            var source = ""
            // a) 向量检索。阈值 0.1 → 0.03：TF-IDF 在中文短句上的余弦普遍偏低，
            //    0.1 会把"用词不同但确实相关"的结果全部滤掉，表现就是永远搜不到。
            runCatching {
                val cache = memorySearchCache ?: com.aicompanion.rag.EmbeddingSearchCache(
                    getApplication<Application>(), "memory_pool"
                ).also { memorySearchCache = it }
                cache.buildIndex(poolEntries.mapIndexed { i, e -> i.toString() to e })
                hits = cache.search(query, topK, 0.03f).map { it.text }
                source = "向量"
            }
            // b) 关键词兜底：中文按 2-gram 命中，避免分词/词序差异导致零召回
            if (hits.isEmpty()) {
                hits = keywordHits(poolEntries, query, topK)
                if (hits.isNotEmpty()) source = "关键词"
            }
            // c) 仍然为空：给最近几条让模型自己判断，比直接回答"没找到"有用
            if (hits.isEmpty()) {
                hits = poolEntries.takeLast(topK).reversed()
                source = "最近记忆"
            }
            if (hits.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.appendLine()
                sb.appendLine(if (source == "最近记忆") "[最近的记忆]（没有直接命中，供参考）" else "[相关记忆]")
                hits.forEach { sb.appendLine("- $it") }
                com.aicompanion.util.AppLogger.i(
                    TAG,
                    "[Chat-Memory] '$query' → ${hits.size} 条（$source / 候选 ${poolEntries.size} 条）",
                )
            }
        }

        if (sb.isEmpty()) {
            return@withContext "未找到与「$query」相关的记忆。注意：记忆池未收录不代表对话中没提过，请优先结合本次会话的聊天上下文回答用户。如需查找日记记录，请使用search_diary工具。"
        }
        sb.toString().trimEnd()
    }

    /** 中文 2-gram 命中打分：向量召回不到时的兜底，宁多给几条也别直接说"没找到" */
    private fun keywordHits(pool: List<String>, query: String, topK: Int): List<String> {
        val chars = query.filter { it.isLetterOrDigit() }
        if (chars.length < 2) return emptyList()
        val grams = (0 until chars.length - 1).map { chars.substring(it, it + 2) }.toSet()
        return pool.map { text -> text to grams.count { text.contains(it) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    suspend fun searchDiary(query: String, topK: Int): String = withContext(Dispatchers.IO) {
        try {
            val dm = com.aicompanion.diary.DiaryManager(getApplication<Application>(), activePersonaId)
            val diaryResults = dm.searchDiariesRag(query, topK)
            if (diaryResults.isEmpty()) {
                return@withContext "未找到与「$query」相关的日记记录。注意：日记只收录较久或重要的经历，最近的对话内容在聊天上下文中，请直接结合上下文回答用户。"
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
            com.aicompanion.util.AppLogger.e(TAG, "[Chat-Diary] searchDiary检索日记失败: ${e.javaClass.simpleName}: ${e.message} | query='${query.take(30)}'")
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
            val messageCount = synchronized(messagesLock) { _messages.size }
            if (discovered.isEmpty() && messageCount >= 4) {
                "\n\n【提示】你还没有给用户设定称呼。如果你觉得通过聊天已经对用户有了一定了解,可以调用 summarize_nicknames 工具为主人总结出几个合适的称呼。"
            } else {
                ""
            }
        } else {
            ""
        }
    }

    // ==================== 阶段1: 验证阶段 ====================

    /**
     * 验证输入内容的合法性
     * @return true表示验证通过,false表示验证失败
     */
    private fun validateInput(message: String): Boolean {
        // 内容安全过滤
        if (com.aicompanion.safety.ContentSafetyFilter.shouldBlock(getApplication<Application>(), message)) {
            val refusalText = com.aicompanion.safety.ContentSafetyFilter.getRefusalResponse()
            val msg = addPetMessageInternal(refusalText, Emotion.NEUTRAL, Action.IDLE)
            if (msg != null) {
                emitEvent(UiEvent.OnPetMessageAdded(msg, getMessageCount() - 1, Emotion.NEUTRAL, Action.IDLE))
            }
            return false
        }

        // API配置检查
        if (apiClient == null || settingsManager == null) {
            com.aicompanion.util.AppLogger.e(TAG, "validateInput: apiClient=${apiClient != null}, settingsManager=${settingsManager != null}")
            val msg = addPetMessageInternal("请先在设置中配置 API 哦~", Emotion.NEUTRAL, Action.IDLE)
            if (msg != null) {
                emitEvent(UiEvent.OnPetMessageAdded(msg, getMessageCount() - 1, Emotion.NEUTRAL, Action.IDLE))
            }
            return false
        }

        return true
    }

    // ==================== 阶段2: 准备阶段 ====================

    /**
     * 准备请求参数
     */
    private suspend fun prepareRequest(
        message: String,
        imageUrls: List<String>,
        currentUserMoodName: String,
        buildSystemContext: (String) -> String,
        getPersonaInfo: suspend (String) -> Pair<String, String>
    ): ChatRequest {
        AppLogger.w(TAG, "prepareRequest: 开始准备请求参数")

        val sm = settingsManager ?: throw IllegalStateException("SettingsManager未初始化")
        val client = apiClient ?: throw IllegalStateException("ApiClient未初始化")
        val actionMgr = aiActionManager

        // 获取角色信息
        AppLogger.w(TAG, "prepareRequest: 获取角色信息")
        val persona = getPersonaInfo(message)
        AppLogger.w(TAG, "prepareRequest: 角色信息获取完成, name=${persona.first}")

        // 构建历史上下文和工具定义
        val (_, history, tools) = withContext(Dispatchers.IO) {
            val ctxH = contextManager?.getRecentTurnsAsPairs() ?: emptyList()
            // 回退路径：末尾的未回复用户消息即本次输入（发送前已加入列表），API 会单独追加当前消息，去掉避免重复
            val snapshot = getMessagesSnapshot()
            val historyBase = if (snapshot.lastOrNull()?.isUser == true) snapshot.dropLast(1) else snapshot
            val h = if (ctxH.isNotEmpty()) ctxH
                    else historyBase.takeLast(settingsManager?.contextTurns ?: 10).filter { it.text.length < 500 }.map { msg ->
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
        val ctxTurns = contextManager?.getRecentTurnsAsPairs() ?: emptyList()
        if (history.isEmpty()) {
            AppLogger.e(TAG, "[MemoryDiag] 历史为空! contextManager=${contextManager != null}, 会话轮=${ctxTurns.size}, 消息快照=${getMessagesSnapshot().size}条")
        } else {
            val source = if (ctxTurns.isNotEmpty()) "ContextManager会话" else "消息快照回退"
            AppLogger.w(TAG, "[MemoryDiag] history=${history.size}条, 来源=$source, 首条=${history.first().second.take(20)}")
        }

        // 情绪分析
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
                com.aicompanion.util.AppLogger.w(TAG, "[Chat-Emotion] 情绪分析失败,使用默认值: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        // 情绪传染: 用户情绪即时影响AI状态
        try {
            subjectivityEngine?.applyEmotionContagion(
                when {
                    emotionParams.ttsEmotion in listOf("happy", "tsundere") -> 0.3f
                    emotionParams.ttsEmotion in listOf("sad", "fearful") -> -0.3f
                    emotionParams.ttsEmotion == "angry" -> -0.2f
                    else -> 0f
                }
            )
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.w(TAG, "[Chat-Emotion] 情绪传染失败: ${e.javaClass.simpleName}: ${e.message}")
        }

        // 构建昵称上下文
        val nicknameContext = buildNicknameContext()

        // 构建系统上下文
        val systemContext = buildSystemContext(message)

        // 应用情绪参数到温度和TopP
        val overrideTemp = if (sm.emotionAnalysisEnabled) emotionParams.applyToTemperature(sm.llmTemperature) else null
        val overrideTopP = if (sm.emotionAnalysisEnabled) emotionParams.applyToTopP(sm.llmTopP) else null

        return ChatRequest(
            message = message,
            imageUrls = imageUrls,
            personaName = persona.first,
            personaPrompt = persona.second,
            currentUserMoodName = currentUserMoodName,
            memories = emptyList(),
            history = history,
            systemContext = systemContext,
            tools = tools,
            emotionParams = emotionParams,
            overrideTemperature = overrideTemp,
            overrideTopP = overrideTopP,
            nicknameContext = nicknameContext
        )
    }

    // ==================== 阶段3: 调用阶段 ====================

    /**
     * 调用API发送请求
     */
    private suspend fun callApi(request: ChatRequest): ChatResponse? {
        val client = apiClient ?: throw IllegalStateException("ApiClient未初始化")
        val sm = settingsManager ?: throw IllegalStateException("SettingsManager未初始化")
        val actionMgr = aiActionManager

        val personaPromptWithContext = request.personaPrompt + request.nicknameContext

        // 技能库清单注入（渐进式披露：只给名称+描述，需要时 AI 自己用 skill 工具读取正文）
        val systemContextWithSkills = withContext(Dispatchers.IO) {
            val ctx = getApplication<android.app.Application>()
            var base = request.systemContext
            // 多步任务的执行纪律（只在带工具时注入，普通闲聊不污染）
            if (request.tools.isNotEmpty()) {
                base = base + "\n\n" + com.aicompanion.agent.AgentPrompt.EXECUTION_DISCIPLINE
            }
            if (!sm.skillEnabled) return@withContext base
            try {
                val index = com.aicompanion.skill.SkillStore(ctx).buildSkillIndexPrompt()
                if (index.isBlank()) base else "$base\n\n$index"
            } catch (_: Exception) {
                base
            }
        }

        val streamMode = sm.isStreamReply
        // 流式模式下：每收到一段文本增量就写进同一个气泡（见 onStreamDelta）
        val deltaHandler: ((String) -> Unit)? =
            if (streamMode) { text -> onStreamDelta(text) } else null

        return if (request.tools.isNotEmpty()) {
            AppLogger.w(TAG, "callApi: 调用sendChatWithToolLoop stream=$streamMode")
            withContext(Dispatchers.IO) {
                client.sendChatWithToolLoop(
                    sm.userId, request.message, request.personaName, personaPromptWithContext,
                    request.currentUserMoodName, "idle", request.memories, request.history, systemContextWithSkills, request.tools,
                    toolExecutor = { name, args ->
                        if (name == "generate_image") {
                            com.aicompanion.AppContainer.setImagePluginWorldId("")
                            com.aicompanion.AppContainer.setAssociatedEventId(null)
                        }
                        actionMgr?.executeTool(name, args) ?: ""
                    },
                    imageUrls = request.imageUrls,
                    overrideTemperature = request.overrideTemperature,
                    overrideTopP = request.overrideTopP,
                    maxIterations = sm.aiToolLoopMaxRounds,
                    stream = streamMode,
                    onDelta = deltaHandler
                )
            }
        } else {
            AppLogger.w(TAG, "callApi: 调用sendChat stream=$streamMode")
            withContext(Dispatchers.IO) {
                client.sendChat(
                    sm.userId, request.message, request.personaName, request.personaPrompt,
                    request.currentUserMoodName, "idle", request.memories, "", systemContextWithSkills, request.history,
                    imageUrls = request.imageUrls,
                    overrideTemperature = request.overrideTemperature,
                    overrideTopP = request.overrideTopP,
                    stream = streamMode,
                    onDelta = deltaHandler
                )
            }
        }
    }

    // ==================== 阶段4: 处理阶段 ====================

    /**
     * 处理API响应
     */
    private suspend fun processResponse(
        response: ChatResponse?,
        request: ChatRequest,
        isFinishing: () -> Boolean,
        isDestroyed: () -> Boolean
    ) {
        AppLogger.w(TAG, "processResponse: API响应=${response != null}, errorMsg=${response?.errorMessage}")

        emitEvent(UiEvent.SetTypingIndicator(false))

        if (response == null) {
            val errorText = "呜...连接不上AI,请检查API设置"
            val msg = addPetMessageInternal(errorText, Emotion.SAD, Action.IDLE)
            if (msg != null) {
                emitEvent(UiEvent.OnPetMessageAdded(msg, getMessageCount() - 1, Emotion.SAD, Action.IDLE))
            }
            // 即使出错也记录对话，防止记忆断层（用户问"刚才让我做什么"AI能回答）
            contextManager?.addTurn(request.message, errorText)
            return
        }

        if (response.errorMessage != null) {
            val errorText = "呜...${response.errorMessage}"
            val msg = addPetMessageInternal(errorText, Emotion.SAD, Action.IDLE)
            if (msg != null) {
                emitEvent(UiEvent.OnPetMessageAdded(msg, getMessageCount() - 1, Emotion.SAD, Action.IDLE))
            }
            // 即使出错也记录对话，防止记忆断层
            contextManager?.addTurn(request.message, errorText)
            return
        }

        val rawText = response.text
        val sm = settingsManager ?: throw IllegalStateException("SettingsManager未初始化")

        // 回复呈现方式：分段拟人 / 一次性整段 / 流式逐字
        // （设置 → AI 设置 → 回复呈现方式；默认分段）
        when (sm.aiReplyMode) {
            SettingsManager.REPLY_MODE_STREAM -> finalizeStreamResponse(rawText, response, sm)
            SettingsManager.REPLY_MODE_SINGLE -> processNoSplitResponse(rawText, response, sm)
            else -> processSplitResponse(rawText, response, sm, request)
        }

        emitEvent(UiEvent.UpdatePetDisplay(response))
        emitEvent(UiEvent.TryAttachVirtualWorldImage(request.message))

        // 处理生成的图片
        val genImagePaths = com.aicompanion.plugin.GenerateImagePlugin.consumeGeneratedImagePaths()
        genImagePaths.forEach { path ->
            emitEvent(UiEvent.AddGeneratedImage(path))
        }

        // 情绪守护
        if (emotionGuardian?.shouldSendCare() == true) {
            val careMsg = emotionGuardian?.getCareMessage() ?: ""
            if (careMsg.isNotBlank()) {
                emotionGuardian?.markCareSent()
                val careChatMsg = addPetMessageInternal(careMsg, Emotion.HAPPY, Action.IDLE)
                if (careChatMsg != null) {
                    emitEvent(UiEvent.OnPetMessageAdded(careChatMsg, getMessageCount() - 1, Emotion.HAPPY, Action.IDLE))
                }
            }
        }

        // 更新上下文
        contextManager?.addTurn(request.message, rawText)

        // 更新主体性状态
        try {
            withContext(Dispatchers.IO) {
                subjectivityEngine?.updateAfterTurn(request.message, rawText, request.emotionParams)
            }
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.w(TAG, "[Chat-Subjectivity] 主观性引擎更新失败: ${e.javaClass.simpleName}: ${e.message}")
        }

        // 触发预测
        triggerPredictionsInternal()
        emitEvent(UiEvent.CheckTurnsDiaryTrigger(request.message))

        // 评估和更新记忆
        memoryScope.launch {
            try {
                val client = apiClient ?: return@launch
                contextManager?.evaluateAndUpdateMemory(client)
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e(TAG, "[Chat-Memory] evaluateAndUpdateMemory评估记忆失败: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        // 检查是否需要新会话
        val needNewSession = contextManager?.needsNewSession() == true
        if (needNewSession) {
            val poolChars = contextManager?.memoryPool?.getPoolCharCount() ?: 0
            emitEvent(UiEvent.CheckNewSession(poolChars))
        }
    }

    /**
     * 处理不分割的响应
     */
    private suspend fun processNoSplitResponse(
        rawText: String,
        response: ChatResponse,
        sm: SettingsManager
    ) {
        if (rawText.isNotBlank()) {
            val msg = addPetMessageInternal(rawText, response.emotion, response.action)
            if (msg != null) {
                emitEvent(UiEvent.OnPetMessageAdded(msg, getMessageCount() - 1, response.emotion, response.action))
                if (sm.isTTSEnabled) {
                    val ttsMsg = getLastPetMessage()
                    if (ttsMsg != null) emitEvent(UiEvent.TriggerTtsAndPlay(rawText, response.emotion, ttsMsg))
                }
            }
        } else {
            val fallbackMsg = addPetMessageInternal("嗯...我好像走神了,能再说一次吗?", Emotion.NEUTRAL, Action.IDLE)
            if (fallbackMsg != null) {
                emitEvent(UiEvent.OnPetMessageAdded(fallbackMsg, getMessageCount() - 1, Emotion.NEUTRAL, Action.IDLE))
            }
        }
    }

    // ==================== 流式回复 ====================

    /** 本轮流式回复所在气泡的下标（-1 表示本轮还没有创建气泡） */
    @Volatile
    private var streamBubbleIndex = -1

    /** 上次因流式增量刷新 UI 的时间戳，用于节流 */
    @Volatile
    private var lastStreamEmitAt = 0L

    /** 流式刷新 UI 的最小间隔（毫秒） */
    private val streamEmitIntervalMs = 60L

    /** 每轮请求开始前重置流式状态 */
    private fun resetStreamState() {
        synchronized(messagesLock) {
            streamBubbleIndex = -1
            lastStreamEmitAt = 0L
        }
    }

    /**
     * 流式文本增量回调。
     *
     * 注意 text 是「本轮已累计的完整文本」而不是增量片段，直接覆盖气泡内容即可。
     * 首次回调时创建气泡，之后一直在同一个气泡里增长。
     *
     * 性能考虑：内容每次都写进消息列表（不丢字），但最多每 STREAM_EMIT_INTERVAL_MS
     * 通知一次 UI，避免每个字都触发整列表重组。
     */
    private fun onStreamDelta(text: String) {
        val idx: Int
        val shouldEmit: Boolean
        var isNewBubble = false
        synchronized(messagesLock) {
            if (streamBubbleIndex < 0) {
                val msg = ChatMessage(
                    text = text,
                    time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                    isUser = false,
                    emotion = Emotion.NEUTRAL,
                    isPartial = true
                )
                _messages.add(msg)
                while (_messages.size > MAX_MESSAGES) _messages.removeAt(0)
                streamBubbleIndex = _messages.size - 1
                idx = streamBubbleIndex
                lastStreamEmitAt = System.currentTimeMillis()
                shouldEmit = true
                isNewBubble = true
            } else {
                idx = streamBubbleIndex
                if (idx !in _messages.indices) return
                // 关键：必须替换成新实例。
                // ChatMessage 是 data class，若原地改字段，Compose 侧拿到的是同一个对象，
                // equals 判定相等 → 列表不重组 → 界面不刷新（流式会看起来没反应）
                _messages[idx] = _messages[idx].copy(text = text, isPartial = true)
                val now = System.currentTimeMillis()
                shouldEmit = now - lastStreamEmitAt >= streamEmitIntervalMs
                if (shouldEmit) lastStreamEmitAt = now
            }
        }
        // 首个字已经出来了，"正在输入…"可以收掉了（避免和流式气泡同时出现）
        if (isNewBubble) emitEvent(UiEvent.SetTypingIndicator(false))
        if (shouldEmit) emitEvent(UiEvent.OnStreamDelta(idx))
    }

    /**
     * 流式回复收尾：用最终文本覆盖气泡（含情绪标记剥离后的结果与最终情绪），
     * 写入聊天历史，并触发 TTS。
     */
    private suspend fun finalizeStreamResponse(rawText: String, response: ChatResponse, sm: SettingsManager) {
        val idx = synchronized(messagesLock) { streamBubbleIndex }
        if (idx < 0 || idx !in messages.indices) {
            // 全程没收到任何增量（空回复 / 整轮只有工具调用）→ 退回常规显示
            if (rawText.isBlank()) {
                val msg = addPetMessageInternal("嗯...我好像走神了,能再说一次吗?", Emotion.NEUTRAL, Action.IDLE)
                if (msg != null) {
                    emitEvent(UiEvent.OnPetMessageAdded(msg, getMessageCount() - 1, Emotion.NEUTRAL, Action.IDLE))
                }
            } else {
                processNoSplitResponse(rawText, response, sm)
            }
            return
        }

        if (rawText.isBlank()) {
            // 最终回复为空：把流式气泡删掉，避免留下一个空气泡
            synchronized(messagesLock) {
                if (idx in _messages.indices) _messages.removeAt(idx)
                streamBubbleIndex = -1
            }
            val msg = addPetMessageInternal("嗯...我好像走神了,能再说一次吗?", Emotion.NEUTRAL, Action.IDLE)
            if (msg != null) {
                emitEvent(UiEvent.OnPetMessageAdded(msg, getMessageCount() - 1, Emotion.NEUTRAL, Action.IDLE))
            }
            return
        }

        val finalMsg = synchronized(messagesLock) {
            val updated = _messages[idx].copy(
                text = rawText,
                emotion = response.emotion,
                isPartial = false
            )
            _messages[idx] = updated
            updated
        }
        saveChatHistory()
        saveMessageToFile(finalMsg)
        // 用完成事件而不是 OnPetMessageAdded：消息已经在列表里了，重复插会变成两条
        emitEvent(UiEvent.OnStreamCompleted(idx, finalMsg, response.emotion, response.action))
        if (sm.isTTSEnabled) {
            emitEvent(UiEvent.TriggerTtsAndPlay(rawText, response.emotion, finalMsg))
        }
    }

    /**
     * 处理分割的响应
     */
    private suspend fun processSplitResponse(
        rawText: String,
        response: ChatResponse,
        sm: SettingsManager,
        request: ChatRequest
    ) {
        val isComplex = humanizer.isComplexQuestion(request.message)
        val chunks = humanizer.humanize(rawText, isComplex)

        if (chunks.isEmpty()) {
            if (rawText.isNotBlank()) {
                val msg = addPetMessageInternal(rawText, response.emotion, response.action)
                if (msg != null) {
                    emitEvent(UiEvent.OnPetMessageAdded(msg, getMessageCount() - 1, response.emotion, response.action))
                    if (sm.isTTSEnabled) {
                        val ttsMsg = getLastPetMessage()
                        if (ttsMsg != null) emitEvent(UiEvent.TriggerTtsAndPlay(rawText, response.emotion, ttsMsg))
                    }
                }
            } else {
                com.aicompanion.util.AppLogger.w(TAG, "processSplitResponse: API响应成功但回复内容为空")
                val msg = addPetMessageInternal("嗯...我好像走神了,能再说一次吗?", Emotion.NEUTRAL, Action.IDLE)
                if (msg != null) {
                    emitEvent(UiEvent.OnPetMessageAdded(msg, getMessageCount() - 1, Emotion.NEUTRAL, Action.IDLE))
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
                    emitEvent(UiEvent.OnPetMessageAdded(msg, getMessageCount() - 1, emot, act))
                    if (i == 0 && sm.isTTSEnabled) {
                        val firstMsg = getLastPetMessage()
                        if (firstMsg != null) {
                            emitEvent(UiEvent.TriggerTtsAndPlay(rawText, response.emotion, firstMsg))
                        }
                    }
                }
            }
        }
    }

    // ==================== 主函数 ====================

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

        // === 阶段1: 验证 ===
        if (!validateInput(message)) return

        AppLogger.w(TAG, "sendToLLM: apiClient已就绪, url=${settingsManager?.chatApiUrl?.take(30)}, model=${settingsManager?.chatModel}")

        emitEvent(UiEvent.SetLoading(true))
        emitEvent(UiEvent.SetTypingIndicator(true))
        emitEvent(UiEvent.ScrollToPosition(getMessageCount() - 1))

        viewModelScope.launch {
            try {
                // === 阶段2: 准备请求 ===
                val request = prepareRequest(
                    message,
                    imageUrls,
                    currentUserMoodName,
                    buildSystemContext,
                    getPersonaInfo
                )

                // === 阶段3: 调用API ===
                resetStreamState()  // 每轮请求开始前清空流式气泡状态
                val response = callApi(request)

                // === 阶段4: 处理响应 ===
                processResponse(response, request, isFinishing, isDestroyed)
            } catch (e: Exception) {
                emitEvent(UiEvent.SetTypingIndicator(false))
                com.aicompanion.util.AppLogger.e(TAG, "[Chat-LLM] sendToLLM调用LLM失败: ${e.javaClass.simpleName}: ${e.message} | model=${settingsManager?.chatModel} url=${settingsManager?.chatApiUrl?.take(40)}", e)
                if (!isFinishing() && !isDestroyed()) {
                    val msg = addPetMessageInternal("出错了: ${e.message}", Emotion.SAD, Action.IDLE)
                    if (msg != null) {
                        emitEvent(UiEvent.OnPetMessageAdded(msg, getMessageCount() - 1, Emotion.SAD, Action.IDLE))
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
        val recent = getMessagesSnapshot().takeLast(10).map { msg ->
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
        try { doSaveChatHistory() } catch (e: Exception) {
            com.aicompanion.util.AppLogger.w(TAG, "[Chat-Lifecycle] onCleared保存聊天历史失败: ${e.javaClass.simpleName}: ${e.message}")
        }
        memoryScope.cancel()
    }
}
