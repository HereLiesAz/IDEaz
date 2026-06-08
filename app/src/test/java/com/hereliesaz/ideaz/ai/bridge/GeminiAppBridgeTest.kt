package com.hereliesaz.ideaz.ai.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiAppBridgeTest {

    @Test
    fun `reset clears phase, submit flag and drains channels`() {
        GeminiAppBridge.pendingPrompt = "x"
        GeminiAppBridge.isWaiting = true
        GeminiAppBridge.phase = GeminiAppBridge.BridgePhase.AWAIT_RESPONSE
        GeminiAppBridge.promptSubmitted = true
        GeminiAppBridge.channel.trySend("stale response")
        GeminiAppBridge.decisionChannel.trySend(true)

        GeminiAppBridge.reset()

        assertEquals(GeminiAppBridge.BridgePhase.IDLE, GeminiAppBridge.phase)
        assertFalse(GeminiAppBridge.promptSubmitted)
        assertNull(GeminiAppBridge.channel.tryReceive().getOrNull())
        assertNull(GeminiAppBridge.decisionChannel.tryReceive().getOrNull())
    }
}
