package com.hereliesaz.ideaz.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ToolDispatchTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `write_file without content is an error and leaves the file alone`() {
        val tools = IdeTools(tempFolder.root)
        File(tempFolder.root, "app.js").writeText("keep me")

        val result = dispatchIdeTool("write_file", mapOf("path" to "app.js", "content" to null), tools)

        assertTrue(result, result.startsWith("Error:"))
        assertEquals("keep me", File(tempFolder.root, "app.js").readText())
        assertTrue(dispatchIdeTool("write_file", mapOf("path" to "app.js"), tools).startsWith("Error:"))
    }

    @Test
    fun `write_file with empty content still writes`() {
        val tools = IdeTools(tempFolder.root)
        File(tempFolder.root, "app.js").writeText("x")
        assertEquals("OK", dispatchIdeTool("write_file", mapOf("path" to "app.js", "content" to ""), tools))
        assertEquals("", File(tempFolder.root, "app.js").readText())
    }
}
