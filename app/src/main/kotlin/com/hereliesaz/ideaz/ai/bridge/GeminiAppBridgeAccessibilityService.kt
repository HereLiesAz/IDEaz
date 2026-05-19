package com.hereliesaz.ideaz.ai.bridge

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads the rendered response text out of the Gemini app's window, pipes it
 * back to [GeminiAppBridgeAdapter] via [GeminiAppBridge.channel]. Only active
 * while [GeminiAppBridge.isWaiting] is true; otherwise events are ignored so
 * the service does nothing in the background.
 *
 * Capture strategy: after each `TYPE_WINDOW_CONTENT_CHANGED` event from the
 * Gemini app, the service walks the active window's node tree and joins all
 * non-empty text into a single string. If the snapshot doesn't change for
 * [STABLE_MS] milliseconds, the service treats the response as complete:
 * it removes the user's pending prompt from the snapshot and ships whatever
 * remains to the bridge channel.
 *
 * The scraper is intentionally liberal — it does not depend on view ids or
 * specific class names that could shift between Gemini app versions.
 */
class GeminiAppBridgeAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastSnapshot: String = ""
    private var stableJob: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!GeminiAppBridge.isWaiting) return
        if (event == null) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg !in MONITORED_PACKAGES) return

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        val root = rootInActiveWindow ?: return
        val snapshot = collectText(root).trim()
        if (snapshot.isEmpty()) return

        if (snapshot == lastSnapshot) {
            // Snapshot unchanged; let the stable-timer continue running.
            return
        }
        lastSnapshot = snapshot

        // Reset the debounce timer.
        stableJob?.let { handler.removeCallbacks(it) }
        val job = Runnable { deliverResponse(snapshot) }
        stableJob = job
        handler.postDelayed(job, STABLE_MS)
    }

    private fun deliverResponse(snapshot: String) {
        if (!GeminiAppBridge.isWaiting) return
        val prompt = GeminiAppBridge.pendingPrompt.orEmpty()
        val response = extractResponse(snapshot, prompt)
        if (response.isBlank()) return
        GeminiAppBridge.isWaiting = false
        GeminiAppBridge.channel.trySend(response)
        // Don't reset lastSnapshot here — let onInterrupt or the next chat()
        // do it so a flapping content-change event right after delivery
        // doesn't immediately re-emit.
    }

    /**
     * Strip the prompt and other ambient UI text (greetings, suggestion
     * chips, etc.) by simple substring removal. The Gemini app shows the
     * user's prompt and the response in the same scrollable view, so what
     * we want is "everything not previously there and not the prompt
     * itself." A perfect implementation would diff against a pre-send
     * baseline; for now, take the snapshot and remove the prompt text.
     */
    private fun extractResponse(snapshot: String, prompt: String): String {
        var text = snapshot
        if (prompt.isNotBlank()) {
            text = text.replace(prompt, "").trim()
        }
        // Drop common chrome strings the Gemini app shows persistently.
        for (junk in CHROME_STRINGS) {
            text = text.replace(junk, "", ignoreCase = true)
        }
        return text.trim()
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: StringBuilder = StringBuilder()): String {
        if (node == null) return out.toString()
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
            if (out.isNotEmpty()) out.append('\n')
            out.append(it)
        }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let {
            // Only add content-description if it differs from the text node we
            // already captured (icons often have content descriptions but no
            // text; bubble text won't be content-description'd).
            if (node.text == null || node.text.toString() != it) {
                if (out.isNotEmpty()) out.append('\n')
                out.append(it)
            }
        }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), out)
        }
        return out.toString()
    }

    override fun onInterrupt() {
        stableJob?.let { handler.removeCallbacks(it) }
        stableJob = null
        lastSnapshot = ""
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastSnapshot = ""
    }

    companion object {
        private const val STABLE_MS = 2_000L

        // Packages the service monitors. The standalone Gemini app and
        // Google's Assistant share Gemini answering — covering both
        // increases the chance the bridge works on different devices.
        private val MONITORED_PACKAGES = setOf(
            "com.google.android.apps.bard",
            "com.google.android.googlequicksearchbox",
        )

        // Common ambient strings that aren't part of the response. Kept
        // conservative — false-positive removal here is worse than leaving
        // a little chrome in the answer.
        private val CHROME_STRINGS = listOf(
            "Enter a prompt here",
            "Listening",
            "Tap to talk",
        )
    }
}
