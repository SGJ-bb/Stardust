package com.aicompanion.voice

import android.content.Context
import android.media.MediaPlayer
import com.aicompanion.models.Emotion
import com.aicompanion.network.ProviderAdapter
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
        return synthesizeWithVoice(text, null, emotion)
    }

    suspend fun synthesizeWithVoice(text: String, overrideVoice: String? = null, emotion: Emotion = Emotion.NEUTRAL): TtsResult {
        if (text.isBlank()) return TtsResult(null, null, false, "文本为空")

        val mode = engineMode
        if (mode == ENGINE_EDGE) {
            return try {
                val audioDir = File(context.filesDir, "tts_audio")
                val voiceId = overrideVoice ?: sm.ttsVoice.ifBlank { "zh-CN-XiaoxiaoNeural" }
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
            cloudSynthesize(text, overrideVoice)
        } catch (e: Exception) {
            AppLogger.e(TAG, "云端TTS失败，回退本地: ${e.message}")
            if (mode == ENGINE_AUTO) {
                TtsResult(null, null, true)
            } else {
                TtsResult(null, null, false, "云端TTS失败: ${e.message}")
            }
        }
    }

    private suspend fun cloudSynthesize(text: String, overrideVoice: String? = null): TtsResult = withContext(Dispatchers.IO) {
        val ttsUrl = sm.ttsApiUrl
        val ttsKey = sm.ttsApiKey
        val ttsModel = sm.ttsModel
        var ttsVoice = overrideVoice ?: sm.ttsVoiceName.ifBlank { sm.ttsVoice }
        val formatType = com.aicompanion.settings.ServicePresets.findTtsPreset(sm.ttsProvider).formatType
        AppLogger.i(TAG, "TTS云端合成开始: text=${text.take(30)}, model=$ttsModel, voice=$ttsVoice, formatType=$formatType")

        val isSiliconFlow = ttsUrl.contains("siliconflow", ignoreCase = true)
        val isCosyVoice = ttsModel.contains("CosyVoice", ignoreCase = true)
        if (isSiliconFlow && isCosyVoice && !ttsVoice.contains(":")) {
            val cosyVoiceNames = setOf("alex", "anna", "bella", "benjamin", "charles", "claire", "david", "diana")
            val voiceName = ttsVoice.lowercase()
            ttsVoice = if (voiceName in cosyVoiceNames) "$ttsModel:$voiceName" else "$ttsModel:alex"
        }

        val jsonBody = ProviderAdapter.buildTtsRequest(formatType, ttsModel, text, ttsVoice)
        val headers = ProviderAdapter.buildTtsHeaders(formatType, ttsKey, ttsModel)
        val requestBuilder = Request.Builder()
            .url(ttsUrl)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val jsonRequest = requestBuilder.build()

        val client = com.aicompanion.network.ApiClient.sharedClient
        client.newCall(jsonRequest).execute().use { jsonResp ->

        if (jsonResp.code == 422 && formatType != com.aicompanion.network.ProviderAdapter.FORMAT_MIMO) {
            jsonResp.body?.close()
            AppLogger.w(TAG, "TTS请求422错误，可能是参数格式不匹配。常见原因: 1)voice名称不正确 2)模型不支持该voice 3)API格式不compatible")
            AppLogger.w(TAG, "TTS JSON格式422，尝试替换voice字段名")

            val fallbackBody = org.json.JSONObject(jsonBody.toString()).apply {
                if (has("voice")) {
                    val voiceVal = optString("voice", "")
                    put("voice_name", voiceVal)
                    remove("voice")
                }
            }
            val fallbackRequest = Request.Builder()
                .url(ttsUrl)
                .addHeader("Authorization", "Bearer $ttsKey")
                .addHeader("Content-Type", "application/json")
                .post(fallbackBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(fallbackRequest).execute().use { fallbackResp ->
                if (fallbackResp.isSuccessful) {
                    return@withContext parseResponse(fallbackResp)
                }
                fallbackResp.body?.close()
                val errBody = fallbackResp.body?.string()?.take(300) ?: "无响应体"
                return@withContext TtsResult(null, null, false, "HTTP ${fallbackResp.code}: $errBody")
            }
        }

        if (!jsonResp.isSuccessful) {
            AppLogger.e(TAG, "TTS请求失败: HTTP ${jsonResp.code}, 常见原因: 1)API Key无效 2)余额不足 3)URL错误 4)模型名错误")
            val errBody = jsonResp.body?.string()?.take(300) ?: "无响应体"
            return@withContext TtsResult(null, null, false, "HTTP ${jsonResp.code}: $errBody")
        }

        return@withContext parseResponse(jsonResp, formatType)
        }
    }

    private fun parseResponse(response: okhttp3.Response, formatType: String = "openai"): TtsResult {
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
            // MiMoTTS 特殊解析：choices[0].message.audio.data (Base64)
            if (formatType == com.aicompanion.network.ProviderAdapter.FORMAT_MIMO) {
                val mimoB64 = com.aicompanion.network.ProviderAdapter.parseMimoResponse(bodyStr)
                if (!mimoB64.isNullOrBlank()) {
                    val audioBytes = android.util.Base64.decode(mimoB64, android.util.Base64.DEFAULT)
                    val audioDir = File(context.filesDir, "tts_audio")
                    if (!audioDir.exists()) audioDir.mkdirs()
                    val audioFile = File(audioDir, "${System.currentTimeMillis()}_mimo.mp3")
                    java.io.FileOutputStream(audioFile).use { it.write(audioBytes) }
                    return TtsResult(audioFile.absolutePath, null, true)
                }
                AppLogger.w(TAG, "MiMoTTS 响应中未找到音频数据")
                return TtsResult(null, null, false, "MiMoTTS 响应中未找到音频数据")
            }

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
        val listener = onPlaybackCompleteListener
        onPlaybackCompleteListener = null
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        currentlyPlayingPath = null
        listener?.invoke()
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
