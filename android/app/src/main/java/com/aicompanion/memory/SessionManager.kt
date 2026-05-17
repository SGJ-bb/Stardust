package com.aicompanion.memory

import android.content.Context
import com.aicompanion.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID

data class SessionInfo(
    val id: String = UUID.randomUUID().toString().take(12),
    val startTime: Long = System.currentTimeMillis(),
    val turnCount: Int = 0,
    val inheritedFrom: String? = null,
    val memorySnapshot: String = ""
)

class SessionManager(private val context: Context) {

    companion object {
        private const val TAG = "SessionManager"
        private const val MAX_SESSIONS = 20
    }

    private var currentSession: SessionInfo = SessionInfo()
    private val sessions = mutableListOf<SessionInfo>()
    private val prefs = context.getSharedPreferences("session_mgr", Context.MODE_PRIVATE)

    var onSessionWarning: ((String) -> Unit)? = null
    var onNewSessionCreated: ((SessionInfo) -> Unit)? = null

    init {
        loadSessions()
    }

    val currentSessionId: String get() = currentSession.id
    val currentTurnCount: Int get() = currentSession.turnCount

    fun incrementTurn() {
        currentSession = currentSession.copy(turnCount = currentSession.turnCount + 1)
        saveCurrentSession()
    }

    fun checkMemoryLimit(memoryPool: MemoryPool): Boolean {
        val charCount = memoryPool.getPoolCharCount()
        if (charCount > 800) {
            onSessionWarning?.invoke("记忆池已达${charCount}字，即将自动整理")
            return false
        }
        return false
    }

    suspend fun createNewSession(
        memoryPool: MemoryPool,
        apiClient: ApiClient,
        diaryCallback: suspend (String) -> Unit
    ): SessionInfo = withContext(Dispatchers.IO) {

        val poolBlock = memoryPool.getPoolBlock()
        if (poolBlock.isNotBlank()) {
            diaryCallback(poolBlock)
        }

        memoryPool.consolidate(apiClient)

        val compressedMemory = memoryPool.getPoolBlock()

        val newSession = SessionInfo(
            startTime = System.currentTimeMillis(),
            inheritedFrom = currentSession.id,
            memorySnapshot = compressedMemory
        )

        archiveCurrentSession()
        sessions.add(0, newSession)
        while (sessions.size > MAX_SESSIONS) {
            sessions.removeAt(sessions.lastIndex)
        }
        currentSession = newSession
        saveSessions()

        onNewSessionCreated?.invoke(newSession)
        newSession
    }

    fun getInheritedMemory(): String {
        return currentSession.memorySnapshot
    }

    fun getAllSessions(): List<SessionInfo> = sessions.toList()

    private fun archiveCurrentSession() {
        val idx = sessions.indexOfFirst { it.id == currentSession.id }
        if (idx >= 0) {
            sessions[idx] = currentSession
        } else {
            sessions.add(0, currentSession)
        }
    }

    private fun saveCurrentSession() {
        val idx = sessions.indexOfFirst { it.id == currentSession.id }
        if (idx >= 0) {
            sessions[idx] = currentSession
        } else {
            sessions.add(0, currentSession)
        }
        saveSessions()
    }

    private fun saveSessions() {
        try {
            val arr = JSONArray()
            for (session in sessions.take(MAX_SESSIONS)) {
                val obj = org.json.JSONObject()
                obj.put("id", session.id)
                obj.put("startTime", session.startTime)
                obj.put("turnCount", session.turnCount)
                obj.put("inheritedFrom", session.inheritedFrom ?: "")
                obj.put("memorySnapshot", session.memorySnapshot)
                arr.put(obj)
            }
            prefs.edit().putString("sessions", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun loadSessions() {
        try {
            val json = prefs.getString("sessions", null) ?: return
            val arr = JSONArray(json)
            sessions.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                sessions.add(SessionInfo(
                    id = obj.optString("id", UUID.randomUUID().toString().take(12)),
                    startTime = obj.optLong("startTime", System.currentTimeMillis()),
                    turnCount = obj.optInt("turnCount", 0),
                    inheritedFrom = obj.optString("inheritedFrom", "").ifBlank { null },
                    memorySnapshot = obj.optString("memorySnapshot", "")
                ))
            }
            if (sessions.isNotEmpty()) {
                currentSession = sessions.first()
            }
        } catch (_: Exception) {
            sessions.clear()
        }
    }

    fun clear() {
        sessions.clear()
        currentSession = SessionInfo()
        prefs.edit().clear().apply()
    }
}
