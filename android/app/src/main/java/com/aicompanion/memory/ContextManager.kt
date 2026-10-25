package com.aicompanion.memory

import android.content.Context
import kotlinx.coroutines.launch
import com.aicompanion.network.ApiClient
import com.aicompanion.util.AppLogger

class ContextManager(private val context: Context, private val personaId: String = "default", private val scope: String = "private") {

    /**
     * 记忆巩固的后台线程。
     *
     * 巩固要调一次 AI（几秒），绝不能卡在聊天主链路上——人脑的巩固也发生在
     * 空闲和睡眠期，而不是正在说话的时候。所以这里 fire-and-forget。
     */
    private val bgScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )


    companion object {
        private const val TAG = "ContextManager"

        /**
         * 记忆提取的实际触发间隔。
         *
         * 以前直接用 contextTurns（默认 10 轮）当门槛，结果很致命：用户很少一口气聊满
         * 10 轮，而待评估的对话（rawTurns）只在内存里，退出应用就没了。于是
         * turns_since_last_eval 会一路累加、rawTurns 却重置，记忆池永远是空的——
         * 「有水管没水」就是这么来的。
         *
         * 现在取 min(contextTurns, 6)：聊 6 轮就抽一次，短对话也能留下记忆。
         */
        private const val MAX_EVAL_INTERVAL = 6

        /** 时间兜底：攒够 2 轮且距上次评估超过该时长，也触发一次（跨天聊天不丢） */
        private const val IDLE_EVAL_AFTER_MS = 6 * 60 * 60 * 1000L

        private const val MAX_EVAL_RETRIES = 3
        private const val KEY_RAW_TURNS = "raw_turns_json"
        private const val KEY_LAST_EVAL_AT = "last_eval_at"
    }

    private val sm = com.aicompanion.settings.SettingsManager(context)
    private val contextTurns: Int get() = sm.contextTurns

    private val prefsScope = if (scope == "private") "ctx_mgr_$personaId" else "ctx_mgr_${personaId}_$scope"

    val memoryPool = MemoryPool(context, personaId, scope)
    val globalMemoryPool = GlobalMemoryPool(context, personaId)
    val sessionManager = SessionManager(context)

    /** 虚拟世界记忆池（按需初始化） */
    private var vwMemoryPool: com.aicompanion.memory.VirtualWorldMemoryPool? = null

    private var rawTurns: MutableList<ConversationTurn> = mutableListOf()
    private var turnsSinceLastEval = 0
    private var totalTurns = 0
    private var evalFailCount = 0
    private var lastEvalAt = 0L
    private var cachedContextBlock: String? = null

    /** 实际评估间隔：不超过 MAX_EVAL_INTERVAL，也不超过用户设置的 contextTurns */
    private val evalInterval: Int get() = minOf(contextTurns, MAX_EVAL_INTERVAL)

    var userNickname: String = "用户"

    var onSessionWarning: ((String) -> Unit)?
        get() = sessionManager.onSessionWarning
        set(value) { sessionManager.onSessionWarning = value }

    init {
        loadState()
        applyInheritedMemory()
    }

    private fun applyInheritedMemory() {
        val inherited = sessionManager.getInheritedMemory()
        if (inherited.isNotBlank() && memoryPool.isEmpty) {
            memoryPool.add(MemoryEntry(
                content = inherited,
                category = "继承",
                sourceTurn = 0
            ))
        }
    }

    private fun loadState() {
        val prefs = context.getSharedPreferences(prefsScope, Context.MODE_PRIVATE)
        turnsSinceLastEval = prefs.getInt("turns_since_last_eval", 0)
        totalTurns = prefs.getInt("total_turns", 0)
        evalFailCount = prefs.getInt("eval_fail_count", 0)
        lastEvalAt = prefs.getLong(KEY_LAST_EVAL_AT, 0L)
        loadRawTurns(prefs)
    }

    /**
     * 待评估对话也要落盘。
     *
     * 只在内存里存意味着：用户聊 5 轮退出、下次再聊 5 轮，两次都凑不满门槛，
     * 这一段对话就永远进不了记忆池。持久化之后跨会话也能攒够一轮评估。
     */
    private fun loadRawTurns(prefs: android.content.SharedPreferences) {
        val json = prefs.getString(KEY_RAW_TURNS, null) ?: return
        try {
            val arr = org.json.JSONArray(json)
            val loaded = mutableListOf<ConversationTurn>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val u = o.optString("u", "")
                val a = o.optString("a", "")
                if (u.isBlank() && a.isBlank()) continue
                loaded.add(ConversationTurn(u, a, o.optLong("t", 0L)))
            }
            if (loaded.isNotEmpty()) {
                rawTurns = loaded.takeLast(contextTurns).toMutableList()
                AppLogger.i(TAG, "loadState: 恢复 ${rawTurns.size} 轮待评估对话")
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "loadState: 待评估对话解析失败 ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun saveState() {
        val prefs = context.getSharedPreferences(prefsScope, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putInt("turns_since_last_eval", turnsSinceLastEval)
            .putInt("total_turns", totalTurns)
            .putInt("eval_fail_count", evalFailCount)
            .putLong(KEY_LAST_EVAL_AT, lastEvalAt)
        try {
            val arr = org.json.JSONArray()
            for (t in rawTurns) {
                arr.put(org.json.JSONObject().apply {
                    put("u", t.userMsg)
                    put("a", t.aiMsg)
                    put("t", t.timestamp)
                })
            }
            editor.putString(KEY_RAW_TURNS, arr.toString())
        } catch (e: Exception) {
            AppLogger.w(TAG, "saveState: 待评估对话序列化失败 ${e.javaClass.simpleName}: ${e.message}")
        }
        editor.apply()
    }

    fun addTurn(userMsg: String, aiMsg: String) {
        if (userMsg.isBlank() || aiMsg.isBlank()) return

        rawTurns.add(ConversationTurn(userMsg, aiMsg, System.currentTimeMillis()))
        if (rawTurns.size > contextTurns) {
            rawTurns = rawTurns.takeLast(contextTurns).toMutableList()
        }

        sessionManager.incrementTurn()
        memoryPool.incrementTurn()
        turnsSinceLastEval++
        totalTurns++
        cachedContextBlock = null
        saveState()
    }

    /**
     * 是否该抽记忆了。
     *
     * 三条路任一满足即可：
     * 1. 待评估轮次攒够 evalInterval（默认 6，不是 10）
     * 2. 攒够 2 轮 + 距上次评估超过 6 小时（跨天/长时间挂后台）
     * 3. 连续失败达到上限后的重试（否则失败会把记忆通道彻底堵死）
     */
    fun shouldEvaluate(): Boolean {
        if (turnsSinceLastEval >= evalInterval) return true
        if (turnsSinceLastEval >= 2 && lastEvalAt > 0 &&
            System.currentTimeMillis() - lastEvalAt >= IDLE_EVAL_AFTER_MS
        ) return true
        return false
    }

    /** 有多少轮对话还没被评估（给"离开时整理"用） */
    fun pendingTurns(): Int = turnsSinceLastEval

    /**
     * 是否值得在离开应用时强行整理一次。
     * 门槛比常规低（3 轮），但两次之间至少隔 5 分钟，避免频繁切后台就疯狂调 AI。
     */
    fun shouldFlushOnLeave(): Boolean {
        if (turnsSinceLastEval < 3) return false
        return System.currentTimeMillis() - lastEvalAt >= 5 * 60 * 1000L
    }

    /**
     * @param force 忽略轮次门槛直接整理（用于切后台/退出），仍然会走空结果兜底
     */
    suspend fun evaluateAndUpdateMemory(client: ApiClient, force: Boolean = false) {
        if (!force && !shouldEvaluate()) {
            return
        }
        if (force && turnsSinceLastEval <= 0) return

        val turnsToEval = if (rawTurns.isNotEmpty()) {
            rawTurns.takeLast(contextTurns)
        } else {
            AppLogger.w(TAG, "evaluateAndUpdateMemory: rawTurns is empty, cannot evaluate (turnsSinceLastEval=$turnsSinceLastEval)")
            turnsSinceLastEval = 0
            evalFailCount = 0
            saveState()
            return
        }

        val turnsText = turnsToEval.joinToString("\n\n") { turn ->
            "$userNickname: ${turn.userMsg}\nAI: ${turn.aiMsg}"
        }

        AppLogger.w(TAG, "evaluateAndUpdateMemory: evaluating ${turnsToEval.size} turns (total=$totalTurns, failCount=$evalFailCount)")

        try {
            val result = memoryPool.evaluateTurn(
                client, turnsText,
                sessionManager.currentTurnCount,
                userNickname
            )

            if (result.isEmpty()) {
                evalFailCount++
                AppLogger.w(TAG, "evaluateAndUpdateMemory: evaluateTurn returned empty (failCount=$evalFailCount)")

                // AI 没抽出东西（模型抽风/输出不合法/只聊了几句），不能让这段对话凭空蒸发。
                // 本地兜底抽一条「片段」记忆，保证记忆池始终有水。
                val rescued = rescueEntries(turnsToEval)
                if (rescued.isNotEmpty()) {
                    for (entry in rescued) memoryPool.addOrUpdate(entry)
                    memoryPool.saveToStorage()
                    cachedContextBlock = null
                    turnsSinceLastEval = 0
                    evalFailCount = 0
                    lastEvalAt = System.currentTimeMillis()
                    saveState()
                    AppLogger.w(TAG, "evaluateAndUpdateMemory: AI 无产出，本地兜底写入 ${rescued.size} 条片段记忆")
                    return
                }

                if (evalFailCount >= MAX_EVAL_RETRIES) {
                    AppLogger.e(TAG, "evaluateAndUpdateMemory: failed $evalFailCount times, resetting counter")
                    turnsSinceLastEval = 0
                    evalFailCount = 0
                    saveState()
                }
                return
            }

            for (entry in result) {
                if (entry.category == "细节") {
                    memoryPool.addDetailEntry(entry)
                } else {
                    memoryPool.addOrUpdate(entry)
                }
            }

            val globalEntries = result.filter { it.isGlobal }
            if (globalEntries.isNotEmpty()) {
                globalMemoryPool.addFromScene(scope, globalEntries)
                if (globalMemoryPool.needsConsolidate()) {
                    try {
                        globalMemoryPool.consolidate(client)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "[Memory-Global] 全局记忆池整合失败: ${e.javaClass.simpleName}: ${e.message}")
                    }
                }
            }

            if (memoryPool.needsConsolidate()) {
                //AppLogger.d(TAG, "evaluateAndUpdateMemory: consolidating after eval")
                val archived = memoryPool.consolidate(client)
                // 将溢出的归档内容写入日记作为长期记忆
                if (archived.isNotBlank()) {
                    try {
                        val diaryMgr = com.aicompanion.diary.DiaryManager(context)
                        diaryMgr.appendMemoryArchive(archived)
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "[Memory-Archive] 记忆归档写入日记失败: ${e.javaClass.simpleName}: ${e.message}")
                    }
                }
            }

            memoryPool.saveToStorage()
            turnsSinceLastEval = 0
            evalFailCount = 0
            lastEvalAt = System.currentTimeMillis()
            saveState()
            cachedContextBlock = null
            AppLogger.w(TAG, "evaluateAndUpdateMemory: success, ${result.size} entries extracted, pool=${memoryPool.size}")

            // 顺手检查是否有到龄期的记忆需要巩固（后台跑，不阻塞聊天）
            if (memoryPool.needsTierConsolidation()) {
                bgScope.launch {
                    runCatching { consolidateTiersIfDue() }
                        .onFailure { AppLogger.w(TAG, "[Memory-Tier] 巩固失败: ${it.message}") }
                }
            }
        } catch (e: Exception) {
            evalFailCount++
            AppLogger.e(TAG, "[Memory-Eval] evaluateAndUpdateMemory评估异常(failCount=$evalFailCount): ${e.javaClass.simpleName}: ${e.message}")
            if (evalFailCount >= MAX_EVAL_RETRIES) {
                // 连着失败就把这点对话本地兜底存下来，再重置，别白聊
                val rescued = runCatching { rescueEntries(turnsToEval) }.getOrDefault(emptyList())
                if (rescued.isNotEmpty()) {
                    rescued.forEach { memoryPool.addOrUpdate(it) }
                    memoryPool.saveToStorage()
                    cachedContextBlock = null
                    lastEvalAt = System.currentTimeMillis()
                    AppLogger.w(TAG, "[Memory-Eval] 连续失败，已本地兜底保存 ${rescued.size} 条")
                }
                turnsSinceLastEval = 0
                evalFailCount = 0
                saveState()
            }
        }
    }

    /**
     * 本地兜底抽取（不调 AI）。
     *
     * 判据：取用户侧「有信息量」的句子——去掉纯寒暄、太短的语气词，保留具体陈述。
     * 宁可记粗糙一点，也不要留白：粗糙的片段还能被检索到，留白就是彻底断片。
     */
    private fun rescueEntries(turns: List<ConversationTurn>): List<MemoryEntry> {
        val chitchat = setOf(
            "你好", "您好", "在吗", "在不在", "嗯", "恩", "哦", "噢", "好", "好的", "好吧",
            "哈哈", "哈哈哈", "hhh", "草", "？", "?", "。。。", "晚安", "早安", "早", "拜拜", "再见"
        )
        val now = System.currentTimeMillis()
        val timeText = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(now))
        val out = mutableListOf<MemoryEntry>()
        val seen = mutableSetOf<String>()

        for (turn in turns.asReversed()) {
            val text = turn.userMsg.trim()
                .replace(Regex("\\s+"), " ")
            if (text.length < 4) continue
            if (chitchat.contains(text.lowercase())) continue
            if (text.all { it.isDigit() || it.isWhitespace() }) continue
            // 去掉重复的贴图/表情占位
            val key = text.take(12)
            if (!seen.add(key)) continue

            out.add(
                MemoryEntry(
                    content = "用户说：${text.take(80)}",
                    category = "片段",
                    sourceTurn = sessionManager.currentTurnCount,
                    eventTime = timeText,
                    people = userNickname,
                    scene = "日常闲聊",
                    details = text.take(200)
                )
            )
            if (out.size >= 3) break
        }
        return out.asReversed()
    }

    /**
     * 时间驱动的分层巩固：把 7 天前的情景记忆压成要点，
     * 30 天前的要点抽象成长期印象。没到龄期就直接返回，不会调 AI。
     */
    suspend fun consolidateTiersIfDue(): String {
        if (!memoryPool.needsTierConsolidation()) return ""
        val client = runCatching { apiClient() }.getOrNull() ?: return ""
        val r = com.aicompanion.memory.MemoryConsolidator(memoryPool).consolidateIfDue(client)
        if (r.changed) {
            cachedContextBlock = null
            AppLogger.i(TAG, "[Memory-Tier] 巩固完成：${r.note}")
        }
        return r.note
    }

    fun needsCompression(): Boolean {
        return memoryPool.needsConsolidate()
    }

    suspend fun compress() {
        val archived = memoryPool.consolidate(apiClient())
        if (archived.isNotBlank()) {
            try {
                val diaryMgr = com.aicompanion.diary.DiaryManager(context)
                diaryMgr.appendMemoryArchive(archived)
            } catch (e: Exception) {
                AppLogger.w(TAG, "[Memory-Archive] 压缩归档写入日记失败: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        memoryPool.saveToStorage()
    }

    private fun apiClient(): ApiClient {
        return com.aicompanion.AppContainer.apiClient ?: throw IllegalStateException("ApiClient not initialized")
    }

    fun getContextBlock(): String {
        cachedContextBlock?.let { return it }
        val result = getFullContextBlock()
        cachedContextBlock = result
        return result
    }

    fun getFullContextBlock(): String {
        val sb = StringBuilder()

        // 第一层：跨场景共享记忆
        val globalBlock = globalMemoryPool.getGlobalBlock()
        if (globalBlock.isNotBlank()) {
            sb.appendLine(globalBlock)
        }

        // 第二层：私聊/群聊场景记忆 —— 按时间分层注入
        // 近期保留细节、7 天以上只留要点、30 天以上只留长期印象（见 TieredMemory）
        val poolBlock = memoryPool.getTieredBlock()
        if (poolBlock.isNotBlank()) {
            sb.appendLine(poolBlock)
        }

        // 第三层：虚拟世界记忆（自动懒加载）
        val vwPool = vwMemoryPool ?: run {
            val pool = com.aicompanion.memory.VirtualWorldMemoryPool(context)
            vwMemoryPool = pool
            pool
        }
        if (!vwPool.isEmpty) {
            val vwBlock = vwPool.getVwBlock()
            if (vwBlock.isNotBlank()) {
                sb.appendLine(vwBlock)
            }
        }

        // 第四层：细节记忆
        val detailBlock = memoryPool.getDetailBlock()
        if (detailBlock.isNotBlank()) {
            sb.appendLine()
            sb.appendLine(detailBlock)
        }

        return sb.toString().trimEnd()
    }

    fun getRecentTurnsText(): String {
        if (rawTurns.isEmpty()) return ""
        return rawTurns.joinToString("\n\n") { turn ->
            "$userNickname: ${turn.userMsg}\nAI: ${turn.aiMsg}"
        }
    }

    fun getRecentTurnsAsPairs(): List<Pair<Boolean, String>> {
        if (rawTurns.isEmpty()) return emptyList()
        return rawTurns.flatMap { turn ->
            listOf(
                true to turn.userMsg,
                false to turn.aiMsg
            )
        }
    }

    fun needsNewSession(): Boolean {
        return sessionManager.checkMemoryLimit(memoryPool)
    }

    suspend fun createNewSession(client: ApiClient, diaryCallback: suspend (String) -> Unit) {
        sessionManager.createNewSession(memoryPool, client) { poolBlock ->
            diaryCallback(poolBlock)
        }
        rawTurns.clear()
    }

    /** 获取或初始化虚拟世界记忆池 */
    fun getOrCreateVwMemoryPool(worldId: String = ""): com.aicompanion.memory.VirtualWorldMemoryPool {
        if (vwMemoryPool == null) {
            vwMemoryPool = com.aicompanion.memory.VirtualWorldMemoryPool(context, worldId)
        }
        return vwMemoryPool!!
    }

    /** 虚拟世界是否活跃且有记忆 */
    fun hasVwMemory(): Boolean = vwMemoryPool?.isEmpty == false

    fun getSessionStats(): String {
        val remaining = maxOf(0, contextTurns - turnsSinceLastEval)
        val failInfo = if (evalFailCount > 0) " [评估失败${evalFailCount}次]" else ""
        return "会话 #${sessionManager.currentSessionId.take(6)} | " +
                "轮次: ${sessionManager.currentTurnCount} | " +
                memoryPool.getStats() +
                " | 下次记忆提取: ${remaining}轮后$failInfo"
    }

    fun clear() {
        rawTurns.clear()
        memoryPool.clear()
        globalMemoryPool.clear()
        sessionManager.clear()
        vwMemoryPool?.clear()
        vwMemoryPool = null
        cachedContextBlock = null
        turnsSinceLastEval = 0
        totalTurns = 0
        evalFailCount = 0
        saveState()
    }

    data class ConversationTurn(
        val userMsg: String,
        val aiMsg: String,
        val timestamp: Long
    )
}
