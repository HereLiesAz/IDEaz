package com.hereliesaz.ideaz.ui.web

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import com.hereliesaz.ideaz.models.ACTION_AI_LOG
import com.hereliesaz.ideaz.models.ACTION_WASM_COMPILE_SUCCESS
import com.hereliesaz.ideaz.models.EXTRA_MESSAGE
import com.hereliesaz.ideaz.models.EXTRA_WWW_DIR
import com.hereliesaz.ideaz.services.WasmCompilerService
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

/**
 * Composable WebView host for PWA and Web projects, and for the Compose
 * Multiplatform (Wasm) preview of Android projects.
 *
 * Content is served via [WebViewAssetLoader] from the virtual origin
 * `https://appassets.androidplatform.net/files/` mapped to [Context.filesDir].
 * This replaces the deprecated `allowFileAccessFromFileURLs` /
 * `allowUniversalAccessFromFileURLs` approach and gives the WebView a proper
 * same-origin policy and service-worker support.
 *
 * @param url            The URL to load. Remote URLs (e.g. GitHub Pages) are passed
 *                       through unchanged - but without the `Ideaz`/`IdeazBridge`
 *                       JS interfaces, which are only added for this app's own
 *                       asset-loader origin (see the load `LaunchedEffect` below).
 *                       For local projects [projectDir] is set and the project is
 *                       loaded from the asset-loader root instead.
 * @param projectDir     The local project directory to mount at the asset-loader root
 *                       (via [WebProjectPathHandler]). `null` for remote URLs.
 * @param reloadTrigger  Soft-reload signal. When this Long changes (and is > 0), the
 *                       WebView reloads the current page without clearing its cache.
 *                       Driven by [StateDelegate.webReloadTrigger].
 * @param hardReloadTrigger  Hard-reload signal. When this Long changes (and is > 0),
 *                           the WebView clears its disk/memory cache then reloads.
 *                           Driven by [StateDelegate.webHardReloadTrigger].
 *
 * Android projects are previewed through this same host: when
 * [WasmCompilerService] finishes compiling a project it broadcasts
 * [ACTION_WASM_COMPILE_SUCCESS], and this composable then mounts `filesDir/www`,
 * clears the WebView cache (Wasm binaries are aggressively cached and would
 * otherwise be re-instantiated from the stale copy) and hot-reloads the HTML
 * host page.
 */
@Composable
fun WebProjectHost(
    url: String,
    projectDir: java.io.File? = null,
    reloadTrigger: Long = 0L,
    hardReloadTrigger: Long = 0L,
    selectMode: Boolean = false,
    onElementContext: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Read ideaz-bridge.js once. If missing (misconfigured build), log and degrade gracefully.
    val bridgeJs: String? = remember {
        try {
            context.assets.open("ideaz-bridge.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            val intent = Intent(ACTION_AI_LOG).apply {
                putExtra(EXTRA_MESSAGE, "[WEB] ideaz-bridge.js missing from assets: ${e.message}")
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
            null
        }
    }

    val currentOnElementContext by rememberUpdatedState(onElementContext)
    val scope = rememberCoroutineScope()

    // Stable instances so add/remove below reference the same objects.
    // Explicit types: lint's JavascriptInterface check can't resolve the
    // @JavascriptInterface-annotated members of a class through remember<T>'s
    // generic inference, and reports "None of the methods in the added
    // interface (T) have been annotated" against the literal unresolved "T"
    // instead of the real type otherwise.
    val ideazJsInterface: IdeazJsInterface = remember { IdeazJsInterface(context) }
    val ideazBridge: WebViewBridge = remember { WebViewBridge { json -> scope.launch { currentOnElementContext(json) } } }

    // The active project directory is held in mutable state so the (single,
    // long-lived) asset loader always serves the currently-previewed project
    // without recreating the WebView when the user switches projects.
    val projectDirState = remember { mutableStateOf(projectDir) }

    // Set to `filesDir/www` once a Wasm compilation succeeds. While non-null it
    // takes precedence over [projectDir] as the mounted root, so the preview
    // shows the compiled binary rather than the project sources. Cleared when
    // the user switches project or URL.
    val wasmPreviewDir = remember { mutableStateOf<File?>(null) }

    // AssetLoader mounts the active project at the origin root
    // (https://appassets.androidplatform.net/) via WebProjectPathHandler, so
    // root-absolute references like /src/main.jsx resolve, and serves the bundled
    // in-browser runtime under /__ideaz__/. Other origins proceed normally.
    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .setDomain(WebProjectUrlUtils.ASSET_DOMAIN)
            .addPathHandler("/", WebProjectPathHandler(context) { projectDirState.value })
            .build()
    }

    val isWebViewDestroyed = remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // allowFileAccess is false (default). We do not need filesystem access;
                // all local content is served via WebViewAssetLoader.
                allowFileAccess = false
                // Disabled: prevents access to Android content providers.
                allowContentAccess = false
                // NOTE: allowFileAccessFromFileURLs and allowUniversalAccessFromFileURLs
                // have been intentionally removed. Those deprecated flags enabled a
                // cross-origin file-read attack surface. WebViewAssetLoader supersedes them.
                safeBrowsingEnabled = true
            }

            // Trust boundary: these interfaces are only added when the target
            // being loaded is this app's own WebViewAssetLoader origin - see
            // the LaunchedEffect below, which adds/removes them per navigation
            // rather than once here unconditionally. addJavascriptInterface has
            // no per-origin scoping of its own, so a remote URL loaded with
            // these always present would hand that page the same bridge local
            // project content gets. resourceId from onInspectResult is
            // path-validated downstream in SourceContextHelper before any file
            // access, independent of this.

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
                // Route asset-loader requests; return null for all other origins so
                // the WebView handles them normally (e.g. GitHub Pages URLs).
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    bridgeJs?.let { js -> view?.evaluateJavascript(js, null) }
                }

                // Surface HTTP errors (e.g. 404 for a missing asset/module served
                // by WebProjectPathHandler). These don't trigger onReceivedError,
                // so without this a missing file fails silently.
                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    val msg = "[WEB] HTTP ${errorResponse?.statusCode} for ${request?.url}"
                    val intent = Intent(ACTION_AI_LOG).apply {
                        putExtra(EXTRA_MESSAGE, msg)
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    val msg = "[WEB] Error loading ${request?.url}: ${error?.description}"
                    // Only show the user-visible Toast for main-frame failures; sub-resource
                    // errors (images, fonts, scripts) are logged but not surfaced as toasts.
                    if (request?.isForMainFrame == true) {
                        Toast.makeText(context, "Error: ${error?.description}", Toast.LENGTH_SHORT).show()
                    }
                    val intent = Intent(ACTION_AI_LOG).apply {
                        putExtra(EXTRA_MESSAGE, msg)
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)
                }
            }
        }
    }

    // Soft reload: triggered by file-system changes (ProjectFileObserver).
    // Skip the initial composition (reloadTrigger == 0L).
    LaunchedEffect(reloadTrigger) {
        if (reloadTrigger > 0L && !isWebViewDestroyed.value) {
            webView.reload()
        }
    }

    // Hard reload: clears disk + memory cache, then reloads.
    // Skip the initial composition (hardReloadTrigger == 0L).
    LaunchedEffect(hardReloadTrigger) {
        if (hardReloadTrigger > 0L && !isWebViewDestroyed.value) {
            webView.clearCache(true)
            webView.reload()
        }
    }

    // Mirror Android select-mode state into the web page cursor.
    // Guard: skip if no page is loaded yet (bridge not injected) or WebView is destroyed.
    LaunchedEffect(selectMode) {
        if (!isWebViewDestroyed.value && webView.url != null) {
            webView.evaluateJavascript("window.ideaz?.selectMode($selectMode);", null)
        }
    }

    // INSPECT_WEB broadcast: Phase 1B tap-to-select plumbing (already present).
    // WASM_COMPILE_SUCCESS: the Compose/Wasm hot-reload path for Android projects.
    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_WASM_COMPILE_SUCCESS) {
                    if (isWebViewDestroyed.value) return
                    val dir = intent.getStringExtra(EXTRA_WWW_DIR)
                        ?.let { File(it) }
                        ?: File(webView.context.filesDir, WasmCompilerService.WWW_DIR_NAME)
                    // Mount the compiler's output directory, drop every cached
                    // copy of the previous binary, then re-enter the host page so
                    // the fresh .wasm is instantiated.
                    wasmPreviewDir.value = dir
                    projectDirState.value = dir
                    webView.clearCache(true)
                    webView.loadUrl(WebProjectUrlUtils.localProjectRootUrl())
                    return
                }

                if (intent?.action == "com.hereliesaz.ideaz.INSPECT_WEB") {
                    val x = intent.getFloatExtra("X", 0f)
                    val y = intent.getFloatExtra("Y", 0f)
                    val density = context?.resources?.displayMetrics?.density ?: 1f
                    val webX = x / density
                    val webY = y / density

                    val js = """
                        (function() {
                            var el = document.elementFromPoint($webX, $webY);
                            if (!el) return;
                            while (el && el.nodeType !== 1) { el = el.parentElement; }
                            if (!el) return;
                            var ctx = window.ideaz && window.ideaz.getElementContext(el);
                            if (ctx) {
                                IdeazBridge.onElementTapped(JSON.stringify(ctx));
                            }
                        })();
                    """.trimIndent()
                    webView.evaluateJavascript(js, null)
                }
            }
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter("com.hereliesaz.ideaz.INSPECT_WEB").apply {
            addAction(ACTION_WASM_COMPILE_SUCCESS)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Load (and reload on project / URL change). Local projects load from the
    // asset-loader root; remote URLs load as-is. Keyed so unrelated recompositions
    // (e.g. selectMode toggles) don't trigger reloads.
    // A project/URL switch invalidates any mounted Wasm preview — the compiled
    // binary belongs to the project we just left.
    LaunchedEffect(projectDir, url) {
        wasmPreviewDir.value = null
        projectDirState.value = projectDir
        if (!isWebViewDestroyed.value) {
            val target = if (projectDir != null) WebProjectUrlUtils.localProjectRootUrl() else url
            // Only expose the privileged bridge when we're actually loading
            // this app's own asset-loader origin - a remote URL (the "passed
            // through unchanged" path below) gets no bridge at all.
            if (target.startsWith(WebProjectUrlUtils.ASSET_ROOT_URL)) {
                webView.addJavascriptInterface(ideazJsInterface, "Ideaz")
                webView.addJavascriptInterface(ideazBridge, "IdeazBridge")
            } else {
                webView.removeJavascriptInterface("Ideaz")
                webView.removeJavascriptInterface("IdeazBridge")
            }
            webView.loadUrl(target)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { webView },
        update = { projectDirState.value = wasmPreviewDir.value ?: projectDir }
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
            isWebViewDestroyed.value = true
            webView.destroy()
        }
    }
}
