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
