package com.aicompanion.game.engine

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * JS → Kotlin 桥(M0)。
 * 前端调用 window.AndroidGameBridge.postToNative(json)，这里在主线程派发给消费者。
 * 协议: { type: String, payload?: any }。
 */
class JsBridge(
    private val onEvent: (type: String, payload: JSONObject?) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postToNative(msg: String) {
        // postToNative 可能从 JS 线程回调，切主线程再派发
        mainHandler.post {
            runCatching {
                val obj = JSONObject(msg)
                val type = obj.optString("type")
                val payload = obj.opt("payload").takeIf { it is JSONObject } as? JSONObject
                onEvent(type, payload)
            }.onFailure {
                onEvent("__error", JSONObject().put("detail", msg))
            }
        }
    }
}