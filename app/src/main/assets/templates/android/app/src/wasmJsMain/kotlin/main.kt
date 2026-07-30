package com.example.helloworld

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

/**
 * Wasm entry point: mounts the Compose canvas into the DOM. This is the binary
 * IDEaz compiles on-device and hot-reloads into the preview WebView.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App()
    }
}
