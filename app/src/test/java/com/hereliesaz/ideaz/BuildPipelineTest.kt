package com.hereliesaz.ideaz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hereliesaz.ideaz.buildlogic.BuildOrchestrator
import com.hereliesaz.ideaz.buildlogic.BuildResult
import com.hereliesaz.ideaz.buildlogic.BuildStep
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BuildPipelineTest {

    class MockStep(private val success: Boolean, private val name: String) : BuildStep {
        var executed = false
        override fun execute(callback: IBuildCallback?): BuildResult {
            executed = true
            callback?.onLog("Running $name")
            return BuildResult(success, "Output from $name")
        }
    }

    private val mockCallback = object : IBuildCallback.Stub() {
        var logs = mutableListOf<String>()
        override fun onLog(message: String) { logs.add(message) }
        override fun onSuccess(apkPath: String) {}
        override fun onFailure(message: String) {}
    }

    @Test
    fun testSuccessfulPipeline() {
        val step1 = MockStep(true, "Step1")
        val step2 = MockStep(true, "Step2")
        val orchestrator = BuildOrchestrator(listOf(step1, step2))

        val result = orchestrator.execute(mockCallback)

        assertTrue(result.success)
        assertTrue(step1.executed)
        assertTrue(step2.executed)
        // Verify logs contain the messages
        // Note: logs list might contain other messages from Orchestrator like "Executing build step..."
        assertTrue(mockCallback.logs.any { it.contains("Running Step1") })
        assertTrue(mockCallback.logs.any { it.contains("Running Step2") })
    }

    @Test
    fun testFailedPipeline() {
        val step1 = MockStep(true, "Step1")
        val step2 = MockStep(false, "Step2")
        val step3 = MockStep(true, "Step3")
        val orchestrator = BuildOrchestrator(listOf(step1, step2, step3))

        val result = orchestrator.execute(mockCallback)

        assertFalse(result.success)
        assertTrue(step1.executed)
        assertTrue(step2.executed)
        assertFalse(step3.executed) // Should halt
    }
}
