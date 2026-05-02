package com.hereliesaz.ideaz.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SourceContextHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun resolveContext_sourceTag_success() {
        val projectDir = tempFolder.newFolder("js_project")
        val appJs = File(projectDir, "App.js")
        appJs.writeText("""
            const greet = () => "Hello";

            function App() {
              const value = greet();
              return value;
            }
        """.trimIndent())

        // Line 4 is "const value = greet();"
        val resourceId = "__source:App.js:4__"

        val result = SourceContextHelper.resolveContext(resourceId, projectDir)

        assertEquals(false, result.isError)
        assertEquals(appJs.absolutePath, result.file)
        assertEquals(4, result.line)
        assertEquals("const value = greet();", result.snippet)
    }

    @Test
    fun resolveContext_sourceTag_fileNotFound() {
        val projectDir = tempFolder.newFolder("js_project_missing")
        val resourceId = "__source:Missing.js:5__"

        val result = SourceContextHelper.resolveContext(resourceId, projectDir)

        assertEquals(true, result.isError)
        assertTrue(result.errorMessage?.contains("Source file not found") == true)
    }

    @Test
    fun resolveContext_androidId_degradesGracefully() {
        // The on-device source-map generator was removed in Phase 0 (Task 6),
        // so any non-__source__ id (e.g., a plain Android resource id) now
        // degrades to a "no source-map data available" error rather than
        // looking up a non-existent map.
        val projectDir = tempFolder.newFolder("android_project")
        val result = SourceContextHelper.resolveContext("myButton", projectDir)

        assertEquals(true, result.isError)
        assertTrue(result.errorMessage?.contains("No source-map data available") == true)
    }

    @Test
    fun resolveContext_androidId_withPackagePrefix_degradesGracefully() {
        val projectDir = tempFolder.newFolder("android_project_full")
        val result = SourceContextHelper.resolveContext("com.example:id/title", projectDir)

        assertEquals(true, result.isError)
        assertTrue(result.errorMessage?.contains("No source-map data available") == true)
    }
}
