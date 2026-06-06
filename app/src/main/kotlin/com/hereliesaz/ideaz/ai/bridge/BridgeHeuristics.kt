package com.hereliesaz.ideaz.ai.bridge

/**
 * Pure, framework-free predicates for matching nodes in the Gemini app's
 * accessibility tree. Kept separate from [GeminiAppBridgeAccessibilityService]
 * so the substring/casing rules can be unit-tested without Android.
 *
 * All matchers are case-insensitive `contains` over a node's text /
 * contentDescription / hintText. Liberal on purpose: the Gemini app's labels
 * shift across versions, so we match on stable keywords rather than exact ids.
 */
object BridgeHeuristics {

    /** Hints that mark the prompt input field ("Ask Gemini", "Enter a prompt", …). */
    val INPUT_HINTS = listOf("ask gemini", "enter a prompt", "message", "talk", "prompt")

    /** Hints that mark the submit affordance (the up-arrow ≈ contentDescription "Send"). */
    val SEND_HINTS = listOf("send", "submit", "run")

    /** Hints that mark a "copy response/code" affordance. */
    val COPY_HINTS = listOf("copy")

    /** Input-field chrome that leaks into a scrape and must be stripped. */
    val CHROME_STRINGS = listOf("Enter a prompt here", "Listening", "Tap to talk")

    /**
     * Reduce a full window scrape to just the model's reply: strip the prompt we
     * sent (the Gemini app echoes it) and known input chrome.
     */
    fun extractResponse(snapshot: String, prompt: String): String {
        var text = snapshot
        if (prompt.isNotBlank()) {
            text = text.replace(prompt, "").trim()
        }
        for (junk in CHROME_STRINGS) {
            text = text.replace(junk, "", ignoreCase = true)
        }
        return text.trim()
    }

    fun isInputHint(hint: String?): Boolean = matches(hint, INPUT_HINTS)

    fun isSendHint(hint: String?): Boolean = matches(hint, SEND_HINTS)

    fun isCopyHint(hint: String?): Boolean = matches(hint, COPY_HINTS)

    private fun matches(hint: String?, needles: List<String>): Boolean {
        val h = hint?.lowercase() ?: return false
        return needles.any { h.contains(it) }
    }
}
