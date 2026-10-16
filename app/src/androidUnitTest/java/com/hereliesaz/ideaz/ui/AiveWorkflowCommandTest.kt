package com.hereliesaz.ideaz.ui

import org.junit.Assert.assertEquals
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
}
