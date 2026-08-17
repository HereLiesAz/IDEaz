package com.hereliesaz.ideaz.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogSanitizerTest {

    @Test
    fun `redacts classic GitHub tokens`() {
        val result = LogSanitizer.sanitize("token: ghp_1234567890abcdefABCDEF1234567890abcd")
        assertFalse(result.contains("ghp_1234567890abcdefABCDEF1234567890abcd"))
    }

    @Test
    fun `redacts fine-grained GitHub PATs`() {
        // GitHub's default PAT format since 2022 - previously unmatched by
        // the gh[pousr]_ pattern above, which was written for the old style.
        val result = LogSanitizer.sanitize(
            "Authorization: token github_pat_11ABCDEFG0abcdefghijklmnop_1234567890abcdefghijklmnopqrstuvwxyz"
        )
        assertFalse(result.contains("github_pat_11ABCDEFG0"))
    }

    @Test
    fun `redacts OpenAI keys without matching Anthropic keys as OpenAI`() {
        val openAiResult = LogSanitizer.sanitize("key=sk-abcdefghijklmnopqrstuvwxyz1234567890")
        assertFalse(openAiResult.contains("sk-abcdefghijklmnopqrstuvwxyz1234567890"))
    }

    @Test
    fun `redacts Anthropic keys`() {
        val result = LogSanitizer.sanitize("key=sk-ant-abcdefghijklmnopqrstuvwxyz1234567890")
        assertFalse(result.contains("sk-ant-abcdefghijklmnopqrstuvwxyz1234567890"))
    }

    @Test
    fun `redacts Hugging Face and Groq keys`() {
        val hf = LogSanitizer.sanitize("hf_abcdefghijklmnopqrstuvwxyz1234567890")
        val groq = LogSanitizer.sanitize("gsk_abcdefghijklmnopqrstuvwxyz1234567890")
        assertFalse(hf.contains("hf_abcdefghijklmnopqrstuvwxyz1234567890"))
        assertFalse(groq.contains("gsk_abcdefghijklmnopqrstuvwxyz1234567890"))
    }

    @Test
    fun `leaves unrelated text untouched`() {
        val text = "Build succeeded in 12s with 0 warnings."
        assertEquals(text, LogSanitizer.sanitize(text))
    }
}
