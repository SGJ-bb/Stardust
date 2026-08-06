package com.aicompanion.network

import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ProviderAdapter {
    private const val TAG = "ProviderAdapter"

    const val FORMAT_OPENAI = "openai"
    const val FORMAT_SILICONFLOW = "siliconflow"
    const val FORMAT_ALIYUN_ASYNC = "aliyun_async"
    const val FORMAT_KLING = "kling"
    const val FORMAT_FISH_AUDIO = "fish_audio"
    const val FORMAT_MIMO = "mimo"

    private val pollClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun buildImageRequest(
        formatType: String,
        model: String,
        prompt: String,
        n: Int,
        size: String
    ): JSONObject {
        return when (formatType) {
            FORMAT_SILICONFLOW -> JSONObject().apply {
                put("model", model)
                put("prompt", prompt)
                put("image_size", size)
                put("batch_size", n)
                put("num_inference_steps", 20)
            }
            FORMAT_ALIYUN_ASYNC -> JSONObject().apply {
                put("model", model)
                put("input", JSONObject().apply {
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                })
                put("parameters", JSONObject().apply {
                    put("n", n)
                    val dashscopeSize = size.replace("x", "*")
                    val supportedSizes = setOf("1024*1024", "768*1024", "1024*768", "768*768")
                    put("size", if (dashscopeSize in supportedSizes) dashscopeSize else "1024*1024")
                })
            }
            FORMAT_KLING -> JSONObject().apply {
                put("model_name", model)
                put("prompt", prompt)
                put("n", n)
                val klingRatio = when (size) {
                    "1024x1024" -> "1:1"
                    "768x1024" -> "3:4"
                    "1024x768" -> "4:3"
                    "576x1024" -> "9:16"
                    "1024x576" -> "16:9"
                    else -> {
                        val parts = size.split("x")
                        if (parts.size == 2) {
                            val w = parts[0].toIntOrNull() ?: 1024
                            val h = parts[1].toIntOrNull() ?: 1024
                            val ratio = w.toDouble() / h.toDouble()
                            when {
                                w == h -> "1:1"
                                ratio >= 1.5 -> "16:9"
                                ratio > 1.0 -> "4:3"
                                ratio <= 0.67 -> "9:16"
                                ratio < 1.0 -> "3:4"
                                else -> "1:1"
                            }
                        } else "1:1"
                    }
                }
                put("aspect_ratio", klingRatio)
            }
            else -> JSONObject().apply {
                put("model", model)
                put("prompt", prompt)
                put("n", n)
                put("size", size)
            }
        }
    }

    fun buildImageHeaders(
        formatType: String,
        apiKey: String,
        secretKey: String = ""
    ): Map<String, String> {
        return when (formatType) {
            FORMAT_ALIYUN_ASYNC -> {
                if (apiKey.startsWith("sk-", ignoreCase = true) || secretKey.isBlank()) {
                    mapOf(
                        "Authorization" to "Bearer $apiKey",
                        "Content-Type" to "application/json",
                        "X-DashScope-Async" to "enable"
                    )
                } else {
                    val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(java.util.Date())
                    val stringToSign = timestamp
                    try {
                        val signingKey = javax.crypto.spec.SecretKeySpec(secretKey.toByteArray(), "HmacSHA256")
                        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
                        mac.init(signingKey)
                        val signature = android.util.Base64.encodeToString(mac.doFinal(stringToSign.toByteArray()), android.util.Base64.NO_WRAP)
                        mapOf(
                            "Authorization" to "ACS $apiKey:$signature",
                            "Content-Type" to "application/json",
                            "X-DashScope-Async" to "enable",
                            "X-DashScope-Date" to timestamp
                        )
                    } catch (e: Exception) {
                        mapOf(
                            "Authorization" to "Bearer $apiKey",
                            "Content-Type" to "application/json",
                            "X-DashScope-Async" to "enable"
                        )
                    }
                }
            }
            FORMAT_KLING -> {
                val jwt = generateKlingJwt(apiKey)
                if (jwt == null) {
                    mapOf(
                        "Content-Type" to "application/json",
                        "X-Error" to "JWT生成失败，请检查API Key格式是否为AK:SK"
                    )
                } else {
                    mapOf(
                        "Authorization" to "Bearer $jwt",
                        "Content-Type" to "application/json"
                    )
                }
            }
            else -> mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            )
        }
    }

    suspend fun parseImageResponse(
        formatType: String,
        responseBody: String,
        apiUrl: String,
        apiKey: String,
        secretKey: String = ""
    ): String? {
        return when (formatType) {
            FORMAT_SILICONFLOW -> {
                val json = JSONObject(responseBody)
                val imagesArr = json.optJSONArray("images")
                if (imagesArr != null && imagesArr.length() > 0) {
                    val firstImg = imagesArr.optJSONObject(0)
                    val url = firstImg?.optString("url", "")?.ifBlank { null }
                    if (url != null) return url
                    val b64 = firstImg?.optString("b64_json", "")?.ifBlank { null }
                    if (b64 != null) return decodeB64ToTempFile(b64)
                }
                val dataArr = json.optJSONArray("data")
                if (dataArr != null && dataArr.length() > 0) {
                    val firstData = dataArr.optJSONObject(0)
                    val url = firstData?.optString("url", "")?.ifBlank { null }
                    if (url != null) return url
                    val b64 = firstData?.optString("b64_json", "")?.ifBlank { null }
                    if (b64 != null) return decodeB64ToTempFile(b64)
                }
                null
            }
            FORMAT_ALIYUN_ASYNC -> {
                val json = JSONObject(responseBody)
                val output = json.optJSONObject("output")
                val status = output?.optString("task_status", "") ?: ""
                when (status) {
                    "SUCCEEDED" -> {
                        val results = output?.optJSONArray("results")
                        results?.optJSONObject(0)?.optString("url", "")?.ifBlank { null }
                    }
                    "FAILED" -> {
                        val msg = output?.optString("message", "Task failed") ?: "Task failed"
                        AppLogger.e(TAG, "Aliyun task failed immediately: $msg")
                        null
                    }
                    else -> {
                        val taskId = output?.optString("task_id", "") ?: ""
                        if (taskId.isNotBlank()) {
                            pollAliyunTask(apiUrl, apiKey, secretKey, taskId)
                        } else {
                            AppLogger.e(TAG, "Aliyun async: no task_id and no results, status=$status")
                            null
                        }
                    }
                }
            }
            FORMAT_KLING -> {
                val json = JSONObject(responseBody)
                val code = json.optInt("code", -1)
                if (code != 0 && code != -1) {
                    val msg = json.optString("message", "Unknown error")
                    AppLogger.e(TAG, "Kling API error: code=$code, message=$msg")
                    return null
                }
                val data = json.optJSONObject("data")
                if (data != null) {
                    val taskStatus = data.optString("task_status", "")
                    if (taskStatus == "succeed") {
                        val taskResult = data.optJSONObject("task_result")
                        val images = taskResult?.optJSONArray("images")
                        val imgUrl = images?.optJSONObject(0)?.optString("url", "")?.ifBlank { null }
                        if (imgUrl != null) return imgUrl
                    }
                    val taskId = data.optString("task_id", "")
                    if (taskId.isNotBlank()) {
                        return pollKlingTask(apiUrl, apiKey, taskId)
                    }
                }
                val dataArr = json.optJSONArray("data")
                if (dataArr != null && dataArr.length() > 0) {
                    val url = dataArr.optJSONObject(0)?.optString("url", "")?.ifBlank { null }
                    if (url != null) return url
                }
                null
            }
            else -> {
                val json = JSONObject(responseBody)
                val dataArr = json.optJSONArray("data")
                val firstData = dataArr?.optJSONObject(0)
                val url = firstData?.optString("url", "")?.ifBlank { null }
                if (url != null) return url
                val b64 = firstData?.optString("b64_json", "")?.ifBlank { null }
                if (b64 != null) {
                    return decodeB64ToTempFile(b64)
                }
                null
            }
        }
    }

    private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    private fun decodeB64ToTempFile(b64: String): String? {
        return try {
            val ctx = appContext
            val dir = if (ctx != null) {
                java.io.File(ctx.filesDir, "generated_images")
            } else {
                java.io.File(System.getProperty("java.io.tmpdir", "/data/local/tmp"), "generated_images")
            }
            dir.mkdirs()
            val file = java.io.File(dir, "img_${java.util.UUID.randomUUID()}.png")
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            AppLogger.e(TAG, "decodeB64ToTempFile failed: ${e.message}")
            null
        }
    }

    private suspend fun pollAliyunTask(apiUrl: String, apiKey: String, secretKey: String, taskId: String): String? =
        withContext(Dispatchers.IO) {
            val baseUrl = try {
                val url = java.net.URL(apiUrl)
                "${url.protocol}://${url.host}"
            } catch (e: Exception) {
                apiUrl.substringBefore("/api/v1/")
            }
            val taskUrl = "${baseUrl}/api/v1/tasks/$taskId"
            val authHeaders = buildImageHeaders(FORMAT_ALIYUN_ASYNC, apiKey, secretKey)
            var attempts = 0
            while (attempts < 20) {
                delay(3000)
                attempts++
                try {
                    val requestBuilder = Request.Builder()
                        .url(taskUrl)
                        .get()
                    authHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
                    val request = requestBuilder.build()
                    pollClient.newCall(request).execute().use { response ->
                        val body = response.body?.string() ?: return@use null
                        val json = JSONObject(body)
                        val status = json.optJSONObject("output")?.optString("task_status", "") ?: ""
                        when (status) {
                            "SUCCEEDED" -> {
                                val results = json.optJSONObject("output")?.optJSONArray("results")
                                return@withContext results?.optJSONObject(0)?.optString("url", "")?.ifBlank { null }
                            }
                            "FAILED" -> {
                                val msg = json.optJSONObject("output")?.optString("message", "Task failed")
                                AppLogger.e(TAG, "Aliyun task failed: $msg")
                                return@withContext null
                            }
                            else -> {
                                AppLogger.w(TAG, "Aliyun task status: $status, polling...")
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Poll aliyun task error: ${e.message}")
                }
            }
            null
        }

    private fun generateKlingJwt(apiKey: String): String? {
        try {
            val colonIdx = apiKey.indexOf(':')
            if (colonIdx < 1) {
                AppLogger.e(TAG, "Kling JWT: apiKey format should be AK:SK, got: ${apiKey.take(10)}...")
                return null
            }
            val ak = apiKey.substring(0, colonIdx)
            val sk = apiKey.substring(colonIdx + 1)
            val now = System.currentTimeMillis() / 1000
            val header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".toByteArray(Charsets.UTF_8))
            val payload = base64UrlEncode("{\"iss\":\"$ak\",\"exp\":${now + 1800},\"nbf\":${now - 5}}".toByteArray(Charsets.UTF_8))
            val signInput = "$header.$payload"
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(sk.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val signature = base64UrlEncode(mac.doFinal(signInput.toByteArray(Charsets.UTF_8)))
            return "$signInput.$signature"
        } catch (e: Exception) {
            AppLogger.e(TAG, "Kling JWT generation failed: ${e.message}")
            return null
        }
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
    }

    private suspend fun pollKlingTask(apiUrl: String, apiKey: String, taskId: String): String? =
        withContext(Dispatchers.IO) {
            val taskUrl = "${apiUrl}/${taskId}"
            val authHeaders = buildImageHeaders(FORMAT_KLING, apiKey)
            var attempts = 0
            while (attempts < 20) {
                delay(3000)
                attempts++
                try {
                    val requestBuilder = Request.Builder()
                        .url(taskUrl)
                        .get()
                    authHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
                    val request = requestBuilder.build()
                    pollClient.newCall(request).execute().use { response ->
                        val body = response.body?.string() ?: return@use null
                        val json = JSONObject(body)
                        val pollCode = json.optInt("code", -1)
                        if (pollCode != 0 && pollCode != -1) {
                            val msg = json.optString("message", "Task query error")
                            AppLogger.e(TAG, "Kling poll error: code=$pollCode, message=$msg")
                            return@withContext null
                        }
                        val data = json.optJSONObject("data")
                        val status = data?.optString("task_status", "") ?: ""
                        when (status) {
                            "succeed" -> {
                                val taskResult = data.optJSONObject("task_result")
                                val images = taskResult?.optJSONArray("images")
                                return@withContext images?.optJSONObject(0)?.optString("url", "")?.ifBlank { null }
                            }
                            "failed" -> {
                                val msg = data?.optString("task_status_msg", "Task failed")
                                AppLogger.e(TAG, "Kling task failed: $msg")
                                return@withContext null
                            }
                            else -> {
                                AppLogger.w(TAG, "Kling task status: $status, polling...")
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Poll kling task error: ${e.message}")
                }
            }
            null
        }

    fun buildTtsRequest(
        formatType: String,
        model: String,
        text: String,
        voice: String
    ): JSONObject {
        return when (formatType) {
            FORMAT_FISH_AUDIO -> JSONObject().apply {
                put("text", text)
                if (voice.isNotBlank()) {
                    put("reference_id", voice)
                }
                put("format", "mp3")
            }
            FORMAT_MIMO -> {
                // MiMoTTS 使用 Chat Completions 格式：messages + audio 配置
                // user消息 = 风格指令（可选），assistant消息 = 待合成文本
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", text)
                    })
                }
                JSONObject().apply {
                    put("model", model)
                    put("messages", messages)
                    put("audio", JSONObject().apply {
                        put("format", "mp3")
                        if (voice.isNotBlank()) put("voice", voice)
                    })
                }
            }
            else -> JSONObject().apply {
                put("model", model)
                put("input", text)
                put("voice", voice)
            }
        }
    }

    fun buildTtsHeaders(
        formatType: String,
        apiKey: String,
        model: String
    ): Map<String, String> {
        return if (formatType == FORMAT_MIMO) {
            // MiMoTTS 使用 api-key 头，非 Bearer token
            mapOf(
                "api-key" to apiKey,
                "Content-Type" to "application/json"
            )
        } else {
            mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            )
        }
    }

    fun buildAsrRequestFields(
        formatType: String,
        model: String
    ): Map<String, String> {
        val fields = mutableMapOf("model" to model)
        if (!model.contains("sense", ignoreCase = true)) {
            fields["language"] = "zh"
        }
        return fields
    }

    fun parseAsrResponse(
        formatType: String,
        responseBody: String
    ): String {
        val json = JSONObject(responseBody)
        val text = json.optString("text", "")
        if (text.isNotBlank()) return text
        val choices = json.optJSONArray("choices")
        return choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "") ?: ""
    }

    /**
     * 解析 MiMoTTS 响应：从 choices[0].message.audio.data 提取 Base64 音频数据
     * 返回 Base64 字符串，如果解析失败返回 null
     */
    fun parseMimoResponse(responseBody: String): String? {
        return try {
            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            val message = choices?.optJSONObject(0)?.optJSONObject("message") ?: return null
            val audio = message.optJSONObject("audio") ?: return null
            if (!audio.has("data") || audio.isNull("data")) return null
            audio.getString("data")
        } catch (e: Exception) {
            AppLogger.e(TAG, "parseMimoResponse 失败: ${e.message}")
            null
        }
    }

    /** MiMoTTS 预置音色列表 (name -> voiceId) */
    val MIMO_VOICES = mapOf(
        "MiMo-默认" to "mimo_default",
        "冰糖" to "冰糖",
        "茉莉" to "茉莉",
        "苏打" to "苏打",
        "白桦" to "白桦",
        "Mia" to "Mia",
        "Chloe" to "Chloe",
        "Milo" to "Milo",
        "Dean" to "Dean"
    )
}
