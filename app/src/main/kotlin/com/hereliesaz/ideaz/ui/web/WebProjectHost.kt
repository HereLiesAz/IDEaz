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
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import com.hereliesaz.ideaz.models.ACTION_AI_LOG
import com.hereliesaz.ideaz.models.ACTION_WASM_COMPILE_SUCCESS
import com.hereliesaz.ideaz.models.EXTRA_MESSAGE
import com.hereliesaz.ideaz.models.EXTRA_WWW_DIR
import java.io.File

/**
 * Translates a WebView/Chromium net-error description into a plain-language
 * explanation with an actionable next step, falling back to the raw text for
 * anything not recognized. Previously a page load failure surfaced the
 * verbatim Chromium string (e.g. "net::ERR_CLEARTEXT_NOT_PERMITTED") directly
 * in a Toast with no translation - meaningless to a user who isn't debugging
 * a WebView.
 */
private fun humanizeWebError(description: String?): String {
    val raw = description.orEmpty()
    val explanation = when {
        raw.contains("ERR_CLEARTEXT_NOT_PERMITTED") ->
            "This project tried to load an insecure (http://) resource, which isn't allowed. Use https:// instead."
        raw.contains("ERR_NAME_NOT_RESOLVED") || raw.contains("ERR_INTERNET_DISCONNECTED") ->
            "Couldn't reach the network. Check your connection."
        raw.contains("ERR_CONNECTION_REFUSED") || raw.contains("ERR_CONNECTION_TIMED_OUT") ->
            "The server didn't respond. It may be down or unreachable."
        raw.contains("ERR_CERT_") ->
            "This site's security certificate couldn't be verified."
        else -> null
    }
    return if (explanation != null) "$explanation ($raw)" else "Error: $raw"
}

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
 * [ACTION_WASM_COMPILE_SUCCESS], and this composable mounts `filesDir/www` and
 * reloads. No cache-clear is needed for that reload to pick up the fresh
 * .wasm - see [WebProjectPathHandler]'s no-store response headers. This does
 * not preserve Compose UI state across a reload; the Wasm module (and
 * everything it renders to its `<canvas>`) is fully reinstantiated each time.
 */
// Lint's JavascriptInterface check can't resolve the annotated members of
// IdeazJsInterface/WebViewBridge through a remember<T>-typed local (confirmed:
// an explicit type annotation on the val didn't change its verdict either),
// so it reports "None of the methods in the added interface (T) have been
// annotated" against the unresolved literal "T". Both classes genuinely do
// have an @JavascriptInterface-annotated method (IdeazJsInterface.onInspectResult,
// WebViewBridge.onElementTapped) - this is a false positive, not a real gap.
@Suppress("JavascriptInterface")
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

                // The LaunchedEffect(projectDir, url) below only reacts to Compose
                // state changes, so a page that navigates itself away from the
                // asset-loader origin - `location.href = 'https://evil.com'`, or
                // just a same-page top-level link - never triggered that effect and
                // kept the privileged Ideaz/IdeazBridge interfaces live on whatever
                // origin the page moved to. onPageStarted fires on every committed
                // navigation (redirects included), so re-check here too.
                // Known residual gap: addJavascriptInterface has no per-frame
                // origin scoping in WebView - an iframe to a different origin still
                // gets the same interfaces as the top-level page, since the API
                // binds at the WebView level, not per-frame. Closing that
                // completely needs migrating off addJavascriptInterface onto
                // WebViewCompat.addWebMessageListener's origin allowlisting, which
                // is a bigger change (touches ideaz-bridge.js's calling convention
                // too) tracked separately, not attempted here.
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (url != null && !url.startsWith(WebProjectUrlUtils.ASSET_ROOT_URL)) {
                        view?.removeJavascriptInterface("Ideaz")
                        view?.removeJavascriptInterface("IdeazBridge")
                    }
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
                        Toast.makeText(context, humanizeWebError(error?.description?.toString()), Toast.LENGTH_LONG).show()
                    }
                    val intent = Intent(ACTION_AI_LOG).apply {
                        putExtra(EXTRA_MESSAGE, msg)
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)
                }
            }

            // Service Worker requests (registration, fetch, etc.) do NOT go through
            // WebViewClient.shouldInterceptRequest above - that only covers the
            // page's own document/subresource loads. Without this separate hook, a
            // project's `sw.js` registration against the WebViewAssetLoader virtual
            // origin fails with "An unknown error occurred when fetching the
            // script", since nothing services that request.
            //
            // ServiceWorkerControllerCompat.getInstance() is a process-wide
            // singleton, NOT tied to this WebView instance - re-registering here is
            // NOT idempotent (that was previously claimed but wrong): each
            // `remember { WebView(...) }` call installs a fresh anonymous client
            // capturing this composition's own `assetLoader`, last-writer-wins.
            // Left unmanaged, a destroyed composition's client (closing over a
            // dead assetLoader/context) could keep serving the singleton after
            // this WebView is gone. The onDispose below nulls it back out.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)
            ) {
                // The main WebView's settings above deliberately disable
                // allowFileAccess/allowContentAccess - the Service Worker context
                // has its own, separate settings object that was never touched and
                // defaults to allowing both, silently reopening the exact access
                // the main settings were hardened to close (just from a service
                // worker's fetch handler instead of a page script).
                if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_FILE_ACCESS) &&
                    WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_CONTENT_ACCESS)
                ) {
                    ServiceWorkerControllerCompat.getInstance().serviceWorkerWebSettings.apply {
                        allowFileAccess = false
                        allowContentAccess = false
                    }
                }
                ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(object : ServiceWorkerClientCompat() {
                    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                        assetLoader.shouldInterceptRequest(request.url)
                })
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
                        ?: return
                    // Mount the compiler's output directory, then re-enter the
                    // host page at its root - not a plain reload(), which would
                    // re-request whatever URL the page is currently on. A CMP
                    // app can push its own History API entries (client-side
                    // routing), so the WebView may no longer be sitting at the
                    // freshly-mounted output's root by the time this fires.
                    // No cache to clear first: WebProjectPathHandler marks
                    // every project response no-store, so this always re-fetches
                    // the fresh .wasm/.js pair even though their filenames (and
                    // therefore this URL) are unchanged across recompiles. This
                    // does not preserve UI state - the Wasm module is fully
                    // reinstantiated - Kotlin/Wasm has no JVM-style live class
                    // redefinition to build a true state-preserving hot reload on
                    // top of.
                    wasmPreviewDir.value = dir
                    projectDirState.value = dir
                    // If the page most recently navigated away from this app's
                    // own origin (e.g. a top-level link tap), onPageStarted
                    // below already stripped the Ideaz/IdeazBridge interfaces.
                    // The LaunchedEffect(projectDir, url) that normally re-adds
                    // them only fires on a project/url *switch*, not on this
                    // broadcast-driven navigation - re-add them here too, since
                    // the target is always this app's trusted origin.
                    webView.addJavascriptInterface(ideazJsInterface, "Ideaz")
                    webView.addJavascriptInterface(ideazBridge, "IdeazBridge")
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
            // Every local project is served from the SAME origin
            // (WebProjectUrlUtils.ASSET_ROOT_URL) with nothing else scoping
            // storage per project. Previously switching projects only cleared
            // the HTTP cache (the hardReloadTrigger effect above) - a service
            // worker registered by project A survived into project B (and past
            // app restarts), able to intercept B's requests and read/serve
            // stale content for it. Tear down everything the outgoing page
            // could have left behind before loading the next one.
            if (webView.url?.startsWith(WebProjectUrlUtils.ASSET_ROOT_URL) == true) {
                webView.evaluateJavascript(
                    "if (navigator.serviceWorker) { navigator.serviceWorker.getRegistrations()" +
                        ".then(rs => rs.forEach(r => r.unregister())); }",
                    null,
                )
            }
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.WebStorage.getInstance().deleteOrigin(WebProjectUrlUtils.ASSET_ROOT_URL)

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
            // See the comment where this client is installed above: the
            // controller is a process-wide singleton, so a destroyed
            // composition's client (closing over this now-dead assetLoader)
            // must not keep serving Service Worker requests after this WebView
            // is gone.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
                ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(null)
            }
            webView.destroy()
        }
    }
}
