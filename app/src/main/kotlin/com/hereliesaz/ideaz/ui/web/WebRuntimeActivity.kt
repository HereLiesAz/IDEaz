package com.hereliesaz.ideaz.ui.web

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class WebRuntimeActivity : ComponentActivity() {

    private val webUrl = mutableStateOf<String?>(null)
    private val reloadTrigger = mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val url by remember { webUrl }
            val trigger by remember { reloadTrigger }
            WebRuntimeScreen(url = url ?: "about:blank", trigger = trigger)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            val newUrl = it.getStringExtra("URL")
            if (newUrl != null) {
                webUrl.value = newUrl
            }
            reloadTrigger.value = it.getLongExtra("TIMESTAMP", System.currentTimeMillis())
        }
    }
}

@Composable
fun WebRuntimeScreen(url: String, trigger: Long) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                webViewClient = WebViewClient()
                // Initial load handled by update or here?
                // If we load here, update logic needs to handle first run correctly.
                // Let's let update handle it by ensuring trigger is set.
            }
        },
        update = { webView ->
            val lastTrigger = webView.tag as? Long ?: -1L
            if (lastTrigger != trigger) {
                webView.loadUrl(url)
                webView.tag = trigger
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
