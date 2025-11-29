package com.hereliesaz.ideaz.minapp.buildlogic

import com.hereliesaz.ideaz.IBuildCallback

/**
 * Orchestrates the sequential execution of a list of [BuildStep]s.
 *
 * This class is the core engine of the build pipeline. It iterates through the provided
 * steps, executes them one by one, logs their progress, and halts execution immediately
 * if any step fails.
 *
 * @property steps The ordered list of build steps to execute.
 */
class BuildOrchestrator(private val steps: List<BuildStep>) {

    /**
     * Executes the build pipeline.
     *
     * @param callback The callback interface for logging progress and errors.
     * @return A [BuildResult] indicating success or failure, containing the aggregated output.
     */
    fun execute(callback: IBuildCallback): BuildResult {
        val overallOutput = StringBuilder()
        for (step in steps) {
            callback.onLog("Executing build step: ${step.javaClass.simpleName}")
            val result = step.execute(callback)
            overallOutput.append("Step: ${step.javaClass.simpleName}\n")
            overallOutput.append("Output: ${result.output}\n\n")
            if (!result.success) {
                callback.onLog("Build step failed: ${step.javaClass.simpleName}")
                return BuildResult(false, overallOutput.toString())
            }
        }
        return BuildResult(true, overallOutput.toString())
    }
}
