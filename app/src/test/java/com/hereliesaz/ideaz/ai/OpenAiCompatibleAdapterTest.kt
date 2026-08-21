package com.hereliesaz.ideaz.ai

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private fun adapter() = OpenAiCompatibleAdapter(
        baseUrl = server.url("/").toString().removeSuffix("/"),
        apiKey = "test-key",
        modelResolver = { "test-model" },
        tools = tools,
        httpClient = OkHttpClient.Builder().build(),
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
    fun `successful round trip applies the edit and returns the final text`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(toolCallResponse))
        server.enqueue(MockResponse().setResponseCode(200).setBody(finalTextResponse))

        val reply = adapter().chat(listOf(ChatMessage("user", "add a console.log")))

        assertEquals("Done.", reply)
        assertEquals("changed by AI", File(tempFolder.root, "app.js").readText())
    }

    @Test
    fun `network failure after a write is restored, not left applied`() = runBlocking {
        File(tempFolder.root, "app.js").writeText("original content")

        server.enqueue(MockResponse().setResponseCode(200).setBody(toolCallResponse))
        // Round 2 fails - a 429/500 here is the common real-world case (a
        // rate-limited free-tier backend) that used to leave the edit applied
        // with no review card and an orphaned checkpoint.
        server.enqueue(MockResponse().setResponseCode(500).setBody("rate limited"))

        val reply = adapter().chat(listOf(ChatMessage("user", "add a console.log")))

        assertTrue("Expected a clean error string, got: $reply", reply.startsWith("Error:"))
        assertEquals(
            "The pre-edit content must be restored, not left as the AI's write.",
            "original content",
            File(tempFolder.root, "app.js").readText(),
        )
        // No leftover checkpoint directory once restored.
        val checkpointBase = File(tempFolder.root.parentFile, ".ideaz-edit-checkpoints")
        assertFalse(
            "restorePendingEdit deletes the checkpoint dir on success",
            checkpointBase.exists() && checkpointBase.listFiles()?.isNotEmpty() == true,
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
}
