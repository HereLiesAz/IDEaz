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
    fun `dispatchTool routes to all four tools correctly`() {
        tempFolder.newFile("test.txt").writeText("hello")
        tempFolder.newFolder("subdir")

        val adapter = GeminiAdapter(
            apiKey = "fake-key",
            tools = IdeTools(tempFolder.root)
        )

        // read_file
        val readResult = adapter.testDispatchTool("read_file", mapOf("path" to "test.txt"))
        assertEquals("hello", readResult)

        // write_file
        val writeResult = adapter.testDispatchTool("write_file", mapOf("path" to "out.txt", "content" to "world"))
        assertEquals("OK", writeResult)

        // list_files
        val listResult = adapter.testDispatchTool("list_files", mapOf("path" to "."))
        assertTrue(listResult.contains("test.txt"))

        // unknown tool
        val unknownResult = adapter.testDispatchTool("bogus_tool", emptyMap<String, Any?>())
        assertTrue(unknownResult.startsWith("Error:"))
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
