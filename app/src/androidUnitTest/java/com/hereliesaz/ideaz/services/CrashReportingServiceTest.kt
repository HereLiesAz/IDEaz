package com.hereliesaz.ideaz.services

import android.app.Service
import android.content.Context
import android.content.Intent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Ignore
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

// The service files its report through CrashReportingService.reportIssue; these tests replace it so
// no real GitHub request is made. Crash-report consent and default-on/off behaviour are covered
// elsewhere and are not touched here.
//
// Still ignored: with the reporter seam in place and no network call left, CI still killed the unit
// test executor ("Gradle Test Executor ... finished with non-zero exit value 1") with this class
// enabled, so the original Robolectric crash is not the GitHub request. It has never been diagnosed;
// re-enable by removing @Ignore once it is reproduced with a stack trace.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@Ignore("Robolectric runner crash (undiagnosed): enabling this class kills the unit test executor in CI.")
class CrashReportingServiceTest {

    private val originalReporter = CrashReportingService.reportIssue

    @After
    fun restoreReporter() {
        CrashReportingService.reportIssue = originalReporter
    }

    @Test
    fun `onStartCommand with valid extras accepts the intent as non-sticky`() {
        val reported = CountDownLatch(1)
        var message: String? = null
        var trace: String? = null
        CrashReportingService.reportIssue = { _: Context, _: String, m: String, t: String ->
            message = m
            trace = t
            reported.countDown()
        }
        val controller = Robolectric.buildService(CrashReportingService::class.java)
        val service = controller.create().get()

        val intent = Intent().apply {
            putExtra(CrashReportingService.EXTRA_GITHUB_TOKEN, "test_github_token")
            putExtra(CrashReportingService.EXTRA_STACK_TRACE, "Exception: Boom")
            putExtra(CrashReportingService.EXTRA_GITHUB_USER, "TestUser")
        }

        val result = service.onStartCommand(intent, 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)
        assertTrue("the report was never filed", reported.await(10, TimeUnit.SECONDS))
        assertEquals("CRASH from TestUser", message)
        assertEquals("Exception: Boom", trace)

        controller.destroy()
    }

    @Test
    fun `onStartCommand without a stack trace stops the service without scheduling a report`() {
        val reports = AtomicInteger()
        CrashReportingService.reportIssue = { _: Context, _: String, _: String, _: String ->
            reports.incrementAndGet()
            Unit
        }
        val controller = Robolectric.buildService(CrashReportingService::class.java)
        val service = controller.create().get()

        val intent = Intent().apply {
            putExtra(CrashReportingService.EXTRA_GITHUB_TOKEN, "test_github_token")
            putExtra(CrashReportingService.EXTRA_GITHUB_USER, "TestUser")
        }

        val result = service.onStartCommand(intent, 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)
        // The service returns before launching anything when the trace is missing.
        assertEquals(0, reports.get())

        controller.destroy()
    }

}
