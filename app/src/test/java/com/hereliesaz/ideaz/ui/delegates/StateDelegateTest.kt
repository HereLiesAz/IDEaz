package com.hereliesaz.ideaz.ui.delegates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class StateDelegateTest {

    private lateinit var stateDelegate: StateDelegate

    @Before
    fun setUp() {
        stateDelegate = StateDelegate()
    }

    @Test
    fun `appendBuildLog appends message`() {
        stateDelegate.appendBuildLog("Hello World")
        assertEquals(listOf("Hello World"), stateDelegate.buildLog.value)
    }

    @Test
    fun `appendBuildLog handles newlines`() {
        stateDelegate.appendBuildLog("Line 1\nLine 2")
        assertEquals(listOf("Line 1", "Line 2"), stateDelegate.buildLog.value)
    }

    @Test
    fun `appendBuildLog caps size at 1000`() {
        val lines = (1..1100).map { "Line $it" }
        stateDelegate.appendBuildLogLines(lines)

        val log = stateDelegate.buildLog.value
        assertEquals(1000, log.size)
        // Log keeps the LAST 1000 lines
        assertEquals("Line 101", log.first())
        assertEquals("Line 1100", log.last())
    }

    @Test
    fun `appendAiLog adds prefix`() {
        stateDelegate.appendAiLog("Thinking...")
        assertEquals(listOf("[AI] Thinking..."), stateDelegate.buildLog.value)
    }

    @Test
    fun `setLoadingProgress updates state`() {
        stateDelegate.setLoadingProgress(50)
        assertEquals(50, stateDelegate.loadingProgress.value)
        stateDelegate.setLoadingProgress(null)
        assertNull(stateDelegate.loadingProgress.value)
    }

    @Test
    fun `clearLog clears logs`() {
        stateDelegate.appendBuildLog("Test")
        assertEquals(1, stateDelegate.buildLog.value.size)
        stateDelegate.clearLog()
        assertEquals(0, stateDelegate.buildLog.value.size)
    }
}
