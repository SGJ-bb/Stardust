package com.aicompanion.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import com.aicompanion.diary.DiaryEntry
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
    val generatedImagePath: String? = null,
    val senderPersonaId: String = "",
    val senderName: String = "",
    val audioPath: String? = null,
    val audioUrl: String? = null,
    val imageUrls: List<String> = emptyList()
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
        if (generatedImagePath != null) put("generatedImagePath", generatedImagePath)
        if (senderPersonaId.isNotBlank()) put("senderPersonaId", senderPersonaId)
        if (senderName.isNotBlank()) put("senderName", senderName)
        if (audioPath != null) put("audioPath", audioPath)
        if (audioUrl != null) put("audioUrl", audioUrl)
        if (imageUrls.isNotEmpty()) put("imageUrls", JSONArray(imageUrls))
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
            stickerPath = obj.optString("stickerPath", "").ifBlank { null },
            generatedImagePath = obj.optString("generatedImagePath", "").ifBlank { null },
            senderPersonaId = obj.optString("senderPersonaId", ""),
            senderName = obj.optString("senderName", ""),
            audioPath = obj.optString("audioPath", "").ifBlank { null },
            audioUrl = obj.optString("audioUrl", "").ifBlank { null },
            imageUrls = try {
                val arr = obj.optJSONArray("imageUrls")
                if (arr != null) (0 until arr.length()).map { arr.getString(it) } else emptyList()
            } catch (_: Exception) { emptyList() }
        )
    }
}

private const val DB_NAME = "chat_history.db"
private const val DB_VERSION = 2
private const val TBL_MESSAGES = "chat_messages"
private const val TBL_DIARIES = "diaries"

/** SQLite-backed storage for chat messages. Replaces the old JSON-file-based implementation. */
class ChatHistoryStorage(private val context: Context) {

    companion object {
        private const val TAG = "ChatHistorySQL"
        private const val MIGRATION_DONE_KEY = "sql_migration_done"
    }

    private val helper = object : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TBL_MESSAGES (
                    id              TEXT NOT NULL,
                    scope           TEXT NOT NULL,
                    scope_id        TEXT NOT NULL,
                    text            TEXT NOT NULL DEFAULT '',
                    time            TEXT NOT NULL DEFAULT '',
                    is_user         INTEGER NOT NULL DEFAULT 0,
                    user_mood       TEXT DEFAULT '',
                    feedback        INTEGER NOT NULL DEFAULT 0,
                    emotion         TEXT DEFAULT 'NEUTRAL',
                    timestamp       INTEGER NOT NULL,
                    is_favorited    INTEGER NOT NULL DEFAULT 0,
                    reaction_emoji  TEXT DEFAULT '',
                    sticker_path    TEXT,
                    generated_image_path TEXT,
                    sender_persona_id   TEXT DEFAULT '',
                    sender_name     TEXT DEFAULT '',
                `audio_path`      TEXT,
                    `audio_url`     TEXT,
                    image_urls      TEXT,
                    PRIMARY KEY (id)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_scope ON $TBL_MESSAGES(scope, scope_id, timestamp)")

            // Diaries table (long-term memory)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TBL_DIARIES (
                    persona_id      TEXT NOT NULL,
                    date            TEXT NOT NULL,
                    title           TEXT NOT NULL DEFAULT '',
                    content         TEXT NOT NULL DEFAULT '',
                    mood            TEXT NOT NULL DEFAULT 'normal',
                    mood_emoji      TEXT NOT NULL DEFAULT '😊',
                    affection_level INTEGER NOT NULL DEFAULT 0,
                    message_count   INTEGER NOT NULL DEFAULT 0,
                    key_memories    TEXT,
                    tags            TEXT,
                    plugin_meta     TEXT,
                    custom_fields   TEXT,
                    created_at      INTEGER NOT NULL,
                    updated_at      INTEGER NOT NULL,
                    app_version     TEXT DEFAULT '',
                    version         INTEGER NOT NULL DEFAULT 2,
                    PRIMARY KEY (persona_id, date)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_diaries_mood ON $TBL_DIARIES(persona_id, mood)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_diaries_date ON $TBL_DIARIES(persona_id, date)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                // v1→v2: add diaries table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TBL_DIARIES (
                        persona_id      TEXT NOT NULL,
                        date            TEXT NOT NULL,
                        title           TEXT NOT NULL DEFAULT '',
                        content         TEXT NOT NULL DEFAULT '',
                        mood            TEXT NOT NULL DEFAULT 'normal',
                        mood_emoji      TEXT NOT NULL DEFAULT '😊',
                        affection_level INTEGER NOT NULL DEFAULT 0,
                        message_count   INTEGER NOT NULL DEFAULT 0,
                        key_memories    TEXT,
                        tags            TEXT,
                        plugin_meta     TEXT,
                        custom_fields   TEXT,
                        created_at      INTEGER NOT NULL,
                        updated_at      INTEGER NOT NULL,
                        app_version     TEXT DEFAULT '',
                        version         INTEGER NOT NULL DEFAULT 2,
                        PRIMARY KEY (persona_id, date)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_diaries_mood ON $TBL_DIARIES(persona_id, mood)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_diaries_date ON $TBL_DIARIES(persona_id, date)")
            }
        }
    }

    private val writableDB get() = helper.writableDatabase
    private val readableDB get() = helper.readableDatabase

    init {
        migrateLegacyJsonIfNeeded()
    }

    // ─── Public API ──────────────────────────────────────────────

    fun addMessage(scope: String, scopeId: String, msg: StoredMessage) {
        try {
            writableDB.insertWithOnConflict(TBL_MESSAGES, null, msgToCv(scope, scopeId, msg),
                SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            AppLogger.e(TAG, "addMessage: ${e.message}")
        }
    }

    fun addMessages(scope: String, scopeId: String, msgs: List<StoredMessage>) {
        if (msgs.isEmpty()) return
        try {
            writableDB.beginTransaction()
            try {
                for (msg in msgs) {
                    writableDB.insertWithOnConflict(TBL_MESSAGES, null, msgToCv(scope, scopeId, msg),
                        SQLiteDatabase.CONFLICT_REPLACE)
                }
                writableDB.setTransactionSuccessful()
            } finally {
                writableDB.endTransaction()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "addMessages: ${e.message}")
        }
    }

    fun getMessages(scope: String, scopeId: String, date: String): List<StoredMessage> {
        return try {
            // date format is yyyy-MM-dd; match against strftime on timestamp column
            val cursor = readableDB.query(TBL_MESSAGES, null,
                "scope=? AND scope_id=? AND date(timestamp/1000,'unixepoch','localtime')=?",
                arrayOf(scope, scopeId, date), null, null, "timestamp ASC")
            cursor.use { c -> generateSequence { if (c.moveToNext()) cvToMsg(c) else null }.toList() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getMessages: ${e.message}")
            emptyList()
        }
    }

    fun getRecentMessages(scope: String, scopeId: String, limit: Int = 100): List<StoredMessage> {
        return try {
            val cursor = readableDB.query(TBL_MESSAGES, null,
                "scope=? AND scope_id=?", arrayOf(scope, scopeId),
                null, null, "timestamp DESC", "$limit")
            cursor.use { c ->
                val list = generateSequence { if (c.moveToNext()) cvToMsg(c) else null }.toList()
                list.reversed() // return in chronological order (oldest first)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getRecentMessages: ${e.message}")
            emptyList()
        }
    }

    fun getDates(scope: String, scopeId: String): List<String> {
        return try {
            val cursor = readableDB.rawQuery(
                "SELECT DISTINCT date(timestamp/1000,'unixepoch','localtime') AS d " +
                "FROM $TBL_MESSAGES WHERE scope=? AND scope_id=? ORDER BY d ASC",
                arrayOf(scope, scopeId))
            cursor.use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getDates: ${e.message}")
            emptyList()
        }
    }

    fun searchMessages(scope: String, scopeId: String, query: String, limit: Int = 20): List<StoredMessage> {
        return try {
            val cursor = readableDB.query(TBL_MESSAGES, null,
                "scope=? AND scope_id=? AND text LIKE ?",
                arrayOf(scope, scopeId, "%$query%"), null, null, "timestamp DESC", "$limit")
            cursor.use { c -> generateSequence { if (c.moveToNext()) cvToMsg(c) else null }.toList() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "searchMessages: ${e.message}")
            emptyList()
        }
    }

    fun deleteDate(scope: String, scopeId: String, date: String) {
        try {
            writableDB.delete(TBL_MESSAGES,
                "scope=? AND scope_id=? AND date(timestamp/1000,'unixepoch','localtime')=?",
                arrayOf(scope, scopeId, date))
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteDate: ${e.message}")
        }
    }

    fun deleteMessage(scope: String, scopeId: String, messageId: String) {
        try {
            val rows = writableDB.delete(TBL_MESSAGES,
                "scope=? AND scope_id=? AND id=?", arrayOf(scope, scopeId, messageId))
            if (rows > 0) {
                AppLogger.i(TAG, "deleteMessage: deleted message $messageId")
            } else {
                AppLogger.w(TAG, "deleteMessage: message $messageId not found")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteMessage: ${e.message}")
        }
    }

    fun rewriteMessages(scope: String, scopeId: String, msgs: List<StoredMessage>) {
        try {
            writableDB.beginTransaction()
            try {
                writableDB.delete(TBL_MESSAGES, "scope=? AND scope_id=?", arrayOf(scope, scopeId))
                for (msg in msgs) {
                    writableDB.insertWithOnConflict(TBL_MESSAGES, null, msgToCv(scope, scopeId, msg),
                        SQLiteDatabase.CONFLICT_REPLACE)
                }
                writableDB.setTransactionSuccessful()
                AppLogger.i(TAG, "rewriteMessages: rewrote ${msgs.size} msgs for $scope/$scopeId")
            } finally {
                writableDB.endTransaction()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "rewriteMessages: ${e.message}")
        }
    }

    fun deleteScope(scope: String, scopeId: String) {
        try {
            writableDB.delete(TBL_MESSAGES, "scope=? AND scope_id=?", arrayOf(scope, scopeId))
            // Only clean up diaries for persona scope (scope_id = personaId)
            if (scope == "persona") {
                writableDB.delete(TBL_DIARIES, "persona_id=?", arrayOf(scopeId))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteScope: ${e.message}")
        }
    }

    fun getMessageCount(scope: String, scopeId: String): Int {
        return try {
            readableDB.rawQuery("SELECT COUNT(*) FROM $TBL_MESSAGES WHERE scope=? AND scope_id=?",
                arrayOf(scope, scopeId)).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getMessageCount: ${e.message}")
            0
        }
    }

    fun getStats(scope: String, scopeId: String): String {
        return try {
            val count = getMessageCount(scope, scopeId)
            val dates = getDates(scope, scopeId)
            val firstDate = dates.firstOrNull() ?: "none"
            val lastDate = dates.lastOrNull() ?: "none"
            "$count messages | ${dates.size} days | $firstDate ~ $lastDate"
        } catch (e: Exception) {
            AppLogger.e(TAG, "getStats: ${e.message}")
            "error"
        }
    }

    // ─── Diary API ──────────────────────────────────────────────

    /** Insert or replace a diary entry. */
    fun insertDiary(personaId: String, entry: DiaryEntry): Boolean {
        return try {
            val keyMemsJson = if (entry.keyMemories.isNotEmpty()) JSONArray(entry.keyMemories).toString() else null
            val tagsJson = if (entry.tags.isNotEmpty()) JSONArray(entry.tags).toString() else null
            writableDB.insertWithOnConflict(TBL_DIARIES, null, ContentValues().apply {
                put("persona_id", personaId)
                put("date", entry.date)
                put("title", entry.title)
                put("content", entry.content)
                put("mood", entry.mood)
                put("mood_emoji", entry.moodEmoji)
                put("affection_level", entry.affectionLevel)
                put("message_count", entry.messageCount)
                put("key_memories", keyMemsJson)
                put("tags", tagsJson)
                put("plugin_meta", entry.pluginMeta?.toString())
                put("custom_fields", entry.customFields?.toString())
                put("created_at", entry.createdAt)
                put("updated_at", entry.updatedAt)
                put("app_version", entry.appVersion)
                put("version", entry.version)
            }, SQLiteDatabase.CONFLICT_REPLACE) >= 0
        } catch (e: Exception) {
            AppLogger.e(TAG, "insertDiary: ${e.message}")
            false
        }
    }

    /** Get a single diary by persona + date. Returns null if not found. */
    fun getDiary(personaId: String, date: String): DiaryEntry? {
        return try {
            val cursor = readableDB.query(TBL_DIARIES, null,
                "persona_id=? AND date=?", arrayOf(personaId, date), null, null, null)
            cursor.use { c ->
                if (c.moveToNext()) cvToDiary(c) else null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getDiary: ${e.message}")
            null
        }
    }

    /** Get all diaries for a persona, ordered by date descending (newest first). */
    fun getAllDiaries(personaId: String): List<DiaryEntry> {
        return try {
            val cursor = readableDB.query(TBL_DIARIES, null,
                "persona_id=?", arrayOf(personaId), null, null, "date DESC")
            cursor.use { c -> generateSequence { if (c.moveToNext()) cvToDiary(c) else null }.toList() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getAllDiaries: ${e.message}")
            emptyList()
        }
    }

    /** Update diary content for an existing entry. */
    fun updateDiary(personaId: String, entry: DiaryEntry): Boolean {
        return try {
            val keyMemsJson = if (entry.keyMemories.isNotEmpty()) JSONArray(entry.keyMemories).toString() else null
            val tagsJson = if (entry.tags.isNotEmpty()) JSONArray(entry.tags).toString() else null
            writableDB.update(TBL_DIARIES, ContentValues().apply {
                put("title", entry.title)
                put("content", entry.content)
                put("mood", entry.mood)
                put("mood_emoji", entry.moodEmoji)
                put("affection_level", entry.affectionLevel)
                put("message_count", entry.messageCount)
                put("key_memories", keyMemsJson)
                put("tags", tagsJson)
                put("plugin_meta", entry.pluginMeta?.toString())
                put("custom_fields", entry.customFields?.toString())
                put("updated_at", entry.updatedAt)
                put("version", entry.version)
            }, "persona_id=? AND date=?", arrayOf(personaId, entry.date)) > 0
        } catch (e: Exception) {
            AppLogger.e(TAG, "updateDiary: ${e.message}")
            false
        }
    }

    /** Delete a diary by persona + date. */
    fun deleteDiary(personaId: String, date: String): Boolean {
        return try {
            writableDB.delete(TBL_DIARIES, "persona_id=? AND date=?", arrayOf(personaId, date)) > 0
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteDiary: ${e.message}")
            false
        }
    }

    /** Count diaries for a persona. */
    fun getDiaryCount(personaId: String): Int {
        return try {
            readableDB.rawQuery("SELECT COUNT(*) FROM $TBL_DIARIES WHERE persona_id=?",
                arrayOf(personaId)).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getDiaryCount: ${e.message}")
            0
        }
    }

    /** Search diaries by text query (title + content LIKE). */
    fun searchDiaries(personaId: String, query: String, limit: Int = 20): List<DiaryEntry> {
        return try {
            readableDB.query(TBL_DIARIES, null,
                "persona_id=? AND (title LIKE ? OR content LIKE ?)",
                arrayOf(personaId, "%$query%", "%$query%"),
                null, null, "date DESC", "$limit")
                .use { c -> generateSequence { if (c.moveToNext()) cvToDiary(c) else null }.toList() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "searchDiaries: ${e.message}")
            emptyList()
        }
    }

    /** Get diaries filtered by mood. */
    fun getDiariesByMood(personaId: String, mood: String): List<DiaryEntry> {
        return try {
            readableDB.query(TBL_DIARIES, null,
                "persona_id=? AND mood=?", arrayOf(personaId, mood),
                null, null, "date DESC")
                .use { c -> generateSequence { if (c.moveToNext()) cvToDiary(c) else null }.toList() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getDiariesByMood: ${e.message}")
            emptyList()
        }
    }

    /** Migrate from SharedPreferences legacy JSON blob into SQLite. */
    fun migrateFromSharedPreferences(prefsName: String, scope: String, scopeId: String): Int {
        try {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val json = prefs.getString("messages", null) ?: return 0
            val arr = JSONArray(json)
            val msgs = mutableListOf<StoredMessage>()
            for (i in 0 until arr.length()) {
                try {
                    msgs.add(StoredMessage.fromJson(arr.getJSONObject(i)))
                } catch (_: Exception) {}
            }
            if (msgs.isNotEmpty()) {
                addMessages(scope, scopeId, msgs)
                // Clean up migrated SP data to prevent stale fallback reads
                prefs.edit().remove("messages").apply()
            }
            return msgs.size
        } catch (e: Exception) {
            AppLogger.e(TAG, "migrateFromSP: ${e.message}")
            return 0
        }
    }

    // ─── Internal helpers ────────────────────────────────────────

    private fun msgToCv(scope: String, scopeId: String, m: StoredMessage) = ContentValues().apply {
        put("id", m.id)
        put("scope", scope)
        put("scope_id", scopeId)
        put("text", m.text)
        put("time", m.time)
        put("is_user", if (m.isUser) 1 else 0)
        put("user_mood", m.userMood)
        put("feedback", m.feedback)
        put("emotion", m.emotion)
        put("timestamp", m.timestamp)
        put("is_favorited", if (m.isFavorited) 1 else 0)
        put("reaction_emoji", m.reactionEmoji)
        put("sticker_path", m.stickerPath)
        put("generated_image_path", m.generatedImagePath)
        put("sender_persona_id", m.senderPersonaId)
        put("sender_name", m.senderName)
        put("audio_path", m.audioPath)
        put("audio_url", m.audioUrl)
        put("image_urls", if (m.imageUrls.isNotEmpty()) JSONArray(m.imageUrls).toString() else null)
    }

    private fun cvToMsg(c: android.database.Cursor): StoredMessage {
        val urlsStr = c.getString(c.getColumnIndexOrThrow("image_urls"))
        val imageUrls = if (!urlsStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(urlsStr)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) { emptyList()
            }
        } else emptyList()

        return StoredMessage(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            text = c.getString(c.getColumnIndexOrThrow("text")),
            time = c.getString(c.getColumnIndexOrThrow("time")),
            isUser = c.getInt(c.getColumnIndexOrThrow("is_user")) == 1,
            userMood = c.getString(c.getColumnIndexOrThrow("user_mood")) ?: "",
            feedback = c.getInt(c.getColumnIndexOrThrow("feedback")),
            emotion = c.getString(c.getColumnIndexOrThrow("emotion")) ?: "NEUTRAL",
            timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
            isFavorited = c.getInt(c.getColumnIndexOrThrow("is_favorited")) == 1,
            reactionEmoji = c.getString(c.getColumnIndexOrThrow("reaction_emoji")) ?: "",
            stickerPath = c.getString(c.getColumnIndexOrThrow("sticker_path")),
            generatedImagePath = c.getString(c.getColumnIndexOrThrow("generated_image_path")),
            senderPersonaId = c.getString(c.getColumnIndexOrThrow("sender_persona_id")) ?: "",
            senderName = c.getString(c.getColumnIndexOrThrow("sender_name")) ?: "",
            audioPath = c.getString(c.getColumnIndexOrThrow("audio_path")),
            audioUrl = c.getString(c.getColumnIndexOrThrow("audio_url")),
            imageUrls = imageUrls
        )
    }

    private fun cvToDiary(c: android.database.Cursor): DiaryEntry {
        val memsStr = c.getString(c.getColumnIndexOrThrow("key_memories"))
        val keyMemories = if (!memsStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(memsStr)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val tagsStr = c.getString(c.getColumnIndexOrThrow("tags"))
        val tags = if (!tagsStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(tagsStr)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val metaStr = c.getString(c.getColumnIndexOrThrow("plugin_meta"))
        val pluginMeta = if (!metaStr.isNullOrBlank()) try { JSONObject(metaStr) } catch (_: Exception) { null } else null

        val cfStr = c.getString(c.getColumnIndexOrThrow("custom_fields"))
        val customFields = if (!cfStr.isNullOrBlank()) try { JSONObject(cfStr) } catch (_: Exception) { null } else null

        return DiaryEntry(
            version = c.getInt(c.getColumnIndexOrThrow("version")),
            date = c.getString(c.getColumnIndexOrThrow("date")),
            title = c.getString(c.getColumnIndexOrThrow("title")),
            content = c.getString(c.getColumnIndexOrThrow("content")),
            mood = c.getString(c.getColumnIndexOrThrow("mood")) ?: "normal",
            moodEmoji = c.getString(c.getColumnIndexOrThrow("mood_emoji")) ?: "😊",
            affectionLevel = c.getInt(c.getColumnIndexOrThrow("affection_level")),
            messageCount = c.getInt(c.getColumnIndexOrThrow("message_count")),
            keyMemories = keyMemories,
            tags = tags,
            pluginMeta = pluginMeta,
            customFields = customFields,
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
            appVersion = c.getString(c.getColumnIndexOrThrow("app_version")) ?: "1.0.0"
        )
    }

    /**
     * One-time migration: read all legacy JSON files under files/chat_history/
     * and insert them into SQLite, then delete the JSON directory.
     */
    private fun migrateLegacyJsonIfNeeded() {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean(MIGRATION_DONE_KEY, false)) return

            val baseDir = File(context.filesDir, "chat_history")
            if (!baseDir.exists()) {
                prefs.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()
                return
            }

            var migrated = 0
            val scopeDirs = baseDir.listFiles()?.filter { it.isDirectory } ?: return

            for (scopeDir in scopeDirs) {
                val scope = scopeDir.name
                val scopeIdDirs = scopeDir.listFiles()?.filter { it.isDirectory } ?: continue
                for (scopeIdDir in scopeIdDirs) {
                    val scopeId = scopeIdDir.name
                    val msgBase = File(scopeIdDir, "messages")
                    if (!msgBase.exists()) continue

                    val dateDirs = msgBase.listFiles()?.filter { it.isDirectory } ?: continue
                    for (dateDir in dateDirs) {
                        val jsonFiles = dateDir.listFiles()?.filter { it.name.endsWith(".json") } ?: continue
                        for (file in jsonFiles) {
                            try {
                                val obj = JSONObject(file.readText())
                                val msg = StoredMessage.fromJson(obj)
                                writableDB.insertWithOnConflict(TBL_MESSAGES, null,
                                    msgToCv(scope, scopeId, msg),
                                    SQLiteDatabase.CONFLICT_REPLACE)
                                migrated++
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            // Clean up legacy JSON files after successful migration
            var migratedDiaries = 0
            if (migrated > 0) {
                // Mark migration complete BEFORE deleting source files
                // to prevent data loss on crash between delete and flag write
                prefs.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()
                baseDir.deleteRecursively()
                AppLogger.i(TAG, "migrated $migrated messages from JSON files to SQLite")
            }

            // Migrate legacy diary JSON files
            val diaryBaseDir = File(context.filesDir, "diaries")
            if (diaryBaseDir.exists()) {
                val personaDirs = diaryBaseDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
                for (pDir in personaDirs) {
                    val pId = pDir.name
                    val jsonFiles = pDir.listFiles()?.filter {
                        it.isFile && it.extension == "json" && it.name != "diary_index.json"
                    } ?: continue
                    for (jf in jsonFiles) {
                        try {
                            val obj = JSONObject(jf.readText())
                            val entry = DiaryEntry.fromJson(obj)
                            insertDiary(pId, entry)
                            migratedDiaries++
                        } catch (_: Exception) {}
                    }
                }
                if (migratedDiaries > 0) {
                    diaryBaseDir.deleteRecursively()
                    AppLogger.i(TAG, "migrated $migratedDiaries diary entries from JSON to SQLite")
                }
            }
            // Write migration flag if not already written (no chat messages to migrate)
            if (migrated == 0 && migratedDiaries == 0) {
                prefs.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "migrateLegacyJson: ${e.message}")
        }
    }
}
