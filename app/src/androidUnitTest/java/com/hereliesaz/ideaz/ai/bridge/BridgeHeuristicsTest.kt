package com.hereliesaz.ideaz.ai.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeHeuristicsTest {

    @Test
    fun recognizesGeneratingControls() {
        assertTrue(BridgeHeuristics.isGeneratingHint("Stop generating"))
        assertTrue(BridgeHeuristics.isGeneratingHint("Stop response"))
        assertFalse(BridgeHeuristics.isGeneratingHint("Copy response"))
    }

    @Test
    fun stripsPromptAndKnownChromeFromScrapedResponse() {
        val prompt = "Make the button red"
        val response = BridgeHeuristics.extractResponse(
            "Ask Gemini\n$prompt\nDone.\nCopy",
            prompt,
        )

        assertFalse(response.contains(prompt))
        assertFalse(response.contains("Ask Gemini"))
        assertTrue(response.contains("Done."))
    }
}
