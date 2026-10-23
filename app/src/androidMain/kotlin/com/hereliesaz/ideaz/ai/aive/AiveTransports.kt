package com.hereliesaz.ideaz.ai.aive

import android.content.Context
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Builds the [AiveTransport] IDEaz uses. */
object AiveTransports {
    fun create(context: Context, settings: SettingsViewModel): AiveTransport {
        val app = context.applicationContext
        return AiveTransportSelector(
            local = AiveRemoteClient(app),
            localInstalled = { AiveRemoteClient.installedPackage(app) != null },
            relayFactory = { AivePairingCode.parseOrNull(settings.getAivePairingCode())?.let { AiveRelayClient(it) } },
        )
    }
}

/**
 * Picks the route per call: The Aive on this device when it is installed and binds, else the relay
 * when a pairing code is saved, else an error naming both options. A launch fixes the route for the
 * [watchRun] that follows it.
 */
class AiveTransportSelector(
    private val local: AiveTransport,
    private val localInstalled: () -> Boolean,
    relayFactory: () -> AiveTransport?,
) : AiveTransport {

    private val relayLazy = lazy(relayFactory)
    private val relay: AiveTransport? get() = relayLazy.value

    @Volatile private var active: AiveTransport? = null

    private fun preferred(): AiveTransport? = if (localInstalled()) local else relay

    override val isRemote: Boolean get() = (active ?: preferred())?.isRemote ?: false

    private suspend fun <T> viaRoute(block: suspend (AiveTransport) -> T): T {
        val first = preferred() ?: throw AiveUnavailableException(NOT_CONFIGURED)
        try {
            return block(first).also { active = first }
        } catch (e: AiveUnavailableException) {
            // Only the local route throws this: The Aive is installed but would not bind.
            val remote = relay ?: throw AiveUnavailableException("${e.message} $OR_PAIR")
            return block(remote).also { active = remote }
        }
    }

    override suspend fun prompt(prompt: String, providerId: String?, model: String?): String =
        viaRoute { it.prompt(prompt, providerId, model) }

    override suspend fun launchWorkflow(projectName: String, objective: String, repositoryUrl: String?): String? =
        viaRoute { it.launchWorkflow(projectName, objective, repositoryUrl) }

    override fun watchRun(): Flow<AiveRemoteClient.RunStatus> = flow {
        val route = active ?: preferred() ?: throw AiveUnavailableException(NOT_CONFIGURED)
        emitAll(route.watchRun())
    }

    override fun openAive(): Boolean = (active ?: preferred())?.openAive() ?: false

    override fun close() {
        // Do not build a relay client just to close it.
        if (relayLazy.isInitialized()) relayLazy.value?.close()
        local.close()
    }

    companion object {
        const val OR_PAIR = "Or pair an Aive on another device: paste its pairing code in Settings."
        const val NOT_CONFIGURED = "The Aive is not available. Install The Aive on this device " +
            "(same source and signing key as IDEaz), or paste the pairing code from The Aive's " +
            "Remote access screen in Settings to use an Aive on another device."
    }
}
