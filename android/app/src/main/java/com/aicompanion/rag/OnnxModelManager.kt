package com.aicompanion.rag

import android.content.Context
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object OnnxModelManager {

    private const val TAG = "OnnxModelManager"

    // 默认下载地址
    private const val DEFAULT_MODEL_URL = "https://huggingface.co/BAAI/bge-small-zh-v1.5/resolve/main/onnx/model.onnx"
    private const val DEFAULT_VOCAB_URL = "https://huggingface.co/BAAI/bge-small-zh-v1.5/resolve/main/vocab.txt"

    // 最小文件大小（字节），用于校验下载完整性
    private const val MIN_MODEL_SIZE = 5_000_000L  // 5MB
    private const val MIN_VOCAB_SIZE = 100_000L    // 100KB

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var isDownloading = false

    fun getModelDir(context: Context): File = File(context.filesDir, "onnx")

    fun getModelFile(context: Context): File = File(getModelDir(context), "bge-small-zh-v1.5.onnx")

    fun getVocabFile(context: Context): File = File(getModelDir(context), "vocab.txt")

    fun isModelReady(context: Context): Boolean {
        val model = getModelFile(context)
        val vocab = getVocabFile(context)
        return model.exists() && model.length() >= MIN_MODEL_SIZE &&
               vocab.exists() && vocab.length() >= MIN_VOCAB_SIZE
    }

    fun getModelSizeMB(context: Context): Long {
        val model = getModelFile(context)
        return if (model.exists()) model.length() / (1024 * 1024) else 0L
    }

    fun getDownloading(): Boolean = isDownloading

    fun deleteModel(context: Context) {
        val dir = getModelDir(context)
        if (dir.exists()) {
            dir.walkTopDown().filter { it.isFile }.forEach { it.delete() }
            dir.delete()
        }
        AppLogger.i(TAG, "ONNX model deleted")
    }

    interface DownloadCallback {
        fun onProgress(downloadedBytes: Long, totalBytes: Long, percent: Int)
        fun onComplete(success: Boolean, error: String?)
    }

    suspend fun downloadModel(
        context: Context,
        modelUrl: String = DEFAULT_MODEL_URL,
        vocabUrl: String = DEFAULT_VOCAB_URL,
        callback: DownloadCallback? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (isDownloading) {
            callback?.onComplete(false, "Already downloading")
            return@withContext false
        }

        isDownloading = true
        try {
            val dir = getModelDir(context)
            dir.mkdirs()

            // 下载词表文件（较小，先下载）
            AppLogger.i(TAG, "Downloading vocab.txt...")
            val vocabSuccess = downloadFile(vocabUrl, getVocabFile(context), null)
            if (!vocabSuccess) {
                callback?.onComplete(false, "Failed to download vocab.txt")
                return@withContext false
            }
            // 校验词表文件大小
            val vocabFile = getVocabFile(context)
            if (vocabFile.length() < MIN_VOCAB_SIZE) {
                vocabFile.delete()
                callback?.onComplete(false, "Vocab file too small (${vocabFile.length()} bytes), download may be corrupted")
                return@withContext false
            }
            AppLogger.i(TAG, "Vocab downloaded: ${vocabFile.length()} bytes")

            // 下载模型文件
            AppLogger.i(TAG, "Downloading model.onnx...")
            val modelSuccess = downloadFile(modelUrl, getModelFile(context)) { downloaded, total ->
                val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
                callback?.onProgress(downloaded, total, percent)
            }
            if (!modelSuccess) {
                callback?.onComplete(false, "Failed to download model file")
                return@withContext false
            }

            // 校验
            val modelFile = getModelFile(context)
            if (modelFile.length() < MIN_MODEL_SIZE) {
                modelFile.delete()
                callback?.onComplete(false, "Model file too small (${modelFile.length()} bytes), download may be corrupted")
                return@withContext false
            }

            AppLogger.i(TAG, "Model downloaded successfully: ${modelFile.length() / (1024*1024)}MB")
            callback?.onComplete(true, null)
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Download failed: ${e.message}")
            callback?.onComplete(false, e.message)
            false
        } finally {
            isDownloading = false
        }
    }

    private fun downloadFile(
        url: String,
        destFile: File,
        progressCallback: ((Long, Long) -> Unit)?
    ): Boolean {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                AppLogger.e(TAG, "Download failed: HTTP ${response.code} for $url")
                response.close()
                return false
            }

            val body = response.body ?: return false
            val contentLength = body.contentLength()

            val tempFile = File(destFile.parentFile, destFile.name + ".tmp")
            try {
                tempFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var lastReportTime = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (progressCallback != null && (now - lastReportTime > 500 || read < 8192)) {
                                progressCallback(downloaded, contentLength)
                                lastReportTime = now
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // 下载中断时清理临时文件
                tempFile.delete()
                throw e
            } finally {
                response.close()
            }

            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }

            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Download file error: ${e.message}")
            false
        }
    }
}
