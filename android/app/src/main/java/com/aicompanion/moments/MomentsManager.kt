package com.aicompanion.moments

import android.content.Context
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
        affectionLevel: Int
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
        commentContent: String
    ): String? {
        if (apiClient == null) return null
        return try {
            val systemPrompt = buildString {
                append("你是「$personaName」，一个可爱的AI桌宠。\n")
                append(personaPrompt)
                append("\n你发了一条动态：「$momentContent」\n")
                append("主人评论了：「$commentContent」\n")
                append("请回复主人的评论，1-2句话，语气自然可爱。\n")
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

    fun deleteMoment(momentId: String) {
        moments.removeAll { it.id == momentId }
        saveIndex()
    }
}
