package com.example.reactnativeapp

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()

        // Allow cross-origin for local files if needed (usually stricter on modern Android)
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        webView.loadUrl("file:///android_asset/index.html")
        setContentView(webView)
    }
}
