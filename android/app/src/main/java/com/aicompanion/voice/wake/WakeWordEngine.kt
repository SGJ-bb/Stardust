/**
 * 离线语音唤醒引擎（sherpa-onnx KeywordSpotter + 16k 单声道实时采集）。
 *
 * 用 sherpa-onnx 的中文 KWS 模型（zipformer 3.3M，int8，约 5MB）：
 * 建模单元是「声母 + 带调韵母」，所以唤醒词可以是任意中文组合，由运行时拼关键词行。
 * 全程离线、不联网、只有 5MB 模型 + 常驻 16kHz 单声道采集，功耗约为一次 ASR 的几十分之一。
 */
package com.aicompanion.voice.wake

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import com.aicompanion.util.AppLogger
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig
import com.k2fsa.sherpa.onnx.OnlineNeMoCtcModelConfig
import java.io.File
import java.io.InputStream

class WakeWordEngine(
    private val context: Context,
    private val onWake: (keyword: String) -> Unit,
    private val onError: (message: String) -> Unit = {},
) {

    companion object {
        private const val TAG = "WakeWordEngine"

        /** 模型目录（来自 lib-sherpa-kws-model AAR 合并进 assets 的路径） */
        const val MODEL_DIR = "sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"

        const val SAMPLE_RATE = 16000

        /** 每帧 512 采样 ≈ 32ms，KWS 的最小可推进一步 */
        private const val FRAME_SAMPLES = 512

        /** 命中后的静默期：避免同一句话重复触发 */
        private const val COOLDOWN_MS = 2500L

        /** model_type 留空让 sherpa 自检；若不认再按顺序试这两型 */
        private val MODEL_TYPES = arrayOf("", "zipformer2", "zipformer")
    }

    @Volatile
    private var spotter: KeywordSpotter? = null

    @Volatile
    private var running = false

    @Volatile
    private var keywordsText: String = ""

    @Volatile
    private var cooldownUntil = 0L

    @Volatile
    var paused: Boolean = false

    /** 初始化失败原因，供 UI 展示 */
    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var activeModelType: String = ""
        private set

    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    val isRunning: Boolean get() = running
    val isReady: Boolean get() = spotter != null

    // ------------------------------------------------------------------ 初始化

    /** 建 KeywordSpotter；model_type 逐个试，成功即用。 */
    @Synchronized
    fun initialize(): Boolean {
        if (spotter != null) return true
        lastError = null
        for (type in MODEL_TYPES) {
            try {
                val featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
                val modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$MODEL_DIR/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                        decoder = "$MODEL_DIR/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                        joiner = "$MODEL_DIR/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                    ),
                    paraformer = OnlineParaformerModelConfig(),
                    zipformer2Ctc = OnlineZipformer2CtcModelConfig(),
                    neMoCtc = OnlineNeMoCtcModelConfig(),
                    tokens = "$MODEL_DIR/tokens.txt",
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                    modelType = type,
                    modelingUnit = "",
                    bpeVocab = "",
                )
                val config = KeywordSpotterConfig(
                    featConfig = featConfig,
                    modelConfig = modelConfig,
                    maxActivePaths = 4,
                    keywordsFile = "$MODEL_DIR/keywords.txt",
                    keywordsScore = 1.0f,
                    keywordsThreshold = 0.25f,
                    numTrailingBlanks = 2,
                )
                val ks = KeywordSpotter(context.assets, config)
                // 立刻建一条流做冒烟，确认模型和关键词都能吃进去
                ks.createStream(keywordsText).release()
                spotter = ks
                activeModelType = if (type.isEmpty()) "(auto)" else type
                AppLogger.i(TAG, "KWS 就绪 modelType=$activeModelType")
                return true
            } catch (e: Throwable) {
                lastError = e.message ?: e.javaClass.simpleName
                AppLogger.w(TAG, "KWS 初始化失败 modelType='$type': $lastError")
                runCatching { spotter?.release() }
                spotter = null
            }
        }
        onError("唤醒模型初始化失败：$lastError")
        return false
    }

    /** 更新唤醒词行（已经由 PinyinConverter 转好的关键词行，一行一个） */
    fun setKeywords(lines: List<String>) {
        val text = lines.joinToString("\n")
        if (text == keywordsText) return
        keywordsText = text
        AppLogger.i(TAG, "唤醒词更新：${lines.size} 条")
        // 实时流里换关键词：重启采集线程即可（尽量不打断太久）
        if (running) {
            stop()
            start()
        }
    }

    // ------------------------------------------------------------------ 采集

    @Synchronized
    fun start(): Boolean {
        if (running) return true
        if (!initialize()) return false
        running = true
        worker = Thread({ captureLoop() }, "wake-kws").apply {
            priority = Thread.NORM_PRIORITY + 2
            start()
        }
        AppLogger.i(TAG, "唤醒监听已启动")
        return true
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        val t = worker
        worker = null
        runCatching { t?.join(800) }
        releaseRecord()
        AppLogger.i(TAG, "唤醒监听已停止")
    }

    @Synchronized
    fun release() {
        stop()
        runCatching { spotter?.release() }
        spotter = null
    }

    private fun captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val ks = spotter ?: return
        var stream: OnlineStream? = null
        try {
            stream = ks.createStream(keywordsText)
        } catch (e: Throwable) {
            AppLogger.e(TAG, "createStream 失败: ${e.message}", e)
            running = false
            onError("唤醒词流创建失败：${e.message}")
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufBytes = if (minBuf > 0) maxOf(minBuf * 2, FRAME_SAMPLES * 2 * 8) else FRAME_SAMPLES * 2 * 8

        // VOICE_RECOGNITION：不做 AGC/降噪，唤醒词保真度最好；个别机器不支持就退到 MIC
        var rec = openRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, bufBytes)
        if (rec == null) rec = openRecord(MediaRecorder.AudioSource.MIC, bufBytes)
        if (rec == null) {
            AppLogger.e(TAG, "AudioRecord 打不开（可能被占用或无录音权限）")
            running = false
            onError("麦克风打开失败，请检查录音权限")
            return
        }
        record = rec
        val shorts = ShortArray(FRAME_SAMPLES)
        var silentFrames = 0

        try {
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                AppLogger.e(TAG, "startRecording 未进入录音态")
                onError("麦克风启动失败")
                return
            }
            while (running) {
                if (paused || VoiceBus.busy() || isAudioPlaying()) {
                    // TTS/媒体正在响：扬声器声音会被自己听到，暂停解码避免自唤醒
                    Thread.sleep(40)
                    continue
                }
                val n = rec.read(shorts, 0, FRAME_SAMPLES)
                if (n <= 0) {
                    if (n == AudioRecord.ERROR_INVALID_OPERATION) Thread.sleep(20)
                    silentFrames++
                    if (silentFrames > 500) {   // 连续读失败约 10s，判定设备异常
                        AppLogger.w(TAG, "长时间读不到音频，重启录音")
                        break
                    }
                    continue
                }
                silentFrames = 0
                val f = FloatArray(n) { shorts[it] / 32768.0f }
                stream.acceptWaveform(f, SAMPLE_RATE)
                while (ks.isReady(stream)) {
                    ks.decode(stream)
                }
                val r = ks.getResult(stream)
                val kw = r.keyword
                if (!kw.isNullOrEmpty()) {
                    ks.reset(stream)
                    val now = System.currentTimeMillis()
                    if (now >= cooldownUntil) {
                        cooldownUntil = now + COOLDOWN_MS
                        AppLogger.i(TAG, "唤醒命中：$kw")
                        runCatching { onWake(kw) }
                    } else {
                        AppLogger.d(TAG, "冷却期内忽略：$kw")
                    }
                }
            }
        } catch (e: Throwable) {
            AppLogger.e(TAG, "采集循环异常: ${e.message}", e)
            lastError = e.message
            onError("唤醒监听异常：${e.message}")
        } finally {
            runCatching { stream?.release() }
            runCatching { rec.stop() }
            releaseRecord()
            running = false
        }
    }

    private fun openRecord(source: Int, bufBytes: Int): AudioRecord? = try {
        val r = AudioRecord(
            source, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes
        )
        if (r.state == AudioRecord.STATE_INITIALIZED) r else { r.release(); null }
    } catch (e: Throwable) {
        AppLogger.w(TAG, "AudioRecord(source=$source) 失败: ${e.message}")
        null
    }

    /**
     * 扬声器是否正在播放（TTS / 语音条 / 视频）。
     * 直接问系统，比在每个播放器里埋钩子更可靠：不管用 TextToSpeech 还是 MediaPlayer 都能覆盖。
     */
    private fun isAudioPlaying(): Boolean {
        val am = audioManager ?: return false
        return try {
            val configs = am.activePlaybackConfigurations
            for (cfg in configs) {
                // isActive() 在公开 SDK 里是 @hide，只能反射调（框架里确实存在）
                val active = runCatching {
                    cfg.javaClass.getMethod("isActive").invoke(cfg) as? Boolean ?: false
                }.getOrDefault(false)
                if (!active) continue
                when (cfg.audioAttributes.usage) {
                    android.media.AudioAttributes.USAGE_MEDIA,
                    android.media.AudioAttributes.USAGE_ASSISTANT,
                    android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION -> return true
                }
            }
            am.isMusicActive
        } catch (_: Throwable) {
            runCatching { am.isMusicActive }.getOrDefault(false)
        }
    }

    private fun releaseRecord() {
        val r = record ?: return
        record = null
        runCatching { if (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) r.stop() }
        runCatching { r.release() }
    }

    // ------------------------------------------------------------------ 自测

    /**
     * 对一段 PCM 做唤醒检测（不影响正在跑的实时流），返回命中的关键词。
     * @param samples -1..1 的浮点采样
     */
    fun detectPcm(samples: FloatArray, sampleRate: Int, keywords: List<String>): String? {
        if (!initialize()) return null
        val ks = spotter ?: return null
        val pcm = if (sampleRate == SAMPLE_RATE) samples else resample(samples, sampleRate, SAMPLE_RATE)
        val stream = try {
            ks.createStream(keywords.joinToString("\n"))
        } catch (e: Throwable) {
            AppLogger.e(TAG, "自测流创建失败: ${e.message}", e)
            return null
        }
        return try {
            var i = 0
            while (i < pcm.size) {
                val n = minOf(FRAME_SAMPLES, pcm.size - i)
                val chunk = FloatArray(n)
                System.arraycopy(pcm, i, chunk, 0, n)
                stream.acceptWaveform(chunk, SAMPLE_RATE)
                while (ks.isReady(stream)) ks.decode(stream)
                val kw = ks.getResult(stream).keyword
                if (!kw.isNullOrEmpty()) return kw
                i += n
            }
            null
        } catch (e: Throwable) {
            AppLogger.e(TAG, "自测解码异常: ${e.message}", e)
            null
        } finally {
            runCatching { stream.release() }
        }
    }

    /** 检测 assets 里的 wav（16bit PCM，任意采样率/声道） */
    fun detectAsset(path: String, keywords: List<String>): String? {
        return try {
            context.assets.open(path).use { input ->
                val (samples, sr) = readWav(input) ?: return null
                detectPcm(samples, sr, keywords)
            }
        } catch (e: Throwable) {
            AppLogger.e(TAG, "读取测试音频失败: ${e.message}", e)
            null
        }
    }

    /** 检测文件 wav */
    fun detectWavFile(file: File, keywords: List<String>): String? {
        return try {
            file.inputStream().use { input ->
                val (samples, sr) = readWav(input) ?: return null
                detectPcm(samples, sr, keywords)
            }
        } catch (e: Throwable) {
            AppLogger.e(TAG, "读取测试音频失败: ${e.message}", e)
            null
        }
    }

    /** 极简 WAV 解析：只认 PCM16，双声道自动混单，保留原采样率 */
    fun readWav(input: InputStream): Pair<FloatArray, Int>? {
        val bytes = input.readBytes()
        if (bytes.size < 44) return null
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte()) return null
        var pos = 12
        var channels = 1
        var sampleRate = SAMPLE_RATE
        var bits = 16
        var dataOffset = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = le32(bytes, pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    channels = le16(bytes, body + 2)
                    sampleRate = le32(bytes, body + 4)
                    bits = le16(bytes, body + 14)
                }
                "data" -> {
                    dataOffset = body
                    dataLen = minOf(size, bytes.size - body)
                }
            }
            pos = body + size + (size and 1)
        }
        if (dataOffset < 0 || bits != 16) return null
        val frames = dataLen / (2 * channels)
        if (frames <= 0) return null
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            var acc = 0
            for (c in 0 until channels) {
                val p = dataOffset + (i * channels + c) * 2
                acc += le16(bytes, p).toShort().toInt()
            }
            out[i] = (acc.toFloat() / channels) / 32768.0f
        }
        return out to sampleRate
    }

    private fun resample(src: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to) return src
        val ratio = from.toDouble() / to.toDouble()
        val outLen = (src.size / ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val x = i * ratio
            val i0 = x.toInt().coerceIn(0, src.size - 1)
            val i1 = (i0 + 1).coerceAtMost(src.size - 1)
            val t = (x - i0).toFloat()
            out[i] = src[i0] * (1 - t) + src[i1] * t
        }
        return out
    }

    private fun le16(b: ByteArray, p: Int): Int =
        (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, p: Int): Int =
        (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or
            ((b[p + 2].toInt() and 0xFF) shl 16) or ((b[p + 3].toInt() and 0xFF) shl 24)
}
