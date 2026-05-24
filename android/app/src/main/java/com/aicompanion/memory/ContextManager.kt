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
            AppLogger.d(TAG, "evaluateAndUpdateMemory: skipped ($turnsSinceLastEval/$contextTurns turns)")
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

        AppLogger.d(TAG, "evaluateAndUpdateMemory: evaluating ${turnsToEval.size} turns (total=$totalTurns, failCount=$evalFailCount)")

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
                        AppLogger.e(TAG, "globalMemoryPool consolidate error: ${e.message}")
                    }
                }
            }

            if (memoryPool.needsConsolidate()) {
                AppLogger.d(TAG, "evaluateAndUpdateMemory: consolidating after eval")
                memoryPool.consolidate(client)
            }

            memoryPool.saveToStorage()
            turnsSinceLastEval = 0
            evalFailCount = 0
            saveState()
            cachedContextBlock = null
            AppLogger.d(TAG, "evaluateAndUpdateMemory: success, ${result.size} entries extracted")
        } catch (e: Exception) {
            evalFailCount++
            AppLogger.e(TAG, "evaluateAndUpdateMemory: exception (failCount=$evalFailCount) - ${e.message}")
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
        memoryPool.consolidate(apiClient())
        memoryPool.saveToStorage()
    }

    private fun apiClient(): ApiClient {
        return com.aicompanion.AppContainer.apiClient ?: throw IllegalStateException("ApiClient not initialized")
    }

    fun getContextBlock(): String {
        cachedContextBlock?.let { return it }
        val result = globalMemoryPool.getGlobalBlock()
        cachedContextBlock = result
        return result
    }

    fun getFullContextBlock(): String {
        val sb = StringBuilder()
        val globalBlock = globalMemoryPool.getGlobalBlock()
        if (globalBlock.isNotBlank()) {
            sb.appendLine(globalBlock)
        }
        val poolBlock = memoryPool.getPoolBlock()
        if (poolBlock.isNotBlank()) {
            sb.appendLine(poolBlock)
        }
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
