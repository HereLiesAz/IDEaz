package com.hereliesaz.ideaz.ai.bridge

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

/** Process-local mailbox between the external-app adapter and accessibility service. */
object GeminiAppBridge {
    enum class BridgePhase { IDLE, INPUT, AWAIT_RESPONSE }

    @Volatile var pendingPrompt: String? = null
    @Volatile var targetPackage: String? = null
    @Volatile var isWaiting: Boolean = false
    @Volatile var phase: BridgePhase = BridgePhase.IDLE
    @Volatile var promptSubmitted: Boolean = false

    val channel: Channel<String> = Channel(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val decisionChannel: Channel<Boolean> = Channel(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun reset() {
        pendingPrompt = null
        targetPackage = null
        isWaiting = false
        phase = BridgePhase.IDLE
        promptSubmitted = false
        while (channel.tryReceive().getOrNull() != null) { /* drain */ }
        while (decisionChannel.tryReceive().getOrNull() != null) { /* drain */ }
    }
}
