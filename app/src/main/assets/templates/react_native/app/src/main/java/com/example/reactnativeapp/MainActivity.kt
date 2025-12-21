package com.example.reactnativeapp

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.widget.Toast
import android.util.Log

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()

        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")

        webView.loadUrl("file:///android_asset/index.html")
        setContentView(webView)
    }

    class AndroidBridge(private val activity: Activity) {
        @JavascriptInterface
        fun log(message: String) {
            Log.d("ReactNativeApp", message)
        }

        @JavascriptInterface
        fun showToast(message: String) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
