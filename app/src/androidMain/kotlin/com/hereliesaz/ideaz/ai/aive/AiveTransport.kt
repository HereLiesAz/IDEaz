package com.hereliesaz.ideaz.ai.aive

import kotlinx.coroutines.flow.Flow

/**
 * How IDEaz reaches The Aive: the bound service on this device ([AiveRemoteClient]) or the
 * encrypted relay to another device ([AiveRelayClient]). [AiveTransports] picks between them.
 */
interface AiveTransport {
    /** True when the work runs on another device, so it cannot be opened from here. */
    val isRemote: Boolean

    /** Runs [prompt] on The Aive's linked provider, or on [providerId] when given. */
    suspend fun prompt(prompt: String, providerId: String? = null, model: String? = null): String

    /** Queues a workflow in The Aive. Returns the run id when the reply carries one, else null. */
    suspend fun launchWorkflow(projectName: String, objective: String, repositoryUrl: String? = null): String?

    /** Status of the run The Aive shows, for as long as this is collected. */
    fun watchRun(): Flow<AiveRemoteClient.RunStatus>

    /** Brings The Aive to the front on this device. False when it cannot be opened. */
    fun openAive(): Boolean

    /** Releases connections and background work. A transport built per message must be closed. */
    fun close() {}
}

/** The bound service is missing or refused the connection (as opposed to failing a request). */
class AiveUnavailableException(message: String) : IllegalStateException(message)
