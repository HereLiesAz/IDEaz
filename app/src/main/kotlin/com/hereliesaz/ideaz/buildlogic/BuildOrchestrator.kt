package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Orchestrates the sequential execution of a list of [BuildStep]s.
 *
 * Behaviour:
 *  - Fail-fast: if any step returns a failure [BuildResult], the pipeline
 *    halts immediately and returns aggregated output up to and including
 *    the failure.
 *  - Cancellation: between steps, [executeSuspending] checks the calling
 *    coroutine's cancellation state via [Thread.currentThread().isInterrupted]
 *    and via [coroutineContext.isActive] (when called from [executeSuspending]).
 *    Individual steps are blocking and cannot themselves be cancelled mid-call,
 *    so the smallest unit of preemption is one step.
 *  - Timing: each [BuildResult] returned to the caller has [BuildResult.durationMs]
 *    populated by the orchestrator. Steps don't track this themselves.
 *  - Logging: the orchestrator emits one "Executing: <name>" line before each
 *    step and one "Completed in Xms" / "Failed after Xms" line after it.
 *
 * Construct with the ordered list of steps and call [execute] (legacy,
 * non-cancellation-aware) or [executeSuspending] (preferred when called
 * from a coroutine).
 */
class BuildOrchestrator(private val steps: List<BuildStep>) {

    /**
     * Synchronous, blocking execution. Retained for compatibility with
     * non-coroutine call sites. Cooperative cancellation is checked via
     * thread interruption only.
     */
    fun execute(callback: IBuildCallback): BuildResult {
        return runPipeline(callback) {
            // No coroutine context available; we can only consult thread interruption.
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Build pipeline cancelled")
            }
        }
    }

    /**
     * Coroutine-aware execution. Use this from within a `serviceScope.launch { }`
     * block. Honours both thread interruption and coroutine cancellation.
     */
    suspend fun executeSuspending(callback: IBuildCallback): BuildResult {
        return runPipeline(callback) {
            if (!coroutineContext.isActive) {
                throw CancellationException("Build pipeline cancelled")
            }
            if (Thread.currentThread().isInterrupted) {
                throw CancellationException("Build pipeline interrupted")
            }
        }
    }

    private inline fun runPipeline(
        callback: IBuildCallback,
        checkCancellation: () -> Unit,
    ): BuildResult {
        val overallOutput = StringBuilder()
        val overallStart = System.currentTimeMillis()

        for ((index, step) in steps.withIndex()) {
            try {
                checkCancellation()
            } catch (e: InterruptedException) {
                callback.onLog("Build cancelled before step ${index + 1}/${steps.size} (${step.displayName})")
                return BuildResult(false, overallOutput.append("Cancelled\n").toString(), System.currentTimeMillis() - overallStart)
            } catch (e: CancellationException) {
                callback.onLog("Build cancelled before step ${index + 1}/${steps.size} (${step.displayName})")
                throw e
            }

            val name = step.displayName
            callback.onLog("[${index + 1}/${steps.size}] Executing: $name")
            val stepStart = System.currentTimeMillis()

            val result = try {
                step.execute(callback)
            } catch (e: Throwable) {
                val elapsed = System.currentTimeMillis() - stepStart
                callback.onLog("[${index + 1}/${steps.size}] $name threw after ${elapsed}ms: ${e.message}")
                overallOutput.append("Step: $name\n")
                overallOutput.append("Output: ${e.stackTraceToString()}\n\n")
                return BuildResult(false, overallOutput.toString(), System.currentTimeMillis() - overallStart)
            }

            val elapsed = System.currentTimeMillis() - stepStart
            val resultWithTiming = result.copy(durationMs = elapsed)

            overallOutput.append("Step: $name (${elapsed}ms)\n")
            overallOutput.append("Output: ${resultWithTiming.output}\n\n")

            if (!resultWithTiming.success) {
                callback.onLog("[${index + 1}/${steps.size}] $name failed after ${elapsed}ms")
                return BuildResult(false, overallOutput.toString(), System.currentTimeMillis() - overallStart)
            }

            callback.onLog("[${index + 1}/${steps.size}] $name completed in ${elapsed}ms")
        }

        return BuildResult(true, overallOutput.toString(), System.currentTimeMillis() - overallStart)
    }
}
