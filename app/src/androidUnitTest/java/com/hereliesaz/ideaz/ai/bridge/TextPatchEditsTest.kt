package com.hereliesaz.ideaz.ai.bridge

import com.hereliesaz.ideaz.ai.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextPatchEditsTest {
    private val diff = "--- a/app.js\n+++ b/app.js\n@@ -1 +1 @@\n-old\n+new"

    @Test
    fun `a fenced diff is extracted`() {
        assertEquals(diff, TextPatchEdits.extractUnifiedDiff("Done.\n```diff\n$diff\n```"))
    }

    @Test
    fun `a bare diff at the end is extracted`() {
        assertEquals(diff, TextPatchEdits.extractUnifiedDiff("Done.\n$diff"))
    }

    @Test
    fun `prose without a diff yields nothing`() {
        assertNull(TextPatchEdits.extractUnifiedDiff("No changes needed."))
    }

    @Test
    fun `the transcript labels each turn`() {
        val transcript = TextPatchEdits.serializeConversation(
            listOf(ChatMessage("user", "hi"), ChatMessage("model", "hello"))
        )
        assertEquals("USER:\nhi\n\nASSISTANT:\nhello", transcript)
    }

    @Test
    fun `a fence inside a markdown diff does not end the block`() {
        val md = "--- a/README.md\n+++ b/README.md\n@@ -1,3 +1,3 @@\n ```sh\n-npm i\n+npm ci\n ```"
        assertEquals(md, TextPatchEdits.extractUnifiedDiff("Here:\n```diff\n$md\n```\nDone."))
    }

    @Test
    fun `a final context line that is a lone space survives`() {
        val withBlankContext = "--- a/app.js\n+++ b/app.js\n@@ -1,2 +1,2 @@\n-old\n+new\n "
        assertEquals(withBlankContext, TextPatchEdits.extractUnifiedDiff("```diff\n$withBlankContext\n```"))
        assertEquals(withBlankContext, TextPatchEdits.extractUnifiedDiff("Done.\n$withBlankContext\n\n"))
    }

    @Test
    fun `every fenced diff block is kept`() {
        val second = "--- a/b.js\n+++ b/b.js\n@@ -1 +1 @@\n-x\n+y"
        assertEquals(
            "$diff\n$second",
            TextPatchEdits.extractUnifiedDiff("```diff\n$diff\n```\nand\n```patch\n$second\n```"),
        )
    }

    @Test
    fun `failure note warns when the restore failed too`() {
        val plain = TextPatchEdits.failureNote("R", "boom", null, restoreFailed = false)
        assertEquals("R\n\nIDEaz could not safely apply the patch: boom", plain)
        val warned = TextPatchEdits.failureNote("R", "boom", "mismatch", restoreFailed = true)
        org.junit.Assert.assertTrue(warned.contains("may have been left modified"))
        org.junit.Assert.assertTrue(warned.contains("mismatch"))
    }
}
