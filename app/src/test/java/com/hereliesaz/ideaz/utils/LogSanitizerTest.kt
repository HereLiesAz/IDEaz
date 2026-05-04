package com.hereliesaz.ideaz.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {

    private val redacted = "***REDACTED***"

    @Test
    fun sanitize_redactsGitHubTokens() {
        val input = "My token is ghp_12345abcde and another gho_xyz987"
        val expected = "My token is $redacted and another $redacted"
        assertEquals(expected, LogSanitizer.sanitize(input))
    }

    @Test
    fun sanitize_redactsGoogleApiKeys() {
        val key = "AIza" + "a".repeat(35)
        val input = "API_KEY=$key"
        val expected = "API_KEY=$redacted"
        assertEquals(expected, LogSanitizer.sanitize(input))
    }

    @Test
    fun sanitize_redactsBearerTokens() {
        val input = "Authorization: Bearer abc123_xyz.789"
        val expected = "Authorization: Bearer $redacted"
        assertEquals(expected, LogSanitizer.sanitize(input))
    }

    @Test
    fun sanitize_redactsGenericKeys() {
        val testCases = listOf(
            "key=secret_value" to "key=$redacted",
            "password: \"my_pwd\"" to "password: \"$redacted\"",
            "access_token = 'token123'" to "access_token = '$redacted'",
            "api_key:val123" to "api_key:$redacted",
            "pwd=password123&other=val" to "pwd=$redacted&other=val",
            "secret: topSecret" to "secret: $redacted"
        )

        testCases.forEach { (input, expected) ->
            assertEquals("Failed for input: $input", expected, LogSanitizer.sanitize(input))
        }
    }

    @Test
    fun sanitize_redactsUrlCredentials() {
        val input = "Connecting to https://user:pass123@example.com/api"
        val expected = "Connecting to https://$redacted@example.com/api"
        assertEquals(expected, LogSanitizer.sanitize(input))

        val ftp = "ftp://admin:secret@192.168.1.1"
        val expectedFtp = "ftp://$redacted@192.168.1.1"
        assertEquals(expectedFtp, LogSanitizer.sanitize(ftp))
    }

    @Test
    fun sanitize_handlesMultipleSecrets() {
        val input = "Error using ghp_token with AIza" + "b".repeat(35) + " at https://u:p@host.com"
        val expected = "Error using $redacted with $redacted at https://$redacted@host.com"
        assertEquals(expected, LogSanitizer.sanitize(input))
    }

    @Test
    fun sanitize_doesNotRedactNormalText() {
        val input = "This is a normal log message with no secrets. key and password are words here."
        assertEquals(input, LogSanitizer.sanitize(input))
    }

    @Test
    fun sanitize_redactsThrowableStackTrace() {
        val secret = "ghp_secretToken"
        val exception = RuntimeException("Error with token $secret")
        val sanitized = LogSanitizer.sanitize(exception)

        assertTrue("Stack trace should contain redacted marker", sanitized.contains(redacted))
        assertTrue("Stack trace should not contain secret", !sanitized.contains(secret))
    }
}
