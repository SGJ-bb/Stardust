package com.aicompanion.network

/** AI后端API客户端: 聊天请求(sendChat)支持工具调用/解析tool_calls, 以及persona/记忆/历史消息注入, 天气查询/角色生成/图片生成/TTS语音/日记生成 */

import android.content.SharedPreferences
import android.util.Log
import com.aicompanion.config.AppConfig
import com.aicompanion.models.*
import com.aicompanion.util.AppLogger
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 超时配置类
 * 支持多种预设配置和用户自定义
 */
data class TimeoutConfig(
    val connectTimeoutMs: Long = AppConfig.NETWORK_TIMEOUT_CONNECT_DEFAULT,
    val readTimeoutMs: Long = AppConfig.NETWORK_TIMEOUT_READ_DEFAULT,
    val writeTimeoutMs: Long = AppConfig.NETWORK_TIMEOUT_WRITE_DEFAULT
) {
    companion object {
        /** 默认配置（标准网络环境） */
        fun default() = TimeoutConfig()

        /** 慢速网络配置（网络不稳定或远程服务器） */
        fun forSlowNetwork() = TimeoutConfig(
            connectTimeoutMs = AppConfig.NETWORK_TIMEOUT_CONNECT_DEFAULT * 2,
            readTimeoutMs = AppConfig.NETWORK_TIMEOUT_READ_DEFAULT * 2,
            writeTimeoutMs = AppConfig.NETWORK_TIMEOUT_WRITE_DEFAULT * 2
        )

        /** 图片生成配置（上传下载耗时长） */
        fun forImageGeneration() = TimeoutConfig(
            connectTimeoutMs = AppConfig.NETWORK_TIMEOUT_CONNECT_DEFAULT,
            readTimeoutMs = AppConfig.NETWORK_TIMEOUT_READ_DEFAULT * 3,
            writeTimeoutMs = AppConfig.NETWORK_TIMEOUT_WRITE_DEFAULT
        )

        /** 从用户偏好设置创建配置 */
        fun fromUserPreference(prefs: SharedPreferences): TimeoutConfig {
            val fastMode = prefs.getBoolean("fast_response_mode", false)
            return if (fastMode) {
                TimeoutConfig(
                    AppConfig.NETWORK_TIMEOUT_CONNECT_DEFAULT * 2 / 3,
                    AppConfig.NETWORK_TIMEOUT_READ_DEFAULT * 2 / 3,
                    AppConfig.NETWORK_TIMEOUT_WRITE_DEFAULT * 2 / 3
                )
            } else {
                default()
            }
        }
    }
}

class ApiClient(
    val chatApiUrl: String,
    val apiKey: String? = null,
    val modelName: String? = null,
    val temperature: Float = 1.05f,
    val topP: Float = 0.92f,
    val frequencyPenalty: Float = 0.35f,
    val presencePenalty: Float = 0.5f,
    val maxTokens: Int = 500,
    val providerId: String = "custom",
    val timeoutConfig: TimeoutConfig = TimeoutConfig.default()
) {
    companion object {
        private const val TAG = "ApiClient"
        private const val MAX_GLOBAL_RETRIES = 3
        private val globalRetryCount = AtomicInteger(0)

        /** 共享的OkHttpClient实例（用于简单的HTTP请求，不涉及AI API） */
        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(AppConfig.NETWORK_TIMEOUT_CONNECT_DEFAULT, TimeUnit.MILLISECONDS)
                .readTimeout(AppConfig.NETWORK_TIMEOUT_READ_DEFAULT, TimeUnit.MILLISECONDS)
                .writeTimeout(AppConfig.NETWORK_TIMEOUT_WRITE_DEFAULT, TimeUnit.MILLISECONDS)
                .build()
        }

        /** 重置重试计数器（每次新请求开始时调用） */
        fun resetRetryCount() = globalRetryCount.set(0)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutConfig.connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutConfig.readTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutConfig.writeTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** 粗略估算 token 数（中文约2字符/token，英文约4字符/tool） */
    private fun estimateTokens(text: String): Int {
        var tokens = 0
        for (c in text) {
            tokens += if (c.code > 0x7E) 1 else 1 // 统一按字符计，安全偏大估计
        }
        return (tokens / 3).coerceAtLeast(1) // 平均每3个字符≈1token（中英混合）
    }

    /** 估算 messages 数组的总 token 数 */
    private fun estimateMessagesTokens(messagesArray: JSONArray, toolsJson: String = ""): Int {
        var total = 0
        for (i in 0 until messagesArray.length()) {
            val msg = messagesArray.getJSONObject(i)
            total += estimateTokens(msg.optString("content", "") ?: "")
            // tool_calls 也算 token
            val tcArray = msg.optJSONArray("tool_calls")
            if (tcArray != null) {
                total += tcArray.length() * 50 // 每个 tool_call 约50 token
            }
            // tool_call_id 字段
            val toolCallId = msg.optString("tool_call_id", "")
            if (toolCallId.isNotBlank()) total += 10
        }
        // tools 定义本身的 token 开销
        if (toolsJson.isNotBlank()) {
            total += estimateTokens(toolsJson)
        }
        return total + messagesArray.length() * 4 // 每条消息的 role/元数据开销
    }

    /** 智能裁剪历史：优先保留工具定义，从最老的对话开始裁剪；最少保留2轮 */
    private fun smartTrimHistory(
        chatHistory: List<Pair<Boolean, String>>,
        systemPrompt: String,
        userContent: String,
        extraMessages: List<Pair<String, String>>,
        tools: List<ToolDefinition>,
        maxBudget: Int = 120_000  // 默认120K上下文窗口，留30%给输出
    ): List<Pair<Boolean, String>> {
        val outputReserve = maxBudget / 3 // 预留输出空间
        val targetBudget = maxBudget - outputReserve

        // 先算工具的固定开销
        val toolsJson = if (tools.isNotEmpty()) buildToolsJson(tools).toString() else ""
        val toolsTokenCost = if (toolsJson.isNotEmpty()) estimateTokens(toolsJson) else 0

        // 系统 prompt + 用户消息 的固定成本
        val fixedCost = estimateTokens(systemPrompt) + estimateTokens(userContent)

        // extraMessages (tool loop 历史不可裁剪)
        var extraCost = 0
        for ((_, content) in extraMessages) {
            extraCost += estimateTokens(content)
        }

        // 可用于历史对话的预算
        val historyBudget = targetBudget - fixedCost - toolsTokenCost - extraCost

        if (historyBudget <= 0) {
            AppLogger.w(TAG, "smartTrimHistory: 预算已耗尽(固定${fixedCost}+工具${toolsTokenCost}+额外${extraCost})，只保留最后2条")
            return chatHistory.takeLast(2)
        }

        // 从最老的消息开始逐条累加，找到能装下的最大数量
        var accumulated = 0
        var keepFromIndex = chatHistory.size
        for (i in chatHistory.indices.reversed()) {
            val cost = estimateTokens(chatHistory[i].second)
            if (accumulated + cost > historyBudget && (chatHistory.size - i) >= 2) {
                keepFromIndex = i + 1
                break
            }
            accumulated += cost
        }

        val trimmed = chatHistory.drop(maxOf(0, keepFromIndex))
        AppLogger.w(TAG, "smartTrimHistory: ${chatHistory.size}条→${trimmed.size}条, " +
            "历史token=${accumulated}/${historyBudget}, 工具token=${toolsTokenCost}, 固定=${fixedCost}")
        return trimmed
    }

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
        imageUrls: List<String> = emptyList(),
        overrideTemperature: Float? = null,
        overrideTopP: Float? = null,
        overrideFrequencyPenalty: Float? = null,
        overridePresencePenalty: Float? = null,
        overrideMaxTokens: Int? = null
    ): ChatResponse? {
        // 每次新请求重置计数器，防止多次调用累积计数
        globalRetryCount.set(0)

        val useModel = modelName ?: "gpt-4o-mini"
        AppLogger.w(TAG, "sendChat: model=$useModel, url=${chatApiUrl.take(30)}, history=${chatHistory.size}条")

        val systemPrompt = buildString {
            append(personaPrompt)
            if (memories.isNotEmpty()) {
                append("\n记得：${memories.takeLast(3).joinToString("；")}")
            }
        }

        // 先构建用户消息内容（smartTrimHistory 需要它来算 token）
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

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        // 智能裁剪：优先保留工具定义，按token预算裁剪历史（而非固定取最后10条）
        val trimmedHistory = smartTrimHistory(
            chatHistory, systemPrompt, userContent, extraMessages, tools
        )
        trimmedHistory.forEach { (isUser, text) ->
            messagesArray.put(JSONObject().apply {
                put("role", if (isUser) "user" else "assistant")
                put("content", text)
            })
        }

        val userMsg = JSONObject()
        userMsg.put("role", "user")
        if (imageUrls.isNotEmpty() && com.aicompanion.settings.ProviderProfile.supportsVision(providerId)) {
            val contentArray = JSONArray()
            contentArray.put(JSONObject().apply {
                put("type", "text")
                put("text", userContent)
            })
            for (url in imageUrls) {
                contentArray.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", url)
                    })
                })
            }
            userMsg.put("content", contentArray)
        } else {
            userMsg.put("content", userContent)
        }
        messagesArray.put(userMsg)

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

            AppLogger.w(TAG, "sendChat: POST ${sanitizeUrl(chatApiUrl)} model=$useModel tools=${tools.size}")
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()?.take(500) ?: ""
                    AppLogger.e(TAG, "Chat failed: HTTP ${response.code} body=$errBody")

                    if (response.code == 401) {
                        AppLogger.e(TAG, "聊天API认证失败(401), 请检查API Key是否正确")
                    }
                    if (response.code == 402) {
                        AppLogger.e(TAG, "聊天API余额不足(402), 请前往API厂商充值")
                    }
                    if (response.code == 429) {
                        AppLogger.w(TAG, "聊天API限流(429), 请求过于频繁，请稍后重试")
                    }
                    if (response.code >= 500) {
                        AppLogger.e(TAG, "聊天API服务端错误(${response.code}), 服务商可能暂时不可用")
                    }

                    if (response.code == 400 && tools.isNotEmpty()) {
                        // 检查重试计数器，防止无限重试
                        if (globalRetryCount.incrementAndGet() > MAX_GLOBAL_RETRIES) {
                            AppLogger.e(TAG, "HTTP 400重试次数超过限制($MAX_GLOBAL_RETRIES)，停止重试")
                            return ChatResponse("", Emotion.SAD, Action.IDLE,
                                errorMessage = "请求失败(HTTP 400)，重试次数过多，请检查API配置或模型是否支持工具调用")
                        }
                        // 第一轮重试：保留工具，激进裁剪历史到最少2条
                        AppLogger.w(TAG, "HTTP 400 with tools, retry: 激进裁剪历史(保留工具) [重试次数: ${globalRetryCount.get()}]")
                        val aggressiveTrim = smartTrimHistory(
                            chatHistory, systemPrompt, userContent,
                            emptyList(), tools, maxBudget = 120_000
                        ).takeLast(2)
                        val retryMessages = JSONArray()
                        retryMessages.put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        aggressiveTrim.forEach { (isUser, text) ->
                            retryMessages.put(JSONObject().apply {
                                put("role", if (isUser) "user" else "assistant")
                                put("content", text)
                            })
                        }
                        retryMessages.put(userMsg)
                        for ((role, content) in extraMessages) {
                            val msgObj = if (content.startsWith("{") && (role == "assistant" || role == "tool")) {
                                try { JSONObject(content) }
                                catch (e: Exception) { JSONObject().apply { put("role", role); put("content", content) } }
                            } else { JSONObject().apply { put("role", role); put("content", content) } }
                            retryMessages.put(msgObj)
                        }
                        val retryBody1 = JSONObject().apply {
                            put("model", useModel)
                            put("messages", retryMessages)
                            put("temperature", effectiveTemp.toDouble())
                            put("max_tokens", effectiveMaxTokens)
                            put("top_p", effectiveTopP.toDouble())
                            if (profile.supportsFreqPenalty) put("frequency_penalty", effectiveFreqPenalty.toDouble())
                            if (profile.supportsPresPenalty) put("presence_penalty", effectivePresPenalty.toDouble())
                            put("tools", buildToolsJson(tools)) // 工具始终保留！
                        }

                        val retryReq1 = Request.Builder()
                            .url(chatApiUrl)
                            .post(retryBody1.toString().toRequestBody(jsonMediaType))
                            .header("Content-Type", "application/json")
                        if (!apiKey.isNullOrEmpty()) { retryReq1.header("Authorization", "Bearer $apiKey") }
                        return client.newCall(retryReq1.build()).execute().use { r1 ->
                            val s1 = r1.body?.string() ?: "{}"
                            AppLogger.w(TAG, "Retry (trim+keep tools): HTTP ${r1.code}")
                            if (r1.isSuccessful) {
                                parseOpenAIResponse(s1)
                            } else if (r1.code == 400) {
                                // 第二轮：确认是模型不支持 function calling（非 token 问题）
                                AppLogger.w(TAG, "Still 400 after trim, model may not support tools. Final retry without tools")
                                retryBody1.remove("tools")
                                val retryReq2 = Request.Builder()
                                    .url(chatApiUrl)
                                    .post(retryBody1.toString().toRequestBody(jsonMediaType))
                                    .header("Content-Type", "application/json")
                                if (!apiKey.isNullOrEmpty()) { retryReq2.header("Authorization", "Bearer $apiKey") }
                                return client.newCall(retryReq2.build()).execute().use { r2 ->
                                    val s2 = r2.body?.string() ?: "{}"
                                    AppLogger.w(TAG, "Final retry (no tools): HTTP ${r2.code}")
                                    if (r2.isSuccessful) parseOpenAIResponse(s2)
                                    else ChatResponse("", Emotion.SAD, Action.IDLE,
                                        errorMessage = "请求失败(HTTP ${r2.code})，模型「$useModel」可能不支持工具调用")
                                }
                            } else {
                                val e1 = when (r1.code) {
                                    in 400..499 -> "请求错误(HTTP ${r1.code})"
                                    else -> "服务端错误(HTTP ${r1.code})"
                                }
                                ChatResponse("", Emotion.SAD, Action.IDLE, errorMessage = e1)
                            }
                        }
                    }

                    val errMsg = when (response.code) {
                        401 -> "API密钥无效，请检查设置中的API Key"
                        402 -> "余额不足，请前往API厂商充值"
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
                AppLogger.w(TAG, "sendChat: response ${bodyStr.length} chars")
                parseOpenAIResponse(bodyStr)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "[API-Chat] sendChat聊天请求失败: ${e.javaClass.simpleName}: ${e.message} | model=$useModel url=${sanitizeUrl(chatApiUrl)}", e)
            AppLogger.e(TAG, "聊天API连接失败, 常见原因: 1)URL错误 2)网络不通 3)代理设置问题 4)DNS解析失败")
            val errMsg = when {
                e.message?.contains("Unable to resolve host") == true -> "无法解析域名，请检查网络和API地址"
                e.message?.contains("timeout") == true -> "连接超时，请检查网络和API地址"
                e.message?.contains("SSL") == true -> "SSL证书错误"
                else -> "连接失败: ${e.message}"
            }
            ChatResponse("", Emotion.SAD, Action.IDLE, errorMessage = errMsg)
        }
    }

    suspend fun sendChatWithToolLoop(
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
        toolExecutor: suspend (String, String) -> String,
        imageUrls: List<String> = emptyList(),
        overrideTemperature: Float? = null,
        overrideTopP: Float? = null
    ): ChatResponse? {
        // 重置重试计数器，防止多次调用累积计数
        resetRetryCount()

        val maxIterations = 3
        var currentHistory = chatHistory.toMutableList()
        val allExtraMessages = mutableListOf<Pair<String, String>>()

        var response = sendChat(
            userId, message, personaName, personaPrompt,
            emotion, action, memories, "",
            systemContext, currentHistory, tools, allExtraMessages,
            imageUrls = imageUrls,
            overrideTemperature = overrideTemperature,
            overrideTopP = overrideTopP
        )

        for (iteration in 1..maxIterations) {
            if (response == null) return null

            val toolCalls = response.toolCalls
            if (toolCalls.isEmpty()) {
                AppLogger.w(TAG, "sendChatWithToolLoop: iteration=$iteration, 无tool_calls, text=${response.text.take(80)}")
                return response
            }

            AppLogger.w(TAG, "sendChatWithToolLoop: iteration=$iteration, 收到${toolCalls.size}个tool_calls: ${toolCalls.map { it.name }}")
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

            AppLogger.w(TAG, "Tool loop iteration $iteration: ${toolCalls.size} tools executed")
            response = sendChat(
                userId, message, personaName, personaPrompt,
                emotion, action, memories, "",
                systemContext, currentHistory, tools, allExtraMessages,
                imageUrls = imageUrls,
                overrideTemperature = overrideTemperature,
                overrideTopP = overrideTopP
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

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val bodyStr = response.body?.string() ?: "{}"
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
                        code == 402 -> "余额不足，请前往API厂商充值"
                        code == 403 -> "无权限访问，请检查API密钥权限"
                        code == 404 -> "接口地址不存在，请确认API地址是否正确"
                        code == 429 -> "请求过于频繁或已超出配额"
                        code >= 400 && code < 500 -> "请求错误: HTTP $code，请检查模型名称「${modelName ?: "未设置"}」是否正确"
                        code >= 500 -> "服务端错误: HTTP $code"
                        else -> "连接失败: HTTP $code"
                    }
                    listener(false, msg)
                }
                }
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e(TAG, "[API-Stream] 流式聊天连接错误: ${e.javaClass.simpleName}: ${e.message}", e)
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
            //AppLogger.d(TAG, "sendProactiveChat: POST ${sanitizeUrl(chatApiUrl)} model=$useModel")
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
            AppLogger.e(TAG, "[API-Proactive] sendProactiveChat主动消息失败: ${e.javaClass.simpleName}: ${e.message}", e)
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
            AppLogger.e(TAG, "[API-Score] scoreMemorableMoments评分难忘时刻失败: ${e.javaClass.simpleName}: ${e.message}")
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
            client.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string() ?: return null
                val json = JSONObject(responseBody as String)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    choices.getJSONObject(0).optJSONObject("message")?.optString("content", "")?.trim()
                } else null
            }
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
            client.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string() ?: return null
                val json = JSONObject(responseBody as String)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    choices.getJSONObject(0).optJSONObject("message")?.optString("content", "")?.trim()
                } else null
            }
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
            AppLogger.e(TAG, "[API-Diary] generateDiaryContent生成日记内容失败: ${e.javaClass.simpleName}: ${e.message}")
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
            AppLogger.e(TAG, "[API-Nag] generateNagContent生成唠叨内容失败: ${e.javaClass.simpleName}: ${e.message}")
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
            AppLogger.e(TAG, "[API-Auto] analyzeAutoOperation分析自动操作失败: ${e.javaClass.simpleName}: ${e.message}")
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
                put("model", modelName ?: "gpt-4o-mini")
                put("messages", messagesArray)
                put("temperature", 0.3)
                put("max_tokens", 1500)
                put("top_p", 0.9)
            }
            val body = requestBody.toString().toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder().url(chatApiUrl)
                .header("Content-Type", "application/json")
                .post(body)
            if (!apiKey.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }
            val request = requestBuilder.build()

            AppLogger.w(TAG, "sendSimplePrompt: calling API")
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()?.take(300) ?: ""
                    AppLogger.e(TAG, "sendSimplePrompt failed: HTTP ${response.code} body=$errBody")
                    return null
                }
                val text = response.body?.string() ?: ""
                AppLogger.w(TAG, "sendSimplePrompt: response ${text.length} chars")
                parseOpenAIResponse(text)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "[API-Simple] sendSimplePrompt简单请求失败: ${e.javaClass.simpleName}: ${e.message}", e)
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
            val requestBuilder = Request.Builder().url(embeddingUrl)
                .header("Content-Type", "application/json")
                .post(body)
            if (!apiKey.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }
            val request = requestBuilder.build()

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
            AppLogger.e(TAG, "[API-Embed] Embedding响应解析失败: ${e.javaClass.simpleName}: ${e.message}", e); null
        }
    }

    private fun sanitizeUrl(url: String): String {
        return url
            .replace(Regex("(key|api[_-]?key|token|secret|access[_-]?token)=([^&\\s]+)", RegexOption.IGNORE_CASE), "$1=***")
            .replace(Regex("/sk-[a-zA-Z0-9_-]+"), "/sk-***")
    }

    /** 从 API 提供商获取可用模型列表 */
    fun fetchAvailableModels(): List<String> {
        if (chatApiUrl.isBlank()) return emptyList()
        
        return try {
            // 从 chat/completions URL 推算 models 端点
            val baseUrl = chatApiUrl
                .removeSuffix("/chat/completions")
                .removeSuffix("/")
            val modelsUrl = "$baseUrl/models"
            
            val requestBuilder = Request.Builder()
                .url(modelsUrl)
                .get()
                .header("Content-Type", "application/json")
            
            if (!apiKey.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }
            
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.e(TAG, "fetchModels failed: HTTP ${response.code}")
                    return emptyList()
                }
                
                val bodyStr = response.body?.string() ?: return emptyList()
                val json = JSONObject(bodyStr)
                val dataArr = json.optJSONArray("data") ?: return emptyList()
                
                val models = mutableListOf<String>()
                for (i in 0 until dataArr.length()) {
                    val modelObj = dataArr.getJSONObject(i)
                    val modelId = modelObj.optString("id", "")
                    if (modelId.isNotBlank()) {
                        models.add(modelId)
                    }
                }
                
                AppLogger.w(TAG, "fetchModels: 获取到 ${models.size} 个模型")
                models.sorted()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "[API-Models] fetchModels获取模型列表失败: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }
}