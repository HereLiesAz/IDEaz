package com.hereliesaz.ideaz.buildlogic

class BuildOrchestrator(private val steps: List<BuildStep>) {

    fun execute(): BuildResult {
        val overallOutput = StringBuilder()
        for (step in steps) {
            println("Executing build step: ${step.javaClass.simpleName}")
            val result = step.execute()
            overallOutput.append("Step: ${step.javaClass.simpleName}\n")
            overallOutput.append("Output: ${result.output}\n\n")
            if (!result.success) {
                println("Build step failed: ${step.javaClass.simpleName}")
                return BuildResult(false, overallOutput.toString())
            }
        }
        return BuildResult(true, overallOutput.toString())
    }
}
