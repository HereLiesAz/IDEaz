package com.hereliesaz.ideaz.ui.web

import org.junit.Assert.assertEquals
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
}
