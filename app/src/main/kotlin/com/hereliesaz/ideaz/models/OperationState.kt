package com.hereliesaz.ideaz.models

/**
 * Shared lifecycle for a long-running operation (clone, build, deploy,
 * download, update, Git fetch/pull/push, ...). Closes the UX audit's P0
 * finding that every one of those categories tracked its own ad-hoc mix of
 * `Boolean` loading flags, raw log strings, and untyped progress callbacks,
 * with no user-facing cancel/retry on most of them.
 *
 * This is the shared TYPE only — see [OperationController] for the piece
 * that actually runs work and publishes updates. Existing delegates are
 * migrated onto this incrementally, not all at once: each delegate's own
 * substrate (a plain suspend function, a WorkManager job, a bound AIDL
 * service) stays what it is; only the state it *reports* changes shape.
 */
sealed interface OperationState<out T> {
    /** Accepted but not yet started — e.g. waiting on a prerequisite check. */
    data object Queued : OperationState<Nothing>

    /** In flight. [progress] is null when the operation can't estimate completion. */
    data class Running(val progress: OperationProgress? = null) : OperationState<Nothing>

    data class Succeeded<T>(val result: T) : OperationState<T>

    /**
     * [retryable] is the operation's own judgment about whether calling
     * start again is safe and likely to help — e.g. a network timeout is
     * retryable, a bad credential usually isn't. The UI shouldn't offer a
     * Retry action when this is false.
     */
    data class Failed(
        val message: String,
        val retryable: Boolean,
        val cause: Throwable? = null,
    ) : OperationState<Nothing>

    /** User-initiated cancellation completed cleanly. */
    data object Cancelled : OperationState<Nothing>
}

/**
 * [fraction] is 0f..1f when the operation can estimate completion (bytes
 * downloaded, files processed), null for indeterminate work. [step] is a
 * short human-readable label ("Uploading (2/5)"), also optional.
 */
data class OperationProgress(
    val fraction: Float? = null,
    val step: String? = null,
) {
    init {
        require(fraction == null || fraction in 0f..1f) {
            "fraction must be in 0f..1f or null, was $fraction"
        }
    }
}

/** True for the three states where [OperationController.retry] can apply. */
fun OperationState<*>.isRetryable(): Boolean =
    this is OperationState.Failed && retryable

/** True once the operation has reached a terminal state (no further transitions). */
fun OperationState<*>.isTerminal(): Boolean = when (this) {
    is OperationState.Succeeded<*>, is OperationState.Failed, OperationState.Cancelled -> true
    OperationState.Queued, is OperationState.Running -> false
}
