package com.hereliesaz.ideaz.ai.aive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import com.hereliesaz.ideaz.ai.aive.AiveRemoteProtocol as P
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Client for The Aive's remote service ([AiveRemoteProtocol]). Each call binds, sends one request
 * and unbinds; [watchRun] stays bound while collected. Binding needs both apps signed with the same
 * key (the service's permission is signature-level).
 */
class AiveRemoteClient(context: Context) : AiveTransport {
    private val context = context.applicationContext

    override val isRemote: Boolean get() = false

    /** Launch ids this client queued, so [watchRun] can report their outcome. */
    private val launches = LaunchOutcomes()

    data class RunStatus(
        val runId: String,
        val objective: String,
        /** A WorkflowRunStatus name: Created, Running, AwaitingHuman, Completed, Failed, Cancelled. */
        val status: String,
        val tasksTotal: Int,
        val tasksDone: Int,
        val progressMessage: String?,
    ) {
        val isFinished: Boolean get() = status == "Completed" || status == "Failed" || status == "Cancelled"
    }

    /**
     * Runs [prompt] on The Aive's linked provider, or on [providerId] when given.
     *
     * The prompt goes through a pipe, which holds about 64 KB; a larger prompt
     * blocks its write until The Aive reads. That write used to run inline, so
     * a service that errored or never read hung this call past its timeout
     * (a blocking write cannot be cancelled, and our own read end kept the pipe
     * open). Now the write runs on its own, outside the caller's scope, and the
     * call waits only for the reply within [PROMPT_TIMEOUT_MS]. Our read end is
     * closed as soon as the request is sent (the service holds its own copy),
     * and both ends are closed when the call ends, so the writer fails with
     * EPIPE once The Aive drops its copy too.
     */
    override suspend fun prompt(prompt: String, providerId: String?, model: String?): String {
        val (readEnd, writeEnd) = ParcelFileDescriptor.createPipe()
        val bytes = prompt.toByteArray(Charsets.UTF_8)
        var writer: Job? = null
        val reply = try {
            call(P.MSG_PROMPT, PROMPT_TIMEOUT_MS, afterSend = {
                runCatching { readEnd.close() }
                writer = pipeWriters.launch {
                    runCatching {
                        ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { it.write(bytes) }
                    }
                }
            }) {
                putParcelable(P.KEY_PROMPT_FD, readEnd)
                providerId?.let { putString(P.KEY_PROVIDER_ID, it) }
                model?.let { putString(P.KEY_MODEL, it) }
            }
        } finally {
            writer?.cancel()
            runCatching { readEnd.close() }
            runCatching { writeEnd.close() }
        }
        return reply.getString(P.KEY_TEXT).orEmpty()
    }

    /**
     * Queues an orchestrated workflow in The Aive. The Aive launches it when it next shows its UI,
     * so call [openAive] afterwards. [repositoryUrl] must be `https://github.com/owner/name`.
     *
     * When the reply carries a launch id ([P.KEY_LAUNCH_ID], protocol 2) it is remembered, and a
     * later [P.MSG_LAUNCH_OUTCOME] reporting that launch failed ends [watchRun] with the reason.
     *
     * @return the run id when The Aive's reply carries one ([P.KEY_RUN_ID]), else null.
     */
    override suspend fun launchWorkflow(projectName: String, objective: String, repositoryUrl: String?): String? {
        val reply = call(P.MSG_LAUNCH_WORKFLOW, REQUEST_TIMEOUT_MS) {
            putString(P.KEY_PROJECT_NAME, projectName)
            putString(P.KEY_OBJECTIVE, objective)
            repositoryUrl?.let { putString(P.KEY_REPOSITORY_URL, it) }
        }
        if (reply.containsKey(P.KEY_LAUNCH_ID)) launches.track(reply.getLong(P.KEY_LAUNCH_ID))
        return reply.getString(P.KEY_RUN_ID)?.takeIf { it.isNotBlank() }
    }

    /** One request/reply within [timeoutMs]; the binding is always released. */
    private suspend fun call(
        what: Int,
        timeoutMs: Long,
        afterSend: suspend () -> Unit = {},
        fill: Bundle.() -> Unit,
    ): Bundle {
        var reply: CompletableDeferred<Bundle>? = null
        try {
            return withTimeout(timeoutMs) {
                val pending = request(what, fill).also { reply = it }
                afterSend()
                pending.await()
            }
        } catch (e: TimeoutCancellationException) {
            // A timeout is a CancellationException; callers rethrow those, which would end the
            // job silently. Surface it as a real error instead.
            throw IllegalStateException(timeoutMessage(timeoutMs), e)
        } finally {
            reply?.cancel()
        }
    }

    /** Status of the run The Aive shows, for as long as this is collected. */
    override fun watchRun(): Flow<RunStatus> = callbackFlow {
        val handler = Handler(Looper.getMainLooper()) { message ->
            if (message.what == P.MSG_RUN_STATUS) {
                val data = message.data
                trySend(
                    RunStatus(
                        runId = data.getString(P.KEY_RUN_ID).orEmpty(),
                        objective = data.getString(P.KEY_OBJECTIVE).orEmpty(),
                        status = data.getString(P.KEY_RUN_STATUS).orEmpty(),
                        tasksTotal = data.getInt(P.KEY_TASKS_TOTAL),
                        tasksDone = data.getInt(P.KEY_TASKS_DONE),
                        progressMessage = data.getString(P.KEY_PROGRESS_MESSAGE),
                    )
                )
            } else if (message.what == P.MSG_LAUNCH_OUTCOME) {
                val data = message.data
                launches.outcome(
                    data.getLong(P.KEY_LAUNCH_ID, -1L),
                    data.getBoolean(P.KEY_LAUNCH_STARTED),
                    data.getString(P.KEY_ERROR),
                )?.let { close(IllegalStateException(it)) }
            } else if (message.what == P.MSG_ERROR) {
                close(IllegalStateException(message.data.getString(P.KEY_ERROR)))
            }
            true
        }
        val replyTo = Messenger(handler)
        val connectedFlag = AtomicBoolean(false)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                connectedFlag.set(true)
                runCatching { send(Messenger(service), P.MSG_WATCH_RUN, nextRequestId.getAndIncrement(), replyTo) {} }
                    .onFailure { close(it) }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                close(IllegalStateException("The Aive disconnected"))
            }

            override fun onBindingDied(name: ComponentName) {
                close(AiveUnavailableException("The Aive's service died"))
            }

            override fun onNullBinding(name: ComponentName) {
                close(AiveUnavailableException("The Aive refused the connection"))
            }
        }
        bind(connection)
        // A service that never answers the bind must not hold this flow (and the binding) forever.
        launch {
            delay(REQUEST_TIMEOUT_MS)
            if (!connectedFlag.get()) close(IllegalStateException(timeoutMessage(REQUEST_TIMEOUT_MS)))
        }
        awaitClose { runCatching { context.unbindService(connection) } }
    }.flowOn(Dispatchers.Main)

    /** Brings The Aive to the front, which launches queued workflows. False when not installed. */
    override fun openAive(): Boolean {
        val pkg = installedPackage(context) ?: return false
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** Binds, sends one request, and completes with the reply's data once it arrives. */
    private suspend fun request(what: Int, fill: Bundle.() -> Unit): CompletableDeferred<Bundle> {
        val reply = CompletableDeferred<Bundle>()
        val requestId = nextRequestId.getAndIncrement()
        val connected = CompletableDeferred<Messenger>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                connected.complete(Messenger(service))
            }

            override fun onServiceDisconnected(name: ComponentName) {
                reply.completeExceptionally(IllegalStateException("The Aive disconnected"))
            }

            // Without these a dead or refused binding waited out the whole timeout.
            override fun onBindingDied(name: ComponentName) {
                val failure = AiveUnavailableException("The Aive's service died")
                connected.completeExceptionally(failure)
                reply.completeExceptionally(failure)
            }

            override fun onNullBinding(name: ComponentName) {
                val failure = AiveUnavailableException("The Aive refused the connection")
                connected.completeExceptionally(failure)
                reply.completeExceptionally(failure)
            }
        }
        val replyTo = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.data.getLong(P.KEY_REQUEST_ID) == requestId) {
                when (message.what) {
                    P.MSG_RESULT -> reply.complete(message.data)
                    P.MSG_ERROR -> reply.completeExceptionally(
                        IllegalStateException(message.data.getString(P.KEY_ERROR) ?: "The Aive failed")
                    )
                }
            }
            true
        })
        // Registered before binding: a cancellation that lands while
        // withContext(Main) returns from bind() used to escape before this was
        // attached, leaving a BIND_AUTO_CREATE binding to The Aive alive until
        // process death. Unbinding a connection that never bound just throws,
        // which runCatching absorbs.
        reply.invokeOnCompletion {
            Handler(Looper.getMainLooper()).post { runCatching { context.unbindService(connection) } }
        }
        try {
            withContext(Dispatchers.Main) { bind(connection) }
            send(connected.await(), what, requestId, replyTo, fill)
        } catch (failure: Throwable) {
            reply.completeExceptionally(failure)
            if (failure is CancellationException) throw failure
        }
        return reply
    }

    private fun send(service: Messenger, what: Int, requestId: Long, replyTo: Messenger, fill: Bundle.() -> Unit) {
        service.send(
            Message.obtain(null, what).apply {
                this.replyTo = replyTo
                data = Bundle().apply {
                    putInt(P.KEY_VERSION, P.VERSION)
                    putLong(P.KEY_REQUEST_ID, requestId)
                    fill()
                }
            }
        )
    }

    private fun bind(connection: ServiceConnection) {
        val pkg = installedPackage(context) ?: throw AiveUnavailableException("The Aive is not installed.")
        // The prompt carries a project snapshot: never hand it to an app that merely claims the package name.
        if (!isSignedLikeUs(pkg)) {
            throw AiveUnavailableException(
                "The installed $pkg is not signed with the same key as IDEaz, so IDEaz will not send it your project. " +
                    "Install both apps from the same source (Play or GitHub)."
            )
        }
        val bound = try {
            context.bindService(Intent(P.ACTION_BIND).setPackage(pkg), connection, Context.BIND_AUTO_CREATE)
        } catch (_: SecurityException) {
            false
        }
        if (!bound) {
            runCatching { context.unbindService(connection) }
            throw AiveUnavailableException("IDEaz cannot reach The Aive. Both apps must come from the same source (Play or GitHub) and be signed by the same key.")
        }
    }

    /** True when [pkg] and this app are signed by exactly the same certificates. */
    private fun isSignedLikeUs(pkg: String): Boolean = try {
        sameSigners(signerDigests(context.packageName), signerDigests(pkg))
    } catch (_: Exception) {
        false
    }

    private fun signerDigests(pkg: String): Set<String> {
        val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        val signers = info.signingInfo?.apkContentsSigners ?: return emptySet()
        return signers.map { sha256Hex(it.toByteArray()) }.toSet()
    }

    companion object {
        private const val PROMPT_TIMEOUT_MS = 180_000L
        private const val REQUEST_TIMEOUT_MS = 15_000L
        private val nextRequestId = AtomicLong(1)

        /**
         * Prompt pipe writers. Not the caller's scope: a write blocked on a
         * full pipe cannot be cancelled, and the caller must not wait for it.
         */
        private val pipeWriters = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        internal fun timeoutMessage(timeoutMs: Long): String =
            "The Aive did not answer in ${timeoutMs / 1000} s. Open The Aive once and try again."

        /** Signer sets match only when ours is non-empty and identical to theirs. */
        internal fun sameSigners(ours: Set<String>, theirs: Set<String>): Boolean =
            ours.isNotEmpty() && ours == theirs

        internal fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** Play and GitHub package names of The Aive, in preference order. */
        val PACKAGES = listOf("com.hereliesaz.aive", "com.hereliesaz.haive")

        /** The installed Aive package exposing the remote service, or null. */
        fun installedPackage(context: Context): String? = PACKAGES.firstOrNull { pkg ->
            context.packageManager.queryIntentServices(Intent(P.ACTION_BIND).setPackage(pkg), 0).isNotEmpty()
        }
    }
}

/**
 * Launch ids queued through the Messenger path, mirroring the relay's launch tracking. An outcome
 * for an id this client did not queue (or a protocol 1 peer that sends none) is ignored.
 */
internal class LaunchOutcomes {
    private val pending = mutableSetOf<Long>()

    fun track(launchId: Long) {
        synchronized(pending) { pending.add(launchId) }
    }

    /** The failure text when [launchId] is ours and did not start; null when started or not ours. */
    fun outcome(launchId: Long, started: Boolean, error: String?): String? = synchronized(pending) {
        if (!pending.remove(launchId)) return null
        if (started) null else error ?: "The Aive could not start the workflow."
    }
}
