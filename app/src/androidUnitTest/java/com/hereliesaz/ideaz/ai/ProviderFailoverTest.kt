package com.hereliesaz.ideaz.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProviderFailoverTest {

    private val userBody =
        """{"error":{"message":"Daily token quota exceeded. Retry after 88446 seconds.","type":"insufficient_quota","code":"quota_exceeded","retry_after":88446}}"""

    // --- classification ---------------------------------------------------------------------

    @Test
    fun `the reported 429 is a long-wait quota failure with the body's reset`() {
        val signal = QuotaClassifier.classify(429, userBody, null)
        assertNotNull(signal)
        assertEquals(88_446L, signal!!.retryAfterSeconds)
        assertTrue(signal.hasQuotaMarker)
        assertTrue(signal.isLongWait)
        assertEquals(88_446L, signal.cooldownSeconds)
    }

    @Test
    fun `Retry-After header wins over the body`() {
        val signal = QuotaClassifier.classify(429, userBody, "120")!!
        assertEquals(120L, signal.retryAfterSeconds)
        assertTrue(signal.isLongWait)
    }

    @Test
    fun `a short Retry-After keeps the ordinary retry policy`() {
        val signal = QuotaClassifier.classify(429, "slow down", "3")!!
        assertEquals(3L, signal.cooldownSeconds)
        assertFalse(signal.isLongWait)
        assertFalse(QuotaClassifier.classify(429, "slow down", "60")!!.isLongWait)
        assertTrue(QuotaClassifier.classify(429, "slow down", "61")!!.isLongWait)
    }

    @Test
    fun `a plain 429 with no reset is short and cools for a minute`() {
        val signal = QuotaClassifier.classify(429, "slow down", null)!!
        assertFalse(signal.isLongWait)
        assertEquals(QuotaClassifier.DEFAULT_RATE_LIMIT_COOLDOWN_SECONDS, signal.cooldownSeconds)
    }

    @Test
    fun `a named quota with no reset is long and cools for an hour`() {
        val signal = QuotaClassifier.classify(403, """{"error":{"code":"insufficient_quota"}}""", null)!!
        assertTrue(signal.isLongWait)
        assertEquals(QuotaClassifier.DEFAULT_QUOTA_COOLDOWN_SECONDS, signal.cooldownSeconds)
    }

    @Test
    fun `any status with a long Retry-After is quota-class`() {
        assertNotNull(QuotaClassifier.classify(503, "busy", "3600"))
    }

    @Test
    fun `unrelated failures are not quota-class`() {
        assertNull(QuotaClassifier.classify(500, "boom", null))
        assertNull(QuotaClassifier.classify(401, "bad key", null))
        assertNull(QuotaClassifier.classify(503, "busy", "5"))
    }

    @Test
    fun `body reset formats are understood`() {
        assertEquals(35L, QuotaClassifier.retryAfterFromBody("""{"retryDelay":"34.2s"}"""))
        assertEquals(12L, QuotaClassifier.retryAfterFromBody("Please retry in 11.5s."))
        assertEquals(88_446L, QuotaClassifier.retryAfterFromBody("Retry after 88446 seconds."))
        assertNull(QuotaClassifier.retryAfterFromBody("no hint here"))
    }

    @Test
    fun `Retry-After parses seconds and HTTP dates`() {
        val now = 1_000_000_000_000L
        assertEquals(30L, QuotaClassifier.parseRetryAfterSeconds("30", now))
        assertNull(QuotaClassifier.parseRetryAfterSeconds("soon", now))
        assertNull(QuotaClassifier.parseRetryAfterSeconds(null, now))
        // 2001-09-09T01:46:40Z is 1_000_000_000_000 ms; the date below is two minutes later.
        assertEquals(120L, QuotaClassifier.parseRetryAfterSeconds("Sun, 09 Sep 2001 01:48:40 GMT", now))
    }

    @Test
    fun `SDK messages that hide the status line are classified from text`() {
        assertNotNull(QuotaClassifier.classifyMessage("429 RESOURCE_EXHAUSTED. You exceeded your current quota"))
        assertNotNull(QuotaClassifier.classifyMessage("quota_exceeded"))
        assertNull(QuotaClassifier.classifyMessage("API key not valid"))
        assertNull(QuotaClassifier.classifyMessage(null))
    }

    // --- cooldown bookkeeping ---------------------------------------------------------------

    @Test
    fun `cooldowns expire and report the earliest reset`() {
        var now = 0L
        val c = ProviderCooldowns { now }
        c.markCooling("A", 100)
        c.markCooling("B", 30)
        assertTrue(c.isCooling("A"))
        assertTrue(c.isCooling("B"))
        assertFalse(c.isCooling("C"))
        assertEquals(30_000L, c.earliestResetInMs(listOf("A", "B", "C")))
        now = 31_000L
        assertFalse(c.isCooling("B"))
        assertEquals(69_000L, c.earliestResetInMs(listOf("A", "B")))
        now = 200_000L
        assertNull(c.earliestResetInMs(listOf("A", "B")))
    }

    @Test
    fun `a later mark never shortens an existing cooldown`() {
        val c = ProviderCooldowns { 0L }
        c.markCooling("A", 1000)
        c.markCooling("A", 10)
        assertEquals(1_000_000L, c.earliestResetInMs(listOf("A")))
    }

    private class MemoryStore(var value: String? = null) : CooldownStore {
        override fun read(): String? = value
        override fun write(value: String) { this.value = value }
    }

    @Test
    fun `cooldowns persist and reload, dropping expired entries`() {
        val store = MemoryStore()
        val first = ProviderCooldowns { 0L }
        first.attachStore(store)
        first.markCooling("A", 100)
        assertNotNull(store.value)

        val second = ProviderCooldowns { 50_000L }
        second.attachStore(store)
        assertTrue(second.isCooling("A"))

        val third = ProviderCooldowns { 500_000L }
        third.attachStore(store)
        assertFalse(third.isCooling("A"))
    }

    @Test
    fun `encode and decode round trip and tolerate junk`() {
        val map = mapOf("KILO_FREE" to 5L, "LLM7_DEFAULT" to 9L)
        assertEquals(map, ProviderCooldowns.decode(ProviderCooldowns.encode(map)))
        assertEquals(mapOf("A" to 1L), ProviderCooldowns.decode("A=1;broken;=3;B=x"))
        assertTrue(ProviderCooldowns.decode(null).isEmpty())
    }

    // --- selection --------------------------------------------------------------------------

    private fun cand(id: String, keyless: Boolean = false, usable: Boolean = true, images: Boolean = false) =
        FailoverCandidate(id, id, keyless, usable, images)

    private val catalog = listOf(
        cand("GEMINI", images = true),
        cand("GROQ"),
        cand("OPENAI", images = true, usable = false),
        cand("KILO", keyless = true),
        cand("LLM7", keyless = true),
        cand("OVH", keyless = true),
    )

    @Test
    fun `order puts the choice first, then keyless, then keyed with a key`() {
        val order = ProviderFailover.order("GROQ", catalog, needsImages = false).map { it.id }
        assertEquals(listOf("GROQ", "KILO", "LLM7", "OVH", "GEMINI"), order)
    }

    @Test
    fun `order drops providers that cannot read images`() {
        val order = ProviderFailover.order("GEMINI", catalog, needsImages = true).map { it.id }
        assertEquals(listOf("GEMINI"), order)
    }

    @Test
    fun `next skips tried and cooling providers`() {
        val c = ProviderCooldowns { 0L }
        val order = ProviderFailover.order("KILO", catalog, false)
        c.markCooling("LLM7", 100)
        assertEquals("KILO", ProviderFailover.next(order, emptySet(), c)!!.id)
        assertEquals("OVH", ProviderFailover.next(order, setOf("KILO"), c)!!.id)
        assertEquals("GEMINI", ProviderFailover.next(order, setOf("KILO", "OVH"), c)!!.id)
        assertNull(ProviderFailover.next(order, order.map { it.id }.toSet(), c))
    }

    // --- formatting -------------------------------------------------------------------------

    @Test
    fun `durations read as hours and minutes`() {
        assertEquals("6 h 20 min", ProviderFailover.formatDuration((6 * 60 + 20) * 60_000L))
        assertEquals("3 h", ProviderFailover.formatDuration(3 * 3_600_000L))
        assertEquals("45 min", ProviderFailover.formatDuration(45 * 60_000L))
        assertEquals("1 min", ProviderFailover.formatDuration(10_000L))
        // Rounds up, so the user is never told it is sooner than it is.
        assertEquals("24 h 35 min", ProviderFailover.formatDuration(88_446_000L))
    }

    @Test
    fun `notices and the exhausted line are plain`() {
        assertEquals("Kilo hit its daily limit. Switched to LLM7.", ProviderFailover.switchNotice("Kilo", "LLM7", 88_446))
        assertEquals("Kilo is rate limited. Switched to LLM7.", ProviderFailover.switchNotice("Kilo", "LLM7", 30))
        assertEquals(
            "All free models are at their daily limit. Resets in about 6 h 20 min. Add your own key in Settings to keep going.",
            ProviderFailover.exhaustedMessage((6 * 60 + 20) * 60_000L, hasOwnKey = false),
        )
        assertTrue(ProviderFailover.exhaustedMessage(null, hasOwnKey = false).none { it == '{' })
        assertFalse("Resets" in ProviderFailover.exhaustedMessage(null, hasOwnKey = false))
        assertTrue(ProviderFailover.exhaustedMessage(60_000L, hasOwnKey = true).startsWith("All available models"))
    }

    // --- FailoverClient ---------------------------------------------------------------------

    private class Scripted(val outcome: () -> String) : ConversationalAiClient {
        var calls = 0
        override suspend fun chat(messages: List<ChatMessage>): String {
            calls++
            return outcome()
        }
    }

    private fun quota(seconds: Long = 88_446) = ProviderQuotaException(seconds, "HTTP 429: raw json")

    @Test
    fun `a quota failure retries the same request on the next provider and says so once`() = runBlocking {
        val cooldowns = ProviderCooldowns { 0L }
        val a = Scripted { throw quota() }
        val b = Scripted { "from b" }
        val notices = mutableListOf<String>()
        val client = FailoverClient(
            order = listOf(cand("A", keyless = true), cand("B", keyless = true)),
            clientFor = { if (it.id == "A") a else b },
            cooldowns = cooldowns,
            onSwitch = { notices += it },
        )

        assertEquals("from b", client.chat(listOf(ChatMessage("user", "hi"))))
        assertEquals(listOf("A hit its daily limit. Switched to B."), notices)
        assertEquals("B", client.activeLabel)
        assertTrue(cooldowns.isCooling("A"))

        // The next request starts on B directly: A is cooling, so no second notice.
        assertEquals("from b", client.chat(listOf(ChatMessage("user", "again"))))
        assertEquals(1, a.calls)
        assertEquals(1, notices.size)
    }

    @Test
    fun `each provider is tried at most once and exhaustion reports the earliest reset`() = runBlocking {
        var now = 0L
        val cooldowns = ProviderCooldowns { now }
        val a = Scripted { throw quota(7_200) }
        val b = Scripted { throw quota(3_600) }
        val client = FailoverClient(
            order = listOf(cand("A", keyless = true), cand("B", keyless = true)),
            clientFor = { if (it.id == "A") a else b },
            cooldowns = cooldowns,
        )

        try {
            client.chat(listOf(ChatMessage("user", "hi")))
            fail("expected exhaustion")
        } catch (e: AllProvidersExhaustedException) {
            assertEquals(
                "All free models are at their daily limit. Resets in about 1 h. Add your own key in Settings to keep going.",
                e.userMessage,
            )
            assertEquals("HTTP 429: raw json", e.details)
        }
        assertEquals(1, a.calls)
        assertEquals(1, b.calls)

        // Everything is cooling: a second request fails fast without calling anyone.
        now = 1_800_000L
        try {
            client.chat(listOf(ChatMessage("user", "hi")))
            fail("expected exhaustion")
        } catch (e: AllProvidersExhaustedException) {
            assertTrue("Resets in about 30 min" in e.userMessage)
        }
        assertEquals(1, a.calls)
        assertEquals(1, b.calls)
    }

    @Test
    fun `the total number of providers per request is bounded`() = runBlocking {
        val calls = Scripted { throw quota(10) }
        val order = (1..10).map { cand("P$it", keyless = true) }
        val client = FailoverClient(
            order = order,
            clientFor = { calls },
            cooldowns = ProviderCooldowns { 0L },
            maxProviders = 3,
        )
        try {
            client.chat(listOf(ChatMessage("user", "hi")))
            fail("expected exhaustion")
        } catch (_: AllProvidersExhaustedException) {
        }
        assertEquals(3, calls.calls)
    }

    @Test
    fun `other failures pass straight through without switching`() = runBlocking {
        val a = Scripted { throw IllegalStateException("boom") }
        val b = Scripted { "from b" }
        val client = FailoverClient(
            order = listOf(cand("A", keyless = true), cand("B", keyless = true)),
            clientFor = { if (it.id == "A") a else b },
            cooldowns = ProviderCooldowns { 0L },
        )
        try {
            client.chat(listOf(ChatMessage("user", "hi")))
            fail("expected the original failure")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }
        assertEquals(0, b.calls)
    }

    @Test
    fun `providers without a client are skipped`() = runBlocking {
        val b = Scripted { "from b" }
        val client = FailoverClient(
            order = listOf(cand("A"), cand("B")),
            clientFor = { if (it.id == "B") b else null },
            cooldowns = ProviderCooldowns { 0L },
        )
        assertEquals("from b", client.chat(listOf(ChatMessage("user", "hi"))))
    }
}
