package com.aicompanion.network

import android.util.Log
import com.aicompanion.models.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class ApiClient(
    val chatApiUrl: String,
    val apiKey: String? = null,
    val modelName: String? = null
) {
    companion object {
        private const val TAG = "ApiClient"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun sendChat(
        userId: String,
        message: String,
        personaName: String,
        personaPrompt: String,
        emotion: String,
        action: String,
        memories: List<String>,
        appCategory: String,
        isOfflineMode: Boolean
    ): ChatResponse? {
        val useModel = modelName ?: "gpt-4o-mini"

        val emotionMap = mapOf(
            "happy" to "开心", "sad" to "伤心", "angry" to "生气",
            "surprised" to "惊讶", "tsundere" to "傲娇", "neutral" to "平静",
            "开心" to "开心", "难过" to "伤心", "生气" to "生气", "疲惫" to "疲惫",
            "兴奋" to "兴奋", "幸福" to "幸福", "焦虑" to "焦虑", "平静" to "平静"
        )
        val emotionCn = emotionMap[emotion.lowercase()] ?: "平静"

        val systemPrompt = buildString {
            append("你是「$personaName」，一个可爱的AI桌宠。")
            append(personaPrompt)
            append("\n你的当前情绪：$emotionCn。你的当前动作：$action。")
            if (memories.isNotEmpty()) {
                append("\n你记得这些关于主人的事：${memories.takeLast(3).joinToString("；")}")
            }
            append("\n规则：用自然的中文回复，像朋友一样聊天。保持在2-4句话以内。")
            append("\n如果用户表达了情绪（如开心、难过、焦虑等），请根据用户的情绪给予适当的情感回应和安慰。")
            append("\n最后，在回复末尾 [[emotion:xxx]] 处标注你的当前情绪（从 happy/sad/angry/surprised/neutral 中选一个）。")
        }

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        val userContent = buildString {
            if (emotion != "neutral" && emotion.isNotEmpty()) {
                val moodMap = mapOf(
                    "开心" to "开心", "难过" to "难过", "生气" to "生气", "疲惫" to "疲惫",
                    "兴奋" to "兴奋", "幸福" to "幸福", "焦虑" to "焦虑", "平静" to "平静",
                    "happy" to "开心", "sad" to "难过", "angry" to "生气"
                )
                val moodCn = moodMap[emotion] ?: emotion
                append("【用户当前心情：$moodCn】\n")
            }
            if (appCategory != "unknown" && appCategory != "") {
                append("用户当前在${appCategory}应用中。\n")
            }
            append(message)
        }
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", userContent)
        })

        val requestBody = JSONObject().apply {
            put("model", useModel)
            put("messages", messagesArray)
            put("temperature", 0.85)
            put("max_tokens", 300)
        }

        return try {
            if (chatApiUrl.isBlank()) {
                Log.e(TAG, "sendChat: API URL is empty!")
                return ChatResponse("", Emotion.SAD, Action.IDLE, errorMessage = "API地址为空，请在设置中配置API地址")
            }
            val body = requestBody.toString().toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder()
                .url(chatApiUrl)
                .post(body)
                .header("Content-Type", "application/json")

            if (!apiKey.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            Log.d(TAG, "sendChat: POST $chatApiUrl model=$useModel")
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()?.take(200) ?: ""
                    Log.e(TAG, "Chat failed: HTTP ${response.code} ${response.message} body=$errBody")
                    val errMsg = when (response.code) {
                        401 -> "API密钥无效，请检查设置中的API Key"
                        403 -> "无权限访问，请检查API密钥权限"
                        404 -> "接口地址不存在，请检查API地址是否正确"
                        429 -> "请求过于频繁或已超出配额"
                        in 400..499 -> "请求错误(HTTP ${response.code})，请检查模型名称「$useModel」是否正确"
                        in 500..599 -> "服务端错误(HTTP ${response.code})，请稍后重试"
                        else -> "连接失败(HTTP ${response.code})"
                    }
                    return ChatResponse("", Emotion.SAD, Action.IDLE, errorMessage = errMsg)
                }
                val bodyStr = response.body?.string() ?: "{}"
                Log.d(TAG, "sendChat: response ${bodyStr.length} chars")
                parseOpenAIResponse(bodyStr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendChat error: ${e.javaClass.simpleName}: ${e.message}", e)
            val errMsg = when {
                e.message?.contains("Unable to resolve host") == true -> "无法解析域名，请检查网络和API地址"
                e.message?.contains("timeout") == true -> "连接超时，请检查网络和API地址"
                e.message?.contains("SSL") == true -> "SSL证书错误"
                else -> "连接失败: ${e.message}"
            }
            ChatResponse("", Emotion.SAD, Action.IDLE, errorMessage = errMsg)
        }
    }

    private fun parseOpenAIResponse(responseJson: String): ChatResponse? {
        val json = JSONObject(responseJson)
        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val message = choices.getJSONObject(0).optJSONObject("message")
            val fullText = message?.optString("content", "") ?: ""

            val (cleanText, extractedEmotion, extractedAction) = extractEmotionAction(fullText)

            return ChatResponse(
                text = cleanText,
                emotion = extractedEmotion,
                action = extractedAction,
                audioUrl = null
            )
        }
        Log.e(TAG, "parseOpenAIResponse: no choices in response")
        return null
    }

    private fun extractEmotionAction(text: String): Triple<String, Emotion, Action> {
        val emotionRegex = Regex("""\[\[emotion:(\w+)\]\]""", RegexOption.IGNORE_CASE)
        val match = emotionRegex.find(text)
        val emotion = if (match != null) {
            try { Emotion.valueOf(match.groupValues[1].uppercase()) }
            catch (_: Exception) { Emotion.HAPPY }
        } else {
            Emotion.HAPPY
        }
        val cleanText = emotionRegex.replace(text, "").trim()

        val action = when (emotion) {
            Emotion.HAPPY -> Action.TAIL_FLICK
            Emotion.SAD -> Action.IDLE
            Emotion.ANGRY -> Action.EAR_TWITCH
            Emotion.SURPRISED -> Action.STRETCH
            Emotion.TSUNDERE -> Action.BLUSH
            Emotion.NEUTRAL -> Action.IDLE
        }

        return Triple(cleanText, emotion, action)
    }

    private fun getFallbackResponse(personaName: String): ChatResponse {
        val fallbacks = listOf(
            "主人主人~ $personaName 在这里哦！",
            "今天天气真好，和主人在一起的每一天都很开心！",
            "喵~ 有什么我可以帮你的吗？",
            "我好喜欢和主人聊天呀！",
            "嘿嘿，被主人注意到啦~"
        )
        return ChatResponse(
            text = fallbacks.random(),
            emotion = Emotion.HAPPY,
            action = Action.TAIL_FLICK,
            audioUrl = null
        )
    }

    fun testConnection(listener: (success: Boolean, message: String) -> Unit) {
        Thread {
            try {
                if (chatApiUrl.isBlank()) {
                    listener(false, "API地址为空，请在设置中配置")
                    return@Thread
                }

                val testMessages = JSONArray()
                testMessages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", "回复一个字：好")
                })
                testMessages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", "测试连接")
                })

                val requestBody = JSONObject().apply {
                    put("model", modelName ?: "gpt-4o-mini")
                    put("messages", testMessages)
                    put("max_tokens", 10)
                }

                val body = requestBody.toString().toRequestBody(jsonMediaType)
                val requestBuilder = Request.Builder()
                    .url(chatApiUrl)
                    .post(body)
                    .header("Content-Type", "application/json")

                if (!apiKey.isNullOrEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        listener(true, "✅ 连接成功！API可用")
                    } else {
                        listener(false, "响应格式异常（缺少choices），但连接可达，请检查模型名称是否正确")
                    }
                } else {
                    val code = response.code
                    val msg = when {
                        code == 401 -> "API密钥无效，请检查"
                        code == 403 -> "无权限访问，请检查API密钥权限"
                        code == 404 -> "接口地址不存在，请确认API地址是否正确"
                        code == 429 -> "请求过于频繁或已超出配额"
                        code >= 400 && code < 500 -> "请求错误: HTTP $code，请检查模型名称「${modelName ?: "未设置"}」是否正确"
                        code >= 500 -> "服务端错误: HTTP $code"
                        else -> "连接失败: HTTP $code"
                    }
                    listener(false, msg)
                }
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("Unable to resolve host") == true -> "无法解析域名，请检查网络和API地址"
                    e.message?.contains("timeout") == true -> "连接超时，请检查网络和API地址"
                    e.message?.contains("SSL") == true -> "SSL证书错误"
                    else -> "连接失败: ${e.message}"
                }
                listener(false, msg)
            }
        }.start()
    }

    fun sendProactiveChat(
        personaName: String,
        personaPrompt: String,
        customSystemPrompt: String,
        userMessage: String
    ): ChatResponse? {
        val useModel = modelName ?: "gpt-4o-mini"

        val systemPrompt = buildString {
            append("你是「$personaName」，一个可爱的AI桌宠。")
            append(personaPrompt)
            append("\n$customSystemPrompt")
            append("\n在回复末尾 [[emotion:xxx]] 处标注你的当前情绪（从 happy/sad/angry/surprised/neutral 中选一个）。")
        }

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })

        val requestBody = JSONObject().apply {
            put("model", useModel)
            put("messages", messagesArray)
            put("temperature", 0.95)
            put("max_tokens", 150)
        }

        return try {
            if (chatApiUrl.isBlank()) return null
            val body = requestBody.toString().toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder()
                .url(chatApiUrl)
                .post(body)
                .header("Content-Type", "application/json")
            if (!apiKey.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }
            Log.d(TAG, "sendProactiveChat: POST $chatApiUrl model=$useModel")
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()?.take(200) ?: ""
                    Log.e(TAG, "sendProactiveChat failed: HTTP ${response.code} body=$errBody")
                    return ChatResponse("", Emotion.SAD, Action.IDLE, errorMessage = "AI主动聊天失败(HTTP ${response.code})")
                }
                val bodyStr = response.body?.string() ?: "{}"
                parseOpenAIResponse(bodyStr)
            }
        } catch (e: Exception) {
            Log.d(TAG, "sendProactiveChat error: ${e.message}")
            null
        }
    }

    suspend fun getMemories(userId: String, limit: Int = 20): List<MemoryFact> = emptyList()

    suspend fun deleteAllMemories(userId: String): Boolean = true

    suspend fun getDailyCard(userId: String): DailyCardData? = null
}