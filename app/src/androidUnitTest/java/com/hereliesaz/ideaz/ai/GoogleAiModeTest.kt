package com.hereliesaz.ideaz.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAiModeTest {

    private val question = "how do I center a div?"
    private val page = """
        Google
        AI Mode
        $question
        Use flexbox on the parent:
        display: flex; justify-content: center;
        AI responses may include mistakes. Learn more
        Ask anything
    """.trimIndent()

    @Test
    fun `url percent-encodes the question for AI Mode`() {
        assertEquals(
            "https://www.google.com/search?udm=50&hl=en&q=a%20b%26c%C3%A9",
            GoogleAiMode.url(" a b&cé "),
        )
    }

    @Test
    fun `answer is cut between the question echo and the disclaimer`() {
        assertEquals(
            "Use flexbox on the parent:\ndisplay: flex; justify-content: center;",
            GoogleAiMode.answer(page, question),
        )
        assertTrue(GoogleAiMode.finished(page, question))
        assertNull(GoogleAiMode.answer("Google\nAI Mode\nloading", question))
    }

    @Test
    fun `refusals say whether a person can clear them`() {
        assertTrue(GoogleAiMode.refusal("Our systems have detected unusual traffic")!!.needsPerson)
        assertTrue(GoogleAiMode.refusal("Before you continue to Google, we use cookies")!!.needsPerson)
        assertFalse(GoogleAiMode.refusal("AI Mode is not available in your country")!!.needsPerson)
        assertNull(GoogleAiMode.refusal(page))
    }

    private class FakeBrowser(
        private val pages: MutableList<String>,
        private val canShow: Boolean = false,
    ) : AiModeBrowser {
        val urls = mutableListOf<String>()
        var shown = 0
        override suspend fun <T> withPage(url: String, read: suspend (AiModePage) -> T): T {
            urls += url
            return read { if (pages.size > 1) pages.removeAt(0) else pages.first() }
        }
        override suspend fun showToUser(url: String): Boolean {
            shown++
            return canShow
        }
    }

    private fun client(browser: AiModeBrowser): GoogleAiModeClient {
        var clock = 0L
        return GoogleAiModeClient(browser, timeoutMillis = 10_000, pollMillis = 500, now = { clock }, pause = { clock += it })
    }

    private fun user(text: String) = ChatMessage("user", text)

    @Test
    fun `asks only the latest user message and returns the finished answer`() = runBlocking {
        val browser = FakeBrowser(mutableListOf("Google\nloading", page))
        val reply = client(browser).chat(listOf(user("earlier"), ChatMessage("assistant", "x"), user(question)))
        assertTrue(reply, reply.startsWith("Use flexbox"))
        assertEquals(listOf(GoogleAiMode.url(question)), browser.urls)
    }

    @Test
    fun `a human check is shown to the user and the question asked again`() = runBlocking {
        val browser = FakeBrowser(mutableListOf("unusual traffic from your network", page), canShow = true)
        val reply = client(browser).chat(listOf(user(question)))
        assertEquals(1, browser.shown)
        assertTrue(reply, reply.startsWith("Use flexbox"))
    }

    @Test
    fun `without a visible browser a human check is an error`() = runBlocking {
        val reply = client(FakeBrowser(mutableListOf("unusual traffic"))).chat(listOf(user(question)))
        assertTrue(reply, reply.startsWith("Error: Google asked for a human check"))
    }

    @Test
    fun `too-long questions are refused, not cut`() = runBlocking {
        val browser = FakeBrowser(mutableListOf(page))
        val reply = client(browser).chat(listOf(user("x".repeat(GoogleAiMode.MAX_QUESTION_CHARS + 1))))
        assertTrue(reply, reply.startsWith("Error:"))
        assertTrue(browser.urls.isEmpty())
    }

    @Test
    fun `a settled partial answer is returned, no answer at all is an error`() = runBlocking {
        val partial = "AI Mode\n$question\nStill writing"
        // Unchanged for 3× settle (5.4 s): returned before the 10 s timeout.
        assertEquals("Still writing", client(FakeBrowser(mutableListOf(partial))).chat(listOf(user(question))))
        val nothing = client(FakeBrowser(mutableListOf("AI Mode\nloading"))).chat(listOf(user(question)))
        assertTrue(nothing, nothing.startsWith("Error: Google did not answer"))
    }
}
