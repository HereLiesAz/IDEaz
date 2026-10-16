package com.hereliesaz.ideaz.ai.aive

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

/** The relay client against a MockWebServer that plays the relay and The Aive's host side. */
class AiveRelayClientTest {
    private lateinit var server: MockWebServer
    private lateinit var code: AivePairingCode
    private val clients = mutableListOf<AiveRelayClient>()
    private val hostSockets = java.util.concurrent.CopyOnWriteArrayList<WebSocket>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        code = AivePairingCode.parse(
            AivePairingCode.encode("http://127.0.0.1:${server.port}", "room-1", "client-token", ByteArray(32) { (it * 3).toByte() })
        )
    }

    @After
    fun tearDown() {
        clients.forEach { it.close() }
        // MockWebServer.shutdown() asserts if a server-side socket is still open.
        hostSockets.forEach { runCatching { it.cancel() } }
        runCatching { server.shutdown() }
    }

    private fun newClient() = AiveRelayClient(code).also { clients += it }

    /** The Aive's side of one relay connection. */
    private inner class Host(
        private val greeting: String? = ONLINE,
        private val handler: (WebSocket, JsonObject, Host) -> Unit,
    ) : WebSocketListener() {
        private val codec = AiveRelayCodec(code, sendDir = "h2c", recvDir = "c2h")

        override fun onOpen(webSocket: WebSocket, response: Response) {
            hostSockets += webSocket
            greeting?.let { webSocket.send(it) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val data = Json.parseToJsonElement(text).jsonObject["data"]!!.jsonPrimitive.content
            handler(webSocket, codec.open(data), this)
        }

        fun send(webSocket: WebSocket, fields: JsonObject) {
            webSocket.send(buildJsonObject { put("data", codec.seal(fields)) }.toString())
        }
    }

    private fun enqueueHost(host: Host) {
        server.enqueue(MockResponse().withWebSocketUpgrade(host))
    }

    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content

    @Test
    fun `prompt round trips encrypted and carries the bearer token`() = runBlocking {
        enqueueHost(Host { ws, msg, host ->
            assertEquals("prompt", msg.text("t"))
            assertEquals("gemini", msg.text("providerId"))
            host.send(ws, buildJsonObject {
                put("t", "result")
                put("id", msg.text("id").toLong())
                put("text", "echo:" + msg.text("prompt"))
            })
        })
        val reply = newClient().prompt("hello", providerId = "gemini")
        assertEquals("echo:hello", reply)
        val upgrade = server.takeRequest()
        assertEquals("Bearer client-token", upgrade.getHeader("Authorization"))
        assertEquals("/v1/rooms/room-1?role=client", upgrade.path)
    }

    @Test
    fun `concurrent prompts are matched to their replies by id`() = runBlocking {
        val held = mutableListOf<JsonObject>()
        enqueueHost(Host { ws, msg, host ->
            val ready = synchronized(held) {
                held += msg
                if (held.size == 2) held.reversed() else null
            }
            ready?.forEach { m ->
                host.send(ws, buildJsonObject {
                    put("t", "result")
                    put("id", m.text("id").toLong())
                    put("text", "re:" + m.text("prompt"))
                })
            }
        })
        val client = newClient()
        val results = listOf("A", "B").map { async { client.prompt(it) } }.awaitAll()
        assertEquals(listOf("re:A", "re:B"), results)
    }

    @Test
    fun `an error reply becomes an exception`() = runBlocking {
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
    fun `an offline host fails fast with a clear message`() = runBlocking {
        enqueueHost(Host(greeting = OFFLINE) { _, _, _ -> fail("nothing should be sent to an offline host") })
        val started = System.nanoTime()
        try {
            newClient().prompt("x")
            fail("expected offline")
        } catch (e: AiveHostOfflineException) {
            assertEquals("Your Aive device is offline or Remote access is off", e.message)
        }
        assertTrue("took too long", (System.nanoTime() - started) / 1_000_000 < 5_000)
    }

    @Test
    fun `the host going offline mid-request fails the request`() = runBlocking {
        enqueueHost(Host { ws, _, _ -> ws.send(OFFLINE) })
        try {
            newClient().prompt("x")
            fail("expected offline")
        } catch (e: AiveHostOfflineException) {
            assertTrue(e.message!!.contains("offline"))
        }
    }

    @Test
    fun `forged and malformed frames are ignored`() = runBlocking {
        enqueueHost(Host { ws, msg, host ->
            ws.send("""{"data":"1.AAAA"}""")
            ws.send("not json")
            val reply = buildJsonObject {
                put("t", "result")
                put("id", msg.text("id").toLong())
                put("text", "real")
            }
            host.send(ws, reply)
        })
        assertEquals("real", newClient().prompt("x"))
    }

    @Test
    fun `ping gets a pong`() = runBlocking {
        enqueueHost(Host { ws, msg, host ->
            host.send(ws, buildJsonObject {
                put("t", "pong")
                put("id", msg.text("id").toLong())
            })
        })
        assertTrue(newClient().ping() >= 0)
    }

    @Test
    fun `an oversize prompt is refused before anything is sent`() = runBlocking {
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
    fun `a rejected pairing is reported without retrying`() = runBlocking {
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
    fun `a failed connect is retried once`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        enqueueHost(Host { ws, msg, host ->
            host.send(ws, buildJsonObject {
                put("t", "result")
                put("id", msg.text("id").toLong())
                put("text", "second try")
            })
        })
        assertEquals("second try", newClient().prompt("x"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a launch the host refuses surfaces on the watch`() = runBlocking {
        enqueueHost(Host { ws, msg, host ->
            if (msg.text("t") == "launch") {
                assertEquals("My app", msg.text("projectName"))
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
        try {
            withTimeout(10_000) { client.watchRun().first() }
            fail("expected the launch failure")
        } catch (e: AiveRelayException) {
            assertEquals("No workspace is open.", e.message)
        }
    }

    @Test
    fun `run status frames arrive on the watch`() = runBlocking {
        enqueueHost(Host { ws, msg, host ->
            if (msg.text("t") == "watch") {
                host.send(ws, buildJsonObject {
                    put("t", "run_status")
                    put("runId", "run-9")
                    put("objective", "add dark mode")
                    put("runStatus", "Running")
                    put("tasksTotal", 4)
                    put("tasksDone", 1)
                    put("progressMessage", "Planning")
                })
            }
        })
        val run = withTimeout(10_000) { newClient().watchRun().first() }
        assertEquals(
            AiveRemoteClient.RunStatus("run-9", "add dark mode", "Running", 4, 1, "Planning"),
            run,
        )
    }

    private companion object {
        const val ONLINE = """{"event":"host-online"}"""
        const val OFFLINE = """{"event":"host-offline"}"""
    }
}
