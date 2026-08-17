package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.models.OperationProgress
import com.hereliesaz.ideaz.models.OperationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Thrown by an [OperationController.start] block to mark its failure as not
 * worth retrying (e.g. a rejected credential, an invalid input) — anything
 * else thrown is treated as retryable. The controller reads only [message]
 * and [cause]; throw this instead of a bare `Exception` when the operation
 * itself knows retrying won't help.
 */
class NonRetryableOperationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Runs one long-running operation at a time and publishes its lifecycle as
 * [OperationState] on [state]. Pairs with the shared type in
 * `models/OperationState.kt` — see that file's doc comment for why this
 * exists and how it's meant to be adopted incrementally.
 *
 * One controller instance owns one operation's state; a delegate that
 * tracks several independent operations (e.g. Git's fetch/pull/push/stash)
 * holds one controller per operation, the same way it previously held one
 * `Job?` field per operation.
 */
class OperationController<T>(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<OperationState<T>>(OperationState.Queued)
    val state: StateFlow<OperationState<T>> = _state.asStateFlow()

    private var job: Job? = null
    private var lastBlock: (suspend (report: (OperationProgress) -> Unit) -> T)? = null

    /**
     * True while an operation is actively running. [OperationState.Queued]
     * is this controller's idle/never-started value as well as its
     * conceptual "accepted, not yet dispatched" meaning — either way,
     * nothing is in flight, so it does not count as active here.
     */
    val isActive: Boolean
        get() = _state.value is OperationState.Running

    /**
     * Starts [block] unless one is already active. [block] receives a
     * `report` callback it can call any number of times to publish
     * [OperationProgress] while running; its return value becomes the
     * [OperationState.Succeeded] result.
     *
     * Returns false without doing anything if an operation is already
     * active — callers that need "cancel the previous one and start a new
     * one" should call [cancel] first, matching the existing pattern in
     * e.g. `AIDelegate.startContextualAITask`.
     */
    fun start(block: suspend (report: (OperationProgress) -> Unit) -> T): Boolean {
        if (isActive) return false
        lastBlock = block
        launch(block)
        return true
    }

    /** Re-runs the last [start]ed block. No-op if nothing failed-and-retryable is pending. */
    fun retry(): Boolean {
        val block = lastBlock ?: return false
        val current = _state.value
        if (current !is OperationState.Failed || !current.retryable) return false
        launch(block)
        return true
    }

    /** Cancels the active operation, if any. Publishes [OperationState.Cancelled]. */
    fun cancel() {
        val activeJob = job ?: return
        activeJob.cancel()
        _state.value = OperationState.Cancelled
    }

    private fun launch(block: suspend (report: (OperationProgress) -> Unit) -> T) {
        _state.value = OperationState.Running()
        job = scope.launch {
            try {
                val result = block { progress -> _state.value = OperationState.Running(progress) }
                _state.value = OperationState.Succeeded(result)
            } catch (e: CancellationException) {
                _state.value = OperationState.Cancelled
                throw e
            } catch (e: NonRetryableOperationException) {
                _state.value = OperationState.Failed(
                    message = e.message ?: "Unknown error",
                    retryable = false,
                    cause = e,
                )
            } catch (e: Exception) {
                _state.value = OperationState.Failed(
                    message = e.message ?: e::class.simpleName ?: "Unknown error",
                    retryable = true,
                    cause = e,
                )
            }
        }
    }
}
