package com.hereliesaz.ideaz.ai.aive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The relay client against a MockWebServer that plays the relay and The Aive's host side.
 *
 * The host side runs on OkHttp's reader thread, where an assertion failure would only kill that
 * thread and leave the client waiting out its timeout. So host handlers never assert: every
 * frame they receive goes into [received], any handler exception into [hostErrors], and the test
 * thread asserts on both (with timeouts) afterwards.
 */
class AiveRelayClientTest {
    private lateinit var server: MockWebServer
    private lateinit var code: AivePairingCode
    private val clients = mutableListOf<AiveRelayClient>()
    private val hostSockets = java.util.concurrent.CopyOnWriteArrayList<WebSocket>()

    /** Every frame the host side opened, in arrival order. */
    private val received = LinkedBlockingQueue<JsonObject>()

    /** Exceptions thrown by host handlers on the reader thread. */
    private val hostErrors = LinkedBlockingQueue<Throwable>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        code = pairing(key = ByteArray(32) { (it * 3).toByte() })
    }

    @After
    fun tearDown() {
        clients.forEach { it.close() }
        // MockWebServer.shutdown() throws if a server-side socket is still open; that failure is
        // exactly the leak this guards against, so it is not swallowed.
        hostSockets.forEach { it.cancel() }
        try {
            server.shutdown()
        } finally {
            assertTrue("host handler failed on the reader thread: ${hostErrors.toList()}", hostErrors.isEmpty())
        }
    }

    private fun pairing(key: ByteArray) = AivePairingCode.parse(
        AivePairingCode.encode("http://127.0.0.1:${server.port}", "room-1", "client-token", key)
    )

    private fun newClient(requestTimeoutMs: Long = 20_000L, promptTimeoutMs: Long = 180_000L) =
        AiveRelayClient(code, requestTimeoutMs = requestTimeoutMs, promptTimeoutMs = promptTimeoutMs)
            .also { clients += it }

    /** Runs a test body on the test thread with a hard ceiling, so a hang fails instead of stalling. */
    private fun blocking(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        withTimeout(60_000) { block() }
    }

    /** The Aive's side of one relay connection. */
    private inner class Host(
        private val greeting: String? = ONLINE,
        private val handler: (WebSocket, JsonObject, Host) -> Unit = { _, _, _ -> },
    ) : WebSocketListener() {
        val codec = AiveRelayCodec(code, sendDir = "h2c", recvDir = "c2h")

        override fun onOpen(webSocket: WebSocket, response: Response) {
            hostSockets += webSocket
            greeting?.let { webSocket.send(it) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val data = Json.parseToJsonElement(text).jsonObject["data"]!!.jsonPrimitive.content
                val message = codec.open(data)
                received.add(message)
                handler(webSocket, message, this)
            } catch (t: Throwable) {
                hostErrors.add(t)
            }
        }

        fun send(webSocket: WebSocket, fields: JsonObject) = sendRaw(webSocket, codec.seal(fields))

        fun sendRaw(webSocket: WebSocket, data: String) {
            webSocket.send(buildJsonObject { put("data", data) }.toString())
        }
    }

    private fun enqueueHost(host: Host) {
        server.enqueue(MockResponse().withWebSocketUpgrade(host))
    }

    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content

    private suspend fun nextReceived(): JsonObject = withContext(Dispatchers.IO) {
        received.poll(10, TimeUnit.SECONDS) ?: error("the host received nothing within 10 s")
    }

    /** True when the host receives no further frame within [waitMs]. */
    private suspend fun nothingMoreReceived(waitMs: Long = 700): Boolean = withContext(Dispatchers.IO) {
        received.poll(waitMs, TimeUnit.MILLISECONDS) == null
    }

    private fun status(runId: String, state: String) = buildJsonObject {
        put("t", "run_status")
        put("runId", runId)
        put("objective", "add dark mode")
        put("runStatus", state)
        put("tasksTotal", 4)
        put("tasksDone", 1)
        put("progressMessage", "Planning")
    }

    private fun result(msg: JsonObject, text: String) = buildJsonObject {
        put("t", "result")
        put("id", msg.text("id").toLong())
        put("text", text)
    }

    @Test
    fun `prompt round trips encrypted and carries the bearer token`() = blocking {
        enqueueHost(Host { ws, msg, host -> host.send(ws, result(msg, "echo:" + msg.text("prompt"))) })
        val reply = newClient().prompt("hello", providerId = "gemini")
        assertEquals("echo:hello", reply)
        val sent = nextReceived()
        assertEquals("prompt", sent.text("t"))
        assertEquals("gemini", sent.text("providerId"))
        val upgrade = server.takeRequest()
        assertEquals("Bearer client-token", upgrade.getHeader("Authorization"))
        assertEquals("/v1/rooms/room-1?role=client", upgrade.path)
    }

    @Test
    fun `concurrent prompts are matched to their replies by id`() = blocking {
        val held = mutableListOf<JsonObject>()
        enqueueHost(Host { ws, msg, host ->
            val ready = synchronized(held) {
                held += msg
                if (held.size == 2) held.reversed() else null
            }
            ready?.forEach { m -> host.send(ws, result(m, "re:" + m.text("prompt"))) }
        })
        val client = newClient()
        val results = listOf("A", "B").map { async { client.prompt(it) } }.awaitAll()
        assertEquals(listOf("re:A", "re:B"), results)
    }

    @Test
    fun `an error reply becomes an exception`() = blocking {
        enqueueHost(Host { ws, msg, host ->
            host.send(ws, buildJsonObject {
                put("t", "error")
                put("id", msg.text("id").toLong())
                put("error", "No provider is linked in The Aive.")
            })
        })
        try {
            newClient().prompt("x")
            fail("expected an error")
        } catch (e: IllegalStateException) {
            assertEquals("No provider is linked in The Aive.", e.message)
        }
    }

    @Test
    fun `an offline host fails fast and nothing is sent to it`() = blocking {
        enqueueHost(Host(greeting = OFFLINE))
        val started = System.nanoTime()
        try {
            newClient().prompt("x")
            fail("expected offline")
        } catch (e: AiveHostOfflineException) {
            assertEquals("Your Aive device is offline or Remote access is off", e.message)
        }
        assertTrue("took too long", (System.nanoTime() - started) / 1_000_000 < 5_000)
        // Asserted on the test thread: a regression that sent the prompt anyway lands in `received`.
        assertTrue("a prompt was sent to an offline host: ${received.toList()}", nothingMoreReceived())
    }

    @Test
    fun `the host going offline mid-request fails the request`() = blocking {
        enqueueHost(Host { ws, _, _ -> ws.send(OFFLINE) })
        try {
            newClient().prompt("x")
            fail("expected offline")
        } catch (e: AiveHostOfflineException) {
            assertTrue(e.message!!.contains("offline"))
        }
    }

    @Test
    fun `malformed frames are ignored`() = blocking {
        enqueueHost(Host { ws, msg, host ->
            ws.send("""{"data":"1.AAAA"}""") // far too short to hold a nonce and tag
            ws.send("not json")
            host.send(ws, result(msg, "real"))
        })
        assertEquals("real", newClient().prompt("x"))
    }

    @Test
    fun `well-sealed frames with the wrong direction or the wrong key are dropped`() = blocking {
        // Authentic-looking frames that fail authentication: sealed for the other direction (wrong
        // AAD) or under a different key. Both carry a FORGED status that must never surface.
        val wrongDirection = AiveRelayCodec(code, sendDir = "c2h", recvDir = "h2c")
        val wrongKey = AiveRelayCodec(pairing(ByteArray(32) { 99 }), sendDir = "h2c", recvDir = "c2h")
        enqueueHost(Host { ws, msg, host ->
            if (msg.text("t") == "watch") {
                host.sendRaw(ws, wrongDirection.seal(status("evil-1", "FORGED")))
                host.sendRaw(ws, wrongKey.seal(status("evil-2", "FORGED")))
                host.send(ws, status("run-1", "Running"))
            }
        })
        val first = withTimeout(10_000) { newClient().watchRun().first() }
        assertEquals("Running", first.status)
        assertEquals("run-1", first.runId)
    }

    @Test
    fun `a replayed frame is dropped`() = blocking {
        enqueueHost(Host { ws, msg, host ->
            if (msg.text("t") == "watch") {
                val firstFrame = host.codec.seal(status("run-1", "First"))
                host.sendRaw(ws, firstFrame)
                host.sendRaw(ws, firstFrame) // same seq again: must not become a second event
                host.send(ws, status("run-1", "Second"))
            }
        })
        val seen = withTimeout(10_000) { newClient().watchRun().take(2).toList() }
        assertEquals(listOf("First", "Second"), seen.map { it.status })
    }

    @Test
    fun `a stale timestamp is not delivered and says why`() = blocking {
        val staleClock = { System.currentTimeMillis() - 20 * 60 * 1000L }
        val stale = AiveRelayCodec(code, staleClock, sendDir = "h2c", recvDir = "c2h")
        enqueueHost(Host { ws, msg, host ->
            host.sendRaw(ws, stale.seal(result(msg, "too old")))
        })
        try {
            newClient().prompt("x")
            fail("a stale reply must not be accepted")
        } catch (e: AiveRelayException) {
            assertEquals(AiveRelayClient.CLOCK_SKEW_HINT, e.message)
        }
    }

    @Test
    fun `ping gets a pong`() = blocking {
        enqueueHost(Host { ws, msg, host ->
            host.send(ws, buildJsonObject {
                put("t", "pong")
                put("id", msg.text("id").toLong())
            })
        })
        assertTrue(newClient().ping() >= 0)
    }

    @Test
    fun `an oversize prompt is refused before anything is sent`() = blocking {
        val client = newClient()
        try {
            client.prompt("x".repeat(AiveRelayClient.MAX_PROMPT_BYTES + 1))
            fail("expected a size error")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("limit"))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a rejected pairing is reported without retrying`() = blocking {
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            newClient().prompt("x")
            fail("expected rejection")
        } catch (e: AiveRelayException) {
            assertTrue(e.message!!.contains("rejected"))
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a failed connect is retried once`() = blocking {
        server.enqueue(MockResponse().setResponseCode(503))
        enqueueHost(Host { ws, msg, host -> host.send(ws, result(msg, "second try")) })
        assertEquals("second try", newClient().prompt("x"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a prompt the host never answers times out and is not sent twice`() = blocking {
        enqueueHost(Host()) // receives the prompt, never replies
        try {
            newClient(promptTimeoutMs = 400).prompt("x")
            fail("expected a timeout")
        } catch (e: AiveRelayException) {
            assertEquals("Timed out waiting for your Aive device.", e.message)
        }
        assertEquals("prompt", nextReceived().text("t"))
        assertTrue("the prompt was sent again after it had already been sent", nothingMoreReceived(1_200))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a request timeout applies to launch too`() = blocking {
        enqueueHost(Host())
        try {
            newClient(requestTimeoutMs = 400).launchWorkflow("My app", "add dark mode", null)
            fail("expected a timeout")
        } catch (e: AiveRelayException) {
            assertEquals("Timed out waiting for your Aive device.", e.message)
        }
        assertEquals("launch", nextReceived().text("t"))
        assertTrue("a launch must not be re-sent after it was sent", nothingMoreReceived(1_200))
    }

    @Test
    fun `the relay dropping mid-request fails it without a retry`() = blocking {
        enqueueHost(Host { ws, _, _ -> ws.close(1001, "going away") })
        try {
            newClient().prompt("x")
            fail("expected the dropped connection to fail the request")
        } catch (e: AiveRelayException) {
            assertTrue(e.message!!, e.message!!.contains("closed"))
        }
        // The request had left this device, so a retry could have run it twice.
        assertEquals("prompt", nextReceived().text("t"))
        assertTrue("the prompt was re-sent after the drop", nothingMoreReceived(1_200))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a launch the host refuses surfaces on the watch`() = blocking {
        enqueueHost(Host { ws, msg, host ->
            if (msg.text("t") == "launch") {
                host.send(ws, buildJsonObject {
                    put("t", "launched")
                    put("id", msg.text("id").toLong())
                    put("launchId", "L1")
                })
                host.send(ws, buildJsonObject {
                    put("t", "launch_outcome")
                    put("launchId", "L1")
                    put("started", false)
                    put("error", "No workspace is open.")
                })
            }
        })
        val client = newClient()
        assertNull(client.launchWorkflow("My app", "add dark mode", "https://github.com/a/b"))
        assertEquals("My app", nextReceived().text("projectName"))
        try {
            withTimeout(10_000) { client.watchRun().first() }
            fail("expected the launch failure")
        } catch (e: AiveRelayException) {
            assertEquals("No workspace is open.", e.message)
        }
    }

    @Test
    fun `run status frames arrive on the watch`() = blocking {
        enqueueHost(Host { ws, msg, host ->
            if (msg.text("t") == "watch") host.send(ws, status("run-9", "Running"))
        })
        val run = withTimeout(10_000) { newClient().watchRun().first() }
        assertEquals(
            AiveRemoteClient.RunStatus("run-9", "add dark mode", "Running", 4, 1, "Planning"),
            run,
        )
    }

    @Test
    fun `the host going offline during a watch ends the watch`() = blocking {
        enqueueHost(Host { ws, msg, _ -> if (msg.text("t") == "watch") ws.send(OFFLINE) })
        try {
            withTimeout(10_000) { newClient().watchRun().first() }
            fail("expected the watch to end")
        } catch (e: AiveHostOfflineException) {
            assertTrue(e.message!!.contains("offline"))
        }
    }

    @Test
    fun `the relay closing a watch ends it with an error`() = blocking {
        enqueueHost(Host { ws, msg, _ -> if (msg.text("t") == "watch") ws.close(1000, "bye") })
        try {
            withTimeout(10_000) { newClient().watchRun().first() }
            fail("expected the watch to end")
        } catch (e: AiveRelayException) {
            assertTrue(e.message!!, e.message!!.contains("closed"))
        }
    }

    @Test
    fun `a closed client refuses new work`() = blocking {
        val client = newClient()
        client.close()
        try {
            client.prompt("hello")
            fail("a closed client must not connect")
        } catch (e: AiveRelayException) {
            assertTrue(e.message!!.contains("closed"))
        }
    }

    private companion object {
        const val ONLINE = """{"event":"host-online"}"""
        const val OFFLINE = """{"event":"host-offline"}"""
    }
}
