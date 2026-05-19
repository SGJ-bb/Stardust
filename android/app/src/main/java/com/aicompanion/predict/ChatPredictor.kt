package com.aicompanion.predict

import android.content.Context
import com.aicompanion.network.ApiClient
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ChatPredictor(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "ChatPredictor"
        private const val MIN_PREDICTIONS = 2
        private const val MAX_PREDICTIONS = 6
    }

    suspend fun predictPrivateChat(
        recentMessages: List<Pair<String, String>>,
        personaName: String,
        personaPersonality: String
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val client = buildClient() ?: return@withContext emptyList()
            val historyBlock = recentMessages.takeLast(10).joinToString("\n") { (sender, text) ->
                if (sender == "user") "用户: $text" else "$personaName: $text"
            }
            val systemPrompt = buildString {
                append("你是用户回复预测器。根据对话上下文，预测用户接下来可能说的话。")
                append("要求：返回2-6个简短预测，每个15字以内，覆盖不同方向（如追问、表达情感、转移话题等）。")
                append("严格返回JSON数组格式，如：[\"预测1\",\"预测2\",\"预测3\"]")
                append("不要返回任何其他内容。")
            }
            val userPrompt = buildString {
                append("对话对象：$personaName（$personaPersonality）\n")
                append("最近对话：\n$historyBlock\n")
                append("请预测用户接下来可能说的话：")
            }
            val response = client.sendSimplePrompt(systemPrompt, userPrompt)
            parsePredictions(response?.text)
        } catch (e: Exception) {
            AppLogger.e(TAG, "predictPrivateChat error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun predictGroupChat(
        recentMessages: List<Pair<String, String>>,
        memberNames: List<String>
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val client = buildClient() ?: return@withContext emptyList()
            val historyBlock = recentMessages.takeLast(10).joinToString("\n") { (sender, text) ->
                if (sender == "user") "用户: $text" else "$sender: $text"
            }
            val systemPrompt = buildString {
                append("你是用户回复预测器。根据群聊上下文，预测用户接下来可能说的话。")
                append("要求：返回2-6个简短预测，每个15字以内，覆盖不同方向（如回应某人、@某人、转移话题等）。")
                append("严格返回JSON数组格式，如：[\"预测1\",\"预测2\",\"预测3\"]")
                append("不要返回任何其他内容。")
            }
            val userPrompt = buildString {
                append("群成员：${memberNames.joinToString("、")}\n")
                append("最近对话：\n$historyBlock\n")
                append("请预测用户接下来可能说的话：")
            }
            val response = client.sendSimplePrompt(systemPrompt, userPrompt)
            parsePredictions(response?.text)
        } catch (e: Exception) {
            AppLogger.e(TAG, "predictGroupChat error: ${e.message}", e)
            emptyList()
        }
    }

    private fun buildClient(): ApiClient? {
        val url = settingsManager.chatApiUrl
        val key = settingsManager.chatApiKey
        val model = settingsManager.chatModel
        if (url.isBlank()) return null
        return ApiClient(
            chatApiUrl = url,
            apiKey = key,
            modelName = model,
            temperature = 0.7f,
            topP = 0.9f,
            frequencyPenalty = 0.0f,
            presencePenalty = 0.0f,
            maxTokens = 300,
            providerId = settingsManager.apiProvider
        )
    }

    private fun parsePredictions(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val text = raw.trim()
        val jsonStr = extractJsonArray(text) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val result = mutableListOf<String>()
            for (i in 0 until minOf(arr.length(), MAX_PREDICTIONS)) {
                val item = arr.optString(i)?.trim()
                if (!item.isNullOrBlank()) result.add(item)
            }
            if (result.size < MIN_PREDICTIONS) emptyList() else result
        } catch (e: Exception) {
            AppLogger.e(TAG, "parsePredictions JSON error: ${e.message}")
            emptyList()
        }
    }

    private fun extractJsonArray(text: String): String? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }
}
