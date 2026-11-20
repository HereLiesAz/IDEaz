package com.hereliesaz.ideaz.ai

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class AiHordeImageGeneratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun generator(timeoutMillis: Long = 60_000, clock: LongArray = longArrayOf(0)) = AiHordeImageGenerator(
        baseUrl = server.url("/api/v2").toString().trimEnd('/'),
        pollMillis = 1_000,
        timeoutMillis = timeoutMillis,
        now = { clock[0] },
        sleep = { clock[0] += it },
    )

    private fun json(body: String) = MockResponse().setResponseCode(200).setBody(body)

    @Test
    fun `submits anonymously with snapped size, then polls check until done`() {
        server.enqueue(json("""{"id":"abc-123"}"""))
        server.enqueue(json("""{"done":false,"faulted":false,"is_possible":true}"""))
        server.enqueue(json("""{"done":true}"""))
        server.enqueue(json("""{"generations":[{"img":"AQID"}]}"""))

        generator().generate("a red fox", 500, 300)

        val submit = server.takeRequest()
        assertEquals("/api/v2/generate/async", submit.path)
        assertEquals("0000000000", submit.getHeader("apikey"))
        val body = submit.body.readUtf8()
        assertTrue(body, "\"prompt\":\"a red fox\"" in body)
        assertTrue(body, "\"width\":512" in body && "\"height\":320" in body)
        assertEquals("/api/v2/generate/check/abc-123", server.takeRequest().path)
        assertEquals("/api/v2/generate/check/abc-123", server.takeRequest().path)
        assertEquals("/api/v2/generate/status/abc-123", server.takeRequest().path)
    }

    @Test
    fun `inline base64 results are decoded`() {
        val bytes = byteArrayOf(9, 8, 7, 0, 6)
        server.enqueue(json("""{"id":"x1"}"""))
        server.enqueue(json("""{"done":true}"""))
        server.enqueue(json("""{"generations":[{"img":"${Base64.getEncoder().encodeToString(bytes)}"}]}"""))

        val image = generator().generate("a cat", 512, 512)

        assertArrayEquals(bytes, image.bytes)
        assertEquals("webp", image.extension)
    }

    @Test
    fun `a faulted job fails and frees its queue slot`() {
        server.enqueue(json("""{"id":"x2"}"""))
        server.enqueue(json("""{"done":false,"faulted":true}"""))
        server.enqueue(json("{}"))
        try {
            generator().generate("a cat", 512, 512)
            fail("expected failure")
        } catch (e: ImageGenerationException) {
            assertTrue(e.message!!.contains("could not generate"))
        }
        server.takeRequest(); server.takeRequest()
        val cancel = server.takeRequest()
        assertEquals("DELETE", cancel.method)
        assertEquals("/api/v2/generate/status/x2", cancel.path)
    }

    @Test
    fun `gives up at the timeout`() {
        server.enqueue(json("""{"id":"x3"}"""))
        repeat(5) { server.enqueue(json("""{"done":false}""")) }
        server.enqueue(json("{}"))
        try {
            generator(timeoutMillis = 2_500).generate("a cat", 512, 512)
            fail("expected timeout")
        } catch (e: ImageGenerationException) {
            assertTrue(e.message!!.contains("did not finish"))
        }
    }

    @Test
    fun `a refused submission reports Horde's message`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"message":"Too many prompts"}"""))
        try {
            generator().generate("a cat", 512, 512)
            fail("expected failure")
        } catch (e: ImageGenerationException) {
            assertTrue(e.message!!, e.message!!.contains("Too many prompts"))
        }
    }

    @Test
    fun `generate_image saves into the project and enforces the extension`() {
        val fake = object : ImageGenerator {
            override val extension = "webp"
            override fun generate(prompt: String, width: Int, height: Int) =
                GeneratedImage(byteArrayOf(1, 0, 2, width.toByte()), "webp")
        }
        val tools = IdeTools(tempFolder.root, fake)

        val bad = dispatchIdeTool("generate_image", mapOf("path" to "a.png", "prompt" to "x"), tools)
        assertTrue(bad, bad.startsWith("Error:") && ".webp" in bad)

        val ok = dispatchIdeTool("generate_image", mapOf("path" to "img/a.webp", "prompt" to "x", "width" to "64.0"), tools)
        assertTrue(ok, ok.startsWith("OK"))
        assertArrayEquals(byteArrayOf(1, 0, 2, 64), File(tempFolder.root, "img/a.webp").readBytes())

        val escape = dispatchIdeTool("generate_image", mapOf("path" to "../x.webp", "prompt" to "x"), tools)
        assertTrue(escape, escape.startsWith("Error:"))
        assertTrue(IdeToolSchema.mutating.contains("generate_image"))
    }

    @Test
    fun `without a generator the tool says so`() {
        val result = dispatchIdeTool("generate_image", mapOf("path" to "a.webp", "prompt" to "x"), IdeTools(tempFolder.root))
        assertTrue(result, result.startsWith("Error:"))
    }
}
