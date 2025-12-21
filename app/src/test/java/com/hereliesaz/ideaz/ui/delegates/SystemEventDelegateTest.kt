package com.hereliesaz.ideaz.ui.delegates

import android.content.Intent
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.ui.web.ACTION_AI_LOG
import com.hereliesaz.ideaz.ui.web.EXTRA_MESSAGE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemEventDelegateTest {

    @Test
    fun testAiLogBroadcast() {
        val app = RuntimeEnvironment.getApplication()
        val scope = CoroutineScope(Dispatchers.Unconfined)

        // Instantiate real delegates
        val settingsVM = SettingsViewModel(app)
        val aiDelegate = AIDelegate(settingsVM, scope, {}, { true })
        val overlayDelegate = OverlayDelegate(app, settingsVM, scope, {})
        val stateDelegate = StateDelegate()

        // Instantiate SystemEventDelegate (registers receivers)
        val systemEventDelegate = SystemEventDelegate(app, aiDelegate, overlayDelegate, stateDelegate)

        val testMsg = "[WEB] Test Log Message"
        val intent = Intent(ACTION_AI_LOG).apply {
            putExtra(EXTRA_MESSAGE, testMsg)
        }

        app.sendBroadcast(intent)
        ShadowLooper.idleMainLooper()

        // Verify logs
        val logs = stateDelegate.buildLog.value
        assertTrue("Log should contain '$testMsg', but was $logs", logs.contains(testMsg))

        systemEventDelegate.cleanup()
    }
}
