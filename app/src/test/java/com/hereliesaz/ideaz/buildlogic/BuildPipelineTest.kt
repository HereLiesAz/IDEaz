package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import org.junit.Assert.*
import org.junit.Test

class BuildPipelineTest {

    private class MockBuildStep(
        val name: String,
        val shouldFail: Boolean = false,
        val outputMessage: String = "Success"
    ) : BuildStep {
        var executed = false

        override fun execute(callback: IBuildCallback?): BuildResult {
            executed = true
            callback?.onLog("Mock step executing: $name")
            return if (shouldFail) {
                BuildResult(false, outputMessage)
            } else {
                BuildResult(true, outputMessage)
            }
        }
    }

    private val mockCallback = object : IBuildCallback.Stub() {
        override fun onLog(message: String) { println(message) }
        override fun onSuccess(apkPath: String) {}
        override fun onFailure(message: String) {}
    }

    @Test
    fun `execute runs all steps successfully`() {
        val step1 = MockBuildStep("Step1")
        val step2 = MockBuildStep("Step2")
        val orchestrator = BuildOrchestrator(listOf(step1, step2))

        val result = orchestrator.execute(mockCallback)

        assertTrue(result.success)
        assertTrue(step1.executed)
        assertTrue(step2.executed)
        assertTrue(result.output.contains("Step: MockBuildStep"))
    }

    @Test
    fun `execute stops on failure`() {
        val step1 = MockBuildStep("Step1")
        val step2 = MockBuildStep("Step2", shouldFail = true, outputMessage = "Failed!")
        val step3 = MockBuildStep("Step3")
        val orchestrator = BuildOrchestrator(listOf(step1, step2, step3))

        val result = orchestrator.execute(mockCallback)

        assertFalse(result.success)
        assertTrue(step1.executed)
        assertTrue(step2.executed)
        assertFalse(step3.executed) // Should not execute
        assertTrue(result.output.contains("Failed!"))
    }
}
