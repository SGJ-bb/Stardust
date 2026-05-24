package com.aicompanion.voice

import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object EdgeTtsEngine {

    private const val TAG = "EdgeTtsEngine"
    private const val WSS_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
    private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val CHROMIUM_VERSION = "143.0.3650.75"
    private const val CHROMIUM_MAJOR = "143"
    private const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_VERSION"
    private const val WIN_EPOCH = 11644473600.0
    private const val S_TO_NS = 1e9

    private var clockSkewSeconds = 0.0

    val VOICES = listOf(
        Voice("zh-CN-XiaoxiaoNeural", "晓晓", "女", "zh-CN"),
        Voice("zh-CN-XiaoyiNeural", "晓伊", "女", "zh-CN"),
        Voice("zh-CN-YunjianNeural", "云健", "男", "zh-CN"),
        Voice("zh-CN-YunxiNeural", "云希", "男", "zh-CN"),
        Voice("zh-CN-YunxiaNeural", "云霞", "男", "zh-CN"),
        Voice("zh-CN-YunyangNeural", "云扬", "男", "zh-CN"),
        Voice("zh-CN-liaoning-XiaobeiNeural", "晓北(东北)", "女", "zh-CN-liaoning"),
        Voice("zh-CN-shaanxi-XiaoniNeural", "晓妮(陕西)", "女", "zh-CN-shaanxi"),
        Voice("zh-HK-HiuGaaiNeural", "曉佳(粵語)", "女", "zh-HK"),
        Voice("zh-HK-WanLungNeural", "雲龍(粵語)", "男", "zh-HK"),
        Voice("zh-TW-HsiaoChenNeural", "曉臻(台灣)", "女", "zh-TW"),
        Voice("zh-TW-YunJheNeural", "雲哲(台灣)", "男", "zh-TW"),
        Voice("ja-JP-NanamiNeural", "Nanami", "女", "ja-JP"),
        Voice("ja-JP-KeitaNeural", "Keita", "男", "ja-JP"),
        Voice("en-US-JennyNeural", "Jenny", "女", "en-US"),
        Voice("en-US-GuyNeural", "Guy", "男", "en-US"),
        Voice("en-US-AriaNeural", "Aria", "女", "en-US"),
        Voice("en-US-DavisNeural", "Davis", "男", "en-US"),
        Voice("ko-KR-SunHiNeural", "SunHi", "女", "ko-KR"),
        Voice("ko-KR-InJoonNeural", "InJoon", "男", "ko-KR")
    )

    data class Voice(
        val id: String,
        val displayName: String,
        val gender: String,
        val locale: String
    ) {
        override fun toString(): String = "$displayName ($id)"
    }

    private fun generateSecMsGec(): String {
        var ticks = System.currentTimeMillis() / 1000.0 + clockSkewSeconds
        ticks += WIN_EPOCH
        ticks -= ticks % 300
        ticks *= S_TO_NS / 100
        val strToHash = "${ticks.toLong()}$TRUSTED_CLIENT_TOKEN"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(strToHash.toByteArray(Charsets.ISO_8859_1))
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun generateMuid(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02X".format(it) }
    }

    private fun adjustClockSkewFromResponse(response: Response?) {
        try {
            val serverDate = response?.header("Date") ?: return
            val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
            val serverTimestamp = sdf.parse(serverDate)?.time?.div(1000.0) ?: return
            val clientTimestamp = System.currentTimeMillis() / 1000.0
            clockSkewSeconds += serverTimestamp - clientTimestamp
            AppLogger.i(TAG, "Clock skew adjusted: ${clockSkewSeconds}s")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to adjust clock skew: ${e.message}")
        }
    }

    suspend fun synthesize(
        text: String,
        voiceId: String,
        outputDir: File,
        rate: Float = 1.0f,
        pitch: Float = 1.0f,
        client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    ): TtsResult = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext TtsResult(null, null, false, "文本为空")

        val truncatedText = if (text.length > 500) text.take(500) + "…" else text

        for (attempt in 1..2) {
            val result = trySynthesize(truncatedText, voiceId, outputDir, rate, pitch, client)
            if (result.success) return@withContext result

            if (attempt == 1 && (result.error?.contains("403") == true || result.error?.contains("401") == true)) {
                AppLogger.w(TAG, "Edge TTS auth failed, will retry with adjusted clock skew")
                continue
            }

            return@withContext result
        }

        return@withContext TtsResult(null, null, false, "Edge TTS重试后仍失败")
    }

    private fun trySynthesize(
        text: String,
        voiceId: String,
        outputDir: File,
        rate: Float,
        pitch: Float,
        client: OkHttpClient
    ): TtsResult {
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val timestamp = System.currentTimeMillis()
        val audioFile = File(outputDir, "edge_${timestamp}.mp3")

        val rateStr = if (rate >= 1.0f) "+${((rate - 1.0f) * 100).toInt()}%" else "${((rate - 1.0f) * 100).toInt()}%"
        val pitchStr = if (pitch >= 1.0f) "+${((pitch - 1.0f) * 100).toInt()}Hz" else "${((pitch - 1.0f) * 100).toInt()}Hz"

        val ssml = buildSsml(text, voiceId, rateStr, pitchStr)

        val resultRef = AtomicReference<TtsResult>(null)
        val latch = CountDownLatch(1)
        val audioBuffer = ByteArrayOutputStream()

        val secMsGec = generateSecMsGec()
        val connectionId = UUID.randomUUID().toString()
        val wsUrl = "$WSS_URL?TrustedClientToken=$TRUSTED_CLIENT_TOKEN&Sec-MS-GEC=$secMsGec&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION&ConnectionId=$connectionId"

        val muid = generateMuid()

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROMIUM_MAJOR.0.0.0 Safari/537.36 Edg/$CHROMIUM_MAJOR.0.0.0")
            .addHeader("Accept-Encoding", "gzip, deflate, br, zstd")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .addHeader("Pragma", "no-cache")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("Sec-WebSocket-Version", "13")
            .addHeader("Cookie", "MUID=$muid")
            .build()

        val webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val configJson = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("synthesis", JSONObject().apply {
                            put("audio", JSONObject().apply {
                                put("metadataoptions", JSONObject().apply {
                                    put("sentenceBoundaryEnabled", "false")
                                    put("wordBoundaryEnabled", "true")
                                })
                                put("outputFormat", "audio-24khz-48kbitrate-mono-mp3")
                            })
                        })
                    })
                }
                webSocket.send(configJson.toString())

                val ssmlMessage = "X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:${getTimestamp()}Z\r\nPath:ssml\r\n\r\n$ssml"
                webSocket.send(ssmlMessage)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    if (audioBuffer.size() > 0) {
                        audioFile.parentFile?.mkdirs()
                        audioFile.writeBytes(audioBuffer.toByteArray())
                        resultRef.set(TtsResult(audioFile.absolutePath, null, true))
                    } else {
                        resultRef.set(TtsResult(null, null, false, "未收到音频数据"))
                    }
                    latch.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val byteArray = bytes.toByteArray()
                if (byteArray.size > 2 && byteArray[0] == 0x00.toByte() && byteArray[1] == 0x67.toByte()) {
                    val headerEnd = findHeaderEnd(byteArray)
                    if (headerEnd >= 0 && headerEnd < byteArray.size) {
                        audioBuffer.write(byteArray, headerEnd, byteArray.size - headerEnd)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code ?: 0
                AppLogger.e(TAG, "WebSocket failure (code=$code): ${t.message}")
                if (code == 403 || code == 401) {
                    adjustClockSkewFromResponse(response)
                }
                resultRef.set(TtsResult(null, null, false, "Edge TTS连接失败($code): ${t.message}"))
                latch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (latch.count > 0) {
                    if (audioBuffer.size() > 0) {
                        audioFile.parentFile?.mkdirs()
                        audioFile.writeBytes(audioBuffer.toByteArray())
                        resultRef.set(TtsResult(audioFile.absolutePath, null, true))
                    } else {
                        resultRef.set(TtsResult(null, null, false, "连接关闭但未收到音频"))
                    }
                    latch.countDown()
                }
            }
        })

        val completed = latch.await(30, TimeUnit.SECONDS)
        webSocket.close(1000, "done")

        if (!completed) {
            return TtsResult(null, null, false, "Edge TTS超时")
        }

        return resultRef.get() ?: TtsResult(null, null, false, "未知错误")
    }

    private fun buildSsml(text: String, voiceId: String, rate: String, pitch: String): String {
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        return """
            <speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>
                <voice name='$voiceId'>
                    <prosody pitch='$pitch' rate='$rate'>
                        $escaped
                    </prosody>
                </voice>
            </speak>
        """.trimIndent()
    }

    private fun getTimestamp(): String {
        val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss", Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun findHeaderEnd(data: ByteArray): Int {
        for (i in 0 until data.size - 1) {
            if (data[i] == 0x0D.toByte() && data[i + 1] == 0x0A.toByte() &&
                i + 3 < data.size && data[i + 2] == 0x0D.toByte() && data[i + 3] == 0x0A.toByte()) {
                return i + 4
            }
        }
        return -1
    }

    fun getVoiceDisplayName(voiceId: String): String {
        return VOICES.find { it.id == voiceId }?.displayName ?: voiceId
    }
}
