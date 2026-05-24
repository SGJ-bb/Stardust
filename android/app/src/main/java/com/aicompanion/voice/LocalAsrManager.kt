package com.aicompanion.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

interface AsrListener {
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onError(error: String)
    fun onReady()
    fun onEndOfSpeech()
}

class LocalAsrManager(private val context: Context) {

    companion object {
        private const val TAG = "LocalAsrManager"
        const val MODE_SYSTEM = "system"
        const val MODE_SHERPA = "sherpa"
        const val MODE_CLOUD = "cloud"

        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_RECORDING_MS = 30000
    }

    private val sm = SettingsManager(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: AsrListener? = null
    private var isListening = false

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingBuffer = mutableListOf<Byte>()

    val mode: String
        get() = sm.asrMode

    val isCloudAvailable: Boolean
        get() = sm.asrApiUrl.isNotBlank() && sm.asrApiKey.isNotBlank()

    val isSherpaAvailable: Boolean
        get() = checkSherpaAvailable()

    val isSystemAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun setListener(l: AsrListener) {
        listener = l
    }

    fun startListening() {
        if (isListening) return
        isListening = true

        val currentMode = mode
        if (currentMode == MODE_CLOUD && isCloudAvailable) {
            startCloudListening()
        } else if (currentMode == MODE_SHERPA && isSherpaAvailable) {
            startSherpaListening()
        } else if (currentMode == MODE_CLOUD && !isCloudAvailable) {
            if (isSystemAvailable) {
                AppLogger.w(TAG, "云端ASR未配置，回退系统ASR")
                startSystemListening()
            } else {
                listener?.onError("云端ASR未配置（需填写API地址和密钥），系统ASR也不可用")
                isListening = false
            }
        } else if (isSystemAvailable) {
            startSystemListening()
        } else {
            listener?.onError("无可用的语音识别引擎，请在设置中配置云端ASR")
            isListening = false
        }
    }

    fun stopListening() {
        if (!isListening) return
        if (isRecording) {
            isRecording = false
            return
        }
        isListening = false
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            AppLogger.e(TAG, "stopListening: ${e.message}")
        }
    }

    fun cancel() {
        isListening = false
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
    }

    fun cleanup() {
        isListening = false
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    private fun startCloudListening() {
        listener?.onReady()
        isRecording = true
        recordingBuffer.clear()

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            listener?.onError("无法初始化音频录制")
            isListening = false
            isRecording = false
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )
        } catch (e: SecurityException) {
            listener?.onError("麦克风权限未授予")
            isListening = false
            isRecording = false
            return
        } catch (e: Exception) {
            listener?.onError("音频录制初始化失败: ${e.message}")
            isListening = false
            isRecording = false
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            listener?.onError("音频录制器初始化失败")
            isListening = false
            isRecording = false
            return
        }

        scope.launch {
            try {
                audioRecord?.startRecording()
                AppLogger.d(TAG, "Cloud ASR: 开始录音")

                val data = ByteArray(bufferSize)
                val startTime = System.currentTimeMillis()

                while (isRecording && isListening) {
                    val read = audioRecord?.read(data, 0, data.size) ?: 0
                    if (read > 0) {
                        for (i in 0 until read) {
                            recordingBuffer.add(data[i])
                        }
                    }
                    if (System.currentTimeMillis() - startTime > MAX_RECORDING_MS) {
                        AppLogger.d(TAG, "Cloud ASR: 达到最大录音时长")
                        break
                    }
                }

                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                isRecording = false
                listener?.onEndOfSpeech()

                if (recordingBuffer.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        listener?.onError("未录制到音频")
                        isListening = false
                    }
                    return@launch
                }

                val pcmBytes = recordingBuffer.toByteArray()
                AppLogger.d(TAG, "Cloud ASR: 录音完成, ${pcmBytes.size} bytes")

                val wavBytes = pcmToWav(pcmBytes, SAMPLE_RATE)

                val text = callCloudAsr(wavBytes)

                withContext(Dispatchers.Main) {
                    isListening = false
                    if (text.isNotBlank()) {
                        listener?.onFinalResult(text)
                    } else {
                        listener?.onError("语音识别无结果")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Cloud ASR error: ${e.message}")
                withContext(Dispatchers.Main) {
                    isListening = false
                    isRecording = false
                    listener?.onError("云端ASR失败: ${e.message}")
                }
            }
        }
    }

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize

        val wav = ByteArray(44 + dataSize)
        val buf = java.nio.ByteBuffer.wrap(wav).order(java.nio.ByteOrder.LITTLE_ENDIAN)

        buf.put(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
        buf.putInt(totalSize)
        buf.put(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))
        buf.put(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(bitsPerSample.toShort())
        buf.put(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
        buf.putInt(dataSize)
        buf.put(pcmData)

        return wav
    }

    private suspend fun callCloudAsr(wavBytes: ByteArray): String = withContext(Dispatchers.IO) {
        try {
            val apiUrl = sm.asrApiUrl
            val apiKey = sm.asrApiKey
            val model = sm.asrModel.ifBlank { "whisper-1" }

            val boundary = "----FormBoundary${System.currentTimeMillis()}"
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val output = DataOutputStream(conn.outputStream)

            output.writeBytes("--$boundary\r\n")
            output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n")
            output.writeBytes("Content-Type: audio/wav\r\n\r\n")
            output.write(wavBytes)
            output.writeBytes("\r\n")

            output.writeBytes("--$boundary\r\n")
            output.writeBytes("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
            output.writeBytes("$model\r\n")

            output.writeBytes("--$boundary\r\n")
            output.writeBytes("Content-Disposition: form-data; name=\"language\"\r\n\r\n")
            output.writeBytes("zh\r\n")

            output.writeBytes("--$boundary--\r\n")
            output.flush()
            output.close()

            val code = conn.responseCode
            if (code != 200) {
                val errBody = try {
                    BufferedReader(InputStreamReader(conn.errorStream)).readText()
                } catch (_: Exception) { "" }
                AppLogger.e(TAG, "Cloud ASR HTTP $code: $errBody")
                return@withContext ""
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            val json = JSONObject(body)

            val text = json.optString("text", "")
            if (text.isNotBlank()) return@withContext text

            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                return@withContext choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
            }

            ""
        } catch (e: Exception) {
            AppLogger.e(TAG, "callCloudAsr: ${e.message}")
            ""
        }
    }

    private fun startSystemListening() {
        try {
            if (speechRecognizer != null) {
                speechRecognizer?.destroy()
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            AppLogger.e(TAG, "createSpeechRecognizer: ${e.message}")
            listener?.onError("无法初始化语音识别: ${e.message}")
            isListening = false
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listener?.onReady()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                listener?.onEndOfSpeech()
            }
            override fun onError(error: Int) {
                isListening = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
                    SpeechRecognizer.ERROR_AUDIO -> "音频错误"
                    SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                    SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                    else -> "识别错误($error)"
                }
                listener?.onError(msg)
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = if (!matches.isNullOrEmpty()) matches[0] else ""
                if (text.isNotBlank()) {
                    listener?.onFinalResult(text)
                } else {
                    listener?.onError("未识别到内容")
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    listener?.onPartialResult(matches[0])
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "startListening: ${e.message}")
            listener?.onError("启动语音识别失败: ${e.message}")
            isListening = false
        }
    }

    private fun startSherpaListening() {
        AppLogger.w(TAG, "Sherpa-ONNX ASR 尚未集成，回退系统ASR")
        startSystemListening()
    }

    private fun checkSherpaAvailable(): Boolean {
        try {
            val modelDir = java.io.File(context.filesDir, "sherpa-onnx/models/sense-voice")
            return modelDir.exists() && modelDir.listFiles()?.isNotEmpty() == true
        } catch (_: Exception) {
            return false
        }
    }
}
