package com.hereliesaz.ideaz.ai

import kotlinx.coroutines.delay

/**
 * Gemini in Google Search ("AI Mode"), reached the way a person reaches it: the search page, in a
 * browser view on the user's own device and connection. No key, no API; only Google sees the
 * question. One answer per question: no system prompt, no history, no tools. So it can explain and
 * research, but it cannot read or edit the project.
 *
 * Page parsing ported from The Aive's providers/llm GoogleAiMode.
 */
object GoogleAiMode {
    /** The longest question the search page takes in its address; longer is refused, never cut. */
    const val MAX_QUESTION_CHARS = 1_800

    /** The page that shows the answer to [question] (`udm=50` is AI Mode). */
    fun url(question: String): String = "https://www.google.com/search?udm=50&hl=en&q=" + percentEncode(question.trim())

    private fun percentEncode(text: String): String = buildString {
        for (byte in text.encodeToByteArray()) {
            val c = byte.toInt() and 0xFF
            val plain = c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code ||
                c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
            if (plain) append(c.toChar()) else append('%').append(HEX[c shr 4]).append(HEX[c and 15])
        }
    }

    private const val HEX = "0123456789ABCDEF"

    /** Why the page refuses, or null when it does not. */
    fun refusal(pageText: String): Refusal? {
        val t = pageText.lowercase()
        return when {
            "unusual traffic" in t || "not a robot" in t || "/sorry/" in t ->
                Refusal("Google asked for a human check (unusual traffic).", needsPerson = true)
            "before you continue" in t && ("cookies" in t || "consent" in t) ->
                Refusal("Google is asking for cookie consent first.", needsPerson = true)
            "ai mode is not available" in t || "ai mode isn't available" in t ->
                Refusal("AI Mode is not available in this region or account.", needsPerson = false)
            else -> null
        }
    }

    /** [needsPerson]: the user can clear it in the visible page (a check or a consent wall). */
    data class Refusal(val message: String, val needsPerson: Boolean)

    /** The line Google ends every answer with; its presence means the answer has finished writing. */
    private val FINISHED = Regex("""(?i)ai responses may include mistakes""")

    /** Where the answer stops: the end-of-answer line, the ads block, or the follow-up box. */
    private val ENDS = listOf(FINISHED, Regex("""(?m)^\s*Sponsored\s*$"""), Regex("""(?m)^\s*Ask anything\s*$"""))

    fun finished(pageText: String, question: String): Boolean =
        FINISHED.containsMatchIn(pageText) && answerStart(pageText, question) != null

    private fun answerStart(pageText: String, question: String): Int? {
        val q = question.trim()
        if (q.isEmpty()) return null
        // The page echoes the question above the answer; the answer follows its last echo.
        val at = pageText.lastIndexOf(q)
        if (at >= 0) return at + q.length
        // Long questions are shown shortened: fall back to the first line of the question.
        val head = q.lineSequence().first().take(60)
        return pageText.lastIndexOf(head).takeIf { it >= 0 }?.let { pageText.indexOf('\n', it).takeIf { n -> n >= 0 } }
    }

    /** The answer in [pageText], without the page chrome, disclaimer or ads; null while there is none. */
    fun answer(pageText: String, question: String): String? {
        val start = answerStart(pageText, question) ?: return null
        val tail = pageText.substring(start)
        // Each end marker cuts from the start of its own line, so the sentence leading into it goes too.
        val end = ENDS.mapNotNull { rx -> rx.find(tail)?.range?.first?.let { tail.lastIndexOf('\n', it) + 1 } }.minOrNull()
            ?: tail.length
        val text = tail.substring(0, end).lines().joinToString("\n") { it.trimEnd() }.trim()
        return text.takeIf { it.length >= 2 }
    }
}

/** The page as it renders: its visible text so far. */
fun interface AiModePage {
    suspend fun text(): String
}

/** A browser view the platform provides, on the user's own connection. */
interface AiModeBrowser {
    /** Runs [read] against the page at [url] in a hidden view, closing it when [read] returns. */
    suspend fun <T> withPage(url: String, read: suspend (AiModePage) -> T): T

    /**
     * Shows [url] to the user so they can pass a human check or consent wall, and returns once
     * they close it. False when this browser cannot show a page.
     */
    suspend fun showToUser(url: String): Boolean = false
}

/** AI Mode could not answer; [message] is fit to show the user. */
class GoogleAiModeUnavailable(message: String, val needsPerson: Boolean = false) : Exception(message)

/**
 * Google AI Mode as a chat client. Sends only the latest user message's text (no project
 * preamble, history or images, which AI Mode cannot take), waits for the finished answer and
 * returns it. A human check or consent wall is shown to the user once, then the question is asked
 * again.
 */
class GoogleAiModeClient(
    private val browser: AiModeBrowser,
    private val timeoutMillis: Long = 75_000L,
    private val pollMillis: Long = 700L,
    private val settleMillis: Long = 1_800L,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : ConversationalAiClient {

    override suspend fun chat(messages: List<ChatMessage>): String {
        val question = messages.lastOrNull { it.role == "user" }?.parts
            ?.filterIsInstance<ChatPart.Text>()?.joinToString("\n") { it.text }?.trim().orEmpty()
        if (question.isEmpty()) return "Error: Google AI Mode needs a question."
        if (question.length > GoogleAiMode.MAX_QUESTION_CHARS) {
            return "Error: the question is ${question.length} characters; Google AI Mode takes at most " +
                "${GoogleAiMode.MAX_QUESTION_CHARS}. Shorten it, or pick another model."
        }
        return try {
            ask(question)
        } catch (e: GoogleAiModeUnavailable) {
            if (e.needsPerson && browser.showToUser(GoogleAiMode.url(question))) {
                try {
                    ask(question)
                } catch (again: GoogleAiModeUnavailable) {
                    "Error: ${again.message}"
                }
            } else {
                "Error: ${e.message}" + if (e.needsPerson) " Open Google in a browser on this connection, pass it, then try again." else ""
            }
        }
    }

    private suspend fun ask(question: String): String = browser.withPage(GoogleAiMode.url(question)) { page ->
        val started = now()
        var last = ""
        var stableSince = started
        while (true) {
            val seen = page.text()
            GoogleAiMode.refusal(seen)?.let { throw GoogleAiModeUnavailable(it.message, it.needsPerson) }
            val answer = GoogleAiMode.answer(seen, question)
            if (answer != null) {
                if (answer != last) {
                    last = answer
                    stableSince = now()
                }
                // Done once Google marks it finished, or once it has stopped changing for a good while.
                if (GoogleAiMode.finished(seen, question) || now() - stableSince >= settleMillis * 3) return@withPage answer
            }
            if (now() - started >= timeoutMillis) {
                if (last.isEmpty()) throw GoogleAiModeUnavailable("Google did not answer in time.")
                return@withPage last
            }
            pause(pollMillis)
        }
        @Suppress("UNREACHABLE_CODE")
        last
    }
}
