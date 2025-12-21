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
import android.widget.Toast
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
import com.hereliesaz.ideaz.models.ACTION_AI_LOG
import com.hereliesaz.ideaz.models.EXTRA_MESSAGE
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
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        val msg = "[WEB] ${it.message()} (${it.sourceId()}:${it.lineNumber()})"
                        val intent = Intent(ACTION_AI_LOG).apply {
                            putExtra(EXTRA_MESSAGE, msg)
                            setPackage(context.packageName)
                        }
                        context.sendBroadcast(intent)
                    }
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    val msg = "[WEB] Error loading ${request?.url}: ${error?.description}"
                    Toast.makeText(context, "Error: ${error?.description}", Toast.LENGTH_SHORT).show()
                    val intent = Intent(ACTION_AI_LOG).apply {
                        putExtra(EXTRA_MESSAGE, msg)
                        setPackage(context.packageName)
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
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Destroy the WebView to prevent memory leaks when the composable is disposed.
            webView.destroy()
        }
    }
}
