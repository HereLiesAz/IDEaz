// app/src/test/java/com/hereliesaz/ideaz/ui/delegates/OverlayDelegateWebContextTest.kt
package com.hereliesaz.ideaz.ui.delegates

import android.app.Application
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class OverlayDelegateWebContextTest {

    private val app: Application = mock()
    private val settingsVm: SettingsViewModel = mock()

    @Test
    fun `onWebElementContext sets pendingContextInfo and shows contextual chat`() = runTest {
        val delegate = OverlayDelegate(
            application = app,
            settingsViewModel = settingsVm,
            scope = this,
            onOverlayLog = {}
        )

        val sampleJson = """{"tagName":"button","id":"cta","selector":"section > button#cta"}"""
        delegate.onWebElementContext(sampleJson)

        assertTrue(delegate.isContextualChatVisible.first())
        assertEquals(sampleJson, delegate.pendingContextInfo)
    }
}
