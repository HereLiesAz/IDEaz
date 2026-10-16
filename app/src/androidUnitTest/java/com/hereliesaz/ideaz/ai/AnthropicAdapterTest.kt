package com.hereliesaz.ideaz.ai

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** AnthropicAdapter against a local server: its URL is fixed, so an interceptor redirects it. */
class AnthropicAdapterTest {

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

    private fun adapter(): AnthropicAdapter {
        val local = server.url("/v1/messages")
        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().url(local).build()) }
            .build()
        return AnthropicAdapter("key", tools, { "claude-test" }, client)
    }

    @Test
    fun `a tool call cut off at max_tokens is not run`() = runBlocking {
        File(tempFolder.root, "app.js").writeText("original content")
        // The cut-off tool_use has no "content": run, it would have emptied app.js.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"stop_reason":"max_tokens","content":[
                  {"type":"tool_use","id":"t1","name":"write_file","input":{"path":"app.js"}}]}"""
            )
        )

        val reply = adapter().chat(listOf(ChatMessage("user", "rewrite app.js")))

        assertTrue("got: $reply", reply.startsWith("Error:") && reply.contains("token limit"))
        assertEquals("original content", File(tempFolder.root, "app.js").readText())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `truncated text is returned with a note`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"stop_reason":"max_tokens","content":[{"type":"text","text":"Part one"}]}"""
            )
        )
        val reply = adapter().chat(listOf(ChatMessage("user", "explain")))
        assertTrue("got: $reply", reply.startsWith("Part one") && reply.contains("cut off"))
    }
}
