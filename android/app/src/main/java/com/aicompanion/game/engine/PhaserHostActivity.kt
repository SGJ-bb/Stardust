package com.aicompanion.game.engine

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/**
 * Phaser 游戏宿主 Activity(M0)。
 * 独立模块: 用纯代码构建 WebView 加载 assets/game/index.html，
 *         不依赖既有 R/layout，最小化对主工程其他功能的影响。
 * 桥: JS→Kotlin 走 JsBridge.postToNative；Kotlin→JS 走 sendToJs 调 window.__onNativeEvent。
 */
class PhaserHostActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var gameLoop: GameLoop? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = createWebView()
        setContentView(webView)
        bindBridge()
        loadGame()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val wv = WebView(this)
        wv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        wv.setBackgroundColor(0xFF2b1f3d.toInt())
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setBuiltInZoomControls(false)
            setSupportZoom(false)
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = WebViewClient()
        return wv
    }

    /** JS → Kotlin 双向桥接线。 */
    private fun bindBridge() {
        val bridge = JsBridge { type, payload ->
            when (type) {
                "game_ready" -> onGameReady()
                else -> Log.d(TAG, "native event: $type $payload")
            }
        }
        webView.addJavascriptInterface(bridge, "AndroidGameBridge")
    }

    private fun loadGame() {
        // M0 加载构建产物; 由构建脚本复制到 assets/game
        webView.loadUrl("file:///android_asset/game/index.html")
    }

    /** JS 上报渲染就绪后: 完成双向验证, 并启动 M1 确定性游戏循环。 */
    private fun onGameReady() {
        val payload = JSONObject().put("text", "来自 Kotlin · 已识别就绪")
        sendToJs("hello", payload)
        startGameLoop()
    }

    /** 启动 M1 闭环: Kotlin 计算 → 经桥下发 NPC 位置 → 前端渲染。 */
    private fun startGameLoop() {
        if (gameLoop != null) return
        gameLoop = GameLoop { _, moves ->
            moves.forEach { m ->
                val payload = JSONObject()
                    .put("id", m.id)
                    .put("x", m.x)
                    .put("y", m.y)
                    .put("flip", m.flipLeft)
                    .put("moving", m.moving)
                sendToJs("npc_move", payload)
            }
        }.also { it.start() }
    }

    /** Kotlin → JS 下发统一 JSON 事件。 */
    private fun sendToJs(type: String, payload: JSONObject?) {
        runCatching {
            val evt = JSONObject()
                .put("type", type)
                .put("payload", payload ?: JSONObject())
            val jsonLiteral = JSONObject.quote(evt.toString())
            val js = "window.__onNativeEvent && window.__onNativeEvent($jsonLiteral)"
            // 必须主线程
            runOnUiThread { webView.evaluateJavascript(js, null) }
        }.onFailure { Log.e(TAG, "sendToJs failed", it) }
    }

    // ===== 生命周期: 复用 Live2DWebView 的暂停/恢复/销毁经验 =====
    override fun onResume() {
        super.onResume()
        runCatching { webView.onResume() }.onFailure { it.printStackTrace() }
    }

    override fun onPause() {
        runCatching { webView.onPause() }.onFailure { it.printStackTrace() }
        super.onPause()
    }

    override fun onDestroy() {
        gameLoop?.stop()
        runCatching {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }.onFailure { it.printStackTrace() }
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    companion object {
        private const val TAG = "PhaserHost"
    }
}