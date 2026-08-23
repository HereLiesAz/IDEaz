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
}
