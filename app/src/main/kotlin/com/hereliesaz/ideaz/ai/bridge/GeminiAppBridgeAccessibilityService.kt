package com.hereliesaz.ideaz.ai.bridge

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Captures the Gemini app's response and pipes it back to
 * [GeminiAppBridgeAdapter] via [GeminiAppBridge.channel]. Only active while
 * [GeminiAppBridge.isWaiting] is true.
 *
 * Capture strategy, in order of preference:
 *  1. **Copy button.** The Gemini app always renders a "Copy" affordance for a
 *     response. Once the content stabilises we click the latest one and read the
 *     clipboard — that yields the FULL response verbatim, regardless of scroll
 *     position or formatting. (Background clipboard reads are restricted on
 *     Android 10+, so this can come back empty; if so we fall back.)
 *  2. **Text scrape.** Walk the window's node tree, join visible text, and strip
 *     the prompt. Liberal and version-tolerant, but only sees on-screen text.
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
        if (snapshot == lastSnapshot) return
        lastSnapshot = snapshot

        // Debounce: act once the content has stopped changing (response done).
        stableJob?.let { handler.removeCallbacks(it) }
        val job = Runnable { onStable(snapshot) }
        stableJob = job
        handler.postDelayed(job, STABLE_MS)
    }

    private fun onStable(snapshot: String) {
        if (!GeminiAppBridge.isWaiting) return
        val root = rootInActiveWindow
        val copyNode = root?.let { findLatestCopyNode(it) }
        if (copyNode != null && copyNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            // Give the app a beat to populate the clipboard, then read it.
            handler.postDelayed({ deliverFromClipboard(snapshot) }, CLIP_DELAY_MS)
        } else {
            deliverScraped(snapshot)
        }
    }

    private fun deliverFromClipboard(fallbackSnapshot: String) {
        if (!GeminiAppBridge.isWaiting) return
        val clip = try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        } catch (e: Exception) {
            null
        }
        val text = clip?.trim()
        if (!text.isNullOrBlank()) {
            GeminiAppBridge.isWaiting = false
            GeminiAppBridge.channel.trySend(text)
        } else {
            // Clipboard empty/unreadable (background-read restriction) — scrape.
            deliverScraped(fallbackSnapshot)
        }
    }

    private fun deliverScraped(snapshot: String) {
        if (!GeminiAppBridge.isWaiting) return
        val response = extractResponse(snapshot, GeminiAppBridge.pendingPrompt.orEmpty())
        if (response.isBlank()) return
        GeminiAppBridge.isWaiting = false
        GeminiAppBridge.channel.trySend(response)
    }

    /**
     * Find the last clickable node that looks like a "copy response" affordance.
     * Last-in-tree-order ≈ the most recent response's button.
     */
    private fun findLatestCopyNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var match: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            val hint = (n.contentDescription?.toString() ?: n.text?.toString())?.lowercase()
            if (hint != null && n.isClickable && COPY_HINTS.any { hint.contains(it) }) {
                match = n
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return match
    }

    private fun extractResponse(snapshot: String, prompt: String): String {
        var text = snapshot
        if (prompt.isNotBlank()) {
            text = text.replace(prompt, "").trim()
        }
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
        private const val STABLE_MS = 2_500L
        private const val CLIP_DELAY_MS = 350L

        private val MONITORED_PACKAGES = setOf(
            "com.google.android.apps.bard",
            "com.google.android.googlequicksearchbox",
        )

        // Substrings that mark a "copy response/code" button (case-insensitive).
        private val COPY_HINTS = listOf("copy")

        private val CHROME_STRINGS = listOf(
            "Enter a prompt here",
            "Listening",
            "Tap to talk",
        )
    }
}
