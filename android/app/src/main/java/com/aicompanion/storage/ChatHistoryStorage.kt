package com.aicompanion.storage

import android.content.Context
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StoredMessage(
    val id: String,
    val text: String,
    val time: String,
    val isUser: Boolean,
    val userMood: String = "",
    val feedback: Int = 0,
    val emotion: String = "NEUTRAL",
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorited: Boolean = false,
    val reactionEmoji: String = "",
    val stickerPath: String? = null,
    val senderPersonaId: String = "",
    val senderName: String = "",
    val audioPath: String? = null,
    val audioUrl: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("time", time)
        put("isUser", isUser)
        put("userMood", userMood)
        put("feedback", feedback)
        put("emotion", emotion)
        put("timestamp", timestamp)
        put("isFavorited", isFavorited)
        put("reactionEmoji", reactionEmoji)
        if (stickerPath != null) put("stickerPath", stickerPath)
        if (senderPersonaId.isNotBlank()) put("senderPersonaId", senderPersonaId)
        if (senderName.isNotBlank()) put("senderName", senderName)
        if (audioPath != null) put("audioPath", audioPath)
        if (audioUrl != null) put("audioUrl", audioUrl)
    }

    companion object {
        fun fromJson(obj: JSONObject): StoredMessage = StoredMessage(
            id = obj.optString("id", ""),
            text = obj.optString("text", ""),
            time = obj.optString("time", ""),
            isUser = obj.optBoolean("isUser", false),
            userMood = obj.optString("userMood", ""),
            feedback = obj.optInt("feedback", 0),
            emotion = obj.optString("emotion", "NEUTRAL"),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            isFavorited = obj.optBoolean("isFavorited", false),
            reactionEmoji = obj.optString("reactionEmoji", ""),
            stickerPath = obj.optString("stickerPath", ""),
            senderPersonaId = obj.optString("senderPersonaId", ""),
            senderName = obj.optString("senderName", ""),
            audioPath = obj.optString("audioPath", ""),
            audioUrl = obj.optString("audioUrl", "")
        )
    }
}

class ChatHistoryStorage(private val context: Context) {

    companion object {
        private const val TAG = "ChatHistory"
        private const val INDEX_FILE = "index.json"
        private const val MSG_DIR = "messages"
    }

    private val baseDir = File(context.filesDir, "chat_history")
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private fun getScopeDir(scope: String, scopeId: String): File {
        return File(baseDir, "$scope/$scopeId")
    }

    private fun getMsgDir(scope: String, scopeId: String, date: String): File {
        return File(getScopeDir(scope, scopeId), "$MSG_DIR/$date")
    }

    fun addMessage(scope: String, scopeId: String, msg: StoredMessage) {
        try {
            val date = dateFmt.format(Date(msg.timestamp))
            val msgDir = getMsgDir(scope, scopeId, date)
            if (!msgDir.exists()) msgDir.mkdirs()

            val fileName = "${msg.timestamp}_${if (msg.isUser) "u" else "a"}_${msg.id.take(8)}.json"
            val file = File(msgDir, fileName)
            file.writeText(msg.toJson().toString())

            updateIndex(scope, scopeId, date)
        } catch (e: Exception) {
            AppLogger.e(TAG, "addMessage: ${e.message}")
        }
    }

    fun addMessages(scope: String, scopeId: String, msgs: List<StoredMessage>) {
        if (msgs.isEmpty()) return
        try {
            val grouped = msgs.groupBy { dateFmt.format(Date(it.timestamp)) }
            grouped.forEach { (date, dateMsgs) ->
                val msgDir = getMsgDir(scope, scopeId, date)
                if (!msgDir.exists()) msgDir.mkdirs()
                dateMsgs.forEach { msg ->
                    val fileName = "${msg.timestamp}_${if (msg.isUser) "u" else "a"}_${msg.id.take(8)}.json"
                    File(msgDir, fileName).writeText(msg.toJson().toString())
                }
                updateIndex(scope, scopeId, date)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "addMessages: ${e.message}")
        }
    }

    fun getMessages(scope: String, scopeId: String, date: String): List<StoredMessage> {
        val msgDir = getMsgDir(scope, scopeId, date)
        if (!msgDir.exists()) return emptyList()
        return try {
            msgDir.listFiles()
                ?.filter { it.name.endsWith(".json") }
                ?.sortedBy { it.name }
                ?.mapNotNull {
                    try { StoredMessage.fromJson(JSONObject(it.readText())) }
                    catch (_: Exception) { null }
                } ?: emptyList()
        } catch (e: Exception) {
            AppLogger.e(TAG, "getMessages: ${e.message}")
            emptyList()
        }
    }

    fun getRecentMessages(scope: String, scopeId: String, limit: Int = 100): List<StoredMessage> {
        val dates = getDates(scope, scopeId)
        val result = mutableListOf<StoredMessage>()
        for (date in dates.reversed()) {
            val dayMsgs = getMessages(scope, scopeId, date)
            if (dayMsgs.isEmpty()) continue
            val needed = limit - result.size
            if (dayMsgs.size <= needed) {
                result.addAll(0, dayMsgs)
            } else {
                result.addAll(0, dayMsgs.takeLast(needed))
            }
            if (result.size >= limit) break
        }
        return result
    }

    fun getDates(scope: String, scopeId: String): List<String> {
        val indexFile = File(getScopeDir(scope, scopeId), INDEX_FILE)
        if (!indexFile.exists()) return emptyList()
        return try {
            val json = JSONObject(indexFile.readText())
            val arr = json.optJSONArray("dates") ?: return emptyList()
            (0 until arr.length()).map { arr.getString(it) }.sorted()
        } catch (e: Exception) {
            AppLogger.e(TAG, "getDates: ${e.message}")
            emptyList()
        }
    }

    fun searchMessages(scope: String, scopeId: String, query: String, limit: Int = 20): List<StoredMessage> {
        val dates = getDates(scope, scopeId)
        val result = mutableListOf<StoredMessage>()
        val lowerQuery = query.lowercase()
        for (date in dates.reversed()) {
            val dayMsgs = getMessages(scope, scopeId, date)
            dayMsgs.reversed().forEach { msg ->
                if (msg.text.lowercase().contains(lowerQuery)) {
                    result.add(msg)
                    if (result.size >= limit) return result
                }
            }
        }
        return result
    }

    fun deleteDate(scope: String, scopeId: String, date: String) {
        try {
            val msgDir = getMsgDir(scope, scopeId, date)
            if (msgDir.exists()) {
                msgDir.listFiles()?.forEach { it.delete() }
                msgDir.delete()
            }
            removeFromIndex(scope, scopeId, date)
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteDate: ${e.message}")
        }
    }

    fun deleteScope(scope: String, scopeId: String) {
        try {
            val dir = getScopeDir(scope, scopeId)
            if (dir.exists()) dir.deleteRecursively()
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteScope: ${e.message}")
        }
    }

    fun getMessageCount(scope: String, scopeId: String): Int {
        val dates = getDates(scope, scopeId)
        var count = 0
        for (date in dates) {
            val msgDir = getMsgDir(scope, scopeId, date)
            if (msgDir.exists()) {
                count += msgDir.listFiles()?.count { it.name.endsWith(".json") } ?: 0
            }
        }
        return count
    }

    fun getStats(scope: String, scopeId: String): String {
        val dates = getDates(scope, scopeId)
        val count = getMessageCount(scope, scopeId)
        val dateCount = dates.size
        val firstDate = dates.firstOrNull() ?: "无"
        val lastDate = dates.lastOrNull() ?: "无"
        return "${count}条消息 | ${dateCount}天 | $firstDate ~ $lastDate"
    }

    private fun updateIndex(scope: String, scopeId: String, date: String) {
        try {
            val scopeDir = getScopeDir(scope, scopeId)
            if (!scopeDir.exists()) scopeDir.mkdirs()
            val indexFile = File(scopeDir, INDEX_FILE)
            val json = if (indexFile.exists()) {
                try { JSONObject(indexFile.readText()) } catch (_: Exception) { JSONObject() }
            } else JSONObject()
            val arr = json.optJSONArray("dates") ?: JSONArray()
            val existing = (0 until arr.length()).map { arr.getString(it) }.toMutableSet()
            if (!existing.contains(date)) {
                arr.put(date)
                json.put("dates", arr)
                indexFile.writeText(json.toString())
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "updateIndex: ${e.message}")
        }
    }

    private fun removeFromIndex(scope: String, scopeId: String, date: String) {
        try {
            val indexFile = File(getScopeDir(scope, scopeId), INDEX_FILE)
            if (!indexFile.exists()) return
            val json = JSONObject(indexFile.readText())
            val arr = json.optJSONArray("dates") ?: return
            val newArr = JSONArray()
            for (i in 0 until arr.length()) {
                val d = arr.getString(i)
                if (d != date) newArr.put(d)
            }
            json.put("dates", newArr)
            indexFile.writeText(json.toString())
        } catch (e: Exception) {
            AppLogger.e(TAG, "removeFromIndex: ${e.message}")
        }
    }

    fun migrateFromSharedPreferences(prefsName: String, scope: String, scopeId: String): Int {
        try {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val json = prefs.getString("messages", null) ?: return 0
            val arr = JSONArray(json)
            val msgs = mutableListOf<StoredMessage>()
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    msgs.add(StoredMessage.fromJson(obj))
                } catch (_: Exception) {}
            }
            if (msgs.isNotEmpty()) {
                addMessages(scope, scopeId, msgs)
                AppLogger.i(TAG, "migrated ${msgs.size} msgs from $prefsName")
            }
            return msgs.size
        } catch (e: Exception) {
            AppLogger.e(TAG, "migrateFromSP: ${e.message}")
            return 0
        }
    }
}
