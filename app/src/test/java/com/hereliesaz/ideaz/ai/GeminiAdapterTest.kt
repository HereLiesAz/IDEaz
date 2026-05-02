package com.hereliesaz.ideaz.ai

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeminiAdapterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `GeminiAdapter satisfies ConversationalAiClient interface`() {
        val adapter: ConversationalAiClient = GeminiAdapter(
            apiKey = "fake-key-not-used-in-this-test",
            tools = IdeTools(tempFolder.root)
        )
        assertNotNull(adapter)
    }

    @Test
    fun `tool dispatch read then write matches IdeTools contract`() {
        val tools = IdeTools(tempFolder.root)
        val writeResult = tools.writeFile("index.html", "<h1>hi</h1>")
        assertEquals("OK", writeResult)
        val readResult = tools.readFile("index.html")
        assertEquals("<h1>hi</h1>", readResult)
    }

    @Test
    fun `tool dispatch unknown tool name returns error string`() {
        val adapter = GeminiAdapter(
            apiKey = "fake-key",
            tools = IdeTools(tempFolder.root)
        )
        val result = adapter.testDispatchTool("unknown_tool", emptyMap<String, Any?>())
        assertTrue("Expected error, got: $result", result.startsWith("Error:"))
    }
}
