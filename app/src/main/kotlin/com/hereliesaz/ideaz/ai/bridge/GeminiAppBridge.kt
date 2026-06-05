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

    @Volatile var pendingPrompt: String? = null

    @Volatile var isWaiting: Boolean = false

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
        // Non-blocking drain.
        while (channel.tryReceive().getOrNull() != null) { /* discard */ }
        while (decisionChannel.tryReceive().getOrNull() != null) { /* discard */ }
    }
}
