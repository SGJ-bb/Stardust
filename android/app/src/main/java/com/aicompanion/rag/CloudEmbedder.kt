package com.aicompanion.rag

import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CloudEmbedder(
    private val apiUrl: String,
    private val apiKey: String,
    private val model: String = "text-embedding-3-small"
) : RagEmbedder {

    private var cachedDim: Int = 1536

    override fun dimension(): Int = cachedDim

    override suspend fun embed(texts: List<String>): List<FloatArray> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        if (apiUrl.isBlank()) {
            AppLogger.e("CloudEmbedder", "API URL is blank, falling back to empty vectors")
            return@withContext texts.map { FloatArray(0) }
        }
        return@withContext try {
            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotBlank()) {
                    conn.setRequestProperty("Authorization", "Bearer $apiKey")
                }
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000

                val body = JSONObject().apply {
                    put("model", model)
                    put("input", JSONArray(texts))
                }

                conn.outputStream.use { os ->
                    os.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                    AppLogger.e("CloudEmbedder", "API error: $err")
                    return@withContext texts.map { FloatArray(0) }
                }

                val response = conn.inputStream.bufferedReader().readText()
                parseEmbeddingResponse(response)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            AppLogger.e("CloudEmbedder", "embed failed: ${e.message}")
            texts.map { FloatArray(0) }
        }
    }

    override suspend fun embedSingle(text: String): FloatArray {
        val results = embed(listOf(text))
        return results.firstOrNull() ?: FloatArray(0)
    }

    private fun parseEmbeddingResponse(response: String): List<FloatArray> {
        val json = JSONObject(response)
        val dataArray = json.getJSONArray("data")
        val results = mutableListOf<FloatArray>()

        for (i in 0 until dataArray.length()) {
            val item = dataArray.getJSONObject(i)
            val embeddingArr = item.getJSONArray("embedding")
            val vec = FloatArray(embeddingArr.length())
            for (j in 0 until embeddingArr.length()) {
                vec[j] = embeddingArr.getDouble(j).toFloat()
            }
            results.add(vec)
            if (i == 0) {
                cachedDim = vec.size
            }
        }

        return results
    }
}
