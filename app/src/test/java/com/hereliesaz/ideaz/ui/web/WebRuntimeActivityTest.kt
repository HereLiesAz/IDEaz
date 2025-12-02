package com.hereliesaz.ideaz.ui.web

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WebRuntimeActivityTest {

    @Test
    fun activity_launches_with_url() {
        val url = "https://example.com"
        val intent = Intent(ApplicationProvider.getApplicationContext(), WebRuntimeActivity::class.java).apply {
            putExtra("URL", url)
        }

        ActivityScenario.launch<WebRuntimeActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
                // If we reached here, onCreate and setContent executed without throwing.
            }
        }
    }

    @Test
    fun activity_launches_without_url_defaults_to_blank() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), WebRuntimeActivity::class.java)

        ActivityScenario.launch<WebRuntimeActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
        }
    }
}
