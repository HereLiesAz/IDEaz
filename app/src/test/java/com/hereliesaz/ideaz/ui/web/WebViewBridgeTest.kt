// app/src/test/java/com/hereliesaz/ideaz/ui/web/WebViewBridgeTest.kt
package com.hereliesaz.ideaz.ui.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `callback not invoked before onElementTapped is called`() {
        var received: String? = null
        @Suppress("UNUSED_VARIABLE")
        val bridge = WebViewBridge { json -> received = json }

        assertNull(received)
    }
}
