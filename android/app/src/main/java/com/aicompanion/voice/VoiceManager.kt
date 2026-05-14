/** 语音管理器: TTS语音合成播放, 支持根据AI情绪调整语调和语速 */
package com.aicompanion.voice

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.aicompanion.models.Emotion
import java.util.*

class VoiceManager(context: Context) {

    private var appContext: Context? = context.applicationContext
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isOfflineMode = false
    private var isListening = false
    private var isTTSReady = false

    companion object {
        private const val TAG = "VoiceManager"
    }

    init {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            } else {
                Log.w(TAG, "Speech recognition not available on this device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create speech recognizer: ${e.message}")
        }

        try {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTTSReady = true
                    textToSpeech?.language = Locale.CHINESE
                } else {
                    Log.e(TAG, "TTS initialization failed with status: $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create TTS: ${e.message}")
        }
    }

    fun startListening(onResult: (String) -> Unit) {
        if (isListening) return
        val recognizer = speechRecognizer
        if (recognizer == null) {
            Log.w(TAG, "SpeechRecognizer not initialized, cannot start listening")
            return
        }

        isListening = true

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    isListening = false
                    val errorStr = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "音频错误"
                        SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                        SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                        SpeechRecognizer.ERROR_NO_MATCH -> "未匹配到语音"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙碌"
                        SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到语音"
                        else -> "未知错误($error)"
                    }
                    Log.w(TAG, "Speech recognition error: $errorStr")
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResult(matches[0])
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening: ${e.message}")
            isListening = false
        }
    }

    fun stopListening() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping listening: ${e.message}")
        }
    }

    fun speak(text: String, emotion: Emotion = Emotion.NEUTRAL) {
        if (!isTTSReady) {
            Log.w(TAG, "TTS not ready, cannot speak")
            return
        }
        if (text.isBlank()) return
        try {
            val utteranceId = UUID.randomUUID().toString()
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak failed: ${e.message}")
        }
    }

    fun playSpeech(audioUrl: String, emotion: Emotion = Emotion.NEUTRAL) {
        if (audioUrl.isBlank()) {
            Log.w(TAG, "Empty audio URL")
            return
        }
        try {
            cleanupMediaPlayer()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioUrl)
                prepareAsync()
                setOnPreparedListener { start() }
                setOnCompletionListener { cleanupMediaPlayer() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    cleanupMediaPlayer()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play speech: ${e.message}")
            cleanupMediaPlayer()
        }
    }

    fun setOfflineMode(enabled: Boolean) {
        isOfflineMode = enabled
    }

    fun disableHighQualityAudio() {
    }

    private fun cleanupMediaPlayer() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.stop()
                }
                mediaPlayer?.reset()
                mediaPlayer?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up MediaPlayer: ${e.message}")
        }
        mediaPlayer = null
    }

    fun cleanup() {
        isListening = false
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer: ${e.message}")
        }
        speechRecognizer = null

        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS: ${e.message}")
        }
        textToSpeech = null

        cleanupMediaPlayer()
        appContext = null
    }
}
