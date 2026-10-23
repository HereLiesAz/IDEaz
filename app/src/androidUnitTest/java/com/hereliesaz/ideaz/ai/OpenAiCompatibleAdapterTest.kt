package com.hereliesaz.ideaz.ai

import com.hereliesaz.ideaz.ai.AiEditApprovalRequiredException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Regression coverage for the cloud tool-loop orchestration fixed this session:
 * none of the 3 cloud adapters wrapped their network round in try/finally, so a
 * failure AFTER a mutating tool call already ran in an earlier round left the
 * edit applied to disk with no review and an orphaned checkpoint. This was zero
 * coverage before - the checkpoint *primitives* (IdeToolsTest) were well tested,
 * but nothing exercised a cloud adapter's actual round-to-round wiring.
 */
class OpenAiCompatibleAdapterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var tools: IdeTools

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tools = IdeTools(tempFolder.root)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(apiKey: String = "test-key") = OpenAiCompatibleAdapter(
        baseUrl = server.url("/").toString().removeSuffix("/"),
        apiKey = apiKey,
        modelResolver = { "test-model" },
        tools = tools,
        httpClient = OkHttpClient.Builder().build(),
        retryBaseDelayMs = 1,
    )

    private val toolCallResponse = """
        {
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": null,
                "tool_calls": [
                  {
                    "id": "call_1",
                    "function": {
                      "name": "write_file",
                      "arguments": "{\"path\":\"app.js\",\"content\":\"changed by AI\"}"
                    }
                  }
                ]
              }
            }
          ]
        }
    """.trimIndent()

    private val finalTextResponse = """
        {
          "choices": [
            { "message": { "role": "assistant", "content": "Done." } }
          ]
        }
    """.trimIndent()

    @Test
    fun `keyless providers get no Authorization header, keyed ones get a bearer`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(finalTextResponse))
        server.enqueue(MockResponse().setResponseCode(200).setBody(finalTextResponse))

        adapter(apiKey = "").chat(listOf(ChatMessage("user", "hi")))
        assertEquals(null, server.takeRequest().getHeader("Authorization"))

        adapter().chat(listOf(ChatMessage("user", "hi")))
        assertEquals("Bearer test-key", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `successful round trip applies the edit and requires review, not a silent reload`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(toolCallResponse))
        server.enqueue(MockResponse().setResponseCode(200).setBody(finalTextResponse))

        // A completed edit is never returned as plain reply text - chat() throws
        // AiEditApprovalRequiredException so the caller (MainViewModel) shows
        // a review card instead of silently reloading an unreviewed change.
        try {
            adapter().chat(listOf(ChatMessage("user", "add a console.log")))
            fail("Expected AiEditApprovalRequiredException")
        } catch (e: AiEditApprovalRequiredException) {
            assertEquals("Done.", e.approval.response)
            assertEquals(listOf("app.js"), e.approval.review.changedFiles)
        }
        assertEquals("changed by AI", File(tempFolder.root, "app.js").readText())
    }

    @Test
    fun `network failure after a write is restored, not left applied`() = runBlocking {
        File(tempFolder.root, "app.js").writeText("original content")

        server.enqueue(MockResponse().setResponseCode(200).setBody(toolCallResponse))
        // Round 2 fails - a 429/500 here is the common real-world case (a
        // rate-limited free-tier backend) that used to leave the edit applied
        // with no review card and an orphaned checkpoint.
        // The adapter retries 5xx, so the failure must outlast every attempt.
        repeat(OpenAiCompatibleAdapter.MAX_HTTP_ATTEMPTS) {
            server.enqueue(MockResponse().setResponseCode(500).setBody("rate limited"))
        }

        val reply = adapter().chat(listOf(ChatMessage("user", "add a console.log")))

        assertTrue("Expected a clean error string, got: $reply", reply.startsWith("Error:"))
        assertEquals(
            "The pre-edit content must be restored, not left as the AI's write.",
            "original content",
            File(tempFolder.root, "app.js").readText(),
        )
        // No leftover checkpoint directory for *this* project once restored.
        // JUnit's TemporaryFolder rule creates each test's root under a shared
        // parent, so a sibling test's own (intentionally un-discarded, pending
        // review) checkpoint can otherwise still be sitting in the same
        // .ideaz-edit-checkpoints base - filter by project path, the same way
        // IdeTools.reconcileEditCheckpoints does, rather than asserting the
        // whole shared directory is empty.
        val checkpointBase = File(tempFolder.root.parentFile, ".ideaz-edit-checkpoints")
        val thisProjectsCheckpoints = checkpointBase.listFiles()?.filter { dir ->
            File(dir, "project-path.txt").let { it.exists() && it.readText() == tempFolder.root.canonicalPath }
        }.orEmpty()
        assertTrue(
            "restorePendingEdit deletes this test's checkpoint dir on success, found: $thisProjectsCheckpoints",
            thisProjectsCheckpoints.isEmpty(),
        )
    }

    @Test
    fun `unexpected response shape after a write is restored, not silently applied`() = runBlocking {
        File(tempFolder.root, "app.js").writeText("original content")

        server.enqueue(MockResponse().setResponseCode(200).setBody(toolCallResponse))
        // A 2xx response with no "message" field (e.g. a proxy emitting "delta"
        // instead) used to bypass complete() entirely and return here directly.
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices": []}"""))

        val reply = adapter().chat(listOf(ChatMessage("user", "add a console.log")))

        assertTrue("Expected a clean error string, got: $reply", reply.startsWith("Error:"))
        assertEquals("original content", File(tempFolder.root, "app.js").readText())
    }

    @Test
    fun `a transient 429 is retried and the next attempt succeeds`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("slow down"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(finalTextResponse))

        val reply = adapter().chat(listOf(ChatMessage("user", "hi")))

        assertTrue("Expected a normal reply, got: $reply", !reply.startsWith("Error:"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a daily quota 429 is not waited out - it surfaces as a quota failure after one call`() = runBlocking {
        File(tempFolder.root, "app.js").writeText("original content")
        server.enqueue(MockResponse().setResponseCode(200).setBody(toolCallResponse))
        server.enqueue(
            MockResponse().setResponseCode(429).setBody(
                """{"error":{"message":"Daily token quota exceeded. Retry after 88446 seconds.","type":"insufficient_quota","code":"quota_exceeded","retry_after":88446}}"""
            )
        )

        try {
            adapter().chat(listOf(ChatMessage("user", "add a console.log")))
            fail("Expected a ProviderQuotaException")
        } catch (e: ProviderQuotaException) {
            assertEquals(88_446L, e.cooldownSeconds)
            assertTrue(e.message!!.contains("HTTP 429"))
        }
        // One call for the tool round, one for the refused round: no retries.
        assertEquals(2, server.requestCount)
        // The earlier round's write is rolled back so the next provider starts clean.
        assertEquals("original content", File(tempFolder.root, "app.js").readText())
    }

    @Test
    fun `a 429 that outlasts the short retries also becomes a quota failure`() = runBlocking {
        repeat(OpenAiCompatibleAdapter.MAX_HTTP_ATTEMPTS) {
            server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "0").setBody("slow down"))
        }
        try {
            adapter().chat(listOf(ChatMessage("user", "hi")))
            fail("Expected a ProviderQuotaException")
        } catch (e: ProviderQuotaException) {
            assertTrue("A short reset keeps the cooldown short: ${e.cooldownSeconds}", e.cooldownSeconds <= QuotaClassifier.SHORT_WAIT_SECONDS)
        }
        assertEquals(OpenAiCompatibleAdapter.MAX_HTTP_ATTEMPTS, server.requestCount)
    }

    @Test
    fun `a client error is not retried`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("bad key"))

        val reply = adapter().chat(listOf(ChatMessage("user", "hi")))

        assertTrue(reply.startsWith("Error:"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `retry policy honours Retry-After and backs off exponentially`() {
        assertTrue(OpenAiCompatibleAdapter.isRetryableStatus(429))
        assertTrue(OpenAiCompatibleAdapter.isRetryableStatus(503))
        assertTrue(!OpenAiCompatibleAdapter.isRetryableStatus(501))
        assertTrue(!OpenAiCompatibleAdapter.isRetryableStatus(400))
        assertEquals(3_000L, OpenAiCompatibleAdapter.retryDelayMs(1, "3", 1_000))
        assertEquals(1_000L, OpenAiCompatibleAdapter.retryDelayMs(1, null, 1_000))
        assertEquals(4_000L, OpenAiCompatibleAdapter.retryDelayMs(3, null, 1_000))
        assertEquals(30_000L, OpenAiCompatibleAdapter.retryDelayMs(1, "999", 1_000))
    }
}
