package com.aicompanion.ilink

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.aicompanion.util.AppLogger
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import kotlin.math.abs

object IlinkApi {

    private const val TAG = "IlinkApi"
    private const val BASE_URL = "https://ilinkai.weixin.qq.com"
    private const val CDN_URL = "https://novac2c.cdn.weixin.qq.com/c2c"

    private val random = SecureRandom()
    private const val CHANNEL_VERSION = "2.1.1"

    private fun generateUin(): String {
        val uid = abs(random.nextInt())
        return Base64.encodeToString(uid.toString().toByteArray(), Base64.NO_WRAP)
    }

    private fun buildCommonHeaders(): Map<String, String> = mapOf(
        "iLink-App-Id" to "bot",
        "iLink-App-ClientVersion" to "1"
    )

    private fun buildHeaders(botToken: String): Map<String, String> = buildCommonHeaders() + mapOf(
        "Content-Type" to "application/json",
        "AuthorizationType" to "ilink_bot_token",
        "X-WECHAT-UIN" to generateUin(),
        "Authorization" to "Bearer $botToken"
    )

    private fun buildBaseInfo(): JSONObject = JSONObject().apply {
        put("channel_version", CHANNEL_VERSION)
    }

    suspend fun getBotQrcode(): QrcodeResult? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/ilink/bot/get_bot_qrcode?bot_type=3")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            buildCommonHeaders().forEach { (k, v) ->
                conn.setRequestProperty(k, v)
            }
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val code = conn.responseCode
            if (code != 200) {
                val errBody = try { readBody(conn) } catch (_: Exception) { "" }
                AppLogger.e(TAG, "getBotQrcode: HTTP $code, body=$errBody")
                return@withContext null
            }

            val body = readBody(conn)
            AppLogger.d(TAG, "getBotQrcode: response=$body")
            val json = JSONObject(body)
            if (json.optInt("ret", -1) != 0) {
                AppLogger.e(TAG, "getBotQrcode: ret=${json.optInt("ret")}, body=$body")
                return@withContext null
            }

            val qrcode = json.optString("qrcode", "")
            val imgContent = json.optString("qrcode_img_content", "")
            AppLogger.d(TAG, "getBotQrcode: qrcode=$qrcode, imgContent=$imgContent")

            QrcodeResult(
                qrcode = qrcode,
                qrcodeImgContent = imgContent
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "getBotQrcode: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    fun generateQrcodeBitmap(content: String, size: Int): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
            val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            AppLogger.e(TAG, "generateQrcodeBitmap: ${e.message}")
            null
        }
    }

    suspend fun getQrcodeStatus(qrcode: String): QrcodeStatus? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/ilink/bot/get_qrcode_status?qrcode=$qrcode")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            buildCommonHeaders().forEach { (k, v) ->
                conn.setRequestProperty(k, v)
            }
            conn.connectTimeout = 15000
            conn.readTimeout = 30000

            val code = conn.responseCode
            if (code != 200) {
                AppLogger.e(TAG, "getQrcodeStatus: HTTP $code")
                return@withContext null
            }

            val body = readBody(conn)
            val json = JSONObject(body)
            val status = json.optString("status", "")

            when (status) {
                "confirmed" -> QrcodeStatus.Confirmed(
                    botToken = json.optString("bot_token", ""),
                    ilinkBotId = json.optString("ilink_bot_id", ""),
                    baseUrl = json.optString("baseurl", BASE_URL),
                    ilinkUserId = json.optString("ilink_user_id", "")
                )
                "scaned", "scanned" -> QrcodeStatus.Scanned
                "wait", "waiting" -> QrcodeStatus.Waiting
                "expired" -> QrcodeStatus.Expired
                else -> QrcodeStatus.Waiting
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getQrcodeStatus: ${e.message}")
            QrcodeStatus.Waiting
        }
    }

    suspend fun getUpdates(botToken: String, baseUrl: String, getUpdatesBuf: String = ""): UpdateResult? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/ilink/bot/getupdates")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 65000

            buildHeaders(botToken).forEach { (k, v) ->
                conn.setRequestProperty(k, v)
            }

            conn.doOutput = true
            val body = JSONObject().apply {
                if (getUpdatesBuf.isNotBlank()) put("get_updates_buf", getUpdatesBuf)
                put("base_info", buildBaseInfo())
            }
            DataOutputStream(conn.outputStream).use { os ->
                os.write(body.toString().toByteArray())
            }

            val code = conn.responseCode
            if (code != 200) {
                AppLogger.e(TAG, "getUpdates: HTTP $code")
                return@withContext null
            }

            val respBody = readBody(conn)
            AppLogger.d(TAG, "getUpdates: body_start=${respBody.take(200)}, body_end=${respBody.takeLast(200)}")
            val json = JSONObject(respBody)

            val errcode = json.optInt("errcode", 0)
            if (errcode == -14) {
                AppLogger.e(TAG, "getUpdates: session expired (errcode -14)")
                return@withContext UpdateResult(messages = emptyList(), getUpdatesBuf = "", sessionExpired = true)
            }

            val msgArray = json.optJSONArray("msgs") ?: json.optJSONArray("messages")
            val messages = mutableListOf<IlinkMessage>()
            if (msgArray != null) {
                for (i in 0 until msgArray.length()) {
                    try {
                        val msgObj = msgArray.getJSONObject(i)
                        messages.add(parseMessage(msgObj))
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "getUpdates: parse msg[$i] failed: ${e.message}")
                    }
                }
            }

            val ret = json.optInt("ret", -1)
            if (ret != 0 && messages.isEmpty()) {
                val errmsg = json.optString("errmsg", "")
                AppLogger.w(TAG, "getUpdates: ret=$ret, errcode=$errcode, errmsg=$errmsg")
                return@withContext null
            }

            if (ret != 0 && messages.isNotEmpty()) {
                AppLogger.w(TAG, "getUpdates: ret=$ret but got ${messages.size} msgs, processing anyway")
            }

            UpdateResult(
                messages = messages,
                getUpdatesBuf = json.optString("get_updates_buf", ""),
                sessionExpired = false
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "getUpdates: ${e.message}")
            null
        }
    }

    suspend fun sendMessage(botToken: String, baseUrl: String, toUserId: String, contextToken: String, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/ilink/bot/sendmessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            buildHeaders(botToken).forEach { (k, v) ->
                conn.setRequestProperty(k, v)
            }

            conn.doOutput = true
            val clientId = abs(random.nextLong()).toString()

            val body = JSONObject().apply {
                put("msg", JSONObject().apply {
                    put("from_user_id", "")
                    put("to_user_id", toUserId)
                    put("client_id", clientId)
                    put("message_type", 2)
                    put("message_state", 2)
                    put("context_token", contextToken)
                    put("item_list", JSONArray().put(
                        JSONObject().apply {
                            put("type", 1)
                            put("text_item", JSONObject().apply {
                                put("text", text)
                            })
                        }
                    ))
                })
                put("base_info", buildBaseInfo())
            }
            DataOutputStream(conn.outputStream).use { os ->
                os.write(body.toString().toByteArray())
            }

            val respCode = conn.responseCode
            if (respCode != 200) {
                AppLogger.e(TAG, "sendMessage: HTTP $respCode")
                return@withContext false
            }

            val respBody = readBody(conn)
            val json = JSONObject(respBody)
            val ret = json.optInt("ret", -1)
            val errcode = json.optInt("errcode", 0)
            if (ret != 0 && errcode != 0) {
                AppLogger.e(TAG, "sendMessage: ret=$ret, errcode=$errcode, body=${respBody.take(300)}")
                return@withContext false
            }
            if (ret != 0 && errcode == 0) {
                AppLogger.w(TAG, "sendMessage: ret=$ret but errcode=0, treating as success. body=${respBody.take(200)}")
            }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendMessage: ${e.message}")
            false
        }
    }

    suspend fun sendVoiceMessage(
        botToken: String,
        baseUrl: String,
        toUserId: String,
        contextToken: String,
        voiceUrl: String,
        voiceDurationMs: Int,
        voiceText: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/ilink/bot/sendmessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            buildHeaders(botToken).forEach { (k, v) ->
                conn.setRequestProperty(k, v)
            }

            conn.doOutput = true
            val clientId = abs(random.nextLong()).toString()

            val body = JSONObject().apply {
                put("msg", JSONObject().apply {
                    put("from_user_id", "")
                    put("to_user_id", toUserId)
                    put("client_id", clientId)
                    put("message_type", 2)
                    put("message_state", 2)
                    put("context_token", contextToken)
                    put("item_list", JSONArray().put(
                        JSONObject().apply {
                            put("type", 3)
                            put("voice_item", JSONObject().apply {
                                put("text", voiceText)
                                put("url", voiceUrl)
                                put("duration", voiceDurationMs)
                                put("voice_type", 0)
                            })
                        }
                    ))
                })
                put("base_info", buildBaseInfo())
            }
            DataOutputStream(conn.outputStream).use { os ->
                os.write(body.toString().toByteArray())
            }

            val respCode = conn.responseCode
            if (respCode != 200) {
                AppLogger.e(TAG, "sendVoiceMessage: HTTP $respCode")
                return@withContext false
            }

            val respBody = readBody(conn)
            val json = JSONObject(respBody)
            val ret = json.optInt("ret", -1)
            val errcode = json.optInt("errcode", 0)
            if (ret != 0 && errcode != 0) {
                AppLogger.e(TAG, "sendVoiceMessage: ret=$ret, errcode=$errcode, body=${respBody.take(300)}")
                return@withContext false
            }
            if (ret != 0 && errcode == 0) {
                AppLogger.w(TAG, "sendVoiceMessage: ret=$ret but errcode=0, treating as success")
            }
            AppLogger.i(TAG, "sendVoiceMessage: success, duration=${voiceDurationMs}ms")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendVoiceMessage: ${e.message}")
            false
        }
    }

    suspend fun sendTyping(botToken: String, baseUrl: String, ilinkUserId: String, typingTicket: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/ilink/bot/sendtyping")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            buildHeaders(botToken).forEach { (k, v) ->
                conn.setRequestProperty(k, v)
            }

            conn.doOutput = true
            val body = JSONObject().apply {
                put("ilink_user_id", ilinkUserId)
                put("typing_ticket", typingTicket)
                put("status", 1)
                put("base_info", buildBaseInfo())
            }
            DataOutputStream(conn.outputStream).use { os ->
                os.write(body.toString().toByteArray())
            }

            conn.responseCode == 200
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendTyping: ${e.message}")
            false
        }
    }

    suspend fun getConfig(botToken: String, baseUrl: String, ilinkUserId: String, contextToken: String): TypingConfig? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/ilink/bot/getconfig")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            buildHeaders(botToken).forEach { (k, v) ->
                conn.setRequestProperty(k, v)
            }

            conn.doOutput = true
            val body = JSONObject().apply {
                put("ilink_user_id", ilinkUserId)
                put("context_token", contextToken)
                put("base_info", buildBaseInfo())
            }
            DataOutputStream(conn.outputStream).use { os ->
                os.write(body.toString().toByteArray())
            }

            if (conn.responseCode != 200) return@withContext null

            val respBody = readBody(conn)
            val json = JSONObject(respBody)
            if (json.optInt("ret", -1) != 0) return@withContext null

            TypingConfig(
                typingTicket = json.optString("typing_ticket", "")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "getConfig: ${e.message}")
            null
        }
    }

    private fun parseMessage(obj: JSONObject): IlinkMessage {
        val itemList = mutableListOf<MessageItem>()
        val itemArray = obj.optJSONArray("item_list")
        if (itemArray != null) {
            for (i in 0 until itemArray.length()) {
                val itemObj = itemArray.getJSONObject(i)
                val type = itemObj.optInt("type", 1)
                val textItem = itemObj.optJSONObject("text_item")
                val imageItem = itemObj.optJSONObject("image_item")
                val voiceItem = itemObj.optJSONObject("voice_item")
                val fileItem = itemObj.optJSONObject("file_item")

                itemList.add(MessageItem(
                    type = type,
                    text = textItem?.optString("text", "") ?: "",
                    imageUrl = imageItem?.optString("url", "") ?: "",
                    voiceText = voiceItem?.optString("text", "") ?: "",
                    fileName = fileItem?.optString("file_name", "") ?: ""
                ))
            }
        }

        val textContent = if (itemList.isNotEmpty()) {
            itemList.filter { it.type == 1 && it.text.isNotBlank() }.joinToString("\n") { it.text }
        } else {
            obj.optString("content", "")
        }

        val contextToken = obj.optString("context_token", "")
            .ifBlank { obj.optString("client_id", "") }

        return IlinkMessage(
            messageId = obj.optLong("message_id", 0L),
            fromUserId = obj.optString("from_user_id", ""),
            toUserId = obj.optString("to_user_id", ""),
            clientId = obj.optString("client_id", ""),
            createTimeMs = obj.optLong("create_time_ms", 0L),
            messageType = obj.optInt("message_type", 1),
            messageState = obj.optInt("message_state", 0),
            contextToken = contextToken,
            itemList = itemList,
            textContent = textContent
        )
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode < 400) conn.inputStream else conn.errorStream
        val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        return sb.toString()
    }
}

data class QrcodeResult(
    val qrcode: String,
    val qrcodeImgContent: String
)

sealed class QrcodeStatus {
    object Waiting : QrcodeStatus()
    object Scanned : QrcodeStatus()
    data class Confirmed(
        val botToken: String,
        val ilinkBotId: String,
        val baseUrl: String,
        val ilinkUserId: String
    ) : QrcodeStatus()
    object Expired : QrcodeStatus()
}

data class UpdateResult(
    val messages: List<IlinkMessage>,
    val getUpdatesBuf: String,
    val sessionExpired: Boolean
)

data class IlinkMessage(
    val messageId: Long,
    val fromUserId: String,
    val toUserId: String,
    val clientId: String,
    val createTimeMs: Long,
    val messageType: Int,
    val messageState: Int,
    val contextToken: String,
    val itemList: List<MessageItem>,
    val textContent: String
)

data class MessageItem(
    val type: Int,
    val text: String,
    val imageUrl: String,
    val voiceText: String,
    val fileName: String
)

data class TypingConfig(
    val typingTicket: String
)
