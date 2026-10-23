package com.hereliesaz.ideaz.ai.aive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiveRemoteClientTest {

    @Test
    fun `signers must be non-empty and identical`() {
        assertTrue(AiveRemoteClient.sameSigners(setOf("aa"), setOf("aa")))
        assertFalse(AiveRemoteClient.sameSigners(emptySet(), emptySet()))
        assertFalse(AiveRemoteClient.sameSigners(setOf("aa"), setOf("bb")))
        assertFalse(AiveRemoteClient.sameSigners(setOf("aa"), setOf("aa", "bb")))
    }

    @Test
    fun `sha256 of empty input is the well-known digest`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            AiveRemoteClient.sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun `timeout message names the seconds`() {
        assertTrue(AiveRemoteClient.timeoutMessage(180_000L).contains("180 s"))
    }
}
