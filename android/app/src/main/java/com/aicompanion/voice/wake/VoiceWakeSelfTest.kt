/** 唤醒自测：录几秒现场声音丢给 KWS，用于调灵敏度和确认唤醒词可用。 */
package com.aicompanion.voice.wake

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.aicompanion.util.AppLogger
import java.io.ByteArrayOutputStream

object VoiceWakeSelfTest {

    private const val TAG = "VoiceWakeSelfTest"

    /**
     * 录一段（默认 3 秒）现场声音，然后判断是否能唤醒。
     * @param done (命中的关键词 or null, 给用户看的一句话)
     */
    fun recordAndDetect(
        context: Context,
        keywords: List<String>,
        seconds: Int = 3,
        done: (String?, String) -> Unit,
    ) {
        if (keywords.isEmpty()) {
            done(null, "唤醒词为空")
            return
        }
        Thread {
            val sr = WakeWordEngine.SAMPLE_RATE
            val minBuf = AudioRecord.getMinBufferSize(
                sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufBytes = if (minBuf > 0) minBuf * 2 else sr
            var record: AudioRecord? = null
            var result: Pair<String?, String> = null to ""
            try {
                // 让常驻监听让出麦克风，避免抢流
                VoiceBus.asrActive = true
                Thread.sleep(150)
                record = runCatching {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION, sr,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes
                    )
                }.getOrNull()
                if (record?.state != AudioRecord.STATE_INITIALIZED) {
                    runCatching { record?.release() }
                    record = AudioRecord(
                        MediaRecorder.AudioSource.MIC, sr,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes
                    )
                }
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    result = null to "麦克风不可用（检查录音权限）"
                } else {
                    record.startRecording()
                    val pcm = ByteArrayOutputStream()
                    val buf = ByteArray(2048)
                    val deadline = System.currentTimeMillis() + seconds * 1000L
                    while (System.currentTimeMillis() < deadline) {
                        val n = record.read(buf, 0, buf.size)
                        if (n > 0) pcm.write(buf, 0, n)
                    }
                    runCatching { record.stop() }
                    val bytes = pcm.toByteArray()
                    val samples = FloatArray(bytes.size / 2) {
                        val lo = bytes[it * 2].toInt() and 0xFF
                        val hi = bytes[it * 2 + 1].toInt()
                        (((hi shl 8) or lo).toShort()).toFloat() / 32768.0f
                    }
                    val engine = WakeWordEngine(context, {}, {})
                    val t0 = System.currentTimeMillis()
                    val hit = engine.detectPcm(samples, sr, keywords)
                    val cost = System.currentTimeMillis() - t0
                    runCatching { engine.release() }
                    result = if (hit.isNullOrEmpty()) {
                        null to "没听到唤醒词（${cost}ms）"
                    } else {
                        hit to "唤醒成功：$hit（${cost}ms）"
                    }
                    AppLogger.i(TAG, "自测 ${seconds}s → ${result.second}")
                }
            } catch (e: Throwable) {
                AppLogger.e(TAG, "自测失败: ${e.message}", e)
                result = null to "自测失败：${e.message}"
            } finally {
                runCatching { record?.release() }
                VoiceBus.asrActive = false
            }
            Handler(Looper.getMainLooper()).post { done(result.first, result.second) }
        }.start()
    }
}
