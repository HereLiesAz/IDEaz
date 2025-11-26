package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback

class BuildOrchestrator(private val steps: List<BuildStep>) {

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
