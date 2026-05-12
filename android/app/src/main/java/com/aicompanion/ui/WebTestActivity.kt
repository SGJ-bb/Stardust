package com.aicompanion.ui

import android.app.Activity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aicompanion.R

class WebTestActivity : Activity() {

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_test)

        webView = findViewById(R.id.webView)
        webView?.settings?.javaScriptEnabled = true
        webView?.webChromeClient = WebChromeClient()
        webView?.webViewClient = WebViewClient()
        webView?.loadUrl("file:///android_asset/vtuber/test_live2d.html")
    }
}
