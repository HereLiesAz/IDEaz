package com.hereliesaz.ideaz.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiveWorkflowCommandTest {
    @Test
    fun `GitHub remotes normalize without credentials`() {
        val expected = "https://github.com/HereLiesAz/IDEaz"
        assertEquals(expected, normalizeGitHubUrl("https://github.com/HereLiesAz/IDEaz.git"))
        assertEquals(expected, normalizeGitHubUrl("https://x-access-token:secret@github.com/HereLiesAz/IDEaz"))
        assertEquals(expected, normalizeGitHubUrl("git@github.com:HereLiesAz/IDEaz.git"))
        assertEquals(expected, normalizeGitHubUrl("ssh://git@github.com/HereLiesAz/IDEaz.git"))
    }

    @Test
    fun `non-GitHub remotes are dropped`() {
        assertNull(normalizeGitHubUrl("https://gitlab.com/a/b.git"))
        assertNull(normalizeGitHubUrl("https://github.com/only-owner"))
    }

    @Test
    fun `keyless models and The Aive need no key`() {
        listOf(AiModels.KILO, AiModels.LLM7, AiModels.OVHCLOUD, AiModels.AIVE).forEach { model ->
            assertEquals(model.id, "", model.requiredKey)
            assertTrue(model.id, model in AiModels.availableModels)
        }
    }

    private fun run(id: String, objective: String, status: String) =
        com.hereliesaz.ideaz.ai.aive.AiveRemoteClient.RunStatus(id, objective, status, 3, 1, null)

    @Test
    fun `a run already finished before launch is ignored, the new one is followed`() {
        val tracker = AiveRunTracker("add dark mode", runId = null)
        assertFalse(tracker.accept(run("old", "add dark mode", "Completed")))
        assertTrue(tracker.accept(run("new", " add dark mode ", "Running")))
        assertFalse(tracker.accept(run("other", "add dark mode", "Running")))
        assertTrue(tracker.accept(run("new", "add dark mode", "Completed")))
    }

    @Test
    fun `a run id from the launch reply is matched exactly`() {
        val tracker = AiveRunTracker("x", runId = "r1")
        assertFalse(tracker.accept(run("r0", "x", "Running")))
        assertTrue(tracker.accept(run("r1", "something else", "Completed")))
    }
}
