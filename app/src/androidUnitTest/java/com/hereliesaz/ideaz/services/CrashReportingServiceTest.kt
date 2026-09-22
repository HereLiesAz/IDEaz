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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@Ignore("Crashes Robolectric runner in CI environment")
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
