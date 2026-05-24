package com.aicompanion.emotion

import com.aicompanion.network.ApiClient
import com.aicompanion.util.AppLogger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class EmotionParams(
    val temperatureOffset: Float = 0f,
    val topPOffset: Float = 0f,
    val ttsPitchOffset: Float = 0f,
    val ttsRateOffset: Float = 0f,
    val emotionIntensity: Float = 0f,
    val ttsEmotion: String = "neutral",
    val ttsEmotionConfidence: Float = 0.5f
) {
    fun applyToTemperature(base: Float): Float {
        return (base + temperatureOffset).coerceIn(0f, 2f)
    }

    fun applyToTopP(base: Float): Float {
        return (base + topPOffset).coerceIn(0f, 1f)
    }

    fun applyToPitch(base: Float): Float {
        return (base + ttsPitchOffset).coerceIn(0.5f, 2.0f)
    }

    fun applyToRate(base: Float): Float {
        return (base + ttsRateOffset).coerceIn(0.5f, 2.0f)
    }
}

class EmotionAnalyzer(private val apiClient: ApiClient) {

    companion object {
        private const val TAG = "EmotionAnalyzer"
        private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    }

    private val client = ApiClient.sharedClient

    fun analyzeEmotion(
        personaName: String,
        personaPrompt: String,
        userMessage: String,
        chatHistory: List<Pair<Boolean, String>> = emptyList(),
        currentEmotion: String = "neutral"
    ): EmotionParams {
        return try {
            val systemPrompt = buildEmotionAnalysisPrompt(personaName, personaPrompt, currentEmotion)

            val messagesArray = org.json.JSONArray()
            messagesArray.put(org.json.JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })

            if (chatHistory.isNotEmpty()) {
                val recentHistory = chatHistory.takeLast(6)
                val historyText = recentHistory.joinToString("\n") { (isUser, text) ->
                    if (isUser) "用户：$text" else "$personaName：$text"
                }
                messagesArray.put(org.json.JSONObject().apply {
                    put("role", "user")
                    put("content", "最近的对话：\n$historyText\n\n用户最新消息：$userMessage")
                })
            } else {
                messagesArray.put(org.json.JSONObject().apply {
                    put("role", "user")
                    put("content", "用户说：$userMessage")
                })
            }

            val requestBody = JSONObject().apply {
                put("model", apiClient.modelName ?: "gpt-4o-mini")
                put("messages", messagesArray)
                put("temperature", 0.3)
                put("max_tokens", 200)
            }

            val body = requestBody.toString().toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder()
                .url(apiClient.chatApiUrl)
                .post(body)
                .header("Content-Type", "application/json")

            if (!apiClient.apiKey.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer ${apiClient.apiKey}")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(TAG, "Emotion analysis failed: HTTP ${response.code}")
                    return EmotionParams()
                }
                val bodyStr = response.body?.string() ?: return EmotionParams()
                val json = JSONObject(bodyStr)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val content = choices.getJSONObject(0)
                        .optJSONObject("message")?.optString("content", "") ?: ""
                    parseEmotionParams(content)
                } else {
                    EmotionParams()
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Emotion analysis error: ${e.message}")
            EmotionParams()
        }
    }

    private fun buildEmotionAnalysisPrompt(
        personaName: String,
        personaPrompt: String,
        currentEmotion: String
    ): String {
        return buildString {
            append("你是情绪参数分析器。根据角色设定、当前情绪和对话内容，分析角色此刻的情绪强度，并返回LLM和TTS参数偏移值。\n\n")
            append("角色名：$personaName\n")
            append("角色设定摘要：${personaPrompt.take(300)}\n")
            append("当前情绪：$currentEmotion\n\n")
            append("参数说明：\n")
            append("- temperature_offset：温度偏移（-0.5~1.0）。情绪越激烈/不稳定，值越大。生气/激动时>0.3，平静时≈0\n")
            append("- top_p_offset：Top-P偏移（-0.2~0.1）。情绪激烈时略微降低使输出更集中\n")
            append("- tts_pitch_offset：语音声调偏移（-0.3~0.5）。开心/激动时升高，悲伤时降低\n")
            append("- tts_rate_offset：语速偏移（-0.2~0.3）。急切/激动时加快，悲伤时减慢\n")
            append("- emotion_intensity：情绪强度（0~1）。0=平静，1=极度激烈\n")
            append("- tts_emotion：TTS情绪标签。可选值：happy(开心/快乐)、sad(悲伤/失落)、angry(生气/愤怒)、surprised(惊讶/震惊)、fearful(恐惧/害怕)、disgusted(厌恶/反感)、tsundere(傲娇/口是心非)、shy(害羞/腼腆)、neutral(平静/中性)\n")
            append("- tts_emotion_confidence：情绪标签置信度（0~1）\n\n")
            append("严格返回JSON格式，不要其他内容：\n")
            append("{\"temperature_offset\":0.0,\"top_p_offset\":0.0,\"tts_pitch_offset\":0.0,\"tts_rate_offset\":0.0,\"emotion_intensity\":0.0,\"tts_emotion\":\"neutral\",\"tts_emotion_confidence\":0.5}")
        }
    }

    private fun parseEmotionParams(responseText: String): EmotionParams {
        return try {
            val cleanJson = responseText.trim()
                .replace(Regex("^```\\s*json\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("```$"), "")
                .trim()

            val jsonStart = cleanJson.indexOf('{')
            val jsonEnd = cleanJson.lastIndexOf('}')
            if (jsonStart < 0 || jsonEnd < 0) return EmotionParams()

            val jsonStr = cleanJson.substring(jsonStart, jsonEnd + 1)
            val json = JSONObject(jsonStr)

            val validEmotions = setOf("happy", "sad", "angry", "surprised", "fearful", "disgusted", "tsundere", "shy", "neutral")
            val rawEmotion = json.optString("tts_emotion", "neutral").lowercase()
            val ttsEmotion = if (rawEmotion in validEmotions) rawEmotion else "neutral"

            EmotionParams(
                temperatureOffset = json.optDouble("temperature_offset", 0.0).toFloat()
                    .coerceIn(-0.5f, 1.0f),
                topPOffset = json.optDouble("top_p_offset", 0.0).toFloat()
                    .coerceIn(-0.2f, 0.1f),
                ttsPitchOffset = json.optDouble("tts_pitch_offset", 0.0).toFloat()
                    .coerceIn(-0.3f, 0.5f),
                ttsRateOffset = json.optDouble("tts_rate_offset", 0.0).toFloat()
                    .coerceIn(-0.2f, 0.3f),
                emotionIntensity = json.optDouble("emotion_intensity", 0.0).toFloat()
                    .coerceIn(0f, 1f),
                ttsEmotion = ttsEmotion,
                ttsEmotionConfidence = json.optDouble("tts_emotion_confidence", 0.5).toFloat()
                    .coerceIn(0f, 1f)
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Parse emotion params failed: ${e.message}")
            EmotionParams()
        }
    }
}
