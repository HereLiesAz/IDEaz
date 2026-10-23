package com.hereliesaz.ideaz.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Each token pattern is tested alone: the secret sits in text with no
 * `key=`/`token:` around it, so the generic by-name rules cannot be what
 * redacts it. (Every test here used to write `key=<secret>`, and stayed green
 * with its own pattern deleted.)
 */
class LogSanitizerTest {

    private fun assertRedacted(secret: String, context: String = "found $secret in the log") {
        val result = LogSanitizer.sanitize(context)
        assertFalse("not redacted: $result", result.contains(secret))
    }

    @Test
    fun `redacts classic GitHub tokens`() = assertRedacted("ghp_1234567890abcdefABCDEF1234567890abcd")

    @Test
    fun `redacts fine-grained GitHub PATs`() =
        assertRedacted("github_pat_11ABCDEFG0abcdefghijklmnop_1234567890abcdefghijklmnopqrstuvwxyz")

    @Test
    fun `redacts Google API keys`() = assertRedacted("AIzaSyA1234567890abcdefghijklmnopqrstuv")

    @Test
    fun `redacts OpenAI keys`() = assertRedacted("sk-abcdefghijklmnopqrstuvwxyz1234567890")

    @Test
    fun `redacts current OpenAI project-scoped keys`() =
        assertRedacted("sk-proj-abcdefGHIJKL_1234567890-abcdefGHIJKL1234567890")

    @Test
    fun `redacts Anthropic keys`() = assertRedacted("sk-ant-abcdefghijklmnopqrstuvwxyz1234567890")

    @Test
    fun `redacts Hugging Face keys`() = assertRedacted("hf_abcdefghijklmnopqrstuvwxyz1234567890")

    @Test
    fun `redacts Groq keys`() = assertRedacted("gsk_abcdefghijklmnopqrstuvwxyz1234567890")

    @Test
    fun `redacts Cerebras keys`() = assertRedacted("csk-abcdefghijklmnopqrstuvwxyz1234567890")

    @Test
    fun `redacts Stripe secret and restricted keys`() {
        assertRedacted("sk_live_abcdefghij1234567890")
        assertRedacted("rk_live_abcdefghij1234567890")
    }

    @Test
    fun `redacts AWS access key ids`() = assertRedacted("AKIAIOSFODNN7EXAMPLE")

    @Test
    fun `keeps the Bearer prefix and redacts the token`() {
        assertEquals("Authorization: Bearer ***REDACTED***", LogSanitizer.sanitize("Authorization: Bearer abc.def-123"))
    }

    @Test
    fun `redacts URL credentials`() {
        assertEquals("https://***REDACTED***@example.com/x", LogSanitizer.sanitize("https://me:pa55@example.com/x"))
    }

    @Test
    fun `redacts a PEM private key in any text`() {
        val pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEAsecretbody\n-----END RSA PRIVATE KEY-----"
        val result = LogSanitizer.sanitize("notes before\n$pem\nnotes after")
        assertEquals("notes before\n***REDACTED***\nnotes after", result)
    }

    @Test
    fun `redacts an unterminated PEM block to the end`() {
        val result = LogSanitizer.sanitize("x\n-----BEGIN PRIVATE KEY-----\nMIIEsecret")
        assertEquals("x\n***REDACTED***", result)
    }

    @Test
    fun `redacts a service-account private_key, escaped newlines and all`() {
        val json = """{"type": "service_account", "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvQ+secret/body\n-----END PRIVATE KEY-----\n", "client_email": "a@b"}"""
        val result = LogSanitizer.sanitize(json)
        assertFalse(result, result.contains("MIIEvQ"))
        assertFalse(result, result.contains("secret/body"))
        assertEquals(
            """{"type": "service_account", "private_key": "***REDACTED***", "client_email": "a@b"}""",
            result,
        )
    }

    @Test
    fun `redacts quoted secret literals by name`() {
        assertEquals("\"password\": \"***REDACTED***\"", LogSanitizer.sanitize("\"password\": \"hunter22\""))
        assertEquals("const apiKey = '***REDACTED***';", LogSanitizer.sanitize("const apiKey = 'abcdef123456';"))
    }

    @Test
    fun `redacts unquoted values written straight after equals`() {
        assertEquals("API_KEY=***REDACTED***", LogSanitizer.sanitize("API_KEY=abc123xyz"))
        assertEquals("storePassword=***REDACTED***", LogSanitizer.sanitize("storePassword=s3cret"))
        assertEquals(
            "https://x.io/cb?token=***REDACTED***&state=1",
            LogSanitizer.sanitize("https://x.io/cb?token=abc123&state=1"),
        )
    }

    @Test
    fun `redacts a netrc password`() {
        assertEquals(
            "machine github.com login me password ***REDACTED***",
            LogSanitizer.sanitize("machine github.com login me password hunter2"),
        )
    }

    @Test
    fun `leaves ordinary source code alone`() {
        listOf(
            "<li key={item.id}>{item.name}</li>",
            "interface Session { token: string; expires: number }",
            "const password = input.value;",
            "<Tab key=\"home\" />",
            "if (!apiKey) throw new Error('missing')",
            "Password must be at least 8 characters.",
        ).forEach { code -> assertEquals(code, LogSanitizer.sanitize(code)) }
    }

    @Test
    fun `leaves unrelated text untouched`() {
        val text = "Build succeeded in 12s with 0 warnings."
        assertEquals(text, LogSanitizer.sanitize(text))
    }

    @Test
    fun `redacts Aive pairing codes`() {
        val code = "aive1:eyJ2IjoxLCJyZWxheSI6Imh0dHBzOi8vZXhhbXBsZS5jb20iLCJrZXkiOiJhYmMtX0RFRiJ9"
        assertRedacted(code, "paste this: $code and go")
        assertEquals("paste this: ***REDACTED*** and go", LogSanitizer.sanitize("paste this: $code and go"))
    }

    @Test
    fun `redacts Basic and token Authorization headers`() {
        assertEquals(
            "Authorization: Basic ***REDACTED***",
            LogSanitizer.sanitize("Authorization: Basic dXNlcjpwYXNzd29yZA=="),
        )
        assertEquals(
            "authorization: token ***REDACTED***",
            LogSanitizer.sanitize("authorization: token abcdef0123456789"),
        )
    }

    @Test
    fun `redacts lowercase bearer tokens`() {
        assertEquals("authorization: bearer ***REDACTED***", LogSanitizer.sanitize("authorization: bearer abc.def-123"))
    }

    @Test
    fun `redacts the x-api-key header in colon form`() {
        assertEquals("X-Api-Key: ***REDACTED***", LogSanitizer.sanitize("X-Api-Key: abc123def456"))
        assertEquals("x-api-key: ***REDACTED***", LogSanitizer.sanitize("x-api-key: abc123def456"))
        assertEquals("\"x-api-key\": \"***REDACTED***\"", LogSanitizer.sanitize("\"x-api-key\": \"abc123def456\""))
    }

    @Test
    fun `replaces GitHub owner and repo with a placeholder`() {
        assertEquals(
            "cloning https://github.com/<owner>/<repo> failed",
            LogSanitizer.sanitize("cloning https://github.com/HereLiesAz/secret-app.git failed"),
        )
        assertEquals("fatal: git@github.com/<owner>/<repo>", LogSanitizer.sanitize("fatal: git@github.com:me/private.git"))
        assertEquals("see github.com/<owner>/<repo>/issues/1", LogSanitizer.sanitize("see github.com/me/app/issues/1"))
    }

    @Test
    fun `replaces the project name in app storage paths`() {
        assertEquals(
            "at /data/user/0/com.hereliesaz.ideaz/files/<project>/src/App.jsx:3",
            LogSanitizer.sanitize("at /data/user/0/com.hereliesaz.ideaz/files/MyDiary/src/App.jsx:3"),
        )
        assertEquals(
            "/data/data/com.hereliesaz.ideaz/files/<project>",
            LogSanitizer.sanitize("/data/data/com.hereliesaz.ideaz/files/MyDiary"),
        )
        assertEquals(
            "/storage/emulated/0/Android/data/com.hereliesaz.ideaz/files/<project>/x",
            LogSanitizer.sanitize("/storage/emulated/0/Android/data/com.hereliesaz.ideaz/files/MyDiary/x"),
        )
        // Not app storage: left alone.
        assertEquals("https://example.com/files/report.pdf", LogSanitizer.sanitize("https://example.com/files/report.pdf"))
    }

    @Test
    fun `sanitizing twice changes nothing more`() {
        val once = LogSanitizer.sanitize("/data/user/0/com.hereliesaz.ideaz/files/P/a github.com/o/r aive1:abc_DEF")
        assertEquals(once, LogSanitizer.sanitize(once))
    }

    @Test
    fun `redactSecrets leaves repository and project names alone`() {
        val text = "see https://github.com/o/r and /data/user/0/com.hereliesaz.ideaz/files/P/a"
        assertEquals(text, LogSanitizer.redactSecrets(text))
        assertEquals("aive1 code ***REDACTED***", LogSanitizer.redactSecrets("aive1 code aive1:abc123"))
    }
}
