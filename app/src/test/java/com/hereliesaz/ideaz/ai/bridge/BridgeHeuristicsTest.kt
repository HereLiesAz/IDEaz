package com.hereliesaz.ideaz.ai.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeHeuristicsTest {

    @Test
    fun `input hint matches Gemini compose labels case-insensitively`() {
        assertTrue(BridgeHeuristics.isInputHint("Ask Gemini"))
        assertTrue(BridgeHeuristics.isInputHint("Enter a prompt here"))
        assertTrue(BridgeHeuristics.isInputHint("Message"))
        assertTrue(BridgeHeuristics.isInputHint("Tap to talk"))
    }

    @Test
    fun `input hint rejects unrelated, null and blank`() {
        assertFalse(BridgeHeuristics.isInputHint("Copy"))
        assertFalse(BridgeHeuristics.isInputHint(null))
        assertFalse(BridgeHeuristics.isInputHint(""))
    }

    @Test
    fun `send hint matches submit affordances`() {
        assertTrue(BridgeHeuristics.isSendHint("Send"))
        assertTrue(BridgeHeuristics.isSendHint("SUBMIT"))
        assertTrue(BridgeHeuristics.isSendHint("Run prompt"))
        assertFalse(BridgeHeuristics.isSendHint("Ask Gemini"))
        assertFalse(BridgeHeuristics.isSendHint(null))
    }

    @Test
    fun `copy hint matches the copy button`() {
        assertTrue(BridgeHeuristics.isCopyHint("Copy"))
        assertTrue(BridgeHeuristics.isCopyHint("copy code"))
        assertFalse(BridgeHeuristics.isCopyHint("Share"))
        assertFalse(BridgeHeuristics.isCopyHint(null))
    }

    @Test
    fun `extractResponse strips the echoed prompt`() {
        val prompt = "Edit the project"
        val snapshot = "$prompt\nHere is the reply"
        assertEquals("Here is the reply", BridgeHeuristics.extractResponse(snapshot, prompt))
    }

    @Test
    fun `extractResponse strips input chrome`() {
        val snapshot = "the answer\nEnter a prompt here\nListening"
        assertEquals("the answer", BridgeHeuristics.extractResponse(snapshot, ""))
    }

    @Test
    fun `extractResponse tolerates a blank prompt`() {
        assertEquals("only text", BridgeHeuristics.extractResponse("only text", ""))
    }
}
