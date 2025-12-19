package com.hereliesaz.ideaz.services

import android.content.Intent
import com.hereliesaz.ideaz.api.AuthInterceptor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashReportingServiceTest {

    @Test
    fun `test service start`() {
        val controller = Robolectric.buildService(CrashReportingService::class.java)
        val service = controller.create().get()

        val intent = Intent().apply {
            putExtra(CrashReportingService.EXTRA_API_KEY, "test_api_key")
            putExtra(CrashReportingService.EXTRA_JULES_PROJECT_ID, "test_project_id")
            putExtra(CrashReportingService.EXTRA_STACK_TRACE, "Exception: Boom")
            putExtra(CrashReportingService.EXTRA_GITHUB_USER, "TestUser")
        }

        service.onStartCommand(intent, 0, 1)

        // Verify API key was set
        assertEquals("test_api_key", AuthInterceptor.apiKey)

        // Since the service launches a coroutine, we can't easily verify the network call
        // without complex mocking of the JulesApiClient singleton (which is an object).
        // However, we verified the critical setup step (API Key injection).

        controller.destroy()
    }
}
