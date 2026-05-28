package com.hereliesaz.ideaz.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GithubIssueReporterTest {

    @Test
    fun `test reportError with stackTraceOverride`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logContent = "Some log content"
        val stackTraceOverride = "Manual Stack Trace\nat line 1\nat line 2"

        // We can't easily mock the GitHub API or Intent launch in this test without more setup,
        // but we can verify the deduplication logic and sanitization which are internal.

        // First report
        val result1 = GithubIssueReporter.reportError(
            context = context,
            token = null, // Trigger browser fallback
            error = null,
            contextMessage = "Test Context",
            logContent = logContent,
            stackTraceOverride = stackTraceOverride
        )
        assertEquals("Opened in browser for manual reporting.", result1)

        // Second report (Duplicate)
        val result2 = GithubIssueReporter.reportError(
            context = context,
            token = null,
            error = null,
            contextMessage = "Test Context",
            logContent = logContent,
            stackTraceOverride = stackTraceOverride
        )
        assertTrue(result2.contains("Skipped (Duplicate report within 24h)"))
    }

    @Test
    fun `test sanitizeContent`() {
        val raw = "My secret key is AIzaTestKey1234567890123456789012345"
        val sanitized = GithubIssueReporter.sanitizeContent(raw)
        assertTrue(sanitized.contains("***REDACTED***"))
        assertTrue(!sanitized.contains("AIzaTestKey"))
    }
}
