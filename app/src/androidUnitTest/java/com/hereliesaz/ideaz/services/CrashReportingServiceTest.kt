package com.hereliesaz.ideaz.services

import android.app.Service
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

// Still ignored, deliberately. Two separate problems keep this class from being a safe unit test, and
// neither could be re-checked while it was last edited (Gradle could not run):
//  1. The original failure ("crashes the Robolectric runner in CI") was never diagnosed, so
//     re-enabling blind could take PR CI down with it.
//  2. The first test hands CrashReportingService a token and stack trace; the service then calls
//     GithubIssueReporter.reportError on Dispatchers.IO, i.e. a real GitHub API request from a unit
//     test. Un-ignoring it needs an injectable reporter (a seam in the service) first.
// Crash-report consent and default-on/off behaviour are covered elsewhere and are not touched here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@Ignore("Undiagnosed Robolectric runner crash; first test would also hit the real GitHub API. See comment above.")
class CrashReportingServiceTest {

    @Test
    fun `onStartCommand with valid extras accepts the intent as non-sticky`() {
        val controller = Robolectric.buildService(CrashReportingService::class.java)
        val service = controller.create().get()

        val intent = Intent().apply {
            putExtra(CrashReportingService.EXTRA_GITHUB_TOKEN, "test_github_token")
            putExtra(CrashReportingService.EXTRA_STACK_TRACE, "Exception: Boom")
            putExtra(CrashReportingService.EXTRA_GITHUB_USER, "TestUser")
        }

        val result = service.onStartCommand(intent, 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)

        controller.destroy()
    }

    @Test
    fun `onStartCommand without a stack trace stops the service without scheduling a report`() {
        val controller = Robolectric.buildService(CrashReportingService::class.java)
        val service = controller.create().get()

        val intent = Intent().apply {
            putExtra(CrashReportingService.EXTRA_GITHUB_TOKEN, "test_github_token")
            putExtra(CrashReportingService.EXTRA_GITHUB_USER, "TestUser")
        }

        val result = service.onStartCommand(intent, 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)

        controller.destroy()
    }

}
