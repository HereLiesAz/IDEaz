package com.hereliesaz.ideaz.ui.web

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File

@Composable
fun WebProjectHost(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Remember the WebView instance to manage its lifecycle
    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        val msg = "[WEB] ${it.message()} (${it.sourceId()}:${it.lineNumber()})"
                        val intent = Intent("com.hereliesaz.ideaz.AI_LOG").apply {
                            putExtra("MESSAGE", msg)
                        }
                        context.sendBroadcast(intent)
                    }
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    val msg = "[WEB] Error loading ${request?.url}: ${error?.description}"
                    val intent = Intent("com.hereliesaz.ideaz.AI_LOG").apply {
                        putExtra("MESSAGE", msg)
                    }
                    context.sendBroadcast(intent)
                }
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { webView },
        update = { view ->
            if (view.url != url) {
                view.loadUrl(url)
            }
        }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                Lifecycle.Event.ON_DESTROY -> webView.destroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Note: We don't call destroy() here because the WebView might be reused or
            // the composable might be recomposed. ON_DESTROY handles final cleanup.
            // But if the composable leaves the screen, we should probably destroy it if we created it in remember.
            // However, remember { WebView } keeps it alive across recompositions, but onDispose means it's gone from UI.
            // Ideally, we should destroy it on dispose to avoid leaks.
            webView.destroy()
        }
    }
}
