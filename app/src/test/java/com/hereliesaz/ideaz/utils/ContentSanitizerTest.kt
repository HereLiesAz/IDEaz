package com.hereliesaz.ideaz.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentSanitizerTest {

    @Test
    fun `sanitize removes Bearer tokens`() {
        val input = "Authorization: Bearer abc123def456.xyz789"
        val expected = "Authorization: ***REDACTED***"
        assertEquals(expected, GithubIssueReporter.sanitizeContent(input))
    }

    @Test
    fun `sanitize removes key query params`() {
        val input = "https://api.example.com?key=secret_key_123&other=value"
        val expected = "https://api.example.com?***REDACTED***&other=value"
        assertEquals(expected, GithubIssueReporter.sanitizeContent(input))
    }

    @Test
    fun `sanitize removes token query params`() {
        val input = "https://api.example.com?token=token_123"
        val expected = "https://api.example.com?***REDACTED***"
        assertEquals(expected, GithubIssueReporter.sanitizeContent(input))
    }

    @Test
    fun `sanitize removes GitHub tokens`() {
        val input = "My token is ghp_AbCdEf123456"
        val expected = "My token is ***REDACTED***"
        assertEquals(expected, GithubIssueReporter.sanitizeContent(input))
    }

    @Test
    fun `sanitize removes Google API keys`() {
        val input = "Key: AIzaSyD-1234567890abcdef1234567890abcde"
        val expected = "Key: ***REDACTED***"
        assertEquals(expected, GithubIssueReporter.sanitizeContent(input))
    }

    @Test
    fun `sanitize ignores safe content`() {
        val input = "This is a normal log message with error 500"
        assertEquals(input, GithubIssueReporter.sanitizeContent(input))
    }
}
