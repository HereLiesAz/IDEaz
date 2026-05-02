# Phase 1A — PWA Render Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the deprecated `file://` WebView loader with `WebViewAssetLoader`, add `ProjectType.PWA` detection, and add reload controls. After this phase, IDEaz can open a hand-written PWA from a local Git repo, render it correctly under a proper HTTPS origin, and reload it after a file edit.

**Architecture:** `WebViewAssetLoader` maps the virtual path `/files/` to `context.filesDir`, serving all local project content from `https://appassets.androidplatform.net/files/{appName}/`. `ProjectType.PWA` is added to the enum; `ProjectAnalyzer` detects it by the presence of `manifest.webmanifest` alongside `index.html`. Reload controls are driven by `StateDelegate.webReloadTrigger` (soft) and a new `StateDelegate.webHardReloadTrigger` (hard), exposed as nav-rail actions. This also lands the Phase-0 security debt: `allowFileAccessFromFileURLs` / `allowUniversalAccessFromFileURLs` are permanently removed and the dead `AccessibilityNodeInfo.recycle()` branch is deleted.

**Tech stack:** Kotlin/Compose, `androidx.webkit:webkit:1.14.0`, JUnit 4 + `TemporaryFolder` rule.

**Project root:** `C:\Users\azrie\OneDrive\Documents\GitHub\IDEaz\.claude\worktrees\priceless-tesla-d309a4`

**Run tests with:** `./gradlew :app:testDebugUnitTest --tests "com.hereliesaz.ideaz.*" --daemon`

**Full build:** `./gradlew :app:assembleDebug --daemon`

---

## Task 1: Add `ProjectType.PWA` and teach `ProjectAnalyzer` to detect it

**Context:** The current `ProjectType.WEB` is too coarse — it covers any project with an `index.html`. A PWA is specifically identified by `index.html` **plus** at least one of: `manifest.webmanifest`, a `manifest.json` whose content contains `"display"`, `service-worker.js`, or `sw.js`. `ProjectType.WEB` stays in the enum for generic web projects; `PWA` is a stricter subtype. All existing callers that handle `WEB` must also handle `PWA` (they're functionally identical for Phase 1A; Phase 1B will diverge them).

**Files:**
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/models/ProjectType.kt`
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/utils/ProjectAnalyzer.kt`
- Modify: `app/src/test/java/com/hereliesaz/ideaz/utils/ProjectAnalyzerTest.kt`

---

**Step 1: Write the failing tests**

Open `app/src/test/java/com/hereliesaz/ideaz/utils/ProjectAnalyzerTest.kt` and add these tests at the end of the `ProjectAnalyzerTest` class (before the closing `}`):

```kotlin
@Test
fun detectPwaProject_withManifestWebmanifest() {
    val projectDir = tempFolder.newFolder("pwa_manifest")
    File(projectDir, "index.html").createNewFile()
    File(projectDir, "manifest.webmanifest").createNewFile()

    val type = ProjectAnalyzer.detectProjectType(projectDir)
    assertEquals(ProjectType.PWA, type)
}

@Test
fun detectPwaProject_withServiceWorker() {
    val projectDir = tempFolder.newFolder("pwa_sw")
    File(projectDir, "index.html").createNewFile()
    File(projectDir, "service-worker.js").createNewFile()

    val type = ProjectAnalyzer.detectProjectType(projectDir)
    assertEquals(ProjectType.PWA, type)
}

@Test
fun detectPwaProject_withSwJs() {
    val projectDir = tempFolder.newFolder("pwa_sw_js")
    File(projectDir, "index.html").createNewFile()
    File(projectDir, "sw.js").createNewFile()

    val type = ProjectAnalyzer.detectProjectType(projectDir)
    assertEquals(ProjectType.PWA, type)
}

@Test
fun detectPwaProject_withManifestJsonContainingDisplay() {
    val projectDir = tempFolder.newFolder("pwa_manifest_json")
    File(projectDir, "index.html").createNewFile()
    File(projectDir, "manifest.json").writeText("""{"display": "standalone"}""")

    val type = ProjectAnalyzer.detectProjectType(projectDir)
    assertEquals(ProjectType.PWA, type)
}

@Test
fun detectWebProject_indexHtmlOnly_remainsWEB() {
    // index.html alone (no PWA markers) → still WEB, not PWA
    val projectDir = tempFolder.newFolder("web_only")
    File(projectDir, "index.html").createNewFile()

    val type = ProjectAnalyzer.detectProjectType(projectDir)
    assertEquals(ProjectType.WEB, type)
}

@Test
fun projectTypePwaFromString() {
    assertEquals(ProjectType.PWA, ProjectType.fromString("PWA"))
}
```

**Step 2: Run the tests to verify they fail**

```
./gradlew :app:testDebugUnitTest --tests "com.hereliesaz.ideaz.utils.ProjectAnalyzerTest" --daemon
```

Expected: FAIL — `ProjectType.PWA` doesn't exist yet, compilation error.

**Step 3: Add `PWA` to the `ProjectType` enum**

In `app/src/main/kotlin/com/hereliesaz/ideaz/models/ProjectType.kt`, replace the entire file content with:

```kotlin
package com.hereliesaz.ideaz.models

enum class ProjectType(val displayName: String) {
    ANDROID("Android"),
    WEB("Web"),
    PWA("PWA"),
    OTHER("Other"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(value: String?): ProjectType {
            return values().find { it.name == value } ?: UNKNOWN
        }
    }
}
```

**Step 4: Update `ProjectAnalyzer.detectProjectType` to return `PWA` when appropriate**

In `app/src/main/kotlin/com/hereliesaz/ideaz/utils/ProjectAnalyzer.kt`, replace the `detectProjectType` function body with:

```kotlin
fun detectProjectType(projectDir: File): ProjectType {
    if (!projectDir.exists()) return ProjectType.OTHER

    // PWA: must have index.html PLUS at least one PWA marker
    val hasIndex = File(projectDir, "index.html").exists()
    if (hasIndex) {
        val isPwa = File(projectDir, "manifest.webmanifest").exists() ||
            File(projectDir, "service-worker.js").exists() ||
            File(projectDir, "sw.js").exists() ||
            run {
                val manifestJson = File(projectDir, "manifest.json")
                manifestJson.exists() && manifestJson.readText().contains("\"display\"")
            }
        return if (isPwa) ProjectType.PWA else ProjectType.WEB
    }

    // Check for Android
    if (File(projectDir, "build.gradle.kts").exists() ||
        File(projectDir, "build.gradle").exists() ||
        File(projectDir, "app/build.gradle.kts").exists() ||
        File(projectDir, "app/build.gradle").exists()) {
        return ProjectType.ANDROID
    }

    return ProjectType.OTHER
}
```

**Step 5: Run the tests to verify they pass**

```
./gradlew :app:testDebugUnitTest --tests "com.hereliesaz.ideaz.utils.ProjectAnalyzerTest" --daemon
```

Expected: all `ProjectAnalyzerTest` tests pass.

**Step 6: Verify the full build still compiles**

The `ProjectType.PWA` addition may require updating `when` exhaustive expressions. Search for compilation errors by running:

```
./gradlew :app:assembleDebug --daemon
```

If you get "when expression must be exhaustive" errors, add `ProjectType.PWA -> ProjectType.WEB` (or the appropriate handling) to whichever `when` block is flagged. For Phase 1A, `PWA` and `WEB` are handled identically everywhere. Fix all compile errors before committing.

**Step 7: Commit**

```bash
git add app/src/main/kotlin/com/hereliesaz/ideaz/models/ProjectType.kt
git add app/src/main/kotlin/com/hereliesaz/ideaz/utils/ProjectAnalyzer.kt
git add app/src/test/java/com/hereliesaz/ideaz/utils/ProjectAnalyzerTest.kt
git commit -m "feat: add ProjectType.PWA with manifest.webmanifest detection"
```

---

## Task 2: Add `androidx.webkit` dependency

**Context:** `WebViewAssetLoader` is in `androidx.webkit`. This task adds it to the version catalog and build file so it's available for Task 3.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

---

**Step 1: Add version + library entry to `gradle/libs.versions.toml`**

In the `[versions]` block, add:
```toml
webkit = "1.14.0"
```

In the `[libraries]` block, add:
```toml
androidx-webkit = { group = "androidx.webkit", name = "webkit", version.ref = "webkit" }
```

**Step 2: Add dependency to `app/build.gradle.kts`**

In the `dependencies { }` block, after the `implementation(libs.androidx.appcompat)` line, add:
```kotlin
implementation(libs.androidx.webkit)
```

**Step 3: Verify build**

```
./gradlew :app:assembleDebug --daemon
```

Expected: `BUILD SUCCESSFUL`. If Gradle complains about a conflict, check if `webkit` is already pulled in transitively — if so, adding it explicitly is still fine.

**Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add androidx.webkit dependency for WebViewAssetLoader"
```

---

## Task 3: Create `WebProjectUrlUtils` (URL generation helper)

**Context:** The asset-loader URL for a local project is `https://appassets.androidplatform.net/files/{appName}/index.html`. This utility centralises that formula so `MainViewModel`, `BuildDelegate`, and any tests all use the same constant. It's pure string logic — no Android deps — so it's fully unit-testable on the JVM.

**Files:**
- Create: `app/src/main/kotlin/com/hereliesaz/ideaz/ui/web/WebProjectUrlUtils.kt`
- Create: `app/src/test/java/com/hereliesaz/ideaz/ui/web/WebProjectUrlUtilsTest.kt`

---

**Step 1: Write the failing tests first**

Create `app/src/test/java/com/hereliesaz/ideaz/ui/web/WebProjectUrlUtilsTest.kt`:

```kotlin
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
```

**Step 2: Run the tests to verify they fail**

```
./gradlew :app:testDebugUnitTest --tests "com.hereliesaz.ideaz.ui.web.WebProjectUrlUtilsTest" --daemon
```

Expected: FAIL — class doesn't exist yet.

**Step 3: Write the implementation**

Create `app/src/main/kotlin/com/hereliesaz/ideaz/ui/web/WebProjectUrlUtils.kt`:

```kotlin
package com.hereliesaz.ideaz.ui.web

import java.io.File

/**
 * URL helpers for [WebProjectHost]'s WebViewAssetLoader origin.
 *
 * All local PWA/Web project content is served from the virtual HTTPS origin
 * `https://appassets.androidplatform.net/files/` which maps to [Context.filesDir].
 * This keeps the WebView under a consistent same-origin policy and eliminates the
 * need for the deprecated `allowFileAccessFromFileURLs` / `allowUniversalAccessFromFileURLs`
 * settings.
 */
object WebProjectUrlUtils {

    /** The virtual HTTPS domain used by [WebViewAssetLoader]. */
    const val ASSET_DOMAIN = "appassets.androidplatform.net"

    /** Base URL prefix; all local project paths are appended after this. */
    const val ASSET_BASE_URL = "https://$ASSET_DOMAIN/files/"

    /**
     * Returns the canonical WebViewAssetLoader URL for a project's `index.html`.
     *
     * @param appName  The project folder name inside [filesDir].
     * @param filesDir The app's `Context.filesDir`.
     */
    fun localProjectUrl(appName: String, filesDir: File): String =
        "${ASSET_BASE_URL}${appName}/index.html"

    /**
     * Converts an absolute file path that lives inside [filesDir] to its
     * corresponding WebViewAssetLoader URL.
     *
     * Returns `null` if [absolutePath] is not under [filesDir].
     *
     * Example:
     *   absolutePath = "/data/user/0/.../files/myapp/index.html"
     *   filesDir     = "/data/user/0/.../files"
     *   →            = "https://appassets.androidplatform.net/files/myapp/index.html"
     */
    fun toAssetUrl(absolutePath: String, filesDir: File): String? {
        val prefix = filesDir.absolutePath.trimEnd('/') + "/"
        if (!absolutePath.startsWith(prefix)) return null
        val relative = absolutePath.removePrefix(prefix)
        return "$ASSET_BASE_URL$relative"
    }

    /** Returns `true` if this URL was produced by [toAssetUrl] or [localProjectUrl]. */
    fun isAssetUrl(url: String): Boolean = url.startsWith(ASSET_BASE_URL)
}
```

**Step 4: Run tests to verify they pass**

```
./gradlew :app:testDebugUnitTest --tests "com.hereliesaz.ideaz.ui.web.WebProjectUrlUtilsTest" --daemon
```

Expected: all 7 tests pass.

**Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hereliesaz/ideaz/ui/web/WebProjectUrlUtils.kt
git add app/src/test/java/com/hereliesaz/ideaz/ui/web/WebProjectUrlUtilsTest.kt
git commit -m "feat: add WebProjectUrlUtils for asset-loader URL generation"
```

---

## Task 4: Rewrite `WebProjectHost` with `WebViewAssetLoader`

**Context:** This task lands the core security fix from `docs/plans/phase-0-followups.md §2`. Replace the `file://`-based loader with `WebViewAssetLoader`. The WebView's `WebViewClient.shouldInterceptRequest` intercepts all requests to `appassets.androidplatform.net` and routes them to files inside `context.filesDir`. The deprecated `allowFileAccessFromFileURLs` and `allowUniversalAccessFromFileURLs` settings are removed permanently. Reload is now driven by `reloadTrigger` and `hardReloadTrigger` parameters (both default to `0L` so existing call sites compile without changes — you will wire them up in Task 6).

**Files:**
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/ui/web/WebProjectHost.kt`

**No unit tests for this task** — WebView rendering is hand-tested (per the design doc, "WebView rendering correctness — hand-test only").

---

**Step 1: Replace the entire contents of `WebProjectHost.kt`**

```kotlin
package com.hereliesaz.ideaz.ui.web

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.InternalStoragePathHandler
import com.hereliesaz.ideaz.models.ACTION_AI_LOG
import com.hereliesaz.ideaz.models.EXTRA_MESSAGE

class IdeazJsInterface(private val context: Context) {
    @JavascriptInterface
    fun onInspectResult(resourceId: String) {
        val intent = Intent("com.hereliesaz.ideaz.PROMPT_SUBMITTED_NODE").apply {
            putExtra("RESOURCE_ID", resourceId)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}

/**
 * Composable WebView host for PWA and Web projects.
 *
 * Content is served via [WebViewAssetLoader] from the virtual origin
 * `https://appassets.androidplatform.net/files/` mapped to [Context.filesDir].
 * This replaces the deprecated `allowFileAccessFromFileURLs` /
 * `allowUniversalAccessFromFileURLs` approach and gives the WebView a proper
 * same-origin policy and service-worker support.
 *
 * @param url            The URL to load. Local projects: use [WebProjectUrlUtils.localProjectUrl].
 *                       Remote URLs (e.g. GitHub Pages) are passed through unchanged.
 * @param reloadTrigger  Soft-reload signal. When this Long changes (and is > 0), the
 *                       WebView reloads the current page without clearing its cache.
 *                       Driven by [StateDelegate.webReloadTrigger].
 * @param hardReloadTrigger  Hard-reload signal. When this Long changes (and is > 0),
 *                           the WebView clears its disk/memory cache then reloads.
 *                           Driven by [StateDelegate.webHardReloadTrigger].
 */
@Composable
fun WebProjectHost(
    url: String,
    reloadTrigger: Long = 0L,
    hardReloadTrigger: Long = 0L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // AssetLoader maps /files/ → context.filesDir so that all local project
    // content is served from https://appassets.androidplatform.net/files/{appName}/.
    // Requests to any other origin are not intercepted and proceed normally.
    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .setDomain(WebProjectUrlUtils.ASSET_DOMAIN)
            .addPathHandler(
                "/files/",
                InternalStoragePathHandler(context, context.filesDir)
            )
            .build()
    }

    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // allowFileAccess is false (default). We do not need filesystem access;
                // all local content is served via WebViewAssetLoader.
                allowFileAccess = false
                // Disabled: prevents access to Android content providers.
                allowContentAccess = false
                // NOTE: allowFileAccessFromFileURLs and allowUniversalAccessFromFileURLs
                // have been intentionally removed. Those deprecated flags enabled a
                // cross-origin file-read attack surface. WebViewAssetLoader supersedes them.
                safeBrowsingEnabled = true
            }

            addJavascriptInterface(IdeazJsInterface(context), "Ideaz")

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        val msg = "[WEB] ${it.message()} (${it.sourceId()}:${it.lineNumber()})"
                        val intent = Intent(ACTION_AI_LOG).apply {
                            putExtra(EXTRA_MESSAGE, msg)
                            setPackage(context.packageName)
                        }
                        context.sendBroadcast(intent)
                    }
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                // Route asset-loader requests; return null for all other origins so
                // the WebView handles them normally (e.g. GitHub Pages URLs).
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    val msg = "[WEB] Error loading ${request?.url}: ${error?.description}"
                    Toast.makeText(context, "Error: ${error?.description}", Toast.LENGTH_SHORT).show()
                    val intent = Intent(ACTION_AI_LOG).apply {
                        putExtra(EXTRA_MESSAGE, msg)
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)
                }
            }
        }
    }

    // Soft reload: triggered by file-system changes (ProjectFileObserver).
    // Skip the initial composition (reloadTrigger == 0L).
    LaunchedEffect(reloadTrigger) {
        if (reloadTrigger > 0L) {
            webView.reload()
        }
    }

    // Hard reload: clears disk + memory cache, then reloads.
    // Skip the initial composition (hardReloadTrigger == 0L).
    LaunchedEffect(hardReloadTrigger) {
        if (hardReloadTrigger > 0L) {
            webView.clearCache(true)
            webView.reload()
        }
    }

    // INSPECT_WEB broadcast: Phase 1B tap-to-select plumbing (already present).
    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.hereliesaz.ideaz.INSPECT_WEB") {
                    val x = intent.getFloatExtra("X", 0f)
                    val y = intent.getFloatExtra("Y", 0f)
                    val density = context?.resources?.displayMetrics?.density ?: 1f
                    val webX = x / density
                    val webY = y / density

                    val js = """
                        (function() {
                            var el = document.elementFromPoint($webX, $webY);
                            var source = null;
                            while (el) {
                                var label = el.getAttribute('aria-label');
                                if (label && label.startsWith('__source:')) {
                                    source = label;
                                    break;
                                }
                                el = el.parentElement;
                            }
                            if (source) {
                                Ideaz.onInspectResult(source);
                            }
                        })();
                    """.trimIndent()
                    webView.evaluateJavascript(js, null)
                }
            }
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter("com.hereliesaz.ideaz.INSPECT_WEB")
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { webView },
        update = { view ->
            if (view.url != url) {
                view.loadUrl(url)
            }
        }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.destroy()
        }
    }
}
```

**Step 2: Build to verify it compiles**

```
./gradlew :app:assembleDebug --daemon
```

Expected: `BUILD SUCCESSFUL`. Fix any import errors before continuing.

**Step 3: Commit**

```bash
git add app/src/main/kotlin/com/hereliesaz/ideaz/ui/web/WebProjectHost.kt
git commit -m "feat: replace file:// loader with WebViewAssetLoader in WebProjectHost"
```

---

## Task 5: Switch URL generation to `WebProjectUrlUtils`

**Context:** `WebProjectHost` now only handles asset-loader URLs for local content. `MainViewModel.launchTargetApp` and `BuildDelegate.handleSuccess` still generate `file://` URLs — this task updates them to use `WebProjectUrlUtils`. After this commit, local project loads work end-to-end with the new origin. The `ProjectType.PWA` case is also wired through all the paths that currently guard on `ProjectType.WEB`.

**Files:**
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainViewModel.kt`
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/ui/delegates/BuildDelegate.kt`
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/ui/IdeNavRail.kt`

---

**Step 1: Update `MainViewModel.kt`**

**Change 1 — web build success callback** (around line 140–151 in `MainViewModel`):

Find this block:
```kotlin
{ path ->
    // Web Build Success Callback
    stateDelegate.setCurrentWebUrl("file://$path")
    stateDelegate.setTargetAppVisible(true) // Switch to "App View"
    ...
```

Replace the `stateDelegate.setCurrentWebUrl("file://$path")` line with:
```kotlin
val filesDir = getApplication<Application>().filesDir
val assetUrl = WebProjectUrlUtils.toAssetUrl(path, filesDir) ?: "file://$path"
stateDelegate.setCurrentWebUrl(assetUrl)
```

Add the import at the top of the file:
```kotlin
import com.hereliesaz.ideaz.ui.web.WebProjectUrlUtils
```

**Change 2 — `launchTargetApp` web branch** (around line 867–882):

Find the block:
```kotlin
if (projectType == ProjectType.WEB) {
    val projectDir = settingsViewModel.getProjectPath(appName)
    if (stateDelegate.currentWebUrl.value == null) {
        // For Web Projects with Kotlin/JS, we rely on the extracted www/index.html
        // which references the compiled app.js.
        val wwwDir = File(c.filesDir, "www")
        val indexFile = File(wwwDir, "index.html")
        // Fallback to project source index.html if www/index.html is missing
        val fileToLoad = if (indexFile.exists()) indexFile else File(projectDir, "index.html")

        if (fileToLoad.exists()) {
            stateDelegate.setCurrentWebUrl("file://${fileToLoad.absolutePath}")
        }
    }
    startFileObservation(projectDir)
    stateDelegate.setTargetAppVisible(true)
```

Replace with:
```kotlin
if (projectType == ProjectType.WEB || projectType == ProjectType.PWA) {
    val projectDir = settingsViewModel.getProjectPath(appName)
    if (stateDelegate.currentWebUrl.value == null) {
        // Prefer the asset-loader URL (same origin, service-worker safe).
        // Fall back gracefully if index.html is missing.
        val indexFile = File(projectDir, "index.html")
        if (indexFile.exists()) {
            stateDelegate.setCurrentWebUrl(
                WebProjectUrlUtils.localProjectUrl(appName, c.filesDir)
            )
        }
    }
    startFileObservation(projectDir)
    stateDelegate.setTargetAppVisible(true)
```

**Change 3 — `deployWebProject`** (around line 321):

Find:
```kotlin
val projectType = ProjectType.fromString(projectTypeStr)
if (projectType != ProjectType.WEB) return
```

Replace with:
```kotlin
val projectType = ProjectType.fromString(projectTypeStr)
if (projectType != ProjectType.WEB && projectType != ProjectType.PWA) return
```

**Change 4 — `saveAndInitialize`** (around line 487):

Find:
```kotlin
if (type == ProjectType.ANDROID) {
    startArtifactPolling(user, appName)
}
```

No change needed here (artifact polling is Android-only; WEB/PWA already skip it).

**Step 2: Update `BuildDelegate.kt`**

Find the `startBuild` web branch (around line 184):
```kotlin
if (type == ProjectType.WEB) {
```

Replace with:
```kotlin
if (type == ProjectType.WEB || type == ProjectType.PWA) {
```

Also update `handleSuccess` (around line 119):
```kotlin
if (type == ProjectType.WEB) {
```

Replace with:
```kotlin
if (type == ProjectType.WEB || type == ProjectType.PWA) {
```

**Step 3: Update `IdeNavRail.kt`**

The "Deploy" button is gated on `projectType == ProjectType.WEB.name` (line 89). Also add `PWA`:

Find:
```kotlin
if (projectType == ProjectType.WEB.name) {
    azRailSubItem(
        id = "deploy",
        ...
    )
}
```

Replace with:
```kotlin
if (projectType == ProjectType.WEB.name || projectType == ProjectType.PWA.name) {
    azRailSubItem(
        id = "deploy",
        ...
    )
}
```

**Step 4: Build and run all tests**

```
./gradlew :app:assembleDebug --daemon
./gradlew :app:testDebugUnitTest --daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

**Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainViewModel.kt
git add app/src/main/kotlin/com/hereliesaz/ideaz/ui/delegates/BuildDelegate.kt
git add app/src/main/kotlin/com/hereliesaz/ideaz/ui/IdeNavRail.kt
git commit -m "feat: switch web/PWA URL generation to WebProjectUrlUtils (asset-loader origin)"
```

---

## Task 6: Add reload controls

**Context:** `StateDelegate.webReloadTrigger` already drives the `ProjectFileObserver` soft reload, but nothing in `WebProjectHost` observes it yet — that gap was wired in Task 4. Now add a `webHardReloadTrigger`, expose both on `MainViewModel`, and surface two buttons in `IdeNavRail` (visible only when a PWA/Web project is active).

**Files:**
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/ui/delegates/StateDelegate.kt`
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainViewModel.kt`
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainScreen.kt`
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/ui/IdeNavRail.kt`

---

**Step 1: Add `webHardReloadTrigger` to `StateDelegate`**

In `StateDelegate.kt`, after the `webReloadTrigger` block (around line 186–192), add:

```kotlin
private val _webHardReloadTrigger = MutableStateFlow(0L)
/**
 * Signal to force a hard reload of the WebView (clears cache first).
 * Observers should react when the value changes.
 */
val webHardReloadTrigger = _webHardReloadTrigger.asStateFlow()
```

And add the mutator below the existing `triggerWebReload()`:

```kotlin
/** Clears the WebView cache and triggers a full reload. */
fun triggerWebHardReload() { _webHardReloadTrigger.value = System.currentTimeMillis() }
```

**Step 2: Expose both triggers on `MainViewModel`**

In `MainViewModel.kt`, in the "Public State Exposure (Delegated)" section, after `val currentWebUrl = stateDelegate.currentWebUrl`:

```kotlin
val webReloadTrigger = stateDelegate.webReloadTrigger
val webHardReloadTrigger = stateDelegate.webHardReloadTrigger
```

And add two public trigger functions anywhere in the proxy methods section:

```kotlin
/** Triggers a soft reload of the WebView (no cache bust). */
fun triggerWebReload() = stateDelegate.triggerWebReload()

/** Clears the WebView cache and triggers a hard reload. */
fun triggerWebHardReload() = stateDelegate.triggerWebHardReload()
```

**Step 3: Pass reload triggers from `MainScreen` to `WebProjectHost`**

In `MainScreen.kt`, find where `currentWebUrl` is collected:

```kotlin
val currentWebUrl by viewModel.currentWebUrl.collectAsState()
```

Add these two lines immediately after:

```kotlin
val webReloadTrigger by viewModel.webReloadTrigger.collectAsState()
val webHardReloadTrigger by viewModel.webHardReloadTrigger.collectAsState()
```

Then find the `WebProjectHost(...)` call and update it:

```kotlin
WebProjectHost(
    url = webUrl,
    reloadTrigger = webReloadTrigger,
    hardReloadTrigger = webHardReloadTrigger,
    modifier = Modifier.fillMaxSize()
)
```

**Step 4: Add Reload / Hard Reload buttons to `IdeNavRail`**

In `IdeNavRail.kt`, directly after the `azRailSubItem` for `"build"` (around line 87), add the two reload items gated on PWA/Web project type:

```kotlin
if (projectType == ProjectType.WEB.name || projectType == ProjectType.PWA.name) {
    azRailSubItem(
        id = "reload",
        hostId = "main",
        text = "Reload",
        onClick = {
            handleActionClick {
                viewModel.triggerWebReload()
            }
        }
    )
    azRailSubItem(
        id = "hard_reload",
        hostId = "main",
        text = "Hard Reload",
        onClick = {
            handleActionClick {
                viewModel.triggerWebHardReload()
            }
        }
    )
}
```

**Step 5: Build and run all tests**

```
./gradlew :app:assembleDebug --daemon
./gradlew :app:testDebugUnitTest --daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

**Step 6: Commit**

```bash
git add app/src/main/kotlin/com/hereliesaz/ideaz/ui/delegates/StateDelegate.kt
git add app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainViewModel.kt
git add app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainScreen.kt
git add app/src/main/kotlin/com/hereliesaz/ideaz/ui/IdeNavRail.kt
git commit -m "feat: add soft/hard reload controls for web/PWA projects"
```

---

## Task 7: Dead-code cleanup

**Context:** Two pieces of dead code flagged in `docs/plans/phase-0-followups.md` are unblocked now that the WebViewAssetLoader migration is done. Neither touches any logic — both are pure deletions.

**Files:**
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/services/IdeazAccessibilityService.kt`

---

**Step 1: Delete the `recycleIfNeeded` method in `IdeazAccessibilityService`**

In `IdeazAccessibilityService.kt`, find and **delete** this entire private method (lines 145–155 approximately):

```kotlin
/**
 * AccessibilityNodeInfo.recycle() is a no-op on API 33+ (the platform
 * pools nodes itself) and emits a deprecation warning. Gate the call so
 * we only do it on 30..32 where it still matters.
 */
private fun recycleIfNeeded(node: AccessibilityNodeInfo) {
    if (Build.VERSION.SDK_INT < 33) {
        @Suppress("DEPRECATION")
        node.recycle()
    }
}
```

Also search the file for any call sites of `recycleIfNeeded(` — if any exist, delete those call sites too (the node pooling is automatic since minSdk 30).

Check for any unused imports after the deletion (e.g. `android.os.Build`) and remove them.

**Step 2: Build and run tests**

```
./gradlew :app:assembleDebug --daemon
./gradlew :app:testDebugUnitTest --daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

**Step 3: Commit**

```bash
git add app/src/main/kotlin/com/hereliesaz/ideaz/services/IdeazAccessibilityService.kt
git commit -m "cleanup: remove dead recycleIfNeeded() branch (minSdk 30, API 33+ auto-managed)"
```

---

## Task 8: Regenerate lint baseline and final verification

**Context:** The lint baseline from Phase 0 will now be stale: it contains entries for files we changed (WebProjectHost, IdeNavRail, etc.) and may be missing entries for new suppressions. Regenerate it so the baseline is accurate before the branch is merged.

**Files:**
- Modify: `app/lint-baseline.xml`

---

**Step 1: Delete the existing baseline**

```bash
rm app/lint-baseline.xml
```

**Step 2: Regenerate**

```bash
./gradlew :app:lintDebug --daemon
```

The lint task will write a new `app/lint-baseline.xml`. The first run will fail (lint exits non-zero when it writes a new baseline) — that is expected.

**Step 3: Run lint again to verify baseline is accepted**

```bash
./gradlew :app:lintDebug --daemon
```

Expected: `BUILD SUCCESSFUL` on the second run.

**Step 4: Run the full test suite one final time**

```
./gradlew :app:testDebugUnitTest --daemon
```

Expected: all tests pass.

**Step 5: Commit**

```bash
git add app/lint-baseline.xml
git commit -m "chore: regenerate lint baseline after Phase 1A changes"
```

---

## Task 9: Smoke-test checklist

**Context:** Write the hand-test checklist for Phase 1A so the milestone can be verified manually. This is a doc-only commit.

**Files:**
- Create: `docs/plans/phase-1a-smoke-test.md`

---

**Step 1: Create the smoke-test document**

Create `docs/plans/phase-1a-smoke-test.md` with:

```markdown
# Phase 1A — Smoke Test Checklist

**Milestone:** Open a hand-written PWA from a local Git repo, see it render
correctly in IDEaz, hit reload after manual file edit, see the change.

**Prerequisite:** A minimal hand-written PWA in a local Git repo on the device:
```
myapp/
  index.html         ← <h1>Hello PWA</h1>
  manifest.webmanifest ← {"name":"Test","display":"standalone","start_url":"/"}
  sw.js              ← empty service worker (just `self.addEventListener(...)`)
```

---

## Setup

- [ ] Clone or copy the test PWA into IDEaz's internal storage (via Load / Import)
- [ ] Verify IDEaz detects it as `PWA` (check Project screen shows type `PWA`)

## Render

- [ ] Tap **IDEaz** rail → **Build** — confirm WebView shows `Hello PWA`
- [ ] Confirm no `file://` errors in the Console tab
- [ ] Confirm console log shows `[WEB]` messages for any JS `console.log(...)` calls

## Correct origin

- [ ] Open Console tab; type in prompt: `window.location.href` — confirm value
  starts with `https://appassets.androidplatform.net/files/`
- [ ] Confirm `file://` is **not** present in any console output

## Soft reload

- [ ] Edit `index.html` directly via Files tab: change `Hello PWA` → `Hello Reload`
- [ ] Tap **Reload** in the nav rail
- [ ] WebView now shows `Hello Reload` without restarting the app

## Hard reload

- [ ] Edit `index.html` again: change back to `Hello PWA`
- [ ] Tap **Hard Reload** in the nav rail
- [ ] WebView now shows `Hello PWA`; confirm no stale cache content

## File observer auto-reload

- [ ] Edit `index.html` via Files tab; save (close the editor)
- [ ] WebView reloads automatically within a few seconds (ProjectFileObserver)

## Security verification

- [ ] Open a prompt and execute JS: `fetch('file:///etc/passwd')`
- [ ] Confirm fetch fails (cross-origin blocked); no file content appears

---

**Phase 1A complete when all checkboxes pass.**
```

**Step 2: Commit**

```bash
git add docs/plans/phase-1a-smoke-test.md
git commit -m "docs: add Phase 1A smoke-test checklist"
```

---

## Summary

| Task | What | New tests |
|------|------|-----------|
| 1 | `ProjectType.PWA` + `ProjectAnalyzer` PWA detection | 6 unit tests |
| 2 | `androidx.webkit` dependency | — |
| 3 | `WebProjectUrlUtils` URL helpers | 7 unit tests |
| 4 | `WebProjectHost` rewrite with `WebViewAssetLoader` | — (hand-test) |
| 5 | Switch URL generation to asset-loader origin | — |
| 6 | Reload controls (soft + hard) in nav rail | — |
| 7 | Delete dead `recycleIfNeeded()` branch | — |
| 8 | Regenerate lint baseline | — |
| 9 | Smoke-test checklist doc | — |

**After Phase 1A, Phase 1B (Bridge) can begin:** `ideaz-bridge.js` injection, element-tap protocol, `WebViewBridge.kt`.
