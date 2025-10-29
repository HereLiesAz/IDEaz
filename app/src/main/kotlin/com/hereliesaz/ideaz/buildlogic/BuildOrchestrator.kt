package com.hereliesaz.ideaz.buildlogic

class BuildOrchestrator(private val steps: List<BuildStep>) {

    fun execute(): Boolean {
        return steps.all { step ->
            println("Executing build step: ${step.javaClass.simpleName}")
            val result = step.execute()
            if (!result) {
                println("Build step failed: ${step.javaClass.simpleName}")
            }
            result
        }
    }
}
