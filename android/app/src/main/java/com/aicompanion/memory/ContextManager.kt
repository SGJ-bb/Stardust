package com.aicompanion.memory

import android.content.Context
import com.aicompanion.network.ApiClient
import com.aicompanion.util.AppLogger

class ContextManager(context: Context, personaId: String = "default") {

    companion object {
        private const val TAG = "ContextManager"
        private const val EVAL_INTERVAL = 2
    }

    val memoryPool = MemoryPool(context, personaId)
    val sessionManager = SessionManager(context)

    private var rawTurns: MutableList<ConversationTurn> = mutableListOf()
    private val maxRawTurns = 8
    private var evalTurnsSinceLastEval = 0
    private var totalTurnsForEval = 0

    var userNickname: String = "用户"

    var onSessionWarning: ((String) -> Unit)?
        get() = sessionManager.onSessionWarning
        set(value) { sessionManager.onSessionWarning = value }

    init {
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

    fun addTurn(userMsg: String, aiMsg: String) {
        if (userMsg.isBlank() || aiMsg.isBlank()) return

        rawTurns.add(ConversationTurn(userMsg, aiMsg, System.currentTimeMillis()))
        if (rawTurns.size > maxRawTurns * 2) {
            rawTurns = rawTurns.takeLast(maxRawTurns).toMutableList()
        }

        sessionManager.incrementTurn()
        memoryPool.incrementTurn()
        evalTurnsSinceLastEval++
        totalTurnsForEval++
    }

    fun shouldEvaluate(): Boolean {
        if (totalTurnsForEval == 1) return true
        if (evalTurnsSinceLastEval >= EVAL_INTERVAL) return true
        return false
    }

    suspend fun evaluateAndUpdateMemory(client: ApiClient) {
        if (rawTurns.isEmpty()) return

        if (memoryPool.needsConsolidate()) {
            AppLogger.d(TAG, "evaluateAndUpdateMemory: consolidating memory pool")
            memoryPool.consolidate(client)
            return
        }

        if (!shouldEvaluate()) {
            AppLogger.d(TAG, "evaluateAndUpdateMemory: skipped ($evalTurnsSinceLastEval turns since last eval)")
            return
        }

        evalTurnsSinceLastEval = 0

        val lastTurn = rawTurns.last()
        val result = memoryPool.evaluateTurn(
            client, lastTurn.userMsg, lastTurn.aiMsg,
            sessionManager.currentTurnCount,
            userNickname
        )
        for (entry in result) {
            memoryPool.addOrUpdate(entry)
        }
    }

    fun needsCompression(): Boolean {
        return memoryPool.needsConsolidate()
    }

    suspend fun compress() {
        memoryPool.saveToStorage()
    }

    fun getContextBlock(): String {
        val sb = StringBuilder()

        val poolBlock = memoryPool.getPoolBlock()
        if (poolBlock.isNotBlank()) {
            sb.appendLine(poolBlock)
        }

        val inherited = sessionManager.getInheritedMemory()
        if (inherited.isNotBlank() && !poolBlock.contains(inherited.take(50))) {
            sb.appendLine()
            sb.appendLine("[继承自上个会话的记忆]")
            sb.appendLine(inherited.take(500))
        }

        if (rawTurns.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("[最近对话]")
            for (turn in rawTurns.takeLast(6)) {
                sb.appendLine("用户: ${turn.userMsg.take(100)}")
                sb.appendLine("你: ${turn.aiMsg.take(100)}")
            }
        }

        return sb.toString().trimEnd()
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
        return "会话 #${sessionManager.currentSessionId.take(6)} | " +
                "轮次: ${sessionManager.currentTurnCount} | " +
                memoryPool.getStats()
    }

    fun clear() {
        rawTurns.clear()
        memoryPool.clear()
        sessionManager.clear()
    }

    data class ConversationTurn(
        val userMsg: String,
        val aiMsg: String,
        val timestamp: Long
    )
}
