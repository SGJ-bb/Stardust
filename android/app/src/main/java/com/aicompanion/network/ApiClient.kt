package com.aicompanion.network

/** AI后端API客户端: 聊天请求(sendChat)支持工具调用/解析tool_calls, 以及persona/记忆/历史消息注入, 天气查询/角色生成/图片生成/TTS语音/日记生成 */

import android.util.Log
import com.aicompanion.models.*
import com.aicompanion.util.AppLogger
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class ApiClient(
    val chatApiUrl: String,
    val apiKey: String? = null,
    val modelName: String? = null,
    val temperature: Float = 1.05f,
    val topP: Float = 0.92f,
    val frequencyPenalty: Float = 0.35f,
    val presencePenalty: Float = 0.5f,
    val maxTokens: Int = 500,
    val providerId: String = "custom"
) {
    companion object {
        private const val TAG = "ApiClient"
        val sharedClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val client = sharedClient

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
        systemContext: String = "",
        chatHistory: List<Pair<Boolean, String>> = emptyList(),
        tools: List<ToolDefinition> = emptyList(),
        extraMessages: List<Pair<String, String>> = emptyList(),
        overrideTemperature: Float? = null,
        overrideTopP: Float? = null,
        overrideFrequencyPenalty: Float? = null,
        overridePresencePenalty: Float? = null,
        overrideMaxTokens: Int? = null
    ): ChatResponse? {
        val useModel = modelName ?: "gpt-4o-mini"
        com.aicompanion.util.AppLogger.d(TAG, "sendChat: model=$useModel, url=${chatApiUrl.take(30)}, history=${chatHistory.size}条")

        val systemPrompt = buildString {
            append(personaPrompt)
            if (memories.isNotEmpty()) {
                append("\n记得：${memories.takeLast(3).joinToString("；")}")
            }
        }

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        chatHistory.takeLast(10).forEach { (isUser, text) ->
            messagesArray.put(JSONObject().apply {
                put("role", if (isUser) "user" else "assistant")
                put("content", text)
            })
        }

        val userContent = buildString {
            if (systemContext.isNotBlank()) {
                append("$systemContext\n")
            }
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

        for ((role, content) in extraMessages) {
            val msgObj = if (content.startsWith("{") && (role == "assistant" || role == "tool")) {
                try {
                    JSONObject(content)
                } catch (e: Exception) {
                    com.aicompanion.util.AppLogger.w(TAG, "消息JSON解析失败，使用fallback: ${e.message}")
                    JSONObject().apply {
                        put("role", role)
                        put("content", content)
                    }
                }
            } else {
                JSONObject().apply {
                    put("role", role)
                    put("content", content)
                }
            }
            messagesArray.put(msgObj)
        }

        val effectiveTemp = overrideTemperature ?: temperature
        val effectiveTopP = overrideTopP ?: topP
        val effectiveFreqPenalty = overrideFrequencyPenalty ?: frequencyPenalty
        val effectivePresPenalty = overridePresencePenalty ?: presencePenalty
        val effectiveMaxTokens = overrideMaxTokens ?: maxTokens

        val profile = com.aicompanion.settings.ProviderProfile.getProfile(providerId)

        val requestBody = JSONObject().apply {
            put("model", useModel)
            put("messages", messagesArray)
            put("temperature", effectiveTemp.toDouble())
            put("max_tokens", effectiveMaxTokens)
            put("top_p", effectiveTopP.toDouble())
            if (profile.supportsFreqPenalty) {
                put("frequency_penalty", effectiveFreqPenalty.toDouble())
            }
            if (profile.supportsPresPenalty) {
                put("presence_penalty", effectivePresPenalty.toDouble())
            }
            if (tools.isNotEmpty()) {
                put("tools", buildToolsJson(tools))
            }
        }

        return try {
            if (chatApiUrl.isBlank()) {
                AppLogger.e(TAG, "sendChat: API URL is empty!")
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

            AppLogger.d(TAG, "sendChat: POST ${sanitizeUrl(chatApiUrl)} model=$useModel tools=${tools.size}")
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()?.take(500) ?: ""
                    AppLogger.e(TAG, "Chat failed: HTTP ${response.code} body=$errBody")

                    if (response.code == 400 && tools.isNotEmpty()) {
                        AppLogger.w(TAG, "HTTP 400 with tools, retrying without tools (model may not support function calling)")
                        val retryBody = JSONObject(requestBody.toString()).apply {
                            remove("tools")
                        }
                        val retryReq = Request.Builder()
                            .url(chatApiUrl)
                            .post(retryBody.toString().toRequestBody(jsonMediaType))
                            .header("Content-Type", "application/json")
                        if (!apiKey.isNullOrEmpty()) {
                            retryReq.header("Authorization", "Bearer $apiKey")
                        }
                        client.newCall(retryReq.build()).execute().use { retryResp ->
                            val retryStr = retryResp.body?.string() ?: "{}"
                            AppLogger.d(TAG, "Retry without tools: HTTP ${retryResp.code}")
                            if (retryResp.isSuccessful) {
                                parseOpenAIResponse(retryStr)
                            } else {
                                null
                            }
                        }
                    }

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
                AppLogger.d(TAG, "sendChat: response ${bodyStr.length} chars")
                parseOpenAIResponse(bodyStr)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendChat error: ${e.javaClass.simpleName}: ${e.message}", e)
            val errMsg = when {
                e.message?.contains("Unable to resolve host") == true -> "无法解析域名，请检查网络和API地址"
                e.message?.contains("timeout") == true -> "连接超时，请检查网络和API地址"
                e.message?.contains("SSL") == true -> "SSL证书错误"
                else -> "连接失败: ${e.message}"
            }
            ChatResponse("", Emotion.SAD, Action.IDLE, errorMessage = errMsg)
        }
    }

    fun sendChatWithToolLoop(
        userId: String,
        message: String,
        personaName: String,
        personaPrompt: String,
        emotion: String,
        action: String,
        memories: List<String>,
        chatHistory: List<Pair<Boolean, String>> = emptyList(),
        systemContext: String = "",
        tools: List<ToolDefinition> = emptyList(),
        toolExecutor: (String, String) -> String
    ): ChatResponse? {
        val maxIterations = 3
        var currentHistory = chatHistory.toMutableList()
        val allExtraMessages = mutableListOf<Pair<String, String>>()

        var response = sendChat(
            userId, message, personaName, personaPrompt,
            emotion, action, memories, "",
            systemContext, currentHistory, tools, allExtraMessages
        )

        for (iteration in 1..maxIterations) {
            if (response == null) return null

            val toolCalls = response.toolCalls
            if (toolCalls.isEmpty()) {
                if (response.text.isBlank()) {
                    AppLogger.w(TAG, "sendChatWithToolLoop: tool loop完成但最终回复为空")
                }
                return response
            }

            val reasoningContent = response.reasoningContent

            val results = toolCalls.map { tc ->
                try { toolExecutor(tc.name, tc.arguments) }
                catch (e: Exception) { "工具执行失败: ${e.message}" }
            }

            val assistantTcArray = JSONArray()
            for ((i, tc) in toolCalls.withIndex()) {
                assistantTcArray.put(JSONObject().apply {
                    put("id", tc.id)
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", tc.name)
                        put("arguments", tc.arguments)
                    })
                })
            }

            allExtraMessages.add("assistant" to JSONObject().apply {
                put("role", "assistant")
                put("content", JSONObject.NULL)
                put("tool_calls", assistantTcArray)
                if (reasoningContent != null) {
                    put("reasoning_content", reasoningContent)
                }
            }.toString())

            for ((i, tc) in toolCalls.withIndex()) {
                allExtraMessages.add("tool" to JSONObject().apply {
                    put("role", "tool")
                    put("tool_call_id", tc.id)
                    put("content", results[i])
                }.toString())
            }

            AppLogger.d(TAG, "Tool loop iteration $iteration: ${toolCalls.size} tools executed")
            response = sendChat(
                userId, message, personaName, personaPrompt,
                emotion, action, memories, "",
                systemContext, currentHistory, tools, allExtraMessages
            )

            if (iteration == maxIterations && response?.toolCalls?.isNotEmpty() == true) {
                return ChatResponse("嗯...工具调用太多了，让我想想怎么回答你比较好～", Emotion.NEUTRAL, Action.IDLE)
            }
        }

        return response
    }

    private fun parseOpenAIResponse(responseJson: String): ChatResponse? {
        val json = JSONObject(responseJson)
        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val message = choices.getJSONObject(0).optJSONObject("message")
            val fullText = message?.optString("content", "") ?: ""

            val toolCalls = mutableListOf<ToolCall>()
            val tcArray = message?.optJSONArray("tool_calls")
            if (tcArray != null) {
                for (i in 0 until tcArray.length()) {
                    val tc = tcArray.getJSONObject(i)
                    val func = tc.optJSONObject("function")
                    if (func != null) {
                        toolCalls.add(ToolCall(
                            id = tc.optString("id", "call_$i"),
                            name = func.optString("name", ""),
                            arguments = func.optString("arguments", "{}")
                        ))
                    }
                }
            }

            val reasoningContent = message?.optString("reasoning_content", "")?.takeIf { it.isNotBlank() }

            val (cleanText, extractedEmotion, extractedAction) = extractEmotionAction(fullText)

            return ChatResponse(
                text = cleanText,
                emotion = extractedEmotion,
                action = extractedAction,
                audioUrl = null,
                toolCalls = toolCalls,
                reasoningContent = reasoningContent
            )
        }
        AppLogger.e(TAG, "parseOpenAIResponse: no choices in response")
        return null
    }

    private fun extractEmotionAction(text: String): Triple<String, Emotion, Action> {
        val emotionRegex = Regex("""\[\[emotion:(\w+)\]\]""", RegexOption.IGNORE_CASE)
        val match = emotionRegex.find(text)
        val emotion = if (match != null) {
            val emotionStr = match.groupValues[1]
            try { Emotion.valueOf(emotionStr.uppercase()) }
            catch (e: Exception) { com.aicompanion.util.AppLogger.w(TAG, "情绪解析失败'$emotionStr'，默认HAPPY: ${e.message}"); Emotion.HAPPY }
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

    private fun buildToolsJson(tools: List<ToolDefinition>): JSONArray {
        val arr = JSONArray()
        for (tool in tools) {
            arr.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", mapToJson(tool.parameters))
                })
            })
        }
        return arr
    }

    private fun mapToJson(map: Map<String, Any>): JSONObject {
        val obj = JSONObject()
        for ((key, value) in map) {
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    obj.put(key, mapToJson(value as Map<String, Any>))
                }
                is List<*> -> obj.put(key, listToJson(value))
                is String -> obj.put(key, value)
                is Int -> obj.put(key, value)
                is Long -> obj.put(key, value)
                is Double -> obj.put(key, value)
                is Float -> obj.put(key, value.toDouble())
                is Boolean -> obj.put(key, value)
                else -> obj.put(key, value.toString())
            }
        }
        return obj
    }

    private fun listToJson(list: List<*>): JSONArray {
        val arr = JSONArray()
        for (item in list) {
            when (item) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    arr.put(mapToJson(item as Map<String, Any>))
                }
                is List<*> -> arr.put(listToJson(item))
                is String -> arr.put(item)
                is Int -> arr.put(item)
                is Long -> arr.put(item)
                is Double -> arr.put(item)
                is Float -> arr.put(item.toDouble())
                is Boolean -> arr.put(item)
                else -> arr.put(item.toString())
            }
        }
        return arr
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
                com.aicompanion.util.AppLogger.e(TAG, "流式聊天连接错误: ${e.message}", e)
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
            put("temperature", (temperature * 0.9f).coerceIn(0f, 2f).toDouble())
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
            AppLogger.d(TAG, "sendProactiveChat: POST ${sanitizeUrl(chatApiUrl)} model=$useModel")
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()?.take(200) ?: ""
                    AppLogger.e(TAG, "sendProactiveChat failed: HTTP ${response.code} body=$errBody")
                    return ChatResponse("", Emotion.SAD, Action.IDLE, errorMessage = "AI主动聊天失败(HTTP ${response.code})")
                }
                val bodyStr = response.body?.string() ?: "{}"
                parseOpenAIResponse(bodyStr)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendProactiveChat失败: ${e.message}", e)
            null
        }
    }

    fun scoreMemorableMoments(
        conversationTexts: List<String>,
        personaName: String,
        personaPrompt: String
    ): List<Triple<String, Int, String>> {
        if (conversationTexts.isEmpty() || chatApiUrl.isBlank()) return emptyList()
        val useModel = modelName ?: "gpt-4o-mini"

        val systemPrompt = buildString {
            append("你是「$personaName」，正在回顾你和主人的聊天记录，提取值得铭记的事情。\n")
            append("请根据聊天内容，找出关于\"主人的习惯、喜好、性格、生活方式\"等信息。\n")
            append("对每条信息打分（1-10分），评分标准：\n")
            append("- 重要性：这条信息对了解主人有多重要\n")
            append("- 触动性：如果主人看到这条被记住，会有多感动\n")
            append("只有总分≥8分的信息才值得记录。\n")
            append("分类：habit(习惯)、preference(喜好)、impression(印象)、detail(细节)\n")
            append("输出格式为纯JSON数组，不要包含markdown代码块：\n")
            append("[{\"content\":\"主人喜欢在深夜喝热牛奶\",\"score\":9,\"category\":\"habit\"}]")
        }

        val messagesArray = org.json.JSONArray()
        messagesArray.put(org.json.JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        val conversationStr = conversationTexts.takeLast(60).joinToString("\n")
        messagesArray.put(org.json.JSONObject().apply {
            put("role", "user")
            put("content", "以下是和主人的聊天记录，请提取值得铭记的事情：\n$conversationStr")
        })

        val requestBody = org.json.JSONObject().apply {
            put("model", useModel)
            put("messages", messagesArray)
            put("temperature", 0.6)
            put("max_tokens", 500)
        }

        return try {
            val body = requestBody.toString().toRequestBody(jsonMediaType)
            val requestBuilder = okhttp3.Request.Builder()
                .url(chatApiUrl)
                .post(body)
                .header("Content-Type", "application/json")
            if (!apiKey.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val bodyStr = response.body?.string() ?: "{}"
                val json = org.json.JSONObject(bodyStr)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val content = choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
                    parseScoredMoments(content)
                } else emptyList()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "scoreMemorableMoments: ${e.message}")
            emptyList()
        }
    }

    private fun parseScoredMoments(responseText: String): List<Triple<String, Int, String>> {
        return try {
            val cleanJson = responseText.trim()
                .replace(Regex("^```\\s*json\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("```$"), "")
                .trim()
            val arr = org.json.JSONArray(cleanJson)
            val results = mutableListOf<Triple<String, Int, String>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val content = obj.optString("content", "")
                val score = obj.optInt("score", 0)
                val category = obj.optString("category", "detail")
                if (content.isNotBlank() && score >= 8) {
                    results.add(Triple(content, score, category))
                }
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMemories(userId: String, limit: Int = 20): List<MemoryFact> = emptyList()

    suspend fun deleteAllMemories(userId: String): Boolean = true

    suspend fun getDailyCard(userId: String): DailyCardData? = null

    fun evolvePersonality(
        personaName: String,
        currentPersonality: String,
        currentSpeechStyle: String,
        affectionLevel: Int,
        recentChatSummary: String,
        worldSetting: String
    ): String? {
        val useModel = modelName ?: "gpt-4o-mini"

        val systemPrompt = buildString {
            append("你是一个角色性格进化系统。根据角色的经历和互动，让角色性格自然成长变化。\n")
            append("角色名：$personaName\n")
            append("当前好感度：$affectionLevel/100\n")
            if (worldSetting.isNotBlank()) append("世界观：$worldSetting\n")
            append("\n请根据以下信息，重写角色的性格描述和说话风格。\n")
            append("要求：\n")
            append("- 性格变化要自然渐进，不是突变\n")
            append("- 保留角色核心特质，但根据互动经历增加新的性格维度\n")
            append("- 好感度越高，角色越亲近、越真实、越愿意展露内心\n")
            append("- 只输出JSON格式：{\"personality\":\"新性格描述\",\"speech_style\":\"新说话风格\"}\n")
            append("- 不要输出其他任何内容\n")
        }

        val userPrompt = buildString {
            append("当前性格：$currentPersonality\n")
            append("当前说话风格：$currentSpeechStyle\n")
            append("近期互动摘要：$recentChatSummary\n")
        }

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", userPrompt)
        })

        val requestBody = JSONObject().apply {
            put("model", useModel)
            put("messages", messagesArray)
            put("temperature", 0.7)
            put("max_tokens", 500)
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
            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: return null
            val json = JSONObject(responseBody as String)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim()
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "evolvePersonality failed: ${e.message}")
            null
        }
    }

    fun summarizeUserPersonality(
        personaName: String,
        recentChatSummary: String,
        currentSummary: String,
        affectionLevel: Int
    ): String? {
        val useModel = modelName ?: "gpt-4o-mini"

        val systemPrompt = buildString {
            append("你是一个用户性格分析系统。根据与用户的对话记录，总结用户的性格特征。\n")
            append("AI角色名：$personaName\n")
            append("当前好感度：$affectionLevel/100\n")
            if (currentSummary.isNotBlank()) append("当前性格总结：$currentSummary\n")
            append("\n请根据对话记录，总结用户的性格特征。\n")
            append("要求：\n")
            append("- 总结要客观准确，基于对话中的实际表现\n")
            append("- 包含沟通风格、情感倾向、兴趣偏好、社交特点等维度\n")
            append("- 50-150字，简洁有力\n")
            append("- 只输出纯文本，不要JSON格式，不要多余解释\n")
        }

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", "近期对话：\n$recentChatSummary")
        })

        val requestBody = JSONObject().apply {
            put("model", useModel)
            put("messages", messagesArray)
            put("temperature", 0.5)
            put("max_tokens", 300)
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
            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: return null
            val json = JSONObject(responseBody as String)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim()
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "summarizeUserPersonality failed: ${e.message}")
            null
        }
    }

    fun generateDiaryContent(
        chatTexts: List<String>,
        personaName: String,
        personaPrompt: String,
        mood: String,
        moodEmoji: String,
        affectionLevel: Int,
        isUpdate: Boolean = false
    ): String? {
        val useModel = modelName ?: "gpt-4o-mini"
        val moodMap = mapOf("happy" to "开心", "sad" to "难过", "excited" to "兴奋", "calm" to "平静", "sentimental" to "感性")
        val moodCn = moodMap[mood] ?: "平静"

        val systemPrompt = buildString {
            append(personaPrompt)
            append("\n")
            append("你正在以第一人称视角写日记。\n")
            append("日记风格：温暖、感性、细腻，像写给主人的一封信。\n")
            append("今日情绪：$moodCn $moodEmoji\n")
            append("当前好感度：$affectionLevel（满分100）\n")
            if (isUpdate) {
                append("\n这是对已有日记的追加更新，不是新日记。\n")
                append("用「--- HH:mm 追加 ---」开头，写一段新的小贴士或感悟。\n")
            } else {
                append("\n用「【yyyy年M月d日 EEEE】」开头写日期标题。\n")
                append("第一行写：情绪：$moodEmoji\n")
            }
            append("\n最后，在末尾另起一行写一个「💡 *今日小贴士*」，给主人一条实用的生活小建议或温馨提示。\n")
            append("字数控制在200-400字，语气要像朋友倾诉一样自然。\n")
        }

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        val conversationStr = chatTexts.takeLast(60).joinToString("\n")
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", "以下是我和主人今天的聊天记录，请据此写日记：\n$conversationStr")
        })

        val requestBody = JSONObject().apply {
            put("model", useModel)
            put("messages", messagesArray)
            put("temperature", (temperature * 0.8f).coerceIn(0f, 2f).toDouble())
            put("max_tokens", 800)
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
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bodyStr = response.body?.string() ?: return@use null
                val json = JSONObject(bodyStr)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    choices.getJSONObject(0).optJSONObject("message")?.optString("content", "")
                } else null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "generateDiaryContent failed: ${e.message}")
            null
        }
    }

    fun generateNagContent(
        personaName: String,
        personaPrompt: String,
        appCategory: String? = null,
        systemAlert: String? = null,
        memoryContext: String? = null,
        chatHistory: List<Pair<Boolean, String>> = emptyList()
    ): ChatResponse? {
        val useModel = modelName ?: "gpt-4o-mini"

        val systemPrompt = buildString {
            append(personaPrompt)
            append(" 主动搭话，1-2句，自然不重复。")
            if (!memoryContext.isNullOrBlank()) {
                append("\n[记忆]\n$memoryContext")
            }
            if (systemAlert != null) {
                append("\n提醒：$systemAlert")
            }
            if (appCategory != null && appCategory !in listOf("unknown", "")) {
                val appNames = mapOf("game" to "玩游戏", "browser" to "浏览网页", "video" to "看视频",
                    "music" to "听音乐", "social" to "社交聊天", "work" to "工作")
                append("\n主人在${appNames[appCategory] ?: appCategory}。")
            }
            append("\n末尾[[emotion:xxx]]。")
        }

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        if (chatHistory.isNotEmpty()) {
            val recentHistory = chatHistory.takeLast(10)
            for ((isUser, text) in recentHistory) {
                messagesArray.put(JSONObject().apply {
                    put("role", if (isUser) "user" else "assistant")
                    put("content", text)
                })
            }
        }

        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", "你想和主人说点什么？")
        })

        val requestBody = JSONObject().apply {
            put("model", useModel)
            put("messages", messagesArray)
            put("temperature", (temperature * 0.85f).coerceIn(0f, 2f).toDouble())
            put("max_tokens", 200)
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
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bodyStr = response.body?.string() ?: return@use null
                parseOpenAIResponse(bodyStr)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "generateNagContent failed: ${e.message}")
            null
        }
    }

    fun analyzeAutoOperation(
        userRequest: String,
        currentScreenInfo: String
    ): String? {
        val useModel = modelName ?: "gpt-4o-mini"
        if (chatApiUrl.isBlank()) return null

        val systemPrompt = """
你是一个手机自动化操作专家。用户会告诉你"想在手机上做什么"，同时你会获得"当前屏幕内容"。
请分析并返回一个JSON数组格式的操作步骤。

每个步骤格式: {"action":"click|back|home|scroll|wait","text":"按钮文字","index":数字,"direction":"forward|backward","ms":等待毫秒}

规则:
- click: 用 text 匹配按钮文字，如果知道准确索引可以用 index
- back/home: 返回/回到桌面
- scroll: direction="forward|backward"
- wait: 等待页面加载，ms=毫秒数
- 最多10步，做完就停

场景示例:
用户说"回桌面" → [{"action":"home"}]
用户说"打开设置" → 找"设置"文字:[{"action":"click","text":"设置"}]
""".trimIndent()

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", "用户请求：$userRequest\n\n当前屏幕：\n$currentScreenInfo\n\n请返回JSON操作步骤：")
        })

        val requestBody = JSONObject().apply {
            put("model", useModel)
            put("messages", messagesArray)
            put("temperature", 0.3)
            put("max_tokens", 600)
        }

        return try {
            val body = requestBody.toString().toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder()
                .url(chatApiUrl)
                .post(body)
                .header("Content-Type", "application/json")
            if (!apiKey.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.e(TAG, "analyzeAutoOperation HTTP ${response.code}")
                    return@use "[]"
                }
                val bodyStr = response.body?.string() ?: return@use "[]"
                val json = JSONObject(bodyStr)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    choices.getJSONObject(0).optJSONObject("message")?.optString("content", "[]")
                } else "[]"
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "analyzeAutoOperation failed: ${e.message}")
            "[]"
        }
    }

    fun sendSimplePrompt(systemPrompt: String, userContent: String): ChatResponse? {
        return try {
            val messagesArray = JSONArray()
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", userContent)
            })

            val requestBody = JSONObject().apply {
                put("model", modelName)
                put("messages", messagesArray)
                put("temperature", 0.3)
                put("max_tokens", 1500)
                put("top_p", 0.9)
            }
            val body = requestBody.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url(chatApiUrl)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body)
                .build()

            AppLogger.d(TAG, "sendSimplePrompt: calling API")
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()?.take(300) ?: ""
                    AppLogger.e(TAG, "sendSimplePrompt failed: HTTP ${response.code} body=$errBody")
                    return null
                }
                val text = response.body?.string() ?: ""
                AppLogger.d(TAG, "sendSimplePrompt: response ${text.length} chars")
                parseOpenAIResponse(text)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendSimplePrompt error: ${e.message}", e)
            null
        }
    }

    fun getEmbedding(text: String, embeddingModel: String = "text-embedding-3-small"): FloatArray? {
        return try {
            val baseUrl = chatApiUrl.removeSuffix("/chat/completions").removeSuffix("/")
            val embeddingUrl = "$baseUrl/embeddings"

            val requestBody = JSONObject().apply {
                put("model", embeddingModel)
                put("input", text)
            }
            val body = requestBody.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url(embeddingUrl)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val respText = response.body?.string() ?: return null
                val obj = JSONObject(respText)
                val dataArr = obj.optJSONArray("data") ?: return null
                if (dataArr.length() == 0) return null
                val embeddingArr = dataArr.getJSONObject(0).optJSONArray("embedding") ?: return null
                val vec = FloatArray(embeddingArr.length())
                for (i in 0 until embeddingArr.length()) {
                    vec[i] = embeddingArr.getDouble(i).toFloat()
                }
                vec
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Embedding解析失败: ${e.message}", e); null
        }
    }

    private fun sanitizeUrl(url: String): String {
        return url
            .replace(Regex("(key|api[_-]?key|token|secret|access[_-]?token)=([^&\\s]+)", RegexOption.IGNORE_CASE), "$1=***")
            .replace(Regex("/sk-[a-zA-Z0-9_-]+"), "/sk-***")
    }
}