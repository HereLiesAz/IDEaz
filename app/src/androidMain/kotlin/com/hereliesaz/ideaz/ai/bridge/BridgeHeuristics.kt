package com.hereliesaz.ideaz.ai.bridge

/**
 * Version-tolerant accessibility matching for conversational AI apps.
 * Deliberately keyword-based: exact resource ids and labels change more often
 * than the underlying concepts (message field, send button, copy response).
 */
object BridgeHeuristics {
    private val inputHints = listOf(
        "ask gemini", "message chatgpt", "message claude", "send a message",
        "enter a prompt", "message", "prompt", "ask"
    )
    private val sendHints = listOf("send", "submit", "run")
    private val copyHints = listOf("copy")
    private val chromeStrings = listOf(
        "Enter a prompt here", "Listening", "Tap to talk", "Ask Gemini"
    )

    fun extractResponse(snapshot: String, prompt: String): String {
        var text = snapshot
        if (prompt.isNotBlank()) text = text.replace(prompt, "").trim()
        chromeStrings.forEach { text = text.replace(it, "", ignoreCase = true) }
        return text.trim()
    }

    fun isInputHint(hint: String?): Boolean = matches(hint, inputHints)
    fun isSendHint(hint: String?): Boolean = matches(hint, sendHints)
    fun isCopyHint(hint: String?): Boolean = matches(hint, copyHints)

    private fun matches(hint: String?, needles: List<String>): Boolean {
        val normalized = hint?.lowercase() ?: return false
        return needles.any(normalized::contains)
    }
}
