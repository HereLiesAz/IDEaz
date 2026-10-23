package com.hereliesaz.ideaz.ui.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebProjectUrlUtilsTest {

    @Test
    fun localProjectRootUrl_isOriginRootIndex() {
        assertEquals(
            "https://appassets.androidplatform.net/index.html",
            WebProjectUrlUtils.localProjectRootUrl()
        )
    }

    @Test
    fun localProjectUrl_usesTheGivenEntryPointNotAlwaysRoot() {
        assertEquals(
            "https://appassets.androidplatform.net/public/index.html",
            WebProjectUrlUtils.localProjectUrl("public/index.html")
        )
        assertEquals(
            "https://appassets.androidplatform.net/index.html",
            WebProjectUrlUtils.localProjectUrl("index.html")
        )
    }

    @Test
    fun navigationStaysOnTheAssetOriginForALocalProject() {
        assertTrue(WebProjectUrlUtils.isAllowedNavigation("https://appassets.androidplatform.net/about.html", null))
        assertTrue(WebProjectUrlUtils.isAllowedNavigation("https://appassets.androidplatform.net:443/x?y=1#z", null))
        listOf(
            "https://evil.example/", "http://appassets.androidplatform.net/", "https://appassets.androidplatform.net.evil.example/",
            "https://appassets.androidplatform.net@evil.example/", "intent://x#Intent;end", "javascript:alert(1)",
            "file:///data/data/com.hereliesaz.ideaz/files/x", "data:text/html,hi", "about:blank", "",
        ).forEach { assertFalse(it, WebProjectUrlUtils.isAllowedNavigation(it, null)) }
    }

    @Test
    fun aRemotePreviewMayNavigateWithinItsOwnOriginOnly() {
        val base = "https://my-app.pages.dev/index.html"
        assertTrue(WebProjectUrlUtils.isAllowedNavigation("https://my-app.pages.dev/other", base))
        assertTrue(WebProjectUrlUtils.isAllowedNavigation("https://MY-APP.pages.dev:443/", base))
        assertFalse(WebProjectUrlUtils.isAllowedNavigation("https://other.pages.dev/", base))
        assertFalse(WebProjectUrlUtils.isAllowedNavigation("http://my-app.pages.dev/", base))
        assertFalse(WebProjectUrlUtils.isAllowedNavigation("https://my-app.pages.dev:8443/", base))
    }
}
