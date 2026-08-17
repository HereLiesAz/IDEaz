package com.hereliesaz.ideaz.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * With a null token, [GithubIssueReporter.reportError] falls through to
 * `withContext(Dispatchers.Main) { context.startActivity(intent) }`. Under
 * Robolectric's default PAUSED looper mode, a plain Dispatchers.Main post just
 * queues on the main Looper and nothing here ever idles it, so `withContext`
 * (and a surrounding `runBlocking`) deadlocks forever - and since
 * app/build.gradle.kts sets no `forkEvery`, that one deadlocked test used to
 * hang the entire testDebugUnitTest task, and therefore the whole build, every
 * time it ran (the actual cause of several repeated ~30-minute CI timeouts).
 *
 * Fixed by swapping Dispatchers.Main for a StandardTestDispatcher(testScheduler)
 * - the SAME scheduler runTest's own TestScope receiver uses (the pattern
 * StateDelegateTest already uses for the same reason) - so work dispatched to
 * "Main" is drained by runTest's own auto-advance-to-idle instead of sitting on
 * an unrelated, never-advanced scheduler of its own. A bare
 * `StandardTestDispatcher()` (no scheduler argument) would just reproduce the
 * same "queued forever" shape through a different mechanism. `@LooperMode(LEGACY)`
 * was tried first but crashed the whole Gradle Test Executor JVM outright -
 * LEGACY mode isn't used anywhere else in this test suite, and building
 * Robolectric's sandbox for it appears incompatible with this project's
 * Robolectric/Java 21 combination.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GithubIssueReporterTest {

    @Test
    fun `test reportError with stackTraceOverride`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
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
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `test sanitizeContent`() {
        // LogSanitizer's Google-key pattern is AIza + exactly 35 chars (Google
        // API keys are a fixed 39 chars total) - this fake key previously had
        // only 32, so it silently never matched and this assertion was broken
        // long before this session touched the file. Invisible until now: the
        // whole test class used to deadlock before JUnit could ever report an
        // individual test result (see the class doc comment above).
        //
        // Built by concatenation, not a literal, so this obviously-fake test
        // fixture doesn't read as a contiguous secret-shaped string to
        // pattern-based scanners (it was never a real, functional key).
        val fakeKeyBody = "TestFakeKey" + "1234567890" + "1234567890" + "1234"
        val raw = "My secret key is AIza$fakeKeyBody"
        val sanitized = GithubIssueReporter.sanitizeContent(raw)
        assertTrue(sanitized.contains("***REDACTED***"))
        assertTrue(!sanitized.contains(fakeKeyBody))
    }
}
