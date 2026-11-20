package com.hereliesaz.ideaz.ai

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

/**
 * The browser behind Google AI Mode: a WebView that is never shown, on the user's own connection.
 * Cookies are kept on purpose: they are what makes a passed human check or consent choice stick
 * for the next question. They live in a WebView profile of their own ([PROFILE]), apart from the
 * project preview's, where the WebView supports profiles; older WebViews fall back to the shared
 * jar. The view refuses to navigate anywhere but Google ([GoogleOnlyClient]).
 *
 * When Google asks for a person, [showToUser] publishes the page in [humanCheck]; MainScreen shows
 * it in a dialog ([GoogleAiModeCheckDialog]) and calls [finishHumanCheck] when the user closes it.
 */
@SuppressLint("SetJavaScriptEnabled")
class AndroidAiModeBrowser private constructor(context: Context) : AiModeBrowser {
    private val appContext = context.applicationContext

    /** The page to show the user now, or null. */
    data class HumanCheck(val url: String, internal val closed: CompletableDeferred<Unit>)

    private val _humanCheck = MutableStateFlow<HumanCheck?>(null)
    val humanCheck: StateFlow<HumanCheck?> = _humanCheck.asStateFlow()

    /** One visible check at a time. */
    private val checkLock = Mutex()

    override suspend fun <T> withPage(url: String, read: suspend (AiModePage) -> T): T = withContext(Dispatchers.Main) {
        val view = WebView(appContext)
        try {
            val cookies = isolate(view)
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.webViewClient = GoogleOnlyClient
            cookies.setAcceptCookie(true)
            view.loadUrl(url)
            read(WebPage(view))
        } finally {
            view.stopLoading()
            view.destroy()
        }
    }

    /** Number of dialogs currently collecting [humanCheck]; zero means nobody would see a check. */
    private val _watchers = java.util.concurrent.atomic.AtomicInteger(0)

    /** Called by the dialog while it is on screen. */
    fun watch(): AutoCloseable {
        _watchers.incrementAndGet()
        return AutoCloseable { _watchers.decrementAndGet() }
    }

    override suspend fun showToUser(url: String): Boolean {
        if (_watchers.get() == 0) return false // no screen to show it on: report the error instead of hanging
        return checkLock.withLock {
            val check = HumanCheck(url, CompletableDeferred())
            _humanCheck.value = check
            try {
                withTimeoutOrNull(HUMAN_CHECK_TIMEOUT_MS) { check.closed.await() } != null
            } finally {
                _humanCheck.compareAndSet(check, null)
                withContext(Dispatchers.Main) { cookieManager().flush() }
            }
        }
    }

    /** The user closed the visible page; the waiting question is asked again. */
    fun finishHumanCheck(check: HumanCheck) {
        check.closed.complete(Unit)
    }

    private class WebPage(private val view: WebView) : AiModePage {
        // A view destroyed mid-call may never answer; treat that as an empty page.
        override suspend fun text(): String = withTimeoutOrNull(READ_TIMEOUT_MS) { read() }.orEmpty()

        private suspend fun read(): String = suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript("(function(){return document.body ? document.body.innerText : ''})()") { raw ->
                // The script's string comes back JSON-quoted ("null" before a page exists).
                val text = runCatching { Json.decodeFromString<String>(raw.orEmpty()) }.getOrDefault("")
                if (continuation.isActive) continuation.resume(text)
            }
        }
    }

    /** Keeps the hidden view on Google: a redirect elsewhere is not followed. */
    private object GoogleOnlyClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !isGoogle(request.url)
    }

    companion object {
        /** The WebView profile AI Mode's views share, so Google's cookies stay out of other WebViews. */
        const val PROFILE = "google-ai-mode"

        private fun profilesSupported() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

        /**
         * Puts [view] in [PROFILE] (must run on the main thread, before the view loads anything)
         * and returns the cookie jar it now uses.
         */
        fun isolate(view: WebView): CookieManager {
            if (!profilesSupported()) return CookieManager.getInstance()
            WebViewCompat.setProfile(view, PROFILE)
            return cookieManager()
        }

        /** [PROFILE]'s cookie jar, or the shared one where profiles are unsupported. Main thread. */
        fun cookieManager(): CookieManager =
            if (profilesSupported()) ProfileStore.getInstance().getOrCreateProfile(PROFILE).cookieManager
            else CookieManager.getInstance()

        private const val HUMAN_CHECK_TIMEOUT_MS = 5 * 60_000L
        private const val READ_TIMEOUT_MS = 5_000L

        /** google.com, its country domains (google.co.uk, google.de) and their subdomains, over https. */
        internal fun isGoogle(uri: Uri): Boolean {
            if (uri.scheme != "https") return false
            val host = uri.host?.lowercase() ?: return false
            return Regex("""(^|\.)google\.(com|[a-z]{2}|co\.[a-z]{2}|com\.[a-z]{2})$""").containsMatchIn(host)
        }

        @Volatile
        private var instance: AndroidAiModeBrowser? = null

        fun get(context: Context): AndroidAiModeBrowser =
            instance ?: synchronized(this) { instance ?: AndroidAiModeBrowser(context).also { instance = it } }

        /** The browser if one was created, without creating it (the UI only needs it once used). */
        fun existing(): AndroidAiModeBrowser? = instance
    }
}
