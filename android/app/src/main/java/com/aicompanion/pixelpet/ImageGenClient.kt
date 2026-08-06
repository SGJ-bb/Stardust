package com.aicompanion.pixelpet

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 图片生成API客户端
 *
 * 支持:
 * - OpenAI DALL-E API
 * - Stable Diffusion WebUI / ComfyUI 兼容API
 * - 通用 SD API (txt2img endpoint)
 *
 * 与PC端 generator.ts 的适配器设计保持一致
 */
class ImageGenClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        const val TAG = "ImageGenClient"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * 生成单张图片，返回原始字节
     */
    suspend fun generate(
        config: ImageGenConfig,
        prompt: String,
    ): ByteArray = withContext(Dispatchers.IO) {
        when (config.provider.lowercase()) {
            "openai" -> generateOpenAI(config, prompt)
            else -> generateSDCompatible(config, prompt)
        }
    }

    // ═════════ OpenAI DALL-E ═════════

    private fun generateOpenAI(config: ImageGenConfig, prompt: String): ByteArray {
        val url = config.apiUrl.ifBlank { "https://api.openai.com/v1/images/generations" }
        val model = config.model.ifBlank { "dall-e-3" }

        val body = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            put("n", 1)
            put("size", "1024x1024")
            put("response_format", "b64_json")
            put("quality", "standard")
            put("style", "natural")
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("OpenAI API error (${response.code}): ${response.body?.string()}")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        val json = JSONObject(responseBody)
        val b64Json = json.getJSONArray("data").getJSONObject(0).getString("b64_json")

        return Base64.decode(b64Json, Base64.DEFAULT)
    }

    // ═════════ SD WebUI / ComfyUI 兼容 ═════════

    private fun generateSDCompatible(config: ImageGenConfig, prompt: String): ByteArray {
        val baseUrl = config.apiUrl.trimEnd('/')
        val apiUrl = "$baseUrl/sdapi/v1/txt2img"

        val sizeParts = config.size.split("x")
        val width = (sizeParts.getOrNull(0)?.toIntOrNull() ?: 128) * 2  // SD需要更大尺寸
        val height = (sizeParts.getOrNull(1)?.toIntOrNull() ?: 128) * 2

        val body = JSONObject().apply {
            put("prompt", prompt)
            put("negative_prompt", "")
            put("width", width)
            put("height", height)
            put("steps", config.steps)
            put("cfg_scale", config.cfgScale.toDouble())
            put("sampler_name", "euler_ancestral")
            put("seed", -1)
            put("batch_size", 1)
        }.toString()

        val builder = Request.Builder()
            .url(apiUrl)
            .post(body.toRequestBody(JSON_MEDIA))

        if (config.apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer ${config.apiKey}")
        }

        val request = builder.build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("SD API error (${response.code}): ${response.body?.string()}")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        val json = JSONObject(responseBody)
        val images = json.getJSONArray("images")
        if (images.length() == 0) throw Exception("No images in SD response")

        val b64 = images.getString(0)
        val cleanB64 = if (b64.contains(",")) b64.substringAfter(",") else b64

        return Base64.decode(cleanB64, Base64.DEFAULT)
    }

    /** 测试API连接是否正常 */
    suspend fun testConnection(config: ImageGenConfig): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (config.apiUrl.isBlank()) return@withContext Result.failure(Exception("API URL 未配置"))
            if (config.apiKey.isBlank()) return@withContext Result.failure(Exception("API Key 未配置"))

            when (config.provider.lowercase()) {
                "openai" -> {
                    val body = JSONObject().apply {
                        put("model", config.model.ifBlank { "dall-e-3" })
                        put("prompt", "a single dot")
                        put("n", 1)
                        put("size", "256x256")
                        put("response_format", "b64_json")
                    }.toString()

                    val req = Request.Builder()
                        .url(config.apiUrl.ifBlank { "https://api.openai.com/v1/images/generations" })
                        .addHeader("Authorization", "Bearer ${config.apiKey}")
                        .post(body.toRequestBody(JSON_MEDIA))
                        .build()

                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        Result.success("连接成功！OpenAI API响应正常")
                    } else {
                        Result.failure(Exception("API 返回错误 (${resp.code})"))
                    }
                }
                else -> {
                    val baseUrl = config.apiUrl.trimEnd('/')
                    val body = JSONObject().apply {
                        put("prompt", "test")
                        put("width", 64)
                        put("height", 64)
                        put("steps", 1)
                    }.toString()

                    val builder = Request.Builder()
                        .url("$baseUrl/sdapi/v1/txt2img")
                        .post(body.toRequestBody(JSON_MEDIA))

                    if (config.apiKey.isNotBlank()) {
                        builder.addHeader("Authorization", "Bearer ${config.apiKey}")
                    }

                    val resp = client.newCall(builder.build()).execute()
                    if (resp.isSuccessful) {
                        Result.success("连接成功！SD API响应正常")
                    } else {
                        Result.failure(Exception("API 返回错误 (${resp.code})"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
