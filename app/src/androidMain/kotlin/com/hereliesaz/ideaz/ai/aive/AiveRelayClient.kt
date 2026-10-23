package com.hereliesaz.ideaz.ai.aive

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Reaches The Aive on another device through the Cloudflare relay (docs/aive-integration.md).
 * Every frame is AES-256-GCM sealed under the pairing code's key ([AiveRelayCodec]), so the relay
 * sees ciphertext and room metadata only.
 *
 * The WebSocket opens on demand and is shared by overlapping calls. It closes [IDLE_LINGER_MS]
 * after the last user, so a launch followed at once by a watch reuses one connection. A request
 * that fails before it was sent is retried once on a fresh connection; one that was already sent
 * is not, since The Aive may have acted on it.
 */
class AiveRelayClient(
    private val code: AivePairingCode,
    private val http: OkHttpClient = defaultHttp(),
    private val clock: () -> Long = System::currentTimeMillis,
) : AiveTransport {

    override val isRemote: Boolean get() = true

    /** Not available over the relay: The Aive is on another device. */
    override fun openAive(): Boolean = false

    private sealed interface RelayEvent {
        class Status(val run: AiveRemoteClient.RunStatus) : RelayEvent
        class LaunchFailed(val error: String) : RelayEvent
        object HostOffline : RelayEvent
        class Closed(val cause: AiveRelayException) : RelayEvent
    }

    /** A failure before the request left this device, so a retry cannot duplicate it. */
    private class ConnectFailed(val reason: AiveRelayException) : RuntimeException(reason.message, reason)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var current: Connection? = null
    private val nextId = AtomicLong(1)
    private var closed = false

    /** launchId -> failure text (null while pending or started), for launches made by this client. */
    private val launches = object : LinkedHashMap<String, String?>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?) = size > MAX_TRACKED_LAUNCHES
    }

    private inner class Connection : WebSocketListener() {
        val codec = AiveRelayCodec(code, clock)
        val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonObject>>()
        val opened = CompletableDeferred<Unit>()
        val presence = CompletableDeferred<Unit>()
        val events = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        @Volatile var hostOnline: Boolean? = null
        @Volatile var failure: AiveRelayException? = null
        @Volatile private var socket: WebSocket? = null
        private val sendLock = Any()

        /** Callers using this connection; guarded by [lock]. */
        var users = 0

        fun start() {
            val url = code.relay.toHttpUrl().newBuilder()
                .addPathSegments("v1/rooms")
                .addPathSegment(code.room)
                .addQueryParameter("role", "client")
                .build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer ${code.clientToken}").build()
            socket = http.newWebSocket(request, this)
        }

        /** Seals and sends one message; sealing and sending are one step so sequence numbers stay in order. */
        fun send(fields: JsonObject): Boolean = synchronized(sendLock) {
            val ws = socket ?: return false
            ws.send(buildJsonObject { put("data", codec.seal(fields)) }.toString())
        }

        fun close() {
            socket?.close(1000, "done")
            fail(AiveRelayException("The relay connection is closed.", retryable = true))
        }

        fun fail(cause: AiveRelayException) {
            synchronized(this) {
                if (failure != null) return
                failure = cause
            }
            opened.completeExceptionally(cause)
            presence.complete(Unit)
            failPending(cause)
            events.tryEmit(RelayEvent.Closed(cause))
        }

        private fun failPending(cause: AiveRelayException) {
            pending.values.forEach { it.completeExceptionally(cause) }
            pending.clear()
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val envelope = try {
                Json.parseToJsonElement(text) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return
            when (envelope.str("event")) {
                "host-online" -> {
                    hostOnline = true
                    presence.complete(Unit)
                }
                "host-offline" -> {
                    hostOnline = false
                    presence.complete(Unit)
                    failPending(AiveHostOfflineException())
                    events.tryEmit(RelayEvent.HostOffline)
                }
            }
            val data = envelope.str("data") ?: return
            // A frame that fails any check is dropped; it never reaches the caller.
            val message = try {
                codec.open(data)
            } catch (_: AiveClockSkewException) {
                // Authentic but stale or from the future: every frame will fail the same way, so
                // say why now instead of letting the caller wait out its timeout.
                val hint = AiveRelayException(CLOCK_SKEW_HINT, retryable = false)
                failPending(hint)
                events.tryEmit(RelayEvent.Closed(hint))
                return
            } catch (_: AiveFrameException) {
                return
            }
            dispatch(message)
        }

        private fun dispatch(message: JsonObject) {
            when (message.str("t")) {
                "launched" -> {
                    message.str("launchId")?.let { synchronized(launches) { launches[it] = null } }
                    complete(message)
                }
                "result", "error", "pong" -> complete(message)
                "launch_outcome" -> {
                    val launchId = message.str("launchId") ?: return
                    val started = (message["started"] as? JsonPrimitive)?.booleanOrNull == true
                    val error = synchronized(launches) {
                        if (!launches.containsKey(launchId)) return
                        if (started) {
                            launches.remove(launchId)
                            null
                        } else {
                            (message.str("error") ?: "The Aive could not start the workflow.").also { launches[launchId] = it }
                        }
                    }
                    if (error != null) events.tryEmit(RelayEvent.LaunchFailed(error))
                }
                "run_status" -> events.tryEmit(
                    RelayEvent.Status(
                        AiveRemoteClient.RunStatus(
                            runId = message.str("runId").orEmpty(),
                            objective = message.str("objective").orEmpty(),
                            status = message.str("runStatus").orEmpty(),
                            tasksTotal = message.lng("tasksTotal")?.toInt() ?: 0,
                            tasksDone = message.lng("tasksDone")?.toInt() ?: 0,
                            progressMessage = message.str("progressMessage"),
                        )
                    )
                )
            }
        }

        private fun complete(message: JsonObject) {
            message.lng("id")?.let { pending.remove(it)?.complete(message) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            fail(AiveRelayException("The relay closed the connection."))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            fail(AiveRelayException("The relay closed the connection."))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            fail(
                when (response?.code) {
                    401, 403 -> AiveRelayException(
                        "The relay rejected your Aive pairing code. Turn Remote access on in The Aive and pair again.",
                        t, retryable = false,
                    )
                    404 -> AiveRelayException(
                        "The relay does not know this Aive pairing. Pair again from The Aive.",
                        t, retryable = false,
                    )
                    else -> AiveRelayException("Could not reach the relay: ${t.message ?: t::class.simpleName}", t)
                }
            )
        }
    }

    private fun acquire(): Connection = synchronized(lock) {
        if (closed) throw AiveRelayException("The relay client is closed.", retryable = false)
        val connection = current?.takeIf { it.failure == null } ?: Connection().also {
            current = it
            it.start()
        }
        connection.users++
        connection
    }

    private fun release(connection: Connection) {
        synchronized(lock) {
            connection.users--
            if (connection.users > 0) return
            if (closed) {
                // close() already cancelled the linger scope; close the socket here.
                if (current === connection) current = null
                connection.close()
                return
            }
        }
        scope.launch {
            delay(IDLE_LINGER_MS)
            synchronized(lock) {
                if (connection.users == 0) {
                    if (current === connection) current = null
                    connection.close()
                }
            }
        }
    }

    /** Closes the connection and any pending work. The client is unusable afterwards. */
    override fun close() {
        scope.cancel()
        synchronized(lock) {
            closed = true
            current?.close()
            current = null
        }
    }

    /** Waits for the socket to open and for The Aive to be online. */
    private suspend fun ready(connection: Connection) {
        try {
            withTimeout(CONNECT_TIMEOUT_MS) { connection.opened.await() }
        } catch (_: TimeoutCancellationException) {
            val timeout = AiveRelayException("Timed out connecting to the relay.")
            connection.fail(timeout)
            throw timeout
        }
        withTimeoutOrNull(PRESENCE_WAIT_MS) { connection.presence.await() }
        connection.failure?.let { throw it }
        if (connection.hostOnline == false) throw AiveHostOfflineException()
    }

    private suspend fun requestOnce(timeoutMs: Long, build: (Long) -> JsonObject): JsonObject {
        val connection = acquire()
        try {
            try {
                ready(connection)
            } catch (e: AiveRelayException) {
                throw if (e.retryable) ConnectFailed(e) else e
            }
            val id = nextId.getAndIncrement()
            val waiter = CompletableDeferred<JsonObject>()
            connection.pending[id] = waiter
            try {
                // Registered first: a failure now either shows here or completes the waiter.
                connection.failure?.let { throw ConnectFailed(it) }
                if (!connection.send(build(id))) {
                    throw ConnectFailed(AiveRelayException("The relay connection dropped."))
                }
                return withTimeoutOrNull(timeoutMs) { waiter.await() }
                    ?: throw AiveRelayException("Timed out waiting for your Aive device.", retryable = false)
            } finally {
                connection.pending.remove(id)
            }
        } finally {
            release(connection)
        }
    }

    private suspend fun request(timeoutMs: Long, build: (Long) -> JsonObject): JsonObject {
        val reply = try {
            requestOnce(timeoutMs, build)
        } catch (_: ConnectFailed) {
            delay(RETRY_DELAY_MS)
            try {
                requestOnce(timeoutMs, build)
            } catch (second: ConnectFailed) {
                throw second.reason
            }
        }
        if (reply.str("t") == "error") {
            throw IllegalStateException(reply.str("error") ?: "The Aive failed.")
        }
        return reply
    }

    override suspend fun prompt(prompt: String, providerId: String?, model: String?): String {
        val size = prompt.toByteArray(Charsets.UTF_8).size
        if (size > MAX_PROMPT_BYTES) {
            throw IllegalArgumentException(
                "This prompt is ${size / 1000} KB, over the ${MAX_PROMPT_BYTES / 1000} KB limit for a remote Aive. " +
                    "Use a smaller project or a shorter conversation."
            )
        }
        val reply = request(PROMPT_TIMEOUT_MS) { id ->
            buildJsonObject {
                put("t", "prompt")
                put("id", id)
                put("prompt", prompt)
                providerId?.let { put("providerId", it) }
                model?.let { put("model", it) }
            }
        }
        return reply.str("text").orEmpty()
    }

    /**
     * Queues the workflow on the Aive device, which starts it when its UI is shown there. The run id
     * is not known yet, so this returns null; a launch The Aive refuses surfaces through [watchRun].
     */
    override suspend fun launchWorkflow(projectName: String, objective: String, repositoryUrl: String?): String? {
        request(REQUEST_TIMEOUT_MS) { id ->
            buildJsonObject {
                put("t", "launch")
                put("id", id)
                put("projectName", projectName)
                put("objective", objective)
                repositoryUrl?.let { put("repositoryUrl", it) }
            }
        }
        return null
    }

    override fun watchRun(): Flow<AiveRemoteClient.RunStatus> = flow {
        val connection = acquire()
        try {
            ready(connection)
            connection.events
                .onSubscription {
                    takeLaunchFailure()?.let { throw AiveRelayException(it, retryable = false) }
                    connection.failure?.let { throw it }
                    val id = nextId.getAndIncrement()
                    val watch = buildJsonObject {
                        put("t", "watch")
                        put("id", id)
                    }
                    if (!connection.send(watch)) throw AiveRelayException("The relay connection dropped.")
                }
                .collect { event ->
                    when (event) {
                        is RelayEvent.Status -> emit(event.run)
                        is RelayEvent.LaunchFailed -> throw AiveRelayException(event.error, retryable = false)
                        is RelayEvent.HostOffline -> throw AiveHostOfflineException()
                        is RelayEvent.Closed -> throw event.cause
                    }
                }
        } finally {
            release(connection)
        }
    }

    private fun takeLaunchFailure(): String? = synchronized(launches) {
        val entry = launches.entries.firstOrNull { it.value != null } ?: return null
        val failure = entry.value
        launches.remove(entry.key)
        failure
    }

    /** Round trip to The Aive, in milliseconds: the Settings "Test connection". */
    suspend fun ping(): Long {
        val started = System.nanoTime()
        val reply = request(REQUEST_TIMEOUT_MS) { id ->
            buildJsonObject {
                put("t", "ping")
                put("id", id)
            }
        }
        if (reply.str("t") != "pong") throw AiveRelayException("Unexpected reply from The Aive.", retryable = false)
        return (System.nanoTime() - started) / 1_000_000
    }

    companion object {
        /** The Aive caps relay prompts here (UTF-8 bytes). */
        const val MAX_PROMPT_BYTES = 500_000

        /** Project snapshot budget when the prompt travels over the relay. */
        const val MAX_SNAPSHOT_BYTES = 200_000

        /** Shown when frames from The Aive are rejected for timestamp skew. */
        const val CLOCK_SKEW_HINT = "Your Aive device's clock and this device's clock differ by more than " +
            "10 minutes, so its replies were rejected. Set the date and time to automatic on both devices."

        private const val PROMPT_TIMEOUT_MS = 180_000L
        private const val REQUEST_TIMEOUT_MS = 20_000L
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val PRESENCE_WAIT_MS = 1_500L
        private const val RETRY_DELAY_MS = 500L
        private const val IDLE_LINGER_MS = 15_000L
        private const val MAX_TRACKED_LAUNCHES = 32

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
