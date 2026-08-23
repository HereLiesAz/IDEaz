package com.hereliesaz.ideaz.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AiEditApplierTest {

    @Test
    fun parsesFileBlock() {
        val resp = """
            Sure, here is the change:
            ```file:src/App.kt
            fun main() {}
            ```
            Done.
        """.trimIndent()
        val edits = AiEditApplier.parse(resp)
        assertEquals(1, edits.size)
        assertEquals("src/App.kt", edits[0].path)
        assertFalse(edits[0].isDiff)
        assertEquals("fun main() {}\n", edits[0].body)
    }

    @Test
    fun parsesDiffBlock() {
        val resp = "```diff\n--- a\n+++ b\n@@\n-x\n+y\n```"
        val edits = AiEditApplier.parse(resp)
        assertEquals(1, edits.size)
        assertTrue(edits[0].isDiff)
    }

    @Test
    fun ignoresPlainCodeBlocks() {
        val resp = "Example:\n```kotlin\nval x = 1\n```\nno edits here"
        assertTrue(AiEditApplier.parse(resp).isEmpty())
    }

    @Test
    fun appliesFileWrite() {
        val dir = Files.createTempDirectory("applier").toFile()
        val tools = IdeTools(dir)
        val resp = "```file:a/b.txt\nhello\nworld\n```"
        val outcomes = AiEditApplier.apply(resp, tools)
        assertEquals(1, outcomes.size)
        assertTrue(outcomes[0].ok)
        assertEquals("hello\nworld", File(dir, "a/b.txt").readText())
    }
}
