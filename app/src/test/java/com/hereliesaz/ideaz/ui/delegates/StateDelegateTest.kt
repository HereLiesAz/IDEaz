package com.hereliesaz.ideaz.ui.delegates

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        // Pass StandardTestDispatcher linked to the TestScope's scheduler to control timing
        stateDelegate = StateDelegate(
            testScope.backgroundScope,
            StandardTestDispatcher(testScope.testScheduler)
        )
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

    @Test
    fun `appendChatMessage adds message to chatMessages`() = testScope.runTest {
        stateDelegate.appendChatMessage(
            com.hereliesaz.ideaz.ai.ChatMessage("user", "hello")
        )
        assertEquals(1, stateDelegate.chatMessages.value.size)
        assertEquals("hello", stateDelegate.chatMessages.value.first().content)
        assertEquals("user", stateDelegate.chatMessages.value.first().role)
    }

    @Test
    fun `appendChatMessage accumulates messages in order`() = testScope.runTest {
        stateDelegate.appendChatMessage(com.hereliesaz.ideaz.ai.ChatMessage("user", "first"))
        stateDelegate.appendChatMessage(com.hereliesaz.ideaz.ai.ChatMessage("model", "second"))
        val msgs = stateDelegate.chatMessages.value
        assertEquals(2, msgs.size)
        assertEquals("first", msgs[0].content)
        assertEquals("second", msgs[1].content)
    }

    @Test
    fun `clearChatHistory empties chatMessages`() = testScope.runTest {
        stateDelegate.appendChatMessage(com.hereliesaz.ideaz.ai.ChatMessage("user", "hi"))
        stateDelegate.clearChatHistory()
        assertTrue(stateDelegate.chatMessages.value.isEmpty())
    }

    @Test
    fun `setChatLoading updates isChatLoading`() = testScope.runTest {
        assertFalse(stateDelegate.isChatLoading.value)
        stateDelegate.setChatLoading(true)
        assertTrue(stateDelegate.isChatLoading.value)
        stateDelegate.setChatLoading(false)
        assertFalse(stateDelegate.isChatLoading.value)
    }
}
