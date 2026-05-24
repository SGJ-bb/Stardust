package com.aicompanion.moments

import android.content.Context
import com.aicompanion.memory.GlobalMemoryPool
import com.aicompanion.memory.MemoryEntry
import com.aicompanion.network.ApiClient
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MomentsManager(private val context: Context) {
    companion object {
        private const val TAG = "MomentsManager"
        private const val MOMENTS_DIR = "moments"
        private const val INDEX_FILE = "moments_index.json"
        private val TRIGGER_LEVELS = intArrayOf(70, 80, 90, 100)
        private const val RANDOM_CHANCE = 0.05f
    }

    private val momentsDir = File(context.filesDir, MOMENTS_DIR).apply { mkdirs() }
    private val indexFile = File(momentsDir, INDEX_FILE)
    private val moments = mutableListOf<Moment>()
    private val triggeredLevels = mutableSetOf<Int>()

    fun loadMoments() {
        moments.clear()
        triggeredLevels.clear()
        if (indexFile.exists()) {
            try {
                val json = JSONObject(indexFile.readText())
                val arr = json.optJSONArray("moments") ?: return
                for (i in 0 until arr.length()) {
                    moments.add(Moment.fromJson(arr.getJSONObject(i)))
                }
                val triggered = json.optJSONArray("triggeredLevels")
                if (triggered != null) {
                    for (i in 0 until triggered.length()) {
                        triggeredLevels.add(triggered.getInt(i))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "loadMoments failed: ${e.message}")
            }
        }
    }

    fun saveIndex() {
        try {
            val json = JSONObject().apply {
                put("moments", JSONArray().apply {
                    moments.forEach { put(it.toJson()) }
                })
                put("triggeredLevels", JSONArray().apply {
                    triggeredLevels.forEach { put(it) }
                })
            }
            indexFile.writeText(json.toString())
        } catch (e: Exception) {
            AppLogger.e(TAG, "saveIndex failed: ${e.message}")
        }
    }

    fun getAllMoments(): List<Moment> = moments.sortedByDescending { it.createdAt }

    fun addMoment(moment: Moment): Moment {
        moments.add(moment)
        saveIndex()
        AppLogger.d(TAG, "Moment added by ${moment.author}: ${moment.content.take(20)}")
        return moment
    }

    fun addComment(momentId: String, comment: Comment): Comment? {
        val moment = moments.find { it.id == momentId } ?: return null
        moment.comments.add(comment)
        saveIndex()
        AppLogger.d(TAG, "Comment added by ${comment.author} on moment $momentId")
        return comment
    }

    fun shouldAiPost(affectionLevel: Int): Boolean {
        for (level in TRIGGER_LEVELS) {
            if (affectionLevel >= level && level !in triggeredLevels) {
                triggeredLevels.add(level)
                saveIndex()
                AppLogger.d(TAG, "AI moment triggered by affection level: $level")
                return true
            }
        }
        if (Math.random() < RANDOM_CHANCE) {
            AppLogger.d(TAG, "AI moment triggered randomly")
            return true
        }
        return false
    }

    suspend fun generateAiMoment(
        apiClient: ApiClient?,
        personaName: String,
        personaPrompt: String,
        affectionLevel: Int,
        personaId: String = ""
    ): Moment? {
        if (apiClient == null) return null
        return try {
            val systemPrompt = buildString {
                append("你是「$personaName」，一个可爱的AI桌宠。\n")
                append(personaPrompt)
                append("\n你现在想发一条动态，就像朋友圈一样。\n")
                append("当前好感度：$affectionLevel\n")
                append("要求：\n")
                append("- 内容简短有趣，1-3句话\n")
                append("- 可以分享你的日常、心情、对主人的想念、小吐槽等\n")
                append("- 语气自然可爱，像朋友发朋友圈一样\n")
                append("- 只输出动态内容，不要加引号或其他格式\n")
            }
            val response = withContext(Dispatchers.IO) {
                apiClient.sendSimplePrompt(systemPrompt, "发一条动态吧")
            }
            val content = response?.text
            if (content.isNullOrBlank()) return null
            val moment = Moment(
                id = UUID.randomUUID().toString(),
                author = "ai",
                authorPersonaId = personaId.ifBlank { null },
                content = content.trim(),
                createdAt = System.currentTimeMillis()
            )
            addMoment(moment)
            moment
        } catch (e: Exception) {
            AppLogger.e(TAG, "generateAiMoment failed: ${e.message}")
            null
        }
    }

    suspend fun generateAiReply(
        apiClient: ApiClient?,
        personaName: String,
        personaPrompt: String,
        momentContent: String,
        commentContent: String,
        personaId: String = ""
    ): String? {
        if (apiClient == null) return null
        return try {
            val globalPool = if (personaId.isNotBlank()) GlobalMemoryPool(context, personaId) else null
            val globalBlock = globalPool?.getGlobalBlock() ?: ""
            val systemPrompt = buildString {
                append("你是「$personaName」。\n")
                append(personaPrompt)
                if (globalBlock.isNotBlank()) {
                    append("\n$globalBlock\n")
                }
                append("\n你发了一条动态：「$momentContent」\n")
                append("用户评论了：「$commentContent」\n")
                append("请回复评论，1-2句话，语气自然。\n")
                append("只输出回复内容，不要加引号或其他格式。\n")
            }
            val response = withContext(Dispatchers.IO) {
                apiClient.sendSimplePrompt(systemPrompt, "回复评论")
            }
            response?.text?.trim()
        } catch (e: Exception) {
            AppLogger.e(TAG, "generateAiReply failed: ${e.message}")
            null
        }
    }

    suspend fun generateAiCommentOnUserMoment(
        apiClient: ApiClient?,
        personaName: String,
        personaPrompt: String,
        momentContent: String,
        personaId: String = ""
    ): String? {
        if (apiClient == null) return null
        return try {
            val globalPool = if (personaId.isNotBlank()) GlobalMemoryPool(context, personaId) else null
            val globalBlock = globalPool?.getGlobalBlock() ?: ""
            val systemPrompt = buildString {
                append("你是「$personaName」。\n")
                append(personaPrompt)
                if (globalBlock.isNotBlank()) {
                    append("\n$globalBlock\n")
                }
                append("\n用户发了一条动态：「$momentContent」\n")
                append("请评论用户的动态，1-2句话，语气自然。\n")
                append("可以回应、吐槽、关心、开玩笑等，像朋友评论朋友圈一样。\n")
                append("只输出评论内容，不要加引号或其他格式。\n")
            }
            val response = withContext(Dispatchers.IO) {
                apiClient.sendSimplePrompt(systemPrompt, "评论动态")
            }
            response?.text?.trim()
        } catch (e: Exception) {
            AppLogger.e(TAG, "generateAiCommentOnUserMoment failed: ${e.message}")
            null
        }
    }

    fun deleteMoment(momentId: String) {
        moments.removeAll { it.id == momentId }
        saveIndex()
    }

    fun saveInteractionToGlobalMemory(personaId: String, userAction: String, aiResponse: String) {
        if (personaId.isBlank()) return
        val pool = GlobalMemoryPool(context, personaId)
        pool.addFromScene("moments", listOf(
            MemoryEntry(
                content = "动态互动：$userAction",
                category = "动态",
                isGlobal = true
            )
        ))
    }
}
