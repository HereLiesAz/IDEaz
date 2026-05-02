package com.hereliesaz.ideaz.ui.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebProjectUrlUtilsTest {

    private val fakeFilesDir = File("/data/user/0/com.hereliesaz.ideaz/files")

    @Test
    fun localProjectUrl_buildsCorrectUrl() {
        val url = WebProjectUrlUtils.localProjectUrl("myapp", fakeFilesDir)
        assertEquals(
            "https://appassets.androidplatform.net/files/myapp/index.html",
            url
        )
    }

    @Test
    fun toAssetUrl_convertsAbsolutePathInsideFilesDir() {
        val absolutePath = "/data/user/0/com.hereliesaz.ideaz/files/myapp/index.html"
        val url = WebProjectUrlUtils.toAssetUrl(absolutePath, fakeFilesDir)
        assertEquals(
            "https://appassets.androidplatform.net/files/myapp/index.html",
            url
        )
    }

    @Test
    fun toAssetUrl_returnsNullForPathOutsideFilesDir() {
        val absolutePath = "/sdcard/myapp/index.html"
        val url = WebProjectUrlUtils.toAssetUrl(absolutePath, fakeFilesDir)
        assertNull(url)
    }

    @Test
    fun toAssetUrl_handlesFilesDirWithTrailingSlash() {
        val dirWithSlash = File("/data/user/0/com.hereliesaz.ideaz/files/")
        val absolutePath = "/data/user/0/com.hereliesaz.ideaz/files/myapp/index.html"
        val url = WebProjectUrlUtils.toAssetUrl(absolutePath, dirWithSlash)
        assertEquals(
            "https://appassets.androidplatform.net/files/myapp/index.html",
            url
        )
    }

    @Test
    fun isAssetUrl_trueForAssetLoaderUrls() {
        assertTrue(WebProjectUrlUtils.isAssetUrl(
            "https://appassets.androidplatform.net/files/myapp/index.html"
        ))
    }

    @Test
    fun isAssetUrl_falseForFileUrls() {
        assertFalse(WebProjectUrlUtils.isAssetUrl(
            "file:///data/user/0/com.hereliesaz.ideaz/files/myapp/index.html"
        ))
    }

    @Test
    fun isAssetUrl_falseForExternalUrls() {
        assertFalse(WebProjectUrlUtils.isAssetUrl("https://example.com/index.html"))
    }
}
