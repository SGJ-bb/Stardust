package com.aicompanion.network

/** AI后端API客户端: 聊天请求(sendChat)支持persona/记忆/历史消息/系统上下文注入, 以及天气查询/角色生成/图片生成/TTS语音 */

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
        isOfflineMode: Boolean,
        systemContext: String = "",
        chatHistory: List<Pair<Boolean, String>> = emptyList()
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
            android.util.Log.e("ApiClient", "scoreMemorableMoments: ${e.message}")
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
            append("你是「$personaName」，一个可爱的AI桌宠。\n")
            append(personaPrompt)
            append("\n你正在以第一人称视角写日记。\n")
            append("日记风格：温暖、感性、细腻，像写给主人的一封信。\n")
            append("今日情绪：$moodCn $moodEmoji\n")
            append("当前好感度：$affectionLevel（满分100）\n")
            if (isUpdate) {
                append("\n这是对已有日记的追加更新，不是新日记。\n")
                append("用「--- HH:mm 追加 ---」开头，写一段新的感悟。\n")
            } else {
                append("\n用「【yyyy年M月d日 EEEE】」开头写日期标题。\n")
                append("第一行写：情绪：$moodEmoji\n")
            }
            append("\n最后，在末尾另起一行写一个「💡 *一句话感悟*」，可以是对主人的评价、今天的感触、或内心独白。\n")
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
            put("temperature", 0.85)
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
            Log.e("ApiClient", "generateDiaryContent failed: ${e.message}")
            null
        }
    }

    fun generateNagContent(
        personaName: String,
        personaPrompt: String,
        appCategory: String? = null,
        systemAlert: String? = null
    ): ChatResponse? {
        val useModel = modelName ?: "gpt-4o-mini"

        val systemPrompt = buildString {
            append("你是「$personaName」，一个可爱的AI桌宠。\n")
            append(personaPrompt)
            append("\n现在主人没有主动找你，但你想主动找主人搭话/关心主人。\n")
            append("像朋友一样自然地搭话，语气可爱自然，不要重复之前说过的话。\n")
            append("保持在1-2句话。\n")
            if (systemAlert != null) {
                append("\n系统提示：$systemAlert\n请根据此系统提示提醒/关心主人。\n")
            }
            if (appCategory != null && appCategory !in listOf("unknown", "")) {
                val appNames = mapOf("game" to "玩游戏", "browser" to "浏览网页", "video" to "看视频",
                    "music" to "听音乐", "social" to "社交聊天", "work" to "工作")
                val appName = appNames[appCategory] ?: appCategory
                append("\n主人正在$appName，结合这个场景自然地搭话。\n")
            }
            append("\n在回复末尾 [[emotion:xxx]] 处标注你的当前情绪（从 happy/sad/angry/surprised/neutral 中选一个）。\n")
        }

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", "你想和主人说点什么？")
        })

        val requestBody = JSONObject().apply {
            put("model", useModel)
            put("messages", messagesArray)
            put("temperature", 0.9)
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
            Log.e("ApiClient", "generateNagContent failed: ${e.message}")
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
                    Log.e("ApiClient", "analyzeAutoOperation HTTP ${response.code}")
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
            Log.e("ApiClient", "analyzeAutoOperation failed: ${e.message}")
            "[]"
        }
    }
}