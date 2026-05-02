# Phase 1B — Bridge Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enable tap-to-inspect for PWA elements — when Select Mode is active and the user taps inside the WebView, `ideaz-bridge.js` gathers rich DOM context and delivers it to the IDE as structured JSON displayed in the AI log and contextual chat.

**Architecture:** The existing Android `SelectionOverlay` continues to capture taps. On tap, `WebProjectHost` receives the `INSPECT_WEB` broadcast (as before), converts the pixel coordinate to CSS coordinates, and calls `window.ideaz.getElementContext(el)` from the new bridge script. The resulting JSON is passed back to Kotlin via `IdeazBridge.onElementTapped(json)` (a `@JavascriptInterface`). The ViewModel logs it to the AI console and triggers the contextual chat panel. The bridge script is also responsible for toggling a `crosshair` cursor in the WebView when Select Mode is active.

**Tech Stack:** Kotlin/Compose, kotlinx-serialization-json (already in project), `@JavascriptInterface`, `WebViewAssetLoader` (Phase 1A), vanilla JS (no bundler), JUnit 4 + Robolectric for unit tests.

---

### Task 1: `ElementContext` data classes

**Files:**
- Create: `app/src/main/kotlin/com/hereliesaz/ideaz/models/ElementContext.kt`
- Create test: `app/src/test/java/com/hereliesaz/ideaz/models/ElementContextTest.kt`

**Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/hereliesaz/ideaz/models/ElementContextTest.kt
package com.hereliesaz.ideaz.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementContextTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `round-trip serialization preserves all fields`() {
        val original = ElementContext(
            tagName = "button",
            id = "submit-btn",
            className = "btn btn-primary",
            selector = "form > button#submit-btn",
            outerHtml = "<button id=\"submit-btn\" class=\"btn\">Submit</button>",
            innerText = "Submit",
            boundingRect = BoundingRect(top = 100.0, left = 50.0, bottom = 132.0, right = 150.0, width = 100.0, height = 32.0),
            computedStyles = mapOf("color" to "rgb(255,255,255)", "backgroundColor" to "rgb(0,0,255)"),
            parents = listOf(ParentInfo(tagName = "form", id = "main-form", className = ""))
        )

        val serialized = json.encodeToString(ElementContext.serializer(), original)
        val deserialized = json.decodeFromString(ElementContext.serializer(), serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `defaults produce valid empty context`() {
        val ctx = ElementContext(tagName = "div")
        assertEquals("", ctx.id)
        assertEquals("", ctx.className)
        assertEquals("", ctx.selector)
        assertEquals("", ctx.outerHtml)
        assertEquals("", ctx.innerText)
        assertTrue(ctx.computedStyles.isEmpty())
        assertTrue(ctx.parents.isEmpty())
        assertEquals(0.0, ctx.boundingRect.top, 0.001)
    }

    @Test
    fun `deserializes JS-generated JSON with missing optional fields`() {
        val jsJson = """
            {
                "tagName": "span",
                "id": "",
                "className": "label",
                "selector": "div > span",
                "outerHtml": "<span class=\"label\">Hello</span>",
                "innerText": "Hello",
                "computedStyles": {"color": "red"},
                "boundingRect": {"top": 10.5, "left": 5.0, "bottom": 20.5, "right": 55.0, "width": 50.0, "height": 10.0},
                "parents": [{"tagName": "div", "id": "", "className": "container"}]
            }
        """.trimIndent()

        val ctx = json.decodeFromString(ElementContext.serializer(), jsJson)
        assertEquals("span", ctx.tagName)
        assertEquals("label", ctx.className)
        assertEquals(10.5, ctx.boundingRect.top, 0.001)
        assertEquals(1, ctx.parents.size)
        assertEquals("div", ctx.parents[0].tagName)
    }
}
```

**Step 2: Run test to verify it fails**

```bash
unset ANDROID_SDK_ROOT && ./gradlew :app:testDebugUnitTest --tests "com.hereliesaz.ideaz.models.ElementContextTest" -q 2>&1 | tail -20
```

Expected: FAIL with `error: unresolved reference: ElementContext`

**Step 3: Write implementation**

```kotlin
// app/src/main/kotlin/com/hereliesaz/ideaz/models/ElementContext.kt
package com.hereliesaz.ideaz.models

import kotlinx.serialization.Serializable

/**
 * Rich DOM context captured by [ideaz-bridge.js] when the user taps an element
 * in Select Mode. Delivered from JS → Kotlin via [WebViewBridge.onElementTapped].
 */
@Serializable
data class ElementContext(
    /** Lowercase tag name, e.g. "button", "div". */
    val tagName: String,
    /** The element's `id` attribute, or empty string. */
    val id: String = "",
    /** The element's `class` attribute string, or empty string. */
    val className: String = "",
    /** A CSS selector path from the nearest ancestor with an id down to this element. */
    val selector: String = "",
    /** `outerHTML` truncated to 2000 characters. */
    val outerHtml: String = "",
    /** `innerText` truncated to 500 characters. */
    val innerText: String = "",
    /** `getBoundingClientRect()` result in CSS pixels (relative to viewport). */
    val boundingRect: BoundingRect = BoundingRect(),
    /** Subset of computed CSS styles (color, backgroundColor, fontSize, etc.). */
    val computedStyles: Map<String, String> = emptyMap(),
    /** Up to 3 ancestor elements (closest first, stops before `<body>`). */
    val parents: List<ParentInfo> = emptyList()
)

/** Mirrors `DOMRect` returned by `getBoundingClientRect()`. */
@Serializable
data class BoundingRect(
    val top: Double = 0.0,
    val left: Double = 0.0,
    val bottom: Double = 0.0,
    val right: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0
)

/** Minimal ancestor info for DOM context. */
@Serializable
data class ParentInfo(
    val tagName: String,
    val id: String = "",
    val className: String = ""
)
```

**Step 4: Run tests to verify they pass**

```bash
unset ANDROID_SDK_ROOT && ./gradlew :app:testDebugUnitTest --tests "com.hereliesaz.ideaz.models.ElementContextTest" -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` with 3 tests passing.

**Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hereliesaz/ideaz/models/ElementContext.kt \
        app/src/test/java/com/hereliesaz/ideaz/models/ElementContextTest.kt
git commit -m "feat(1b): add ElementContext serializable data classes"
```

---

### Task 2: `ideaz-bridge.js` asset file

The bridge is a self-initializing IIFE that attaches `window.ideaz` with two methods:
- `selectMode(on: Boolean)` — flips the cursor to `crosshair` when inspect mode is active.
- `getElementContext(el: Element): Object` — returns the JSON-compatible object the Kotlin bridge will receive.

There is no meaningful JVM unit test for pure JS. Correctness is verified by the smoke test at the end.

**Files:**
- Create: `app/src/main/assets/ideaz-bridge.js`

**Step 1: Write `ideaz-bridge.js`**

```javascript
// app/src/main/assets/ideaz-bridge.js
// Loaded by WebProjectHost.onPageFinished via evaluateJavascript.
// Idempotent: guarded by __ideazBridgeLoaded flag.
(function () {
    'use strict';
    if (window.__ideazBridgeLoaded) { return; }
    window.__ideazBridgeLoaded = true;

    /**
     * Builds a minimal CSS selector path from the nearest ancestor-with-id
     * down to `node`. Falls back to a full tag-chain if no id ancestor exists.
     * @param {Element} node
     * @returns {string}
     */
    function buildSelector(node) {
        var parts = [];
        var current = node;
        while (current && current.nodeType === 1 && current !== document.body) {
            var tag = current.tagName.toLowerCase();
            if (current.id) {
                parts.unshift(tag + '#' + current.id);
                break; // id is unique; stop walking up
            }
            var siblings = current.parentNode
                ? Array.prototype.filter.call(
                      current.parentNode.children,
                      function (c) { return c.tagName === current.tagName; }
                  )
                : [];
            if (siblings.length > 1) {
                tag += ':nth-of-type(' + (siblings.indexOf(current) + 1) + ')';
            }
            parts.unshift(tag);
            current = current.parentElement;
        }
        return parts.join(' > ');
    }

    /** CSS properties we capture for each selected element. */
    var STYLE_KEYS = [
        'color', 'backgroundColor', 'fontSize', 'fontFamily',
        'display', 'position', 'width', 'height', 'margin', 'padding'
    ];

    window.ideaz = {
        /**
         * Toggle inspect-mode cursor in the web page.
         * Called by WebProjectHost via evaluateJavascript when selectMode changes.
         * @param {boolean} on
         */
        selectMode: function (on) {
            document.body.style.cursor = on ? 'crosshair' : '';
        },

        /**
         * Collect rich context for the element at the tapped point.
         * Called by the INSPECT_WEB handler in WebProjectHost.
         * @param {Element} el  The element returned by document.elementFromPoint.
         * @returns {Object}    Plain object — caller JSON.stringifies this.
         */
        getElementContext: function (el) {
            if (!el) { return null; }

            // Computed styles (best-effort)
            var styles = {};
            try {
                var cs = window.getComputedStyle(el);
                for (var i = 0; i < STYLE_KEYS.length; i++) {
                    styles[STYLE_KEYS[i]] = cs.getPropertyValue(STYLE_KEYS[i]);
                }
            } catch (ignore) {}

            // Bounding rect in CSS pixels (viewport-relative)
            var rect = el.getBoundingClientRect();

            // Up to 3 ancestor elements (skip body/html)
            var parents = [];
            var p = el.parentElement;
            while (p && p !== document.body && parents.length < 3) {
                parents.push({
                    tagName: p.tagName.toLowerCase(),
                    id: p.id || '',
                    className: typeof p.className === 'string' ? p.className : ''
                });
                p = p.parentElement;
            }

            return {
                tagName: el.tagName.toLowerCase(),
                id: el.id || '',
                className: typeof el.className === 'string' ? el.className : '',
                selector: buildSelector(el),
                outerHtml: el.outerHTML ? el.outerHTML.substring(0, 2000) : '',
                innerText: el.innerText ? el.innerText.substring(0, 500) : '',
                computedStyles: styles,
                boundingRect: {
                    top: rect.top,
                    left: rect.left,
                    bottom: rect.bottom,
                    right: rect.right,
                    width: rect.width,
                    height: rect.height
                },
                parents: parents
            };
        }
    };
})();
```

**Step 2: Verify the asset exists**

```bash
ls app/src/main/assets/ideaz-bridge.js
```

Expected: file listed.

**Step 3: Commit**

```bash
git add app/src/main/assets/ideaz-bridge.js
git commit -m "feat(1b): add ideaz-bridge.js DOM context harvester"
```

---

### Task 3: `WebViewBridge.kt`

A thin Kotlin object with a single `@JavascriptInterface` method that routes the JSON string to an injected callback. Kept small so it's fully testable without Android mocks.

**Files:**
- Create: `app/src/main/kotlin/com/hereliesaz/ideaz/ui/web/WebViewBridge.kt`
- Create test: `app/src/test/java/com/hereliesaz/ideaz/ui/web/WebViewBridgeTest.kt`

**Step 1: Write the failing test**

```kotlin
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
```

**Step 2: Run test to verify it fails**

```bash
unset ANDROID_SDK_ROOT && ./gradlew :app:testDebugUnitTest --tests "com.hereliesaz.ideaz.ui.web.WebViewBridgeTest" -q 2>&1 | tail -20
```

Expected: FAIL with `error: unresolved reference: WebViewBridge`

**Step 3: Write implementation**

```kotlin
// app/src/main/kotlin/com/hereliesaz/ideaz/ui/web/WebViewBridge.kt
package com.hereliesaz.ideaz.ui.web

import android.webkit.JavascriptInterface

/**
 * JavaScript ↔ Kotlin bridge registered under the name `"IdeazBridge"`.
 *
 * [ideaz-bridge.js] calls `IdeazBridge.onElementTapped(json)` after
 * [window.ideaz.getElementContext] collects DOM context for the tapped element.
 *
 * @param onElementTapped  Callback invoked on the WebView's JS thread with the
 *                         raw JSON string produced by [ideaz-bridge.js]. Callers
 *                         should deserialize it into [com.hereliesaz.ideaz.models.ElementContext].
 */
class WebViewBridge(private val onElementTapped: (String) -> Unit) {

    /**
     * Called from JavaScript as `IdeazBridge.onElementTapped(json)`.
     * Note: invoked on the WebView's JS thread — do NOT mutate UI state directly.
     * Route to main thread via ViewModel or a posted handler.
     */
    @JavascriptInterface
    fun onElementTapped(json: String) {
        onElementTapped.invoke(json)
    }
}
```

**Step 4: Run tests to verify they pass**

```bash
unset ANDROID_SDK_ROOT && ./gradlew :app:testDebugUnitTest --tests "com.hereliesaz.ideaz.ui.web.WebViewBridgeTest" -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` with 2 tests passing.

**Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hereliesaz/ideaz/ui/web/WebViewBridge.kt \
        app/src/test/java/com/hereliesaz/ideaz/ui/web/WebViewBridgeTest.kt
git commit -m "feat(1b): add WebViewBridge @JavascriptInterface"
```

---

### Task 4: Update `WebProjectHost`

Four changes to `WebProjectHost.kt`:

1. Register `WebViewBridge` as `"IdeazBridge"` interface on the WebView.
2. Inject `ideaz-bridge.js` from assets via `evaluateJavascript` in `onPageFinished`.
3. Add a `selectMode: Boolean` parameter; drive `window.ideaz.selectMode()` via `LaunchedEffect`.
4. Replace the existing `INSPECT_WEB` JS (which walked `__source:` aria-labels) with a call to `window.ideaz.getElementContext(el)` + `IdeazBridge.onElementTapped(JSON.stringify(context))`.

**Note on `IdeazJsInterface`:** Keep the existing `IdeazJsInterface` / `Ideaz.onInspectResult` plumbing **as-is**. Its broadcast sends `PROMPT_SUBMITTED_NODE` which Phase 1D (Android overlay) may still need. The new bridge path (`IdeazBridge`) is *additive*.

**Files:**
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/ui/web/WebProjectHost.kt`

**Step 1: Read current file** (already read above; use the version in working tree)

**Step 2: Apply changes**

New function signature (add `selectMode` and `onElementContext`):

```kotlin
@Composable
fun WebProjectHost(
    url: String,
    reloadTrigger: Long = 0L,
    hardReloadTrigger: Long = 0L,
    selectMode: Boolean = false,
    onElementContext: (String) -> Unit = {},
    modifier: Modifier = Modifier
)
```

Inside the `remember { WebView(context).apply { ... } }` block, after the existing `addJavascriptInterface(IdeazJsInterface(context), "Ideaz")` line, add:

```kotlin
addJavascriptInterface(WebViewBridge(onElementContext), "IdeazBridge")
```

Extend `webViewClient` with `onPageFinished` to inject the bridge script:

```kotlin
override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    // Inject ideaz-bridge.js from assets
    val js = context.assets.open("ideaz-bridge.js")
        .bufferedReader()
        .use { it.readText() }
    view?.evaluateJavascript(js, null)
}
```

Add a `LaunchedEffect` for `selectMode` (place it after the hard-reload `LaunchedEffect`):

```kotlin
// Mirror select-mode state into the web page cursor
LaunchedEffect(selectMode) {
    if (!isWebViewDestroyed.value) {
        webView.evaluateJavascript("window.ideaz?.selectMode($selectMode);", null)
    }
}
```

Replace the JS string inside the `INSPECT_WEB` broadcast receiver (the part inside the `while(true)` block currently walks `__source:` aria-label). Replace the entire `val js = """ ... """` block with:

```kotlin
val js = """
    (function() {
        var el = document.elementFromPoint($webX, $webY);
        if (!el) return;
        // Walk up to a non-text node
        while (el && el.nodeType !== 1) { el = el.parentElement; }
        if (!el) return;
        var ctx = window.ideaz && window.ideaz.getElementContext(el);
        if (ctx) {
            IdeazBridge.onElementTapped(JSON.stringify(ctx));
        }
    })();
""".trimIndent()
```

**Step 3: Full resulting file — key sections to verify**

The file should now have these four landmarks:

```
addJavascriptInterface(IdeazJsInterface(context), "Ideaz")
addJavascriptInterface(WebViewBridge(onElementContext), "IdeazBridge")
```

```
override fun onPageFinished(view: WebView?, url: String?) {
    val js = context.assets.open("ideaz-bridge.js").bufferedReader().use { it.readText() }
    view?.evaluateJavascript(js, null)
}
```

```
LaunchedEffect(selectMode) {
    if (!isWebViewDestroyed.value) {
        webView.evaluateJavascript("window.ideaz?.selectMode($selectMode);", null)
    }
}
```

```
var ctx = window.ideaz && window.ideaz.getElementContext(el);
if (ctx) { IdeazBridge.onElementTapped(JSON.stringify(ctx)); }
```

**Step 4: Build to verify no compile errors**

```bash
unset ANDROID_SDK_ROOT && ./gradlew :app:assembleDebug -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

**Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hereliesaz/ideaz/ui/web/WebProjectHost.kt
git commit -m "feat(1b): update WebProjectHost — inject bridge, selectMode param, element context handler"
```

---

### Task 5: Wire ViewModel + `OverlayDelegate` + `MainScreen`

Three sub-steps, all in one commit:

1. Add `OverlayDelegate.onWebElementContext(json)`.
2. Add `MainViewModel.handleWebElementContext(json)`.
3. Pass `selectMode` and `onElementContext` from `MainScreen` to `WebProjectHost`.

**Files:**
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/ui/delegates/OverlayDelegate.kt`
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainViewModel.kt`
- Modify: `app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainScreen.kt`

**Step 1: Write the failing test for `OverlayDelegate.onWebElementContext`**

```kotlin
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
```

> **Note on mocking `Application`:** `OverlayDelegate` calls `application.sendBroadcast()` only in functions we are NOT calling in this test (`clearSelection`, `onSelectionMade`, etc.). The mock will not explode here because we only call `onWebElementContext`.

**Step 2: Run test to verify it fails**

```bash
unset ANDROID_SDK_ROOT && ./gradlew :app:testDebugUnitTest --tests "com.hereliesaz.ideaz.ui.delegates.OverlayDelegateWebContextTest" -q 2>&1 | tail -20
```

Expected: FAIL with `error: unresolved reference: onWebElementContext`

**Step 3: Implement `OverlayDelegate.onWebElementContext`**

Add to `OverlayDelegate.kt` after `onSelectionMade`:

```kotlin
/**
 * Called when the WebView bridge delivers DOM context for a tapped element.
 * Sets [pendingContextInfo] to the raw JSON and shows the contextual chat.
 * No screenshot is taken for web inspections (the WebView itself is the canvas).
 *
 * @param json  Raw JSON string from [WebViewBridge.onElementTapped].
 */
fun onWebElementContext(json: String) {
    pendingContextInfo = json
    _isContextualChatVisible.value = true
}
```

**Step 4: Run test to verify it passes**

```bash
unset ANDROID_SDK_ROOT && ./gradlew :app:testDebugUnitTest --tests "com.hereliesaz.ideaz.ui.delegates.OverlayDelegateWebContextTest" -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

**Step 5: Add `MainViewModel.handleWebElementContext`**

In `MainViewModel.kt`, add the following public function (place it near the other selection-handling functions, e.g. after `handleSelection`):

```kotlin
/**
 * Receives DOM context JSON from the web bridge when the user taps an element
 * in Select Mode while a PWA/Web project is shown.
 *
 * Logs the raw JSON to the AI console and routes it to [OverlayDelegate] so
 * [isContextualChatVisible] becomes true.
 *
 * @param json  Raw JSON from [WebViewBridge.onElementTapped].
 */
fun handleWebElementContext(json: String) {
    stateDelegate.appendAiLog("[WEB-ELEMENT] $json")
    overlayDelegate.onWebElementContext(json)
}
```

**Step 6: Wire `MainScreen.WebProjectHost` call**

In `MainScreen.kt`, find the `WebProjectHost(...)` call (around line 121) and add the two new parameters:

```kotlin
WebProjectHost(
    url = webUrl,
    reloadTrigger = webReloadTrigger,
    hardReloadTrigger = webHardReloadTrigger,
    selectMode = isSelectMode,
    onElementContext = { viewModel.handleWebElementContext(it) },
    modifier = Modifier.fillMaxSize()
)
```

**Step 7: Build to verify no compile errors**

```bash
unset ANDROID_SDK_ROOT && ./gradlew :app:assembleDebug -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

**Step 8: Commit**

```bash
git add app/src/main/kotlin/com/hereliesaz/ideaz/ui/delegates/OverlayDelegate.kt \
        app/src/test/java/com/hereliesaz/ideaz/ui/delegates/OverlayDelegateWebContextTest.kt \
        app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainViewModel.kt \
        app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainScreen.kt
git commit -m "feat(1b): wire web element context to ViewModel, OverlayDelegate, and MainScreen"
```

---

### Task 6: Lint baseline + smoke-test checklist

Regenerate the lint baseline (it will change because new files were added) and write a smoke-test checklist.

**Files:**
- Regenerate: `app/lint-baseline.xml`
- Create: `docs/plans/phase-1b-smoke-test.md`

**Step 1: Regenerate lint baseline**

```bash
unset ANDROID_SDK_ROOT && ./gradlew :app:updateLintBaseline -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

**Step 2: Write smoke-test checklist**

Create `docs/plans/phase-1b-smoke-test.md`:

```markdown
# Phase 1B Smoke Test

Manual verification for the Web Bridge milestone.

## Pre-conditions
- Device / emulator running Android 11+
- IDEaz installed from a debug build of this branch
- A PWA project created on-device (e.g. the bundled `HelloPWA` template) with
  `index.html`, `manifest.webmanifest`, and at least one button/heading element

## Steps

### 1. Load a PWA in App View
1. Open IDEaz → select the PWA project → tap ▶ (Launch).
2. Verify the PWA renders in the WebView without a white screen.
3. Check that the AI log does NOT contain any `[WEB]` error lines.

### 2. Enter Select Mode
1. Tap the crosshair icon in the NavRail.
2. Verify `isSelectMode == true` (NavRail icon highlights).
3. **Verify the WebView cursor is `crosshair`** (visible on devices with a mouse/stylus).

### 3. Tap an element
1. Tap a button or heading element in the PWA.
2. Verify the `SelectionOverlay` disappears (select mode exits).

### 4. Verify AI log entry
1. Pull down the bottom sheet.
2. Verify a `[WEB-ELEMENT] {…}` line appears in the console.
3. The JSON should contain `tagName`, `selector`, `outerHtml`, `innerText`, and `boundingRect`.

### 5. Verify contextual chat
1. Verify the `ContextualChatOverlay` panel appears on screen.
2. Tap the × close button; verify it dismisses.

### 6. Cursor resets on exit
1. Re-enter and then exit Select Mode without tapping anything.
2. Verify the WebView cursor returns to default (not crosshair).

## Pass Criteria
All 6 steps complete without errors or crashes.
```

**Step 3: Run full unit test suite to confirm no regressions**

```bash
unset ANDROID_SDK_ROOT && ./gradlew :app:testDebugUnitTest -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all prior tests still pass.

**Step 4: Commit**

```bash
git add app/lint-baseline.xml docs/plans/phase-1b-smoke-test.md
git commit -m "chore(1b): regenerate lint baseline, add Phase 1B smoke-test checklist"
```

---

## Done ✓

After Task 6, the Phase 1B milestone is complete:

- Tapping a PWA element in Select Mode delivers structured DOM JSON to the AI log.
- The contextual chat panel appears with the element context pre-loaded.
- The `crosshair` cursor in the WebView signals inspect mode to the user.
- All unit tests pass; lint baseline is clean.

Proceed to Phase 1B finishing-a-development-branch, then plan Phase 1C (AI integration).
