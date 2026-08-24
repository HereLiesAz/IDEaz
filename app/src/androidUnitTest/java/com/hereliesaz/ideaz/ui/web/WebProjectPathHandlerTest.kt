package com.hereliesaz.ideaz.ui.web

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for the pure HTML/MIME helpers in [WebProjectPathHandler], plus
 * `handle()`'s SPA history-API fallback (the rest of `handle()`'s request path
 * is covered manually on device - see the plan's verification section).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebProjectPathHandlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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

    // --- handle(): SPA history-API fallback ---

    private fun handlerFor(projectDir: File) = WebProjectPathHandler(
        ApplicationProvider.getApplicationContext(),
    ) { projectDir }

    @Test
    fun handle_fallsBackToIndexForAnExtensionlessClientSideRoute() {
        val project = tempFolder.newFolder("project")
        File(project, "index.html").writeText("<html><body>app shell</body></html>")

        // A client-side router (React Router, etc.) changes the WebView's URL
        // to routes like this that are never real files. A reload replays it
        // as a fresh request; without the fallback this 404s instead of
        // re-mounting the app so the router can resolve it.
        val response = handlerFor(project).handle("dashboard/settings")

        assertEquals(200, response?.statusCode)
        assertEquals("text/html", response?.mimeType)
        val body = response?.data?.readBytes()?.toString(Charsets.UTF_8)
        assertTrue("serves the SPA shell, not a 404", body?.contains("app shell") == true)
    }

    @Test
    fun handle_stillReturns404ForAGenuinelyMissingAsset() {
        val project = tempFolder.newFolder("project2")
        File(project, "index.html").writeText("<html><body>app shell</body></html>")

        // A path with a real extension that doesn't exist is a missing
        // asset, not a client-side route - it must still 404 rather than
        // silently serving HTML in its place.
        val response = handlerFor(project).handle("assets/logo.png")

        assertEquals(404, response?.statusCode)
    }

    @Test
    fun handle_404sAnExtensionlessRouteWhenProjectHasNoIndexToFallBackTo() {
        val project = tempFolder.newFolder("project3")
        // No index.html anywhere in this project: nothing to fall back to.

        val response = handlerFor(project).handle("dashboard/settings")

        assertEquals(404, response?.statusCode)
    }
}
