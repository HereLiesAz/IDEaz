package com.hereliesaz.ideaz.services

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.hereliesaz.ideaz.BuildConfig
import com.hereliesaz.ideaz.ai.bridge.BridgeHeuristics
import com.hereliesaz.ideaz.ai.bridge.GeminiAppBridge

/**
 * GitHub-distribution external-AI driver.
 *
 * It is registered only in src/debug and src/release manifests. The Play build
 * has neither the service declaration nor the automation flag, so this class is
 * unreachable there and may be removed by R8.
 */
class IdeazAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastSnapshot = ""
    private var stableJob: Runnable? = null
    private var inputJob: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!BuildConfig.EXTERNAL_AI_AUTOMATION || !GeminiAppBridge.isWaiting) return
        val target = GeminiAppBridge.targetPackage ?: return
        if (event?.packageName?.toString() != target) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        when (GeminiAppBridge.phase) {
            GeminiAppBridge.BridgePhase.INPUT -> scheduleInput()
            GeminiAppBridge.BridgePhase.AWAIT_RESPONSE -> scheduleCapture()
            GeminiAppBridge.BridgePhase.IDLE -> Unit
        }
    }

    private fun scheduleInput() {
        inputJob?.let(handler::removeCallbacks)
        inputJob = Runnable { submitPrompt() }.also {
            handler.postDelayed(it, INPUT_STABLE_MS)
        }
    }

    private fun submitPrompt() {
        if (!GeminiAppBridge.isWaiting ||
            GeminiAppBridge.phase != GeminiAppBridge.BridgePhase.INPUT ||
            GeminiAppBridge.promptSubmitted
        ) return

        val root = targetRoot() ?: return
        val field = findInputField(root) ?: return
        val prompt = GeminiAppBridge.pendingPrompt.orEmpty()
        if (prompt.isBlank()) return

        if (field.text?.toString() != prompt) {
            setInputText(field, prompt)
            return
        }

        val send = findClickable(root, BridgeHeuristics::isSendHint) ?: return
        if (!send.isEnabled) return

        GeminiAppBridge.baselineCopyActions = countClickable(root, BridgeHeuristics::isCopyHint)
        GeminiAppBridge.baselineSnapshot = collectText(root).trim()
        GeminiAppBridge.observedGenerating = false

        if (send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            GeminiAppBridge.promptSubmitted = true
            GeminiAppBridge.phase = GeminiAppBridge.BridgePhase.AWAIT_RESPONSE
            lastSnapshot = ""
        }
    }

    private fun scheduleCapture() {
        val root = targetRoot() ?: return
        val snapshot = collectText(root).trim()
        val generating = containsHint(root, BridgeHeuristics::isGeneratingHint)
        if (generating) {
            GeminiAppBridge.observedGenerating = true
            stableJob?.let(handler::removeCallbacks)
            lastSnapshot = ""
            return
        }
        if (!isCompletedResponse(root, snapshot) || snapshot == lastSnapshot) return

        lastSnapshot = snapshot
        stableJob?.let(handler::removeCallbacks)
        stableJob = Runnable { captureStableResponse(snapshot) }.also {
            handler.postDelayed(it, RESPONSE_STABLE_MS)
        }
    }

    private fun captureStableResponse(candidateSnapshot: String) {
        if (!GeminiAppBridge.isWaiting ||
            GeminiAppBridge.phase != GeminiAppBridge.BridgePhase.AWAIT_RESPONSE
        ) return

        val root = targetRoot() ?: return
        val currentSnapshot = collectText(root).trim()

        if (currentSnapshot != candidateSnapshot || !isCompletedResponse(root, currentSnapshot)) {
            lastSnapshot = ""
            scheduleCapture()
            return
        }

        val copy = findClickable(root, BridgeHeuristics::isCopyHint)
        if (copy != null && copy.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            handler.postDelayed({ deliverClipboardOrScrape(currentSnapshot) }, CLIPBOARD_DELAY_MS)
        } else {
            deliverScraped(currentSnapshot)
        }
    }

    private fun isCompletedResponse(root: AccessibilityNodeInfo, snapshot: String): Boolean {
        if (snapshot.isBlank() || snapshot == GeminiAppBridge.baselineSnapshot) return false
        if (containsHint(root, BridgeHeuristics::isGeneratingHint)) return false
        val copyActions = countClickable(root, BridgeHeuristics::isCopyHint)
        return copyActions > GeminiAppBridge.baselineCopyActions ||
            (GeminiAppBridge.observedGenerating && copyActions > 0)
    }

    private fun deliverClipboardOrScrape(snapshot: String) {
        if (!GeminiAppBridge.isWaiting) return
        val text = runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                ?.trim()
        }.getOrNull()

        if (!text.isNullOrBlank() && text != GeminiAppBridge.pendingPrompt) {
            finish(text)
        } else {
            deliverScraped(snapshot)
        }
    }

    private fun deliverScraped(snapshot: String) {
        val response = BridgeHeuristics.extractResponse(
            snapshot,
            GeminiAppBridge.pendingPrompt.orEmpty(),
        )
        if (response.isNotBlank()) finish(response)
    }

    private fun finish(response: String) {
        GeminiAppBridge.isWaiting = false
        GeminiAppBridge.phase = GeminiAppBridge.BridgePhase.IDLE
        GeminiAppBridge.channel.trySend(response)
    }

    private fun findInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var hinted: AccessibilityNodeInfo? = null
        var fallback: AccessibilityNodeInfo? = null
        walk(root) { node ->
            val editable = node.isEditable || node.className?.toString()?.contains("EditText") == true
            if (editable) {
                fallback = node
                val hint = node.hintText?.toString()
                    ?: node.contentDescription?.toString()
                    ?: node.text?.toString()
                if (BridgeHeuristics.isInputHint(hint)) hinted = node
            }
        }
        return hinted ?: fallback
    }

    private fun findClickable(
        root: AccessibilityNodeInfo,
        matcher: (String?) -> Boolean,
    ): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        walk(root) { node ->
            val hint = nodeHint(node)
            if (node.isClickable && matcher(hint)) found = node
        }
        return found
    }

    private fun countClickable(
        root: AccessibilityNodeInfo,
        matcher: (String?) -> Boolean,
    ): Int {
        var count = 0
        walk(root) { node ->
            if (node.isClickable && matcher(nodeHint(node))) count++
        }
        return count
    }

    private fun containsHint(
        root: AccessibilityNodeInfo,
        matcher: (String?) -> Boolean,
    ): Boolean {
        var found = false
        walk(root) { node ->
            if (!found && matcher(nodeHint(node))) found = true
        }
        return found
    }

    private fun nodeHint(node: AccessibilityNodeInfo): String? =
        node.contentDescription?.toString()
            ?: node.text?.toString()
            ?: node.hintText?.toString()

    private fun setInputText(field: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true

        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("IDEaz prompt", text))
            field.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }.getOrDefault(false)
    }

    private fun targetRoot(): AccessibilityNodeInfo? {
        val target = GeminiAppBridge.targetPackage ?: return null
        rootInActiveWindow
            ?.takeIf { it.packageName?.toString() == target }
            ?.let { return it }

        return runCatching {
            windows.asSequence()
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { it.root }
                .firstOrNull { it.packageName?.toString() == target }
        }.getOrNull()
    }

    private fun walk(root: AccessibilityNodeInfo?, block: (AccessibilityNodeInfo) -> Unit) {
        if (root == null) return
        block(root)
        for (i in 0 until root.childCount) walk(root.getChild(i), block)
    }

    private fun collectText(
        node: AccessibilityNodeInfo?,
        out: StringBuilder = StringBuilder(),
    ): String {
        if (node == null) return out.toString()
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
            if (out.isNotEmpty()) out.append('\n')
            out.append(it)
        }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let {
            if (node.text?.toString() != it) {
                if (out.isNotEmpty()) out.append('\n')
                out.append(it)
            }
        }
        for (i in 0 until node.childCount) collectText(node.getChild(i), out)
        return out.toString()
    }

    override fun onInterrupt() {
        stableJob?.let(handler::removeCallbacks)
        inputJob?.let(handler::removeCallbacks)
        stableJob = null
        inputJob = null
        lastSnapshot = ""
    }

    companion object {
        private const val INPUT_STABLE_MS = 500L
        private const val RESPONSE_STABLE_MS = 1_250L
        private const val CLIPBOARD_DELAY_MS = 300L
    }
}
