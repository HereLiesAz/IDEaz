package com.hereliesaz.ideaz.ai

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IdeToolsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var tools: IdeTools

    @Before
    fun setUp() {
        tools = IdeTools(tempFolder.root)
    }

    // --- readFile ---

    @Test
    fun `readFile returns file contents`() {
        tempFolder.newFile("index.html").writeText("<h1>Hello</h1>")
        val result = tools.readFile("index.html")
        assertEquals("<h1>Hello</h1>", result)
    }

    @Test
    fun `readFile returns error message for missing file`() {
        val result = tools.readFile("nonexistent.html")
        assertTrue("Expected error prefix, got: $result", result.startsWith("Error:"))
    }

    // --- writeFile ---

    @Test
    fun `writeFile creates file with content and returns OK`() {
        val result = tools.writeFile("app.js", "console.log('hi')")
        assertEquals("OK", result)
        assertEquals("console.log('hi')", File(tempFolder.root, "app.js").readText())
    }

    @Test
    fun `writeFile creates intermediate directories`() {
        val result = tools.writeFile("src/js/app.js", "const x = 1;")
        assertEquals("OK", result)
        assertTrue(File(tempFolder.root, "src/js/app.js").exists())
    }

    @Test
    fun `writeFile overwrites existing content`() {
        tempFolder.newFile("style.css").writeText("old")
        tools.writeFile("style.css", "new")
        assertEquals("new", File(tempFolder.root, "style.css").readText())
    }

    // --- listFiles ---

    @Test
    fun `listFiles lists directory entries`() {
        tempFolder.newFile("a.html")
        tempFolder.newFile("b.css")
        val result = tools.listFiles(".")
        assertTrue(result.contains("a.html"))
        assertTrue(result.contains("b.css"))
    }

    @Test
    fun `listFiles returns error for non-directory path`() {
        tempFolder.newFile("file.txt")
        val result = tools.listFiles("file.txt")
        assertTrue("Expected error prefix, got: $result", result.startsWith("Error:"))
    }

    // --- applyPatch ---

    @Test
    fun `applyPatch applies unified diff and returns OK`() {
        val file = tempFolder.newFile("style.css")
        file.writeText("body { color: red; }\n")
        val patch = "--- a/style.css\n+++ b/style.css\n@@ -1 +1 @@\n-body { color: red; }\n+body { color: blue; }\n"
        val result = tools.applyPatch(patch)
        assertEquals("OK", result)
        assertEquals("body { color: blue; }\n", File(tempFolder.root, "style.css").readText())
        assertFalse(".git should be cleaned up after patch", File(tempFolder.root, ".git").exists())
    }

    @Test
    fun `applyPatch returns error for malformed patch`() {
        val result = tools.applyPatch("this is not a valid patch")
        assertTrue("Expected error prefix, got: $result", result.startsWith("Error:"))
    }
}
