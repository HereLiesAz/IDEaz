package com.hereliesaz.ideaz.ui.delegates

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StateDelegateTest {

    private lateinit var stateDelegate: StateDelegate
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        testScope = TestScope()
        // Use backgroundScope for the long-running consumer loop
        stateDelegate = StateDelegate(testScope.backgroundScope)
    }

    @Test
    fun `appendBuildLog appends message`() = testScope.runTest {
        stateDelegate.appendBuildLog("Hello World")
        advanceTimeBy(200) // Wait for batch interval (100ms) + processing
        advanceUntilIdle()
        assertEquals(listOf("Hello World"), stateDelegate.buildLog.value)
    }

    @Test
    fun `appendBuildLog handles newlines`() = testScope.runTest {
        stateDelegate.appendBuildLog("Line 1\nLine 2")
        advanceTimeBy(200)
        advanceUntilIdle()
        assertEquals(listOf("Line 1", "Line 2"), stateDelegate.buildLog.value)
    }

    @Test
    fun `appendBuildLog caps size at 1000`() = testScope.runTest {
        val lines = (1..1100).map { "Line $it" }
        stateDelegate.appendBuildLogLines(lines)
        advanceTimeBy(200)
        advanceUntilIdle()

        val log = stateDelegate.buildLog.value
        assertEquals(1000, log.size)
        // Log keeps the LAST 1000 lines
        assertEquals("Line 101", log.first())
        assertEquals("Line 1100", log.last())
    }

    @Test
    fun `appendAiLog adds prefix`() = testScope.runTest {
        stateDelegate.appendAiLog("Thinking...")
        advanceTimeBy(200)
        advanceUntilIdle()
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
    fun `clearLog clears logs`() = testScope.runTest {
        stateDelegate.appendBuildLog("Test")
        advanceTimeBy(200)
        advanceUntilIdle()
        assertEquals(1, stateDelegate.buildLog.value.size)
        stateDelegate.clearLog()
        assertEquals(0, stateDelegate.buildLog.value.size)
    }
}
