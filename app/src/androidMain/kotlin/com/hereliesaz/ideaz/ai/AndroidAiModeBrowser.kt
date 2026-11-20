package com.hereliesaz.ideaz.ai

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

/**
 * The browser behind Google AI Mode: a WebView that is never shown, on the user's own connection.
 * Cookies are kept (in the app's shared cookie jar) on purpose: they are what makes a passed human
 * check or consent choice stick for the next question. Cache is cleared with each view.
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
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            view.loadUrl(url)
            read(WebPage(view))
        } finally {
            view.stopLoading()
            view.clearCache(true)
            view.destroy()
        }
    }

    override suspend fun showToUser(url: String): Boolean = checkLock.withLock {
        val check = HumanCheck(url, CompletableDeferred())
        _humanCheck.value = check
        try {
            check.closed.await()
        } finally {
            _humanCheck.compareAndSet(check, null)
            withContext(Dispatchers.Main) { CookieManager.getInstance().flush() }
        }
        true
    }

    /** The user closed the visible page; the waiting question is asked again. */
    fun finishHumanCheck(check: HumanCheck) {
        check.closed.complete(Unit)
    }

    private class WebPage(private val view: WebView) : AiModePage {
        override suspend fun text(): String = suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript("(function(){return document.body ? document.body.innerText : ''})()") { raw ->
                // The script's string comes back JSON-quoted ("null" before a page exists).
                val text = runCatching { Json.decodeFromString<String>(raw.orEmpty()) }.getOrDefault("")
                if (continuation.isActive) continuation.resume(text)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: AndroidAiModeBrowser? = null

        fun get(context: Context): AndroidAiModeBrowser =
            instance ?: synchronized(this) { instance ?: AndroidAiModeBrowser(context).also { instance = it } }

        /** The browser if one was created, without creating it (the UI only needs it once used). */
        fun existing(): AndroidAiModeBrowser? = instance
    }
}
