package com.hereliesaz.ideaz.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationStateTest {

    @Test
    fun `isRetryable is true only for a retryable Failed state`() {
        assertTrue(OperationState.Failed("boom", retryable = true).isRetryable())
        assertFalse(OperationState.Failed("boom", retryable = false).isRetryable())
        assertFalse(OperationState.Queued.isRetryable())
        assertFalse(OperationState.Running().isRetryable())
        assertFalse(OperationState.Succeeded(Unit).isRetryable())
        assertFalse(OperationState.Cancelled.isRetryable())
    }

    @Test
    fun `isTerminal is true for Succeeded, Failed, and Cancelled only`() {
        assertTrue(OperationState.Succeeded(Unit).isTerminal())
        assertTrue(OperationState.Failed("boom", retryable = true).isTerminal())
        assertTrue(OperationState.Cancelled.isTerminal())
        assertFalse(OperationState.Queued.isTerminal())
        assertFalse(OperationState.Running().isTerminal())
    }

    @Test
    fun `OperationProgress accepts null and in-range fractions`() {
        OperationProgress(fraction = null)
        OperationProgress(fraction = 0f)
        OperationProgress(fraction = 1f)
        OperationProgress(fraction = 0.5f, step = "Uploading (2/5)")
    }

    @Test
    fun `OperationProgress rejects an out-of-range fraction`() {
        assertThrows(IllegalArgumentException::class.java) {
            OperationProgress(fraction = 1.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationProgress(fraction = -0.1f)
        }
    }
}
