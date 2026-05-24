package com.aicompanion.voice

import android.content.Context
import android.media.MediaPlayer
import com.aicompanion.models.Emotion
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class TtsResult(
    val audioPath: String?,
    val audioUrl: String?,
    val success: Boolean,
    val error: String? = null
)

class TtsManager(private val context: Context) {

    companion object {
        private const val TAG = "TtsManager"
        const val ENGINE_CLOUD = "cloud"
        const val ENGINE_LOCAL = "local"
        const val ENGINE_AUTO = "auto"
        const val ENGINE_EDGE = "edge"
    }

    private val sm = SettingsManager(context)
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPath: String? = null
    private var onPlaybackCompleteListener: (() -> Unit)? = null

    val engineMode: String
        get() = sm.ttsEngineMode

    val isCloudConfigured: Boolean
        get() = sm.ttsApiUrl.isNotBlank() && sm.ttsApiKey.isNotBlank()

    suspend fun synthesize(text: String, emotion: Emotion = Emotion.NEUTRAL): TtsResult {
        if (text.isBlank()) return TtsResult(null, null, false, "文本为空")

        val mode = engineMode
        if (mode == ENGINE_EDGE) {
            return try {
                val audioDir = File(context.filesDir, "tts_audio")
                val voiceId = sm.ttsVoice.ifBlank { "zh-CN-XiaoxiaoNeural" }
                EdgeTtsEngine.synthesize(text, voiceId, audioDir, sm.ttsRate, sm.ttsPitch)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Edge TTS失败: ${e.message}")
                TtsResult(null, null, false, "Edge TTS失败: ${e.message}")
            }
        }

        if (mode == ENGINE_LOCAL || (mode == ENGINE_AUTO && !isCloudConfigured)) {
            return TtsResult(null, null, true)
        }

        return try {
            cloudSynthesize(text)
        } catch (e: Exception) {
            AppLogger.e(TAG, "云端TTS失败，回退本地: ${e.message}")
            if (mode == ENGINE_AUTO) {
                TtsResult(null, null, true)
            } else {
                TtsResult(null, null, false, "云端TTS失败: ${e.message}")
            }
        }
    }

    private suspend fun cloudSynthesize(text: String): TtsResult = withContext(Dispatchers.IO) {
        val ttsUrl = sm.ttsApiUrl
        val ttsKey = sm.ttsApiKey
        val ttsModel = sm.ttsModel
        var ttsVoice = sm.ttsVoice

        val isSiliconFlow = ttsUrl.contains("siliconflow", ignoreCase = true)
        val isCosyVoice = ttsModel.contains("CosyVoice", ignoreCase = true)
        if (isSiliconFlow && isCosyVoice && !ttsVoice.contains(":")) {
            val cosyVoiceNames = setOf("alex", "anna", "bella", "benjamin", "charles", "claire", "david", "diana")
            val voiceName = ttsVoice.lowercase()
            ttsVoice = if (voiceName in cosyVoiceNames) "$ttsModel:$voiceName" else "$ttsModel:alex"
        }

        val jsonBody = JSONObject().apply {
            put("model", ttsModel)
            put("input", text)
            put("voice", ttsVoice)
        }

        val jsonRequest = Request.Builder()
            .url(ttsUrl)
            .addHeader("Authorization", "Bearer $ttsKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val client = com.aicompanion.network.ApiClient.sharedClient
        val jsonResp = client.newCall(jsonRequest).execute()

        if (jsonResp.code == 422) {
            jsonResp.body?.close()
            AppLogger.d(TAG, "TTS JSON格式422，尝试multipart/form-data")

            val textBytes = text.toByteArray(Charsets.UTF_8)
            val textFileBody = textBytes.toRequestBody("text/plain".toMediaType(), 0, textBytes.size)

            val formBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "input.txt", textFileBody)
                .addFormDataPart("model", ttsModel)
                .addFormDataPart("voice", ttsVoice)
                .build()

            val formRequest = Request.Builder()
                .url(ttsUrl)
                .addHeader("Authorization", "Bearer $ttsKey")
                .post(formBody)
                .build()

            val formResp = client.newCall(formRequest).execute()
            return@withContext parseResponse(formResp)
        }

        if (!jsonResp.isSuccessful) {
            val errBody = jsonResp.body?.string()?.take(300) ?: "无响应体"
            return@withContext TtsResult(null, null, false, "HTTP ${jsonResp.code}: $errBody")
        }

        parseResponse(jsonResp)
    }

    private fun parseResponse(response: okhttp3.Response): TtsResult {
        if (!response.isSuccessful) {
            val errBody = response.body?.string()?.take(300) ?: "无响应体"
            return TtsResult(null, null, false, "HTTP ${response.code}: $errBody")
        }

        val contentType = response.header("Content-Type", "")
        val body = response.body ?: return TtsResult(null, null, false, "空响应")

        if (contentType?.contains("audio") == true || contentType?.contains("octet-stream") == true) {
            val extension = if (contentType.contains("wav")) "wav" else "mp3"
            val audioDir = File(context.filesDir, "tts_audio")
            if (!audioDir.exists()) audioDir.mkdirs()
            val audioFile = File(audioDir, "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension")
            body.byteStream().use { input ->
                java.io.FileOutputStream(audioFile).use { output ->
                    input.copyTo(output)
                }
            }
            return TtsResult(audioFile.absolutePath, null, true)
        }

        val bodyStr = body.string()
        try {
            val json = JSONObject(bodyStr)

            val audioUrl = json.optString("url", "")
            if (audioUrl.isNotBlank()) return TtsResult(null, audioUrl, true)

            val audioB64 = json.optString("audio", "")
            if (audioB64.isNotBlank()) {
                val audioBytes = android.util.Base64.decode(audioB64, android.util.Base64.DEFAULT)
                val audioDir = File(context.filesDir, "tts_audio")
                if (!audioDir.exists()) audioDir.mkdirs()
                val audioFile = File(audioDir, "${System.currentTimeMillis()}.mp3")
                java.io.FileOutputStream(audioFile).use { it.write(audioBytes) }
                return TtsResult(audioFile.absolutePath, null, true)
            }

            val dataArr = json.optJSONArray("data")
            if (dataArr != null && dataArr.length() > 0) {
                val firstData = dataArr.optJSONObject(0)
                val dataUrl = firstData?.optString("url", "") ?: ""
                val dataB64 = firstData?.optString("b64_json", "") ?: ""
                if (dataUrl.isNotBlank()) return TtsResult(null, dataUrl, true)
                if (dataB64.isNotBlank()) {
                    val audioBytes = android.util.Base64.decode(dataB64, android.util.Base64.DEFAULT)
                    val audioDir = File(context.filesDir, "tts_audio")
                    if (!audioDir.exists()) audioDir.mkdirs()
                    val audioFile = File(audioDir, "${System.currentTimeMillis()}.mp3")
                    java.io.FileOutputStream(audioFile).use { it.write(audioBytes) }
                    return TtsResult(audioFile.absolutePath, null, true)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "parseResponse JSON: ${e.message}")
        }

        return TtsResult(null, null, false, "未找到音频内容")
    }

    fun playAudio(path: String?, url: String?, onComplete: (() -> Unit)? = null) {
        stopPlayback()
        onPlaybackCompleteListener = onComplete
        try {
            mediaPlayer = MediaPlayer()
            when {
                !path.isNullOrBlank() -> {
                    currentlyPlayingPath = path
                    mediaPlayer?.setDataSource(path)
                }
                !url.isNullOrBlank() -> {
                    currentlyPlayingPath = url
                    mediaPlayer?.setDataSource(url)
                }
                else -> return
            }
            mediaPlayer?.prepareAsync()
            mediaPlayer?.setOnPreparedListener { mp ->
                mp.start()
            }
            mediaPlayer?.setOnCompletionListener { mp ->
                currentlyPlayingPath = null
                onPlaybackCompleteListener?.invoke()
                cleanupPlayer(mp)
            }
            mediaPlayer?.setOnErrorListener { mp, what, extra ->
                AppLogger.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                currentlyPlayingPath = null
                onPlaybackCompleteListener?.invoke()
                cleanupPlayer(mp)
                true
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "playAudio: ${e.message}")
            currentlyPlayingPath = null
            onPlaybackCompleteListener?.invoke()
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        currentlyPlayingPath = null
    }

    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

    val playingPath: String?
        get() = currentlyPlayingPath

    private fun cleanupPlayer(mp: MediaPlayer?) {
        try {
            mp?.let {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
        } catch (_: Exception) {}
        if (mp == mediaPlayer) {
            mediaPlayer = null
            currentlyPlayingPath = null
        }
    }

    fun cleanup() {
        stopPlayback()
        onPlaybackCompleteListener = null
    }

    fun cleanupOldAudio(maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        try {
            val audioDir = File(context.filesDir, "tts_audio")
            if (!audioDir.exists()) return
            val cutoff = System.currentTimeMillis() - maxAgeMs
            audioDir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "cleanupOldAudio: ${e.message}")
        }
    }
}
