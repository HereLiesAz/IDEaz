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
}
