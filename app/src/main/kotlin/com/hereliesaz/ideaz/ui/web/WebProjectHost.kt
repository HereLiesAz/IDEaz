package com.hereliesaz.ideaz.ui.web

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hereliesaz.ideaz.models.ACTION_AI_LOG
import com.hereliesaz.ideaz.models.EXTRA_MESSAGE
import java.io.File

class IdeazJsInterface(private val context: Context) {
    @JavascriptInterface
    fun onInspectResult(resourceId: String) {
        val intent = Intent("com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE").apply {
            putExtra("RESOURCE_ID", resourceId)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}

@Composable
fun WebProjectHost(
    url: String,
    reloadTrigger: StateFlow<Long>? = null,
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
                // IDEaz: Enabled to allow loading the project's own files from local storage.
                allowFileAccess = true
                // SENTINEL: Disabled to prevent access to Android content providers (contacts, etc.).
                allowContentAccess = false
                // IDEaz: Enabled to allow loading app.js relative to index.html
                allowFileAccessFromFileURLs = true
                // IDEaz: Enabled to allow cross-origin requests for local files (modules, imports)
                allowUniversalAccessFromFileURLs = true
                // SENTINEL: Enable Safe Browsing to protect against known threats (phishing, malware).
                safeBrowsingEnabled = true
            }

            addJavascriptInterface(IdeazJsInterface(context), "Ideaz")

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    // Bridge console log to IDE
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

    if (reloadTrigger != null) {
        val trigger by reloadTrigger.collectAsState()
        LaunchedEffect(trigger) {
            if (trigger > 0L) {
                webView.reload()
            }
        }
    }

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.hereliesaz.ideaz.INSPECT_WEB") {
                    val x = intent.getFloatExtra("X", 0f)
                    val y = intent.getFloatExtra("Y", 0f)
                    val density = context?.resources?.displayMetrics?.density ?: 1f
                    val webX = x / density
                    val webY = y / density

                    val js = """
                        (function() {
                            var el = document.elementFromPoint($webX, $webY);
                            var source = null;
                            while (el) {
                                var label = el.getAttribute('aria-label');
                                if (label && label.startsWith('__source:')) {
                                    source = label;
                                    break;
                                }
                                el = el.parentElement;
                            }
                            if (source) {
                                Ideaz.onInspectResult(source);
                            }
                        })();
                    """
                    webView.evaluateJavascript(js, null)
                }
            }
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter("com.hereliesaz.ideaz.INSPECT_WEB")
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
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
