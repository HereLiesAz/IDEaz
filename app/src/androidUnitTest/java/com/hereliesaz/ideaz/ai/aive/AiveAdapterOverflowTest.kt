package com.hereliesaz.ideaz.ai.aive

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiveAdapterOverflowTest {
    @Test
    fun fittingPromptIsAccepted() {
        assertNull(AiveAdapter.overflowMessage(100, 50, 40, 500))
    }

    @Test
    fun conversationAloneOverTheLimitBlamesTheConversation() {
        val m = AiveAdapter.overflowMessage(900_000, 600_000, 200_000, 500_000)!!
        assertTrue(m.contains("conversation is too long"))
    }

    @Test
    fun snapshotTippingTheTotalOverBlamesTheProject() {
        val m = AiveAdapter.overflowMessage(520_000, 400_000, 120_000, 500_000)!!
        assertTrue(m.contains("project"))
        assertTrue(!m.contains("conversation is too long"))
    }
}
