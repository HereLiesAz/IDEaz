package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.models.OperationProgress
import com.hereliesaz.ideaz.models.OperationState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationControllerTest {

    private lateinit var testScope: TestScope
    private lateinit var controller: OperationController<String>

    @Before
    fun setUp() {
        testScope = TestScope()
        controller = OperationController(testScope.backgroundScope)
    }

    @Test
    fun `start runs the block and publishes Succeeded with its result`() = testScope.runTest {
        val started = controller.start { "done" }
        assertTrue(started)
        advanceUntilIdle()
        assertEquals(OperationState.Succeeded("done"), controller.state.value)
    }

    @Test
    fun `start publishes Running immediately, before the block completes`() = testScope.runTest {
        controller.start {
            delay(100)
            "done"
        }
        assertTrue(controller.state.value is OperationState.Running)
        assertTrue(controller.isActive)
        advanceUntilIdle()
    }

    @Test
    fun `start reports progress through the callback`() = testScope.runTest {
        controller.start { report ->
            report(OperationProgress(fraction = 0.5f, step = "halfway"))
            delay(100) // keep the operation in flight so the report is observable
            "done"
        }
        runCurrent() // let the coroutine run up to its first suspension point (the delay)
        val running = controller.state.value
        assertTrue(running is OperationState.Running)
        assertEquals(0.5f, (running as OperationState.Running).progress?.fraction)
        assertEquals("halfway", running.progress?.step)
        advanceUntilIdle()
        assertEquals(OperationState.Succeeded("done"), controller.state.value)
    }

    @Test
    fun `start does nothing while an operation is already active`() = testScope.runTest {
        controller.start {
            delay(100)
            "first"
        }
        val startedSecond = controller.start { "second" }
        assertFalse(startedSecond)
        advanceUntilIdle()
        assertEquals(OperationState.Succeeded("first"), controller.state.value)
    }

    @Test
    fun `a thrown exception publishes a retryable Failed state`() = testScope.runTest {
        controller.start { error("boom") }
        advanceUntilIdle()
        val state = controller.state.value
        assertTrue(state is OperationState.Failed)
        assertEquals("boom", (state as OperationState.Failed).message)
        assertTrue(state.retryable)
    }

    @Test
    fun `cancel stops the running operation and publishes Cancelled`() = testScope.runTest {
        controller.start {
            delay(1000)
            "done"
        }
        controller.cancel()
        assertEquals(OperationState.Cancelled, controller.state.value)
        assertFalse(controller.isActive)
        advanceUntilIdle()
    }

    @Test
    fun `cancel on an inactive controller is a no-op`() = testScope.runTest {
        controller.cancel()
        assertEquals(OperationState.Queued, controller.state.value)
    }

    @Test
    fun `a fresh never-started controller is not active`() {
        // Regression guard: isActive must not treat the idle Queued default
        // as busy, or start() would refuse to ever run on a fresh controller.
        assertFalse(controller.isActive)
    }

    @Test
    fun `retry re-runs the last block after a retryable failure`() = testScope.runTest {
        var attempt = 0
        controller.start {
            attempt++
            if (attempt == 1) error("first attempt fails") else "second attempt succeeds"
        }
        advanceUntilIdle()
        assertTrue(controller.state.value is OperationState.Failed)

        val retried = controller.retry()
        assertTrue(retried)
        advanceUntilIdle()
        assertEquals(OperationState.Succeeded("second attempt succeeds"), controller.state.value)
        assertEquals(2, attempt)
    }

    @Test
    fun `retry does nothing when nothing has failed yet`() = testScope.runTest {
        assertFalse(controller.retry())
        assertEquals(OperationState.Queued, controller.state.value)
    }

    @Test
    fun `a thrown NonRetryableOperationException publishes a non-retryable Failed state`() = testScope.runTest {
        controller.start { throw NonRetryableOperationException("bad credential") }
        advanceUntilIdle()
        val state = controller.state.value
        assertTrue(state is OperationState.Failed)
        assertEquals("bad credential", (state as OperationState.Failed).message)
        assertFalse(state.retryable)
    }

    @Test
    fun `retry does nothing after a non-retryable failure`() = testScope.runTest {
        controller.start { throw NonRetryableOperationException("bad credential") }
        advanceUntilIdle()
        assertFalse(controller.retry())
        assertEquals(false, (controller.state.value as OperationState.Failed).retryable)
    }
}
