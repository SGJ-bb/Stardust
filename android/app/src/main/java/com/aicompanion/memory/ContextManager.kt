package com.aicompanion.memory

import android.content.Context
import com.aicompanion.network.ApiClient
import com.aicompanion.util.AppLogger

class ContextManager(private val context: Context, private val personaId: String = "default", private val scope: String = "private") {

    companion object {
        private const val TAG = "ContextManager"
        private const val DEFAULT_INTERVAL = 10
        private const val MAX_EVAL_RETRIES = 3
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
    private var cachedContextBlock: String? = null

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
    }

    private fun saveState() {
        val prefs = context.getSharedPreferences(prefsScope, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("turns_since_last_eval", turnsSinceLastEval)
            .putInt("total_turns", totalTurns)
            .putInt("eval_fail_count", evalFailCount)
            .apply()
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

    fun shouldEvaluate(): Boolean = turnsSinceLastEval >= contextTurns

    suspend fun evaluateAndUpdateMemory(client: ApiClient) {
        if (!shouldEvaluate()) {
            //AppLogger.d(TAG, "evaluateAndUpdateMemory: skipped ($turnsSinceLastEval/$contextTurns turns)")
            return
        }

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
            saveState()
            cachedContextBlock = null
            AppLogger.w(TAG, "evaluateAndUpdateMemory: success, ${result.size} entries extracted")
        } catch (e: Exception) {
            evalFailCount++
            AppLogger.e(TAG, "[Memory-Eval] evaluateAndUpdateMemory评估异常(failCount=$evalFailCount): ${e.javaClass.simpleName}: ${e.message}")
            if (evalFailCount >= MAX_EVAL_RETRIES) {
                AppLogger.e(TAG, "evaluateAndUpdateMemory: failed $evalFailCount times, resetting counter")
                turnsSinceLastEval = 0
                evalFailCount = 0
                saveState()
            }
        }
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

        // 第二层：私聊/群聊场景记忆（650字上限）
        val poolBlock = memoryPool.getPoolBlock()
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
