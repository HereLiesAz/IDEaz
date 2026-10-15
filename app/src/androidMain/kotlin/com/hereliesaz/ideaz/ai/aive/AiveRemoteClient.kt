package com.hereliesaz.ideaz.ai.aive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import com.hereliesaz.ideaz.ai.aive.AiveRemoteProtocol as P
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

/**
 * Client for The Aive's remote service ([AiveRemoteProtocol]). Each call binds, sends one request
 * and unbinds; [watchRun] stays bound while collected. Binding needs both apps signed with the same
 * key (the service's permission is signature-level).
 */
class AiveRemoteClient(context: Context) {
    private val context = context.applicationContext

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

    /** Runs [prompt] on The Aive's linked provider, or on [providerId] when given. */
    suspend fun prompt(prompt: String, providerId: String? = null, model: String? = null): String {
        val (readEnd, writeEnd) = ParcelFileDescriptor.createPipe()
        val reply = try {
            call(P.MSG_PROMPT, PROMPT_TIMEOUT_MS, afterSend = {
                withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { it.write(prompt.toByteArray(Charsets.UTF_8)) }
                }
            }) {
                putParcelable(P.KEY_PROMPT_FD, readEnd)
                providerId?.let { putString(P.KEY_PROVIDER_ID, it) }
                model?.let { putString(P.KEY_MODEL, it) }
            }
        } finally {
            readEnd.close()
            runCatching { writeEnd.close() }
        }
        return reply.getString(P.KEY_TEXT).orEmpty()
    }

    /**
     * Queues an orchestrated workflow in The Aive. The Aive launches it when it next shows its UI,
     * so call [openAive] afterwards. [repositoryUrl] must be `https://github.com/owner/name`.
     */
    suspend fun launchWorkflow(projectName: String, objective: String, repositoryUrl: String? = null) {
        call(P.MSG_LAUNCH_WORKFLOW, REQUEST_TIMEOUT_MS) {
            putString(P.KEY_PROJECT_NAME, projectName)
            putString(P.KEY_OBJECTIVE, objective)
            repositoryUrl?.let { putString(P.KEY_REPOSITORY_URL, it) }
        }
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
        } finally {
            reply?.cancel()
        }
    }

    /** Status of the run The Aive shows, for as long as this is collected. */
    fun watchRun(): Flow<RunStatus> = callbackFlow {
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
            } else if (message.what == P.MSG_ERROR) {
                close(IllegalStateException(message.data.getString(P.KEY_ERROR)))
            }
            true
        }
        val replyTo = Messenger(handler)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                runCatching { send(Messenger(service), P.MSG_WATCH_RUN, nextRequestId.getAndIncrement(), replyTo) {} }
                    .onFailure { close(it) }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                close(IllegalStateException("The Aive disconnected"))
            }
        }
        bind(connection)
        awaitClose { runCatching { context.unbindService(connection) } }
    }.flowOn(Dispatchers.Main)

    /** Brings The Aive to the front, which launches queued workflows. False when not installed. */
    fun openAive(): Boolean {
        val pkg = installedPackage(context) ?: return false
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
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
        withContext(Dispatchers.Main) { bind(connection) }
        reply.invokeOnCompletion {
            Handler(Looper.getMainLooper()).post { runCatching { context.unbindService(connection) } }
        }
        try {
            send(connected.await(), what, requestId, replyTo, fill)
        } catch (failure: Throwable) {
            reply.completeExceptionally(failure)
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
        val pkg = installedPackage(context) ?: error("The Aive is not installed.")
        val bound = try {
            context.bindService(Intent(P.ACTION_BIND).setPackage(pkg), connection, Context.BIND_AUTO_CREATE)
        } catch (_: SecurityException) {
            false
        }
        if (!bound) {
            runCatching { context.unbindService(connection) }
            error("IDEaz cannot reach The Aive. Both apps must come from the same source (Play or GitHub) and be signed by the same key.")
        }
    }

    companion object {
        private const val PROMPT_TIMEOUT_MS = 180_000L
        private const val REQUEST_TIMEOUT_MS = 15_000L
        private val nextRequestId = AtomicLong(1)

        /** Play and GitHub package names of The Aive, in preference order. */
        val PACKAGES = listOf("com.hereliesaz.aive", "com.hereliesaz.haive")

        /** The installed Aive package exposing the remote service, or null. */
        fun installedPackage(context: Context): String? = PACKAGES.firstOrNull { pkg ->
            context.packageManager.queryIntentServices(Intent(P.ACTION_BIND).setPackage(pkg), 0).isNotEmpty()
        }
    }
}
