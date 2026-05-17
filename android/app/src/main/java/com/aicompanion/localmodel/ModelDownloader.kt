package com.aicompanion.localmodel

import android.content.Context
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadProgress(
    val modelId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val percentage: Int,
    val isComplete: Boolean
)

class ModelDownloader(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloader"
        private const val BUFFER_SIZE = 8192
    }

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val activeDownloads = mutableMapOf<String, Boolean>()

    suspend fun downloadModel(
        modelInfo: ModelInfo,
        progressCallback: ((DownloadProgress) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val url = modelInfo.downloadUrl
        if (url.isNullOrBlank()) {
            AppLogger.e(TAG, "downloadModel: no download URL for ${modelInfo.id}")
            return@withContext false
        }

        activeDownloads[modelInfo.id] = true

        try {
            val request = okhttp3.Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                AppLogger.e(TAG, "downloadModel: HTTP ${response.code}")
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val totalBytes = body.contentLength()
            val modelsDir = File(context.getDir("models", Context.MODE_PRIVATE), modelInfo.fileName)

            var downloadedBytes = 0L
            val inputStream = body.byteStream()
            val outputStream = modelsDir.outputStream()
            val buffer = ByteArray(BUFFER_SIZE)

            inputStream.use { input ->
                outputStream.use { output ->
                    while (true) {
                        if (activeDownloads[modelInfo.id] != true) {
                            AppLogger.d(TAG, "downloadModel: cancelled ${modelInfo.id}")
                            modelsDir.delete()
                            return@withContext false
                        }

                        val read = input.read(buffer)
                        if (read == -1) break

                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        if (totalBytes > 0) {
                            val percentage = ((downloadedBytes * 100) / totalBytes).toInt()
                            progressCallback?.invoke(DownloadProgress(
                                modelId = modelInfo.id,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                percentage = percentage,
                                isComplete = false
                            ))
                        }
                    }
                    output.flush()
                }
            }

            if (modelInfo.md5 != null) {
                val actualMd5 = calculateMd5(modelsDir)
                if (actualMd5 != modelInfo.md5) {
                    AppLogger.e(TAG, "downloadModel: MD5 mismatch for ${modelInfo.id}")
                    modelsDir.delete()
                    return@withContext false
                }
            }

            progressCallback?.invoke(DownloadProgress(
                modelId = modelInfo.id,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                percentage = 100,
                isComplete = true
            ))

            AppLogger.d(TAG, "downloadModel: ${modelInfo.id} downloaded successfully")
            return@withContext true
        } catch (e: Exception) {
            AppLogger.e(TAG, "downloadModel failed: ${e.message}")
            return@withContext false
        } finally {
            activeDownloads.remove(modelInfo.id)
        }
    }

    fun cancelDownload(modelId: String) {
        activeDownloads[modelId] = false
    }

    fun isModelDownloaded(modelInfo: ModelInfo): Boolean {
        if (modelInfo.builtIn) return true
        val file = File(context.getDir("models", Context.MODE_PRIVATE), modelInfo.fileName)
        return file.exists() && file.length() > 0
    }

    fun deleteModel(modelInfo: ModelInfo): Boolean {
        if (modelInfo.builtIn) return false
        return try {
            val file = File(context.getDir("models", Context.MODE_PRIVATE), modelInfo.fileName)
            file.delete()
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteModel failed: ${e.message}")
            false
        }
    }

    fun getDownloadedModelSize(modelInfo: ModelInfo): Long {
        if (modelInfo.builtIn) return 0
        val file = File(context.getDir("models", Context.MODE_PRIVATE), modelInfo.fileName)
        return if (file.exists()) file.length() else 0
    }

    private fun calculateMd5(file: File): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        val inputStream = file.inputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        inputStream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
