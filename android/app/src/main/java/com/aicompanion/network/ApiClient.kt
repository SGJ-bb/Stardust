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

        /**
         * 工具调用循环的默认最大轮次（可由设置页调整，见 SettingsManager.aiToolLoopMaxRounds）。
         * 注意：这是「LLM 连续调用工具」的轮次上限，与「手机操作的动作步数上限」是两个不同的参数。
         * 默认 20；AppContainer 启动时会用 SettingsManager 里的用户值覆盖它。
         */
        @Volatile
        var defaultToolLoopMaxIterations: Int = 20
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
            // 预算耗尽时仍保留最近10条（5轮对话），防止AI完全失忆。
            // 现代 API 普遍百万上下文，即使略微超限也能处理。
            AppLogger.w(TAG, "smartTrimHistory: 预算已耗尽(固定${fixedCost}+工具${toolsTokenCost}+额外${extraCost})，保底保留最后10条")
            return chatHistory.takeLast(10)
        }

        // 从最老的消息开始逐条累加，找到能装下的最大数量
        // keepFromIndex 默认 0 = 全部保留；只有预算装不下时才从该位置之后保留（丢更老的）
        var accumulated = 0
        var keepFromIndex = 0
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
        overrideMaxTokens: Int? = null,
        /**
         * 是否走流式（SSE）。
         * 传入 onDelta 后，模型每生成一段文本就会回调一次；回调参数是「本轮已累计的完整文本」，
         * 调用方直接覆盖气泡内容即可，不需要自己拼接。
         * 若服务端不支持流式（4xx/无 SSE 数据帧/IO 异常），会自动降级为非流式请求，不会失败。
         */
        stream: Boolean = false,
        onDelta: ((String) -> Unit)? = null
    ): ChatResponse? {
        // 每次新请求重置计数器，防止多次调用累积计数
        globalRetryCount.set(0)

        val useModel = modelName ?: "gpt-4o-mini"
        if (chatHistory.isEmpty()) {
            AppLogger.e(TAG, "[MemoryDiag] sendChat收到的历史为0条! model=$useModel, url=${chatApiUrl.take(30)}")
        } else {
            AppLogger.w(TAG, "sendChat: model=$useModel, url=${chatApiUrl.take(30)}, history=${chatHistory.size}条")
        }

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

        AppLogger.w(TAG, "[MemoryDiag] 请求组装完成: messages共${messagesArray.length()}条 (system+历史${trimmedHistory.size}+当前1+额外${extraMessages.size})")

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
            if (stream) {
                put("stream", true)
            }
        }

        return try {
            if (chatApiUrl.isBlank()) {
                AppLogger.e(TAG, "sendChat: API URL is empty!")
                return ChatResponse("", Emotion.SAD, Action.IDLE, errorMessage = "API地址为空，请在设置中配置API地址")
            }
            if (stream) {
                // 流式通道：正常时直接返回；返回 null 表示"流式不可用"，
                // 继续往下走非流式逻辑（下方有完整的 400/工具降级重试处理）
                val streamed = executeStream(requestBody, useModel, onDelta)
                if (streamed != null) return streamed
                AppLogger.w(TAG, "[Stream] 流式通道不可用，降级为非流式请求")
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

    /**
     * 执行流式（SSE）请求。
     *
     * 解析 OpenAI 兼容的 `data: {...}` 数据帧，逐段回调文本增量；同时按 index 拼接
     * 分片下发的 tool_calls（流式下 function.arguments 是按片段返回的，必须累加）。
     *
     * @param onDelta 文本增量回调，参数为「本轮已累计的完整文本」（已剥掉 [[emotion:xx]] 标记）
     * @return 正常完成返回 ChatResponse；返回 null 表示"流式通道不可用，应降级为非流式"
     */
    private fun executeStream(
        requestBody: JSONObject,
        useModel: String,
        onDelta: ((String) -> Unit)?
    ): ChatResponse? {
        val startedAt = System.currentTimeMillis()
        return try {
            val body = requestBody.toString().toRequestBody(jsonMediaType)
            val rb = Request.Builder()
                .url(chatApiUrl)
                .post(body)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
            if (!apiKey.isNullOrEmpty()) {
                rb.header("Authorization", "Bearer $apiKey")
            }

            AppLogger.w(TAG, "sendChat[Stream]: POST ${sanitizeUrl(chatApiUrl)} model=$useModel")
            client.newCall(rb.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string()?.take(300) ?: ""
                    AppLogger.w(TAG, "[Stream] HTTP ${resp.code} body=$errBody")
                    return@use when (resp.code) {
                        401 -> ChatResponse("", Emotion.SAD, Action.IDLE, errorMessage = "API密钥无效，请检查设置中的API Key")
                        402 -> ChatResponse("", Emotion.SAD, Action.IDLE, errorMessage = "余额不足，请前往API厂商充值")
                        429 -> ChatResponse("", Emotion.SAD, Action.IDLE, errorMessage = "请求过于频繁或已超出配额")
                        // 其余错误码交给非流式通道：那边有 400→裁历史→去工具 的完整降级链
                        else -> null
                    }
                }

                val source = resp.body?.source() ?: return@use null
                val textBuf = StringBuilder()
                val reasoningBuf = StringBuilder()
                // index -> { id, name, args(StringBuilder) }：流式 tool_calls 是分片下发的
                val tcFragments = LinkedHashMap<Int, JSONObject>()
                var frameCount = 0

                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty() || line.startsWith(":")) continue   // 空行分隔 / SSE 注释心跳
                    val payload = when {
                        line.startsWith("data:") -> line.removePrefix("data:").trim()
                        line.startsWith("{") -> line.trim()               // 少数网关不带 data: 前缀
                        else -> continue                                   // event: / id: / retry: 等忽略
                    }
                    if (payload.isEmpty()) continue
                    if (payload == "[DONE]") break

                    val obj = try { JSONObject(payload) } catch (_: Exception) { continue }
                    frameCount++

                    obj.optJSONObject("error")?.let { err ->
                        val m = err.optString("message", "").ifBlank { "流式响应返回错误" }
                        AppLogger.e(TAG, "[Stream] 服务端下发 error 事件: $m")
                        return@use ChatResponse("", Emotion.SAD, Action.IDLE, errorMessage = m)
                    }

                    val choices = obj.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val choice = choices.optJSONObject(0) ?: continue
                    // 标准是 delta；个别厂商（含非流式回包）用 message
                    val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message") ?: continue

                    delta.optString("reasoning_content", "")
                        .takeIf { it.isNotEmpty() }?.let { reasoningBuf.append(it) }

                    val piece = delta.optString("content", "")
                    if (piece.isNotEmpty()) {
                        textBuf.append(piece)
                        val shown = stripEmotionTagStreaming(textBuf.toString())
                        if (shown.isNotBlank()) onDelta?.invoke(shown)
                    }

                    delta.optJSONArray("tool_calls")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val tcd = arr.optJSONObject(i) ?: continue
                            val idx = tcd.optInt("index", i)
                            val holder = tcFragments.getOrPut(idx) {
                                JSONObject().apply { put("args", StringBuilder()) }
                            }
                            tcd.optString("id", "").takeIf { it.isNotEmpty() }?.let { holder.put("id", it) }
                            tcd.optJSONObject("function")?.let { fn ->
                                fn.optString("name", "").takeIf { it.isNotEmpty() }?.let { holder.put("name", it) }
                                val argPiece = fn.optString("arguments", "")
                                if (argPiece.isNotEmpty()) {
                                    (holder.get("args") as StringBuilder).append(argPiece)
                                }
                            }
                        }
                    }
                }

                if (frameCount == 0) {
                    AppLogger.w(TAG, "[Stream] 未解析到任何 SSE 数据帧（服务端可能不支持 stream），降级为非流式")
                    return@use null
                }

                val toolCalls = tcFragments.entries.sortedBy { it.key }.mapNotNull { (i, h) ->
                    val name = h.optString("name", "")
                    if (name.isBlank()) return@mapNotNull null
                    ToolCall(
                        id = h.optString("id", "call_$i"),
                        name = name,
                        arguments = (h.get("args") as? StringBuilder)?.toString()?.ifBlank { "{}" } ?: "{}"
                    )
                }

                val (cleanText, emo, act) = extractEmotionAction(textBuf.toString())
                AppLogger.w(
                    TAG,
                    "[Stream] 完成 用时${System.currentTimeMillis() - startedAt}ms " +
                        "text=${cleanText.length}字 toolCalls=${toolCalls.size}"
                )
                ChatResponse(
                    text = cleanText,
                    emotion = emo,
                    action = act,
                    audioUrl = null,
                    toolCalls = toolCalls,
                    reasoningContent = reasoningBuf.toString().takeIf { it.isNotBlank() }
                )
            }
        } catch (e: java.io.IOException) {
            AppLogger.w(TAG, "[Stream] 流式 IO 异常，降级为非流式: ${e.javaClass.simpleName}: ${e.message}")
            null
        } catch (e: Exception) {
            AppLogger.e(TAG, "[Stream] 流式异常，降级为非流式: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    /**
     * 流式显示专用：剥掉情绪标记，避免把 `[[emotion:happy]]` 明晃晃地显示在气泡里。
     * 除了完整标记，还要处理"标记只收到一半"的中间态（如 `[[emot`、`[[emotion:ha`），
     * 否则用户会看到标记慢慢长出来。
     */
    private fun stripEmotionTagStreaming(raw: String): String {
        val withoutComplete = raw.replace(Regex("""\[\[emotion:\w+\]\]""", RegexOption.IGNORE_CASE), "")
        return withoutComplete.replace(Regex("""\[\[[a-zA-Z:_]{0,15}$"""), "")
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
        overrideTopP: Float? = null,
        /** 工具调用最大轮次；<=0 表示使用 defaultToolLoopMaxIterations（可在设置页调整） */
        maxIterations: Int = 0,
        /** 是否流式输出；流式下每一轮回复都会实时回调文本增量（见 sendChat） */
        stream: Boolean = false,
        /** 流式文本增量回调，参数为本轮已累计的完整文本 */
        onDelta: ((String) -> Unit)? = null
    ): ChatResponse? {
        // 重置重试计数器，防止多次调用累积计数
        resetRetryCount()

        val iterationLimit = if (maxIterations > 0) maxIterations else defaultToolLoopMaxIterations
        val maxIterations = iterationLimit.coerceIn(1, 50)
        var currentHistory = chatHistory.toMutableList()
        val allExtraMessages = mutableListOf<Pair<String, String>>()

        var response = sendChat(
            userId, message, personaName, personaPrompt,
            emotion, action, memories, "",
            systemContext, currentHistory, tools, allExtraMessages,
            imageUrls = imageUrls,
            overrideTemperature = overrideTemperature,
            overrideTopP = overrideTopP,
            stream = stream,
            onDelta = onDelta
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
                overrideTopP = overrideTopP,
                stream = stream,
                onDelta = onDelta
            )

            if (iteration == maxIterations && response?.toolCalls?.isNotEmpty() == true) {
                AppLogger.w(TAG, "sendChatWithToolLoop: 达到工具调用轮次上限 $maxIterations，仍有待执行的 tool_calls")
                return ChatResponse(
                    "这一步还没做完（已达工具调用轮次上限 $maxIterations 轮）。可以让我继续，" +
                        "或者到「设置 → 无障碍 → AI 工具调用轮次」把上限调高一些。",
                    Emotion.NEUTRAL, Action.IDLE
                )
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

    /**
     * 分析自动操作：根据用户请求 + 当前屏幕 + 已学过的 UI 知识，规划动作序列。
     *
     * 与旧方案的区别：
     * 旧方案：返回 use_skill 时盲目回放存储的 raw actions —— 脆弱。
     * 新方案：把已学过的 UI 知识（uiMap + workflow）作为"先验"喂给 LLM，
     *        LLM 每次都基于知识 + 当前屏幕重新规划动作。这样屏幕变化也能容错。
     *
     * @param userRequest 用户指令
     * @param currentScreenInfo 当前屏幕结构化内容
     * @param uiKnowledge 已学过的 UI 知识（同 APP 的 uiMap + 匹配任务的 workflow），可为空
     * @return JSONArray 字符串：[{"action":"click",...}, ...]
     */
    fun analyzeAutoOperation(
        userRequest: String,
        currentScreenInfo: String,
        uiKnowledge: String = ""
    ): String? {
        val useModel = modelName ?: "gpt-4o-mini"
        if (chatApiUrl.isBlank()) return null

        val knowledgeSection = if (uiKnowledge.isNotBlank()) {
            """
【你已学过的 UI 知识（先验，可信赖但需结合当前屏幕校验）】
$uiKnowledge

使用规则：
- 这是你之前操作该 APP 时归纳的 UI 结构知识，可作为规划参考
- 但仍需结合"当前屏幕"实际内容校验，不要盲目按旧路径执行
- 若当前屏幕与知识描述不符（APP 改版/页面切换异常），以当前屏幕为准并据此更新认知
"""
        } else ""

        val systemPrompt = """
你是一个手机自动化操作专家，能根据屏幕内容规划操作步骤。
用户会告诉你"想在手机上做什么"，同时你会获得"当前屏幕内容"。

【屏幕内容格式说明】
页面：页面名 (包名)  屏幕 1080x2400
  [3] 微信 {TextView #tab_title}        ← [数字] 是可点击元素索引，可直接 click index=3
  [input] 搜索 {EditText #search}        ← [input] 输入框
  文本内容 ×5                            ← 普通文本，×N 表示同名重复出现
  (scroll) 可滚动区域                     ← 可滑动容器
  ‹无文字可点元素› {ImageButton} @540,2100 ← 无文字的图标按钮，用 index 点击
  — 附加窗口[系统对话框] com.xxx —          ← 弹窗/悬浮窗/输入法里的内容
⚠ 内容超长已截断                           ← 只看到了一部分，需要 scroll 翻页后再看
$knowledgeSection
【返回格式】
返回 JSON 数组，每个元素是一个动作：
[{"action":"click","index":3},{"action":"wait","ms":1500},...]

【动作类型】
1. click - {"action":"click","index":3} 或 {"action":"click","text":"微信"}
2. tap   - {"action":"tap","x":540,"y":2100}  坐标点击（无文字元素/自绘控件兜底）
3. input - {"action":"input","text":"内容"}，指定输入框：{"action":"input","target":"搜索","text":"内容"}
4. swipe - {"action":"swipe","direction":"up"}，或自定义 {"action":"swipe","start_x":..,"start_y":..,"end_x":..,"end_y":..}
5. scroll - {"action":"scroll","direction":"forward"}
6. long_press - {"action":"long_press","text":"消息"}
7. back/home - {"action":"back"} / {"action":"home"}
8. wait - {"action":"wait","ms":1500}
9. read - {"action":"read"}  强制重新读取屏幕（页面加载慢或内容截断时用）
10. notifications / recents

【规则】
- 你能操作90%的日常应用，不要说"做不到"
- 当前不在目标App时，先 home 回桌面再 click 打开
- 页面切换后加适当wait（加载慢用2000ms，快用1000ms）
- 最多30步，复杂任务也要尽量完成
- 遇到支付/密码/验证码页面，返回 {"error":"原因说明"} 表示拒绝执行
- 优先用 index 点击（快照里的索引最准确）；没有 index 时再用 text
- 看到「内容超长已截断」时，先 scroll 翻页，再 {"action":"read"} 重新读取
- 看到「无文字可点元素」时用它的 index 或 @x,y 坐标 tap
- 需要进入列表里的某一项但屏幕上看不到完整文字时，可以用 tap 坐标兜底
- 若有 UI 知识，按知识描述的 workflow 规划，但每个动作都要对照当前屏幕确认元素存在

【示例】
[{"action":"home"},{"action":"click","text":"微信"},{"action":"wait","ms":1500},{"action":"click","text":"搜索"},{"action":"input","text":"小明"},{"action":"wait","ms":1000},{"action":"click","index":0},{"action":"wait","ms":500},{"action":"input","text":"明天见"},{"action":"click","text":"发送"}]
""".trimIndent()

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", "用户请求：$userRequest\n\n当前屏幕：\n$currentScreenInfo\n\n请返回动作JSON数组：")
        })

        val requestBody = JSONObject().apply {
            put("model", useModel)
            put("messages", messagesArray)
            put("temperature", 0.3)
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
                    return@use null
                }
                val bodyStr = response.body?.string() ?: return@use null
                val json = JSONObject(bodyStr)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    var content = choices.getJSONObject(0).optJSONObject("message")?.optString("content", "")
                    if (content != null) {
                        content = content.trim()
                        if (content.startsWith("```")) {
                            content = content.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        }
                    }
                    content
                } else null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "[API-Auto] analyzeAutoOperation分析自动操作失败: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * 通用的 OpenAI 兼容 chat/completions 调用（无工具、单轮），返回 message.content。
     */
    private fun postChatCompletion(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.3
    ): String? {
        val useModel = modelName ?: "gpt-4o-mini"
        if (chatApiUrl.isBlank()) return null
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
            put("temperature", temperature)
        }
        return try {
            val body = requestBody.toString().toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder()
                .url(chatApiUrl)
                .post(body)
                .header("Content-Type", "application/json")
            if (!apiKey.isNullOrEmpty()) requestBuilder.header("Authorization", "Bearer $apiKey")
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.e(TAG, "postChatCompletion HTTP ${response.code}")
                    return@use null
                }
                val bodyStr = response.body?.string() ?: return@use null
                val json = JSONObject(bodyStr)
                val choices = json.optJSONArray("choices") ?: return@use null
                if (choices.length() == 0) return@use null
                var content = choices.getJSONObject(0).optJSONObject("message")?.optString("content", "")
                if (content != null) {
                    content = content.trim()
                    if (content.startsWith("```")) {
                        content = content.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    }
                }
                content
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "postChatCompletion 失败: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * 反应式单步规划：给定「当前屏幕 + 已执行历史」，决定下一步做什么。
     *
     * 与 analyzeAutoOperation（一次性规划整段动作）的区别：
     * 这是"观察 → 决策 → 行动 → 再观察"闭环中的一环，每次只决策眼前这一步，
     * 因此进入新页面后 LLM 能真正看到新页面的内容，而不是照着开局那张屏幕盲走。
     *
     * @return JSON 对象字符串，三种形态之一：
     *   {"thought":"...","actions":[{...}]}  继续执行
     *   {"done":true,"summary":"..."}         任务完成
     *   {"error":"..."}                       拒绝执行/无法完成
     */
    fun analyzePhoneOpStep(
        userRequest: String,
        screenInfo: String,
        history: List<String>,
        stepIndex: Int,
        maxSteps: Int,
        uiKnowledge: String = "",
        skillsSection: String = ""
    ): String? {
        val knowledgeSection = if (uiKnowledge.isNotBlank()) {
            "【你已学过的该 APP UI 知识（先验，可信赖但必须结合当前屏幕校验）】\n$uiKnowledge\n"
        } else ""

        val historySection = if (history.isEmpty()) "（尚未执行任何动作）" else history.joinToString("\n")

        val systemPrompt = """
你是一个手机自动化操作专家，正在以"每一步都重新观察屏幕"的方式操作手机。

【屏幕内容格式】
页面：页面名 (包名)
  [3] 微信 {TextView #tab_title}        ← [数字]=可点击元素索引，用 click index=3
  [input] 搜索 {EditText #search}        ← [input]=输入框
  文本内容 ×5                            ← 普通文本，×N=同名重复出现
  (scroll) 可滚动区域
  ‹无文字可点元素› {ImageButton} @540,2100 ← 无文字图标按钮，用 index 或坐标 tap
  — 附加窗口[系统对话框] com.xxx —          ← 弹窗/悬浮窗/输入法里的内容
⚠ 内容超长已截断                           ← 只看到一部分，先 scroll 再 read

【UI 树格式（JSON，优先看懂这个）】
"当前屏幕 UI 树（JSON）："后面是一棵带层级的树，每个节点有稳定 id 与 pid（父节点 id）：
- id：形如 "n14"，本快照内唯一且稳定，用 {"action":"click","target":"n14"} 引用最可靠
- role：button(按钮)/textbox(输入框)/checkbox(开关/复选/单选)/text(文本)/scroll(可滚动区)/node(其它)
- bounds/center：屏幕像素范围/中心点，元素没文字时用坐标 tap，"center":[x,y]
- index：该元素在可点击列表中的下标（与文本版 [n] 一致），也可用 click index
- pid：父节点 id，用它推断归属（如某按钮属于哪张卡片、弹窗盖在哪层）
- checked：开关/复选当前是否选中；你要改变它用 set_checked
- src:"a11y" 表示来自无障碍，src:"vision" 表示来自截图识别（同样可点）
说明：id 在该快照内有效；如果页面刷新后 id 变了，用 target 提示找不到就先把 id 更新一下。

【你每一步必须返回一个 JSON 对象】
1) 需要继续操作：
{"thought":"简述你的判断","actions":[{"action":"click","index":3}]}
2) 任务已完成：
{"done":true,"summary":"一句话说明做完了什么"}
3) 无法继续或遇到支付/密码/验证码等敏感页面：
{"error":"原因说明"}

【动作类型】
- {"action":"click","index":3} 或 {"action":"click","text":"微信"} 或 {"action":"click","target":"n14"}
- {"action":"tap","x":540,"y":2100}        坐标点击（无文字元素兜底）
- {"action":"input","text":"内容"}         指定输入框：再加 "target":"搜索"
- {"action":"set_checked","target":"n14","checked":true}   开关/复选：设定选中态
- {"action":"swipe","direction":"up"}  / {"action":"scroll","direction":"forward"}
- {"action":"long_press","text":"消息"}
- {"action":"back"} / {"action":"home"} / {"action":"notifications"} / {"action":"recents"}
- {"action":"wait","ms":1500}
- {"action":"read"}                        强制重读屏幕

【expect：可选但强烈建议加】
每个动作都可以附带 "expect" 字段，声明「这一步做完后应该看到什么」。系统会在动作执行后校验，
不满足就把这一步判为失败、并把实际状况告诉你，这样你能立刻纠正，
而不会在"以为点成功了"的假前提下越走越远。写法（多个条件用逗号分隔，需全部满足）：
- "expect":"出现:我的钱包"              动作后屏幕上应出现该文字（最常用）
- "expect":"消失:正在加载"              该文字应当消失
- "expect":"包名:com.android.settings"  前台应用应切换到该包名
- "expect":"变化"                       屏幕内容或前台应用至少有一项变化（点按钮后的通用写法）
例如：{"action":"click","index":3,"expect":"变化"}
      {"action":"click","text":"发送","expect":"消失:发送"}

【规则】
- 一次返回 1~4 个动作即可，页面会变的时候少给几个；页面切换后上层会重新观察
- 优先用 index（索引来自当前快照，最准）；没有 index 再用 text；都没有就用 tap 坐标
- 每次都要基于【当前屏幕】判断，不要照抄历史
- 点按钮、切页面、提交表单时务必加 expect，否则「点了没反应」会被你误判成成功
- 看到「内容超长已截断」：先 scroll 翻页，再 {"action":"read"}
- 觉得"已经做完了"就返回 done，不要为了凑步数继续点
- 不要说"做不到"，你能操作绝大多数日常 App
$knowledgeSection$skillsSection
""".trimIndent()

        val userPrompt = """
用户目标：$userRequest

【当前屏幕】
$screenInfo

【已执行动作与结果】
$historySection

【进度】第 $stepIndex 步 / 最多 $maxSteps 步

请返回下一步的 JSON 对象：
""".trimIndent()

        return postChatCompletion(systemPrompt, userPrompt, temperature = 0.3)
    }

    /**
     * 生成技能（Skill Creator）：把用户的一句话需求（可附带当前屏幕内容）写成一条可复用技能。
     *
     * @return JSON 字符串：
     * {"name":"...","description":"...","scope":"app|global","appName":"...","appPackage":"...",
     *  "triggers":["..."],"content":"Markdown 正文"}
     */
    fun generateSkill(
        requirement: String,
        screenContext: String = "",
        packageName: String = "",
        existingContent: String = ""
    ): String? {
        val screenSection = if (screenContext.isNotBlank()) {
            "\n【当前屏幕内容（可作为编写技能的参考）】\n$screenContext\n"
        } else ""
        val packageSection = if (packageName.isNotBlank()) "\n目标包名：$packageName\n" else ""
        val refineSection = if (existingContent.isNotBlank()) {
            "\n【已有技能内容（请在此基础上改进，而不是重写无关内容）】\n$existingContent\n"
        } else ""

        val systemPrompt = """
你是一位「手机自动化技能」作者。你的任务是把用户的需求整理成一条可复用、可被另一个 AI 直接照着执行的技能。

【输出格式】只返回一个 JSON 对象，不要任何解释文字：
{
  "name": "技能名（简短中文，如：微信给好友发消息）",
  "description": "一句话说明这个技能做什么、什么时候用（给 AI 判断是否加载，尽量具体）",
  "scope": "app 或 global",
  "appName": "目标 App 中文名（通用流程留空）",
  "appPackage": "目标 App 包名（不确定就留空）",
  "triggers": ["用户可能说出的触发词", "如：发消息", "发微信"],
  "content": "Markdown 正文"
}

【正文(content)写作要求】
- 用「步骤 1/2/3」写清操作路径，每一步说明：在哪个页面、点什么（优先写元素的文字或资源ID/图标含义）
- 给出失败时的判断与兜底办法（比如找不到元素就下滑找、或用坐标点击）
- 明确列出「危险/需要用户确认」的环节（支付、删除、发送、转账、验证码）
- 如果任务需要文字输入，写清输入框怎么找、发送按钮在哪
- 需要执行系统命令时，注明可用 run_shell 工具，并给出命令示例
- 不要编造不确定的 UI 细节；不确定的地方写"需现场确认"
- 控制在 600 字以内，条理清晰
$packageSection$refineSection
""".trimIndent()

        val userPrompt = "用户需求：$requirement\n$screenSection\n请返回技能 JSON："
        return postChatCompletion(systemPrompt, userPrompt, temperature = 0.4)
    }

    /**
     * 归纳 UI 知识：执行成功后，把指令+执行轨迹发给 LLM，让其抽象出 UI 结构知识 + 任务 workflow。
     *
     * 与旧方案的区别：
     * 旧方案：直接复制 raw actions + 加几个 keyword —— 屏幕变化即失效。
     * 新方案：让 LLM 抽象出"页面布局/关键元素/导航路径 + 高层 workflow"（自然语言），
     *        下次作为先验知识喂给 LLM 重新规划。这样真正"懂"UI，容错性强。
     *
     * @return JSON: {"appName":"微信","appPackage":"com.tencent.mm","uiMap":"...","taskName":"发消息","triggerKeywords":[...],"workflow":"..."}
     */
    fun summarizeSkill(userRequest: String, actionsJson: String): String? {
        val useModel = modelName ?: "gpt-4o-mini"
        if (chatApiUrl.isBlank()) return null

        val systemPrompt = """
你刚成功执行了一项手机自动化任务。请从执行轨迹中归纳出"UI 结构知识"和"任务 workflow"，供下次相同任务时作为先验参考。

【返回格式（JSON对象）】
{
  "appName": "目标APP名（如微信、QQ、淘宝）",
  "appPackage": "APP 包名（如 com.tencent.mm，若不确定留空）",
  "uiMap": "UI 结构知识（自然语言，描述本次经过的主要页面布局、关键元素、导航关系）",
  "taskName": "任务名（简短，如发消息、搜索商品、打开设置）",
  "triggerKeywords": ["触发关键词", "用户说这些词时可能匹配此任务"],
  "workflow": "高层 workflow（自然语言步骤描述，不要写 raw click，要写人能看懂的操作流程）"
}

【uiMap 写法示例】
"【主界面】底部4个tab：微信|通讯录|发现|我；顶部右上角'+'和搜索图标
【聊天列表】点击某条聊天进入对话页
【对话页】顶部返回/对方名字/更多；底部输入框、'+'、麦克风、表情图标
【发消息】在对话页底部输入框输入文本，点击发送按钮（输入框右侧）"

【workflow 写法示例】
"1.从主界面点击底部'微信'tab 进入聊天列表
 2.在列表中找到目标聊天（可通过顶部搜索）
 3.点击进入对话页
 4.在底部输入框输入消息内容
 5.点击输入框右侧的发送按钮"

【规则】
- uiMap 要提炼本次经过的页面结构（不是 raw click 序列）
- workflow 要写高层步骤（人能看懂的），不要写 {"action":"click","text":"xxx"}
- triggerKeywords 要覆盖用户可能的表述方式（如"发消息""发个""发给""说一下"）
- 只返回JSON，不要解释
""".trimIndent()

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", "用户原始请求：$userRequest\n\n执行成功的动作序列：\n$actionsJson\n\n请归纳为 UI 知识 JSON：")
        })

        val requestBody = JSONObject().apply {
            put("model", useModel)
            put("messages", messagesArray)
            put("temperature", 0.2)
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
                    AppLogger.e(TAG, "summarizeSkill HTTP ${response.code}")
                    return@use null
                }
                val bodyStr = response.body?.string() ?: return@use null
                val json = JSONObject(bodyStr)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    var content = choices.getJSONObject(0).optJSONObject("message")?.optString("content", "")
                    if (content != null) {
                        content = content.trim()
                        if (content.startsWith("```")) {
                            content = content.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        }
                    }
                    content
                } else null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "summarizeSkill failed: ${e.message}")
            null
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