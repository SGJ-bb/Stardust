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
    const val FORMAT_FISH_AUDIO = "fish_audio"

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
                val firstImg = imagesArr?.optJSONObject(0)
                val url = firstImg?.optString("url", "")?.ifBlank { null }
                if (url != null) return url
                val b64 = firstImg?.optString("b64_json", "")?.ifBlank { null }
                if (b64 != null) {
                    return decodeB64ToTempFile(b64)
                }
                null
            }
            FORMAT_ALIYUN_ASYNC -> {
                val json = JSONObject(responseBody)
                val output = json.optJSONObject("output")
                val results = output?.optJSONArray("results")
                val resultUrl = results?.optJSONObject(0)?.optString("url", "")?.ifBlank { null }
                if (resultUrl != null) return resultUrl
                val taskId = output?.optString("task_id", "") ?: ""
                if (taskId.isNotBlank()) {
                    pollAliyunTask(apiUrl, apiKey, secretKey, taskId)
                } else null
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
            val pollClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val baseUrl = apiUrl.substringBefore("/api/v1/")
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
                    val response = pollClient.newCall(request).execute()
                    val body = response.body?.string() ?: continue
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
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Poll aliyun task error: ${e.message}")
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
        return mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )
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
}
