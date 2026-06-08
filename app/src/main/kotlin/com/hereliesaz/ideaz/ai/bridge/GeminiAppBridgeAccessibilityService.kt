package com.hereliesaz.ideaz.ai.bridge

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.hereliesaz.ideaz.services.IdeazOverlayService

/**
 * Drives the Gemini app for [GeminiAppBridgeAdapter] over [GeminiAppBridge]. Two
 * phases, both gated on [GeminiAppBridge.isWaiting]:
 *
 *  1. **INPUT** ([GeminiAppBridge.BridgePhase.INPUT]) — the share intent has
 *     opened Gemini's compose screen with `project.txt` attached, but Gemini
 *     drops the intent's `EXTRA_TEXT`, so the prompt field is empty. We find the
 *     editable field, type [GeminiAppBridge.pendingPrompt] into it, then tap Send.
 *  2. **AWAIT_RESPONSE** ([GeminiAppBridge.BridgePhase.AWAIT_RESPONSE]) — capture
 *     the reply, in order of preference:
 *       a. **Copy button.** Click the latest "Copy" affordance and read the
 *          clipboard — the FULL response verbatim. (Background clipboard reads are
 *          restricted on Android 10+, so this can come back empty; if so we fall back.)
 *       b. **Text scrape.** Walk the node tree, join visible text, strip the prompt.
 *
 * On the first Gemini event of a run we also re-assert the touch-block scrim, in
 * case [GeminiAppBridgeAdapter] raced it up before Gemini came to the foreground.
 */
class GeminiAppBridgeAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastSnapshot: String = ""
    private var stableJob: Runnable? = null
    private var inputJob: Runnable? = null

    /** Re-assert the block scrim only once per run (reset when the run ends). */
    private var blockReasserted = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!GeminiAppBridge.isWaiting) return
        if (event == null) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg !in MONITORED_PACKAGES) return

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        // Gemini is now foreground — make sure the scrim is on top of it.
        reassertBlock()

        when (GeminiAppBridge.phase) {
            GeminiAppBridge.BridgePhase.INPUT -> {
                // Throttle, don't debounce: Gemini fires a continuous stream of
                // events while ingesting the file (cursor blink, animations), so
                // resetting the timer each time could starve onInputStable. Only
                // schedule when nothing is pending — it then fires after the delay
                // regardless of how many more events arrive.
                if (inputJob == null) {
                    val job = Runnable {
                        inputJob = null
                        onInputStable()
                    }
                    inputJob = job
                    handler.postDelayed(job, INPUT_STABLE_MS)
                }
            }

            GeminiAppBridge.BridgePhase.AWAIT_RESPONSE -> {
                val root = geminiRoot() ?: return
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

            GeminiAppBridge.BridgePhase.IDLE -> { /* nothing to do */ }
        }
    }

    // ---- INPUT phase ---------------------------------------------------------

    /**
     * Type the prompt and submit, idempotently across the storm of content
     * changes Gemini fires while it ingests the attached file. We fill on one
     * pass and click Send on a later pass: setting the text re-fires a content
     * change, by which point Send has enabled itself.
     */
    private fun onInputStable() {
        if (!GeminiAppBridge.isWaiting) return
        if (GeminiAppBridge.phase != GeminiAppBridge.BridgePhase.INPUT) return
        if (GeminiAppBridge.promptSubmitted) return

        val root = geminiRoot() ?: return
        val field = findInputField(root) ?: return // not laid out yet — retry next event
        val prompt = GeminiAppBridge.pendingPrompt.orEmpty()
        if (prompt.isBlank()) return

        // Fill first; only once the field holds our prompt do we try Send.
        if (field.text?.toString() != prompt) {
            setInputText(field, prompt) // failure just means we retry next event
            return
        }

        val send = findSendButton(root) ?: return
        if (!send.isEnabled) return // present but disabled — wait for it to enable
        if (send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            GeminiAppBridge.promptSubmitted = true
            GeminiAppBridge.phase = GeminiAppBridge.BridgePhase.AWAIT_RESPONSE
            lastSnapshot = "" // fresh baseline so the reply isn't mistaken for stale chrome
        }
    }

    /** Last editable node, preferring one whose hint marks it as the prompt field. */
    private fun findInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var hinted: AccessibilityNodeInfo? = null
        var anyEditable: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            val editable = n.isEditable || (n.className?.toString()?.contains("EditText") == true)
            if (editable) {
                anyEditable = n
                val hint = n.text?.toString()
                    ?: n.contentDescription?.toString()
                    ?: n.hintText?.toString()
                if (BridgeHeuristics.isInputHint(hint)) hinted = n
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return hinted ?: anyEditable
    }

    /** Set the field's text via ACTION_SET_TEXT, falling back to clipboard paste. */
    private fun setInputText(field: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true

        // Some Compose editors reject ACTION_SET_TEXT — focus + paste instead.
        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ideaz-prompt", text))
            field.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (e: Exception) {
            false
        }
    }

    /** Last enabled, clickable node that looks like a Send/Submit affordance. */
    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var match: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            val hint = n.contentDescription?.toString() ?: n.text?.toString()
            if (n.isClickable && BridgeHeuristics.isSendHint(hint)) match = n
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return match
    }

    // ---- AWAIT_RESPONSE phase ------------------------------------------------

    private fun onStable(snapshot: String) {
        if (!GeminiAppBridge.isWaiting) return
        val root = geminiRoot()
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
            finishRun()
            GeminiAppBridge.channel.trySend(text)
        } else {
            // Clipboard empty/unreadable (background-read restriction) — scrape.
            deliverScraped(fallbackSnapshot)
        }
    }

    private fun deliverScraped(snapshot: String) {
        if (!GeminiAppBridge.isWaiting) return
        val response =
            BridgeHeuristics.extractResponse(snapshot, GeminiAppBridge.pendingPrompt.orEmpty())
        if (response.isBlank()) return
        finishRun()
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
            val hint = n.contentDescription?.toString() ?: n.text?.toString()
            if (n.isClickable && BridgeHeuristics.isCopyHint(hint)) match = n
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return match
    }

    // ---- shared --------------------------------------------------------------

    /**
     * The Gemini window's root. Prefers [rootInActiveWindow] when it actually
     * belongs to Gemini, but the block scrim (even non-focusable) can leave the
     * active window ambiguous, so fall back to scanning [getWindows] by package.
     */
    private fun geminiRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow
            ?.takeIf { it.packageName?.toString() in MONITORED_PACKAGES }
            ?.let { return it }
        return try {
            // Each window.root is a fresh AccessibilityNodeInfo — recycle the
            // ones we don't keep so we don't exhaust the node pool on older OSes.
            for (window in windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = window.root ?: continue
                if (root.packageName?.toString() in MONITORED_PACKAGES) return root
                root.recycle()
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Lift the touch-block scrim onto the now-foreground Gemini window, once. */
    private fun reassertBlock() {
        if (blockReasserted) return
        blockReasserted = true
        if (!Settings.canDrawOverlays(this)) return
        try {
            startForegroundService(
                Intent(this, IdeazOverlayService::class.java).putExtra("STATE", "wait")
            )
        } catch (e: Exception) {
            // Best-effort — the adapter already requested the scrim.
        }
    }

    /** End-of-run bookkeeping so the next run starts clean. */
    private fun finishRun() {
        GeminiAppBridge.isWaiting = false
        GeminiAppBridge.phase = GeminiAppBridge.BridgePhase.IDLE
        blockReasserted = false
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
        inputJob?.let { handler.removeCallbacks(it) }
        stableJob = null
        inputJob = null
        lastSnapshot = ""
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastSnapshot = ""
        blockReasserted = false
    }

    companion object {
        private const val STABLE_MS = 2_500L
        private const val INPUT_STABLE_MS = 600L
        private const val CLIP_DELAY_MS = 350L

        private val MONITORED_PACKAGES = setOf(
            "com.google.android.apps.bard",
            "com.google.android.googlequicksearchbox",
        )
    }
}
