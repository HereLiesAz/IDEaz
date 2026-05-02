// app/src/test/java/com/hereliesaz/ideaz/ui/web/WebViewBridgeTest.kt
package com.hereliesaz.ideaz.ui.web

import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewBridgeTest {

    @Test
    fun `onElementTapped invokes callback with exact json string`() {
        var received: String? = null
        val bridge = WebViewBridge { json -> received = json }

        val payload = """{"tagName":"div","id":"hero","className":"container"}"""
        bridge.onElementTapped(payload)

        assertEquals(payload, received)
    }

    @Test
    fun `onElementTapped forwards empty string without modification`() {
        var received: String? = null
        val bridge = WebViewBridge { json -> received = json }

        bridge.onElementTapped("")

        assertEquals("", received)
    }
}
