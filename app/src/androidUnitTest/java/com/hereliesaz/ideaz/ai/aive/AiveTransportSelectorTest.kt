package com.hereliesaz.ideaz.ai.aive

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiveTransportSelectorTest {
    private class Fake(override val isRemote: Boolean, private val failWith: Throwable? = null) : AiveTransport {
        var calls = 0
        override suspend fun prompt(prompt: String, providerId: String?, model: String?): String {
            calls++
            failWith?.let { throw it }
            return if (isRemote) "relay" else "local"
        }

        override suspend fun launchWorkflow(projectName: String, objective: String, repositoryUrl: String?): String? {
            calls++
            failWith?.let { throw it }
            return null
        }

        override fun watchRun(): Flow<AiveRemoteClient.RunStatus> = emptyFlow()
        override fun openAive(): Boolean = !isRemote
    }

    @Test
    fun `the local Aive wins when installed and bound`() = runBlocking {
        val local = Fake(isRemote = false)
        val relay = Fake(isRemote = true)
        val selector = AiveTransportSelector(local, { true }, { relay })
        assertEquals("local", selector.prompt("x"))
        assertEquals(0, relay.calls)
        assertFalse(selector.isRemote)
    }

    @Test
    fun `the relay is used when The Aive is not installed`() = runBlocking {
        val local = Fake(isRemote = false)
        val relay = Fake(isRemote = true)
        val selector = AiveTransportSelector(local, { false }, { relay })
        assertTrue(selector.isRemote)
        assertEquals("relay", selector.prompt("x"))
        assertEquals(0, local.calls)
    }

    @Test
    fun `the relay is the fallback when the local bind fails`() = runBlocking {
        val local = Fake(isRemote = false, failWith = AiveUnavailableException("IDEaz cannot reach The Aive."))
        val relay = Fake(isRemote = true)
        val selector = AiveTransportSelector(local, { true }, { relay })
        assertEquals("relay", selector.prompt("x"))
        assertTrue(selector.isRemote)
    }

    @Test
    fun `an ordinary local failure does not fall back`() = runBlocking {
        val local = Fake(isRemote = false, failWith = IllegalStateException("No provider is linked."))
        val relay = Fake(isRemote = true)
        val selector = AiveTransportSelector(local, { true }, { relay })
        try {
            selector.prompt("x")
            fail("expected the local failure")
        } catch (e: IllegalStateException) {
            assertEquals("No provider is linked.", e.message)
        }
        assertEquals(0, relay.calls)
    }

    @Test
    fun `with neither route the error names both options`() = runBlocking {
        val selector = AiveTransportSelector(Fake(isRemote = false), { false }, { null })
        try {
            selector.launchWorkflow("p", "o")
            fail("expected unavailable")
        } catch (e: AiveUnavailableException) {
            assertTrue(e.message!!.contains("Install The Aive"))
            assertTrue(e.message!!.contains("pairing code"))
        }
    }

    @Test
    fun `a failed local bind without a pairing mentions pairing`() = runBlocking {
        val local = Fake(isRemote = false, failWith = AiveUnavailableException("IDEaz cannot reach The Aive."))
        val selector = AiveTransportSelector(local, { true }, { null })
        try {
            selector.prompt("x")
            fail("expected unavailable")
        } catch (e: AiveUnavailableException) {
            assertTrue(e.message!!.contains("cannot reach"))
            assertTrue(e.message!!.contains("pair"))
        }
    }
}
