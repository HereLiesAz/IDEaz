package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback

/**
 * Orchestrates the sequential execution of a list of [BuildStep]s.
 *
 * **Role:**
 * This class acts as the pipeline runner for the build system. It enforces a strict "Fail-Fast" policy:
 * if any step returns a failure [BuildResult], the pipeline halts immediately, preventing cascading errors
 * and saving resources.
 *
 * **Usage:**
 * Construct with a list of steps (e.g., ProcessManifest -> Aapt2 -> Kotlinc -> D8 -> ApkBuilder -> Signer)
 * and call [execute].
 *
 * @property steps The ordered list of [BuildStep] implementations to run.
 */
class BuildOrchestrator(private val steps: List<BuildStep>) {

    /**
     * Executes the build pipeline.
     *
     * @param callback The callback interface for logging realtime progress to the UI.
     * @return A [BuildResult] indicating overall success or failure, containing the aggregated output log.
     */
    fun execute(callback: IBuildCallback): BuildResult {
        val overallOutput = StringBuilder()
        for (step in steps) {
            val stepName = step.javaClass.simpleName
            callback.onLog("Executing build step: $stepName")

            // Execute the step (blocking)
            val result = step.execute(callback)

            overallOutput.append("Step: $stepName\n")
            overallOutput.append("Output: ${result.output}\n\n")

            if (!result.success) {
                callback.onLog("Build step failed: $stepName")
                return BuildResult(false, overallOutput.toString())
            }
        }
        return BuildResult(true, overallOutput.toString())
    }
}
