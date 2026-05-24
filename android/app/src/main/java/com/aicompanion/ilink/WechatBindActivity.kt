package com.aicompanion.ilink

import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aicompanion.persona.PersonaManager
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WechatBindActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WechatBind"
    }

    private lateinit var authManager: IlinkAuthManager
    private lateinit var personaManager: PersonaManager
    private var currentQrcode = ""
    private var isPollingStatus = false

    private var ivQrcode: ImageView? = null
    private var tvStatus: TextView? = null
    private var btnAction: Button? = null
    private var tvInfo: TextView? = null
    private var tvPersonaHint: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = IlinkAuthManager(this)
        personaManager = PersonaManager(this)
        personaManager.load()

        val density = resources.displayMetrics.density
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(
                (24 * density).toInt(),
                (40 * density).toInt(),
                (24 * density).toInt(),
                (24 * density).toInt()
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF0F0F2E.toInt(), 0xFF080818.toInt())
            )
        }

        val tvTitle = TextView(this).apply {
            text = "🔗 微信连接"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(tvTitle)

        tvStatus = TextView(this).apply {
            text = if (authManager.isBound) "已绑定微信" else "准备获取二维码..."
            setTextColor(0xFF81D4FA.toInt())
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * density).toInt() }
        }
        rootLayout.addView(tvStatus)

        ivQrcode = ImageView(this).apply {
            val size = (240 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                topMargin = (24 * density).toInt()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(8, 8, 8, 8)
            visibility = if (authManager.isBound) android.view.View.GONE else android.view.View.VISIBLE
        }
        rootLayout.addView(ivQrcode)

        tvInfo = TextView(this).apply {
            text = if (authManager.isBound) {
                "微信ID: ${authManager.ilinkUserId}\n机器人: ${authManager.ilinkBotId}"
            } else {
                "请用微信扫描上方二维码\n在微信「设置→插件→ClawBot」中扫码绑定"
            }
            setTextColor(0xFFCCCCDD.toInt())
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setLineSpacing(6f, 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (16 * density).toInt() }
        }
        rootLayout.addView(tvInfo)

        val activePersona = personaManager.getActivePersona()
        tvPersonaHint = TextView(this).apply {
            text = "🤖 回复角色: ${activePersona.name}"
            setTextColor(0xFFC4B5FD.toInt())
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).toInt() }
        }
        rootLayout.addView(tvPersonaHint)

        val btnSelectPersona = Button(this).apply {
            text = "选择回复角色"
            setTextColor(0xFFC4B5FD.toInt())
            background = GradientDrawable().apply {
                setStroke(2, 0xFF7C4DFF.toInt())
                setColor(0x00000000)
                setCornerRadius(8 * density)
            }
            layoutParams = LinearLayout.LayoutParams(
                (180 * density).toInt(),
                (36 * density).toInt()
            ).apply { topMargin = (8 * density).toInt() }
            setOnClickListener { showPersonaSelector() }
        }
        rootLayout.addView(btnSelectPersona)

        btnAction = Button(this).apply {
            text = if (authManager.isBound) "解除绑定" else "刷新二维码"
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                setColor(if (authManager.isBound) 0xFFD32F2F.toInt() else 0xFF7C4DFF.toInt())
                setCornerRadius(12 * density)
            }
            layoutParams = LinearLayout.LayoutParams(
                (200 * density).toInt(),
                (44 * density).toInt()
            ).apply { topMargin = (24 * density).toInt() }
            setOnClickListener {
                if (authManager.isBound) {
                    unbind()
                } else {
                    fetchQrcode()
                }
            }
        }
        rootLayout.addView(btnAction)

        if (authManager.isBound) {
            val btnStart = Button(this).apply {
                text = "启动微信消息监听"
                setTextColor(0xFFFFFFFF.toInt())
                background = GradientDrawable().apply {
                    setColor(0xFF4CAF50.toInt())
                    setCornerRadius(12 * density)
                }
                layoutParams = LinearLayout.LayoutParams(
                    (200 * density).toInt(),
                    (44 * density).toInt()
                ).apply { topMargin = (12 * density).toInt() }
                setOnClickListener { startListening() }
            }
            rootLayout.addView(btnStart)

            val btnStop = Button(this).apply {
                text = "停止监听"
                setTextColor(0xFFFFFFFF.toInt())
                background = GradientDrawable().apply {
                    setColor(0xFF555577.toInt())
                    setCornerRadius(12 * density)
                }
                layoutParams = LinearLayout.LayoutParams(
                    (200 * density).toInt(),
                    (44 * density).toInt()
                ).apply { topMargin = (8 * density).toInt() }
                setOnClickListener { stopListening() }
            }
            rootLayout.addView(btnStop)
        }

        val btnViewLogs = Button(this).apply {
            text = "📋 查看微信日志"
            setTextColor(0xFF81D4FA.toInt())
            background = GradientDrawable().apply {
                setStroke(1, 0xFF557788.toInt())
                setColor(0x00000000)
                setCornerRadius(8 * density)
            }
            layoutParams = LinearLayout.LayoutParams(
                (200 * density).toInt(),
                (36 * density).toInt()
            ).apply { topMargin = (16 * density).toInt() }
            setOnClickListener { showLogs() }
        }
        rootLayout.addView(btnViewLogs)

        if (authManager.isBound) {
            val btnTestConn = Button(this).apply {
                text = "🔧 测试连接"
                setTextColor(0xFFFFB74D.toInt())
                background = GradientDrawable().apply {
                    setStroke(1, 0xFFFFB74D.toInt())
                    setColor(0x00000000)
                    setCornerRadius(8 * density)
                }
                layoutParams = LinearLayout.LayoutParams(
                    (200 * density).toInt(),
                    (36 * density).toInt()
                ).apply { topMargin = (8 * density).toInt() }
                setOnClickListener { testConnection() }
            }
            rootLayout.addView(btnTestConn)
        }

        setContentView(rootLayout)

        if (!authManager.isBound) {
            fetchQrcode()
        }
    }

    private fun testConnection() {
        tvStatus?.text = "测试连接中..."
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = java.net.URL("${authManager.baseUrl}/ilink/bot/getupdates")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 15000
                    conn.readTimeout = 35000

                    val uid = kotlin.math.abs(java.security.SecureRandom().nextInt())
                    val uin = android.util.Base64.encodeToString(uid.toString().toByteArray(), android.util.Base64.NO_WRAP)

                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("iLink-App-Id", "bot")
                    conn.setRequestProperty("iLink-App-ClientVersion", "1")
                    conn.setRequestProperty("AuthorizationType", "ilink_bot_token")
                    conn.setRequestProperty("X-WECHAT-UIN", uin)
                    conn.setRequestProperty("Authorization", "Bearer ${authManager.botToken}")

                    conn.doOutput = true
                    val body = org.json.JSONObject().apply {
                        put("get_updates_buf", "")
                        put("base_info", org.json.JSONObject().put("channel_version", "2.1.1"))
                    }
                    java.io.DataOutputStream(conn.outputStream).use { os ->
                        os.write(body.toString().toByteArray())
                    }

                    val code = conn.responseCode
                    val respBody = try {
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(
                            if (code < 400) conn.inputStream else conn.errorStream, "UTF-8"))
                        val sb = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) sb.append(line)
                        reader.close()
                        sb.toString()
                    } catch (e: Exception) {
                        "读取响应失败: ${e.message}"
                    }

                    buildString {
                        append("HTTP $code\n\n")
                        append("请求头:\n")
                        append("  iLink-App-Id: bot\n")
                        append("  iLink-App-ClientVersion: 1\n")
                        append("  AuthorizationType: ilink_bot_token\n")
                        append("  X-WECHAT-UIN: $uin\n")
                        append("  Authorization: Bearer ${authManager.botToken.take(20)}...\n\n")
                        append("请求体:\n${body.toString(2)}\n\n")
                        append("响应体:\n$respBody")
                    }
                }

                tvStatus?.text = "测试完成"

                val scrollView = android.widget.ScrollView(this@WechatBindActivity).apply {
                    val textView = android.widget.TextView(this@WechatBindActivity).apply {
                        text = result
                        textSize = 11f
                        setTextColor(0xFFCCCCDD.toInt())
                        setPadding(32, 24, 32, 24)
                        setTextIsSelectable(true)
                    }
                    addView(textView)
                }

                androidx.appcompat.app.AlertDialog.Builder(this@WechatBindActivity)
                    .setTitle("🔧 连接测试结果")
                    .setView(scrollView)
                    .setPositiveButton("复制") { _, _ ->
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("测试结果", result))
                        Toast.makeText(this@WechatBindActivity, "已复制", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("关闭", null)
                    .show()
            } catch (e: Exception) {
                tvStatus?.text = "测试失败: ${e.message}"
            }
        }
    }

    private fun showLogs() {
        val allLogs = com.aicompanion.util.AppLogger.getRecentLogs(500)
        val ilinkLogs = allLogs.lines().filter {
            it.contains("IlinkPolling") || it.contains("IlinkApi") || it.contains("WechatBind")
        }.joinToString("\n")
        val errorLogs = allLogs.lines().filter {
            it.contains("E/") && (it.contains("Ilink") || it.contains("ilink") || it.contains("Wechat"))
        }.joinToString("\n")

        val fullLog = buildString {
            if (errorLogs.isNotBlank()) {
                append("=== ❌ 微信相关错误 ===\n\n")
                append(errorLogs)
                append("\n\n")
            }
            if (ilinkLogs.isNotBlank()) {
                append("=== 📋 微信相关日志 (最近500条中筛选) ===\n\n")
                append(ilinkLogs)
                append("\n\n")
            }
            if (ilinkLogs.isBlank() && errorLogs.isBlank()) {
                append("暂无微信相关日志\n\n请先启动监听服务，然后给微信发消息，再回来查看日志。")
            }
            append("\n=== 📋 全部日志 (最近50条) ===\n\n")
            append(allLogs.lines().takeLast(50).joinToString("\n"))
        }

        val scrollView = android.widget.ScrollView(this).apply {
            val textView = android.widget.TextView(this@WechatBindActivity).apply {
                text = fullLog
                textSize = 11f
                setTextColor(0xFFCCCCDD.toInt())
                setPadding(32, 24, 32, 24)
                setTextIsSelectable(true)
            }
            addView(textView)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("📋 微信连接日志")
            .setView(scrollView)
            .setPositiveButton("复制全部") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("微信日志", fullLog))
                Toast.makeText(this@WechatBindActivity, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showPersonaSelector() {
        val personas = personaManager.getAllPersonas()
        if (personas.isEmpty()) {
            Toast.makeText(this, "暂无角色，请先在主页创建角色", Toast.LENGTH_SHORT).show()
            return
        }

        val names = personas.map { it.name }.toTypedArray()
        val activeId = personaManager.getActivePersona().id
        val checkedItem = personas.indexOfFirst { it.id == activeId }.coerceAtLeast(0)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择微信回复角色")
            .setSingleChoiceItems(names, checkedItem) { dialog, which ->
                val selected = personas[which]
                personaManager.setActivePersona(selected.id)
                tvPersonaHint?.text = "🤖 回复角色: ${selected.name}"
                Toast.makeText(this, "已切换为: ${selected.name}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun fetchQrcode() {
        tvStatus?.text = "正在获取二维码..."
        lifecycleScope.launch {
            try {
                val result = IlinkApi.getBotQrcode()
                if (result != null && result.qrcodeImgContent.isNotBlank()) {
                    currentQrcode = result.qrcode
                    loadQrcodeImage(result.qrcodeImgContent)
                    tvStatus?.text = "请用微信扫描二维码"
                    startPollingStatus()
                } else {
                    tvStatus?.text = "获取二维码失败，请重试"
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "fetchQrcode: ${e.message}")
                tvStatus?.text = "网络错误: ${e.message}"
            }
        }
    }

    private fun loadQrcodeImage(imageContent: String) {
        lifecycleScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) {
                    if (imageContent.startsWith("http://") || imageContent.startsWith("https://")) {
                        IlinkApi.generateQrcodeBitmap(imageContent, 600)
                    } else if (imageContent.startsWith("iVBOR") || imageContent.startsWith("/9j/") || imageContent.length > 200 && !imageContent.contains("/")) {
                        val pureBase64 = if (imageContent.contains(",")) {
                            imageContent.substringAfter(",")
                        } else {
                            imageContent
                        }
                        val bytes = android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else {
                        IlinkApi.generateQrcodeBitmap(imageContent, 600)
                    }
                }
                if (bmp != null) {
                    ivQrcode?.setImageBitmap(bmp)
                } else {
                    AppLogger.e(TAG, "loadQrcodeImage: decoded bitmap is null")
                    tvStatus?.text = "二维码生成失败"
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "loadQrcodeImage: ${e.message}, prefix=${imageContent.take(50)}")
                tvStatus?.text = "二维码加载失败"
            }
        }
    }

    private fun startPollingStatus() {
        if (isPollingStatus || currentQrcode.isBlank()) return
        isPollingStatus = true

        lifecycleScope.launch {
            var expired = false
            while (!expired && isPollingStatus) {
                try {
                    val status = IlinkApi.getQrcodeStatus(currentQrcode)
                    when (status) {
                        is QrcodeStatus.Confirmed -> {
                            isPollingStatus = false
                            authManager.saveBinding(status)
                            withContext(Dispatchers.Main) {
                                tvStatus?.text = "✅ 绑定成功！自动启动监听..."
                                tvInfo?.text = "微信ID: ${status.ilinkUserId}\n机器人: ${status.ilinkBotId}"
                                IlinkPollingService.start(this@WechatBindActivity)
                                Toast.makeText(this@WechatBindActivity, "微信已连接，消息监听已启动", Toast.LENGTH_LONG).show()
                                recreate()
                            }
                            return@launch
                        }
                        is QrcodeStatus.Scanned -> {
                            withContext(Dispatchers.Main) {
                                tvStatus?.text = "已扫描，请在手机上确认..."
                            }
                        }
                        is QrcodeStatus.Expired -> {
                            expired = true
                            withContext(Dispatchers.Main) {
                                tvStatus?.text = "二维码已过期，请刷新"
                            }
                        }
                        is QrcodeStatus.Waiting -> {}
                        null -> {
                            withContext(Dispatchers.Main) {
                                tvStatus?.text = "查询状态失败，请重试"
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "pollStatus: ${e.message}")
                }
                delay(2000)
            }
            isPollingStatus = false
        }
    }

    private fun unbind() {
        authManager.clearBinding()
        IlinkPollingService.stop(this)
        tvStatus?.text = "已解除绑定"
        recreate()
    }

    private fun startListening() {
        IlinkPollingService.start(this)
        tvStatus?.text = "监听已启动"
        Toast.makeText(this, "微信消息监听已启动", Toast.LENGTH_SHORT).show()
    }

    private fun stopListening() {
        IlinkPollingService.stop(this)
        tvStatus?.text = "监听已停止"
    }

    override fun onDestroy() {
        isPollingStatus = false
        super.onDestroy()
    }
}
