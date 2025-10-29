package com.hereliesaz.ideaz.buildlogic

data class BuildResult(val success: Boolean, val output: String)

interface BuildStep {
    fun execute(): BuildResult
}
