package com.hereliesaz.ideaz.ai.bridge

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

/**
 * Process-singleton mailbox between [GeminiAppBridgeAdapter] (the caller that
 * fires the intent into the Gemini app) and [GeminiAppBridgeAccessibilityService]
 * (the listener that scrapes the rendered response).
 *
 * The adapter sets [pendingPrompt] + [isWaiting] = true, fires the intent,
 * then suspends on [channel.receive]. The accessibility service watches for
 * window content changes from the Gemini app, captures the visible text once
 * it stabilises, removes the pending prompt from the captured text, and
 * sends the diff to the channel. The adapter resumes and returns the text.
 *
 * Capacity 1 with DROP_OLDEST: if a new prompt arrives before the previous
 * response, the stale response is discarded rather than blocking.
 */
object GeminiAppBridge {

    /**
     * Where the bridge is in its run:
     *  - [IDLE]: no run in flight.
     *  - [INPUT]: intent fired; the accessibility service is waiting for Gemini's
     *    compose screen so it can type [pendingPrompt] and tap Send.
     *  - [AWAIT_RESPONSE]: prompt submitted; the service is scraping the reply.
     */
    enum class BridgePhase { IDLE, INPUT, AWAIT_RESPONSE }

    @Volatile var pendingPrompt: String? = null

    @Volatile var isWaiting: Boolean = false

    @Volatile var phase: BridgePhase = BridgePhase.IDLE

    /** Set once the prompt has been typed and Send clicked — guards double-submit. */
    @Volatile var promptSubmitted: Boolean = false

    val channel: Channel<String> = Channel(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * User's answer to the "Gemini is still responding — keep waiting or cancel?"
     * prompt shown on the block scrim once a wait window elapses. `true` = keep
     * waiting, `false` = cancel. Delivered by the overlay service's buttons (same
     * process) and consumed by the adapter.
     */
    val decisionChannel: Channel<Boolean> = Channel(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Drain any pending response/decision before starting a new wait. Called by
     * the adapter immediately before firing an intent.
     */
    fun reset() {
        phase = BridgePhase.IDLE
        promptSubmitted = false
        // Non-blocking drain.
        while (channel.tryReceive().getOrNull() != null) { /* discard */ }
        while (decisionChannel.tryReceive().getOrNull() != null) { /* discard */ }
    }
}
