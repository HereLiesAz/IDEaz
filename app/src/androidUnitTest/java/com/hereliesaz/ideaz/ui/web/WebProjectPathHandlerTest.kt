package com.hereliesaz.ideaz.ui.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure HTML/MIME helpers in [WebProjectPathHandler]. The
 * `handle()` request path needs an Android Context + WebResourceResponse and is
 * covered manually on device (see the plan's verification section).
 */
class WebProjectPathHandlerTest {

    @Test
    fun needsRuntime_trueForModuleScripts() {
        assertTrue(WebProjectPathHandler.needsRuntime("""<script type="module" src="/src/main.jsx"></script>"""))
        assertTrue(WebProjectPathHandler.needsRuntime("""<script type='module'>import x from './a'</script>"""))
        // Case-insensitive.
        assertTrue(WebProjectPathHandler.needsRuntime("""<SCRIPT TYPE="MODULE" src="/m.js"></SCRIPT>"""))
    }

    @Test
    fun needsRuntime_falseForStaticHtml() {
        assertFalse(WebProjectPathHandler.needsRuntime("""<script src="script.js"></script>"""))
        assertFalse(WebProjectPathHandler.needsRuntime("<h1>hi</h1>"))
    }

    @Test
    fun injectRuntime_insertsRuntimeAndNeutralizesModuleScripts() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>t</title></head>
            <body><script type="module" src="/src/main.jsx"></script></body>
            </html>
        """.trimIndent()

        val out = WebProjectPathHandler.injectRuntime(html)
        val prefix = WebProjectPathHandler.RUNTIME_PREFIX

        assertTrue("import map injected", out.contains("""<script type="importmap">"""))
        // Runtime paths are versioned with the app build (see RUNTIME_PREFIX) so
        // an app upgrade can't leave a stale cached runtime behind - assert
        // against the real prefix rather than a hardcoded unversioned one.
        // Bundled libraries are exposed via the import map (regression guard).
        assertTrue("react mapped", out.contains(""""react":"${prefix}react.js""""))
        assertTrue("react-router-dom mapped", out.contains("${prefix}react-router-dom-entry.js"))
        assertTrue("redux toolkit mapped", out.contains("${prefix}reduxjs-toolkit.js"))
        assertTrue("babel injected", out.contains("${prefix}babel.min.js"))
        assertTrue("loader injected", out.contains("${prefix}ideaz-loader.js"))
        assertTrue("react umd injected", out.contains("${prefix}react.umd.js"))
        // The browser must not natively execute raw JSX.
        assertTrue("module neutralized", out.contains("""type="ideaz-module""""))
        assertFalse("no native module script remains", out.contains("""type="module""""))
        // Injection lands inside <head>.
        assertTrue(out.indexOf("${prefix}ideaz-loader.js") < out.indexOf("<body"))
    }

    @Test
    fun injectRuntime_isIdempotent() {
        val html = """<html><head></head><body><script type="module" src="/m.jsx"></script></body></html>"""
        val once = WebProjectPathHandler.injectRuntime(html)
        val twice = WebProjectPathHandler.injectRuntime(once)
        assertEquals(once, twice)
    }

    @Test
    fun mimeFor_mapsSourceExtensionsToJavascript() {
        assertEquals("text/javascript", WebProjectPathHandler.mimeFor("jsx"))
        assertEquals("text/javascript", WebProjectPathHandler.mimeFor("tsx"))
        assertEquals("text/javascript", WebProjectPathHandler.mimeFor("ts"))
        assertEquals("text/javascript", WebProjectPathHandler.mimeFor("mjs"))
    }

    @Test
    fun mimeFor_mapsCommonAssetTypes() {
        assertEquals("text/css", WebProjectPathHandler.mimeFor("css"))
        assertEquals("application/json", WebProjectPathHandler.mimeFor("json"))
        assertEquals("image/png", WebProjectPathHandler.mimeFor("png"))
        assertEquals("image/svg+xml", WebProjectPathHandler.mimeFor("svg"))
        assertEquals("application/octet-stream", WebProjectPathHandler.mimeFor("unknownext"))
    }
}
