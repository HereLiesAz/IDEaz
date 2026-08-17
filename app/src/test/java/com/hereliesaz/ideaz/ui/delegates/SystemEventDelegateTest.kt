package com.hereliesaz.ideaz.ui.delegates

import android.content.Intent
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.models.ACTION_AI_LOG
import com.hereliesaz.ideaz.models.EXTRA_MESSAGE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@Ignore("Crashes Robolectric runner in CI environment")
class SystemEventDelegateTest {

    @Test
    fun testAiLogBroadcast() = runTest {
        val app = RuntimeEnvironment.getApplication()
        // Same shape of bug that hung the whole build's testDebugUnitTest
        // task elsewhere (see AIDelegateTest): an uncancelled real
        // CoroutineScope() built in a test. Latent only because this class
        // is @Ignore'd - un-ignoring it without this fix would reintroduce
        // that hang. try/finally so the scope is cancelled even if an
        // assertion below fails.
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val testScope = TestScope()
        try {
            // Instantiate real delegates
            val settingsVM = SettingsViewModel(app)
            val aiDelegate = AIDelegate(settingsVM, scope, {}, { true })
            val overlayDelegate = OverlayDelegate(app, settingsVM, scope, {})
            val stateDelegate = StateDelegate(testScope)

            // Instantiate SystemEventDelegate (registers receivers)
            val systemEventDelegate = SystemEventDelegate(app, aiDelegate, overlayDelegate, stateDelegate)

            val testMsg = "[WEB] Test Log Message"
            val intent = Intent(ACTION_AI_LOG).apply {
                putExtra(EXTRA_MESSAGE, testMsg)
            }

            app.sendBroadcast(intent)
            ShadowLooper.idleMainLooper()

            testScope.advanceUntilIdle() // Process channel in StateDelegate

            // Verify logs
            val logs = stateDelegate.buildLog.value
            assertTrue("Log should contain '$testMsg', but was $logs", logs.contains(testMsg))

            systemEventDelegate.cleanup()
        } finally {
            scope.cancel()
            testScope.cancel()
        }
    }
}
