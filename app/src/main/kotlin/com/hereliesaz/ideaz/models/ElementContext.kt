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
