package com.hereliesaz.peridiumide.buildlogic

class BuildOrchestrator(private val buildSteps: List<BuildStep>) {

    fun execute(): Boolean {
        for (step in buildSteps) {
            if (!step.execute()) {
                println("Build failed at step: ${step::class.simpleName}")
                return false
            }
        }
        println("Build finished successfully")
        return true
    }
}
