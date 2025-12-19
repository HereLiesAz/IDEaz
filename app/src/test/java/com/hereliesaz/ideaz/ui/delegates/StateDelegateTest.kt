package com.hereliesaz.ideaz.ui.delegates

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StateDelegateTest {

    @Test
    fun testAppendBuildLog() = runBlocking {
        val delegate = StateDelegate()
        delegate.appendBuildLog("Line 1")
        delegate.appendBuildLog("Line 2")

        val logs = delegate.buildLog.value
        assertEquals(listOf("Line 1", "Line 2"), logs)
    }

    @Test
    fun testAppendAiLog() = runBlocking {
        val delegate = StateDelegate()
        delegate.appendAiLog("Response")

        val logs = delegate.buildLog.value
        assertEquals(listOf("[AI] Response"), logs)
    }

    @Test
    fun testClearLog() = runBlocking {
        val delegate = StateDelegate()
        delegate.appendBuildLog("Log")
        delegate.clearLog()

        val logs = delegate.buildLog.value
        assertEquals(emptyList<String>(), logs)
    }
}
