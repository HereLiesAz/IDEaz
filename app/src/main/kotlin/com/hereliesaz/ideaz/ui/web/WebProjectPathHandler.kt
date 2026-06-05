package com.hereliesaz.ideaz.ui.web

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/**
 * [WebViewAssetLoader.PathHandler] that serves a single web/PWA project at the
 * asset-loader origin **root** (`https://appassets.androidplatform.net/`).
 *
 * Serving at root (rather than under `/files/{app}/`) is what makes root-absolute
 * references — `/src/main.jsx`, `/logo.png`, `/assets/…` — resolve. Those are the
 * paths Vite and most hand-written PWAs emit; under the old `/files/` mount they
 * escaped the handler and the WebView fell through to the real network
 * (`ERR_NAME_NOT_RESOLVED`).
 *
 * Responsibilities:
 *  - `/__ideaz__/…` → the bundled in-browser runtime (Babel, React shims,
 *    [com.hereliesaz.ideaz] loader) from `assets/ideaz-runtime/`.
 *  - any other path → a file inside [projectDirProvider]'s directory, with a
 *    correct JS/CSS/… MIME type and path-traversal protection.
 *  - module-based HTML (contains `<script type="module">`) → the HTML with the
 *    runtime injected and module scripts neutralized so [ideaz-loader.js] can
 *    transpile JSX/TSX at runtime; plain static HTML is served unchanged.
 *
 * Missing/forbidden paths return a real 404 response (never `null`) so the WebView
 * keeps the request local instead of attempting a network DNS lookup.
 *
 * @param context             For access to bundled `assets/ideaz-runtime/…`.
 * @param projectDirProvider  Supplies the active project directory. May return
 *                            `null` (e.g. a remote URL is loaded), in which case
 *                            requests are not intercepted.
 */
class WebProjectPathHandler(
    private val context: Context,
    private val projectDirProvider: () -> File?,
) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        // WebViewAssetLoader strips the registered "/" prefix before calling us,
        // so paths arrive WITHOUT a leading slash ("index.html", "src/main.jsx",
        // "__ideaz__/babel.min.js"). Tolerate an optional leading slash too.
        val rel = path.removePrefix("/")
        if (rel.startsWith(RUNTIME_DIR)) {
            return serveRuntimeAsset(rel.removePrefix(RUNTIME_DIR))
        }

        val projectDir = projectDirProvider() ?: return null
        val relative = rel.ifEmpty { "index.html" }

        val root = projectDir.canonicalFile
        val target = File(root, relative).canonicalFile
        if (target.path != root.path && !target.path.startsWith(root.path + File.separator)) {
            return notFound() // path traversal attempt — refuse, stay local.
        }

        val file = if (target.isDirectory) File(target, "index.html") else target
        if (!file.isFile) {
            // A missing entry document otherwise renders as a blank white page
            // with no explanation; serve a diagnostic instead.
            return if (relative == "index.html") diagnosticIndexPage(projectDir) else notFound()
        }

        val ext = file.extension.lowercase()
        if (ext == "html" || ext == "htm") {
            return serveHtml(file)
        }

        return try {
            response(mimeFor(ext), file.inputStream())
        } catch (e: IOException) {
            notFound()
        }
    }

    private fun serveHtml(file: File): WebResourceResponse {
        val html = file.readText()
        val body = if (needsRuntime(html)) injectRuntime(html) else html
        return response("text/html", ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)))
    }

    private fun diagnosticIndexPage(projectDir: File): WebResourceResponse {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
        val contents = projectDir.listFiles()?.joinToString(", ") { esc(it.name) }
            ?.ifEmpty { "(empty)" } ?: "(unreadable)"
        val html = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>body{font-family:system-ui,sans-serif;padding:1.25rem;line-height:1.5;color:#222}code{background:#0001;padding:.1em .3em;border-radius:3px}</style>
            </head><body>
            <h2>No <code>index.html</code> in this project</h2>
            <p>IDEaz served <code>${esc(projectDir.name)}</code> but found no <code>index.html</code> at its root, so there is nothing to display.</p>
            <p>If this project was generated from a template repository, that repo may be empty or not yet populated (or not marked as a template).</p>
            <p><strong>Project root contains:</strong> $contents</p>
            </body></html>
        """.trimIndent()
        return response("text/html", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
    }

    private fun serveRuntimeAsset(name: String): WebResourceResponse {
        if (name.isEmpty() || name.contains("..")) return notFound()
        return try {
            response(mimeFor(name.substringAfterLast('.', "").lowercase()),
                context.assets.open("$RUNTIME_ASSET_DIR/$name"))
        } catch (e: IOException) {
            notFound()
        }
    }

    private fun response(mime: String, stream: java.io.InputStream): WebResourceResponse {
        val encoding = if (mime.startsWith("text/") || mime.endsWith("javascript") ||
            mime.endsWith("json") || mime.endsWith("xml") || mime == "image/svg+xml"
        ) "utf-8" else null
        return WebResourceResponse(mime, encoding, stream)
    }

    private fun notFound(): WebResourceResponse = WebResourceResponse(
        "text/plain", "utf-8", 404, "Not Found", emptyMap(),
        ByteArrayInputStream(ByteArray(0))
    )

    companion object {
        /** URL prefix for runtime assets (used in injected HTML). */
        const val RUNTIME_PREFIX = "/__ideaz__/"
        /** Same prefix as seen by [handle] after WebViewAssetLoader strips "/". */
        private const val RUNTIME_DIR = "__ideaz__/"
        private const val RUNTIME_ASSET_DIR = "ideaz-runtime"

        /**
         * Runtime is injected only for module-based projects (Vite/React/ESM).
         * Plain static pages that use classic `<script>` tags are left untouched,
         * so their load time and behavior are unchanged.
         */
        fun needsRuntime(html: String): Boolean {
            val lower = html.lowercase()
            return lower.contains("type=\"module\"") || lower.contains("type='module'")
        }

        /** The runtime + import map injected into the `<head>` of module-based HTML. */
        private val INJECTION = """
            |<script type="importmap">{"imports":{"react":"/__ideaz__/react.js","react-dom":"/__ideaz__/react-dom.js","react-dom/client":"/__ideaz__/react-dom-client.js","react/jsx-runtime":"/__ideaz__/jsx-runtime.js","react/jsx-dev-runtime":"/__ideaz__/jsx-runtime.js","react-router":"/__ideaz__/react-router.js","react-router/dom":"/__ideaz__/react-router-dom-entry.js","react-router-dom":"/__ideaz__/react-router-dom-entry.js","zustand":"/__ideaz__/zustand.js","zustand/middleware":"/__ideaz__/zustand-middleware.js","zustand/shallow":"/__ideaz__/zustand-shallow.js","@reduxjs/toolkit":"/__ideaz__/reduxjs-toolkit.js","react-redux":"/__ideaz__/react-redux.js","axios":"/__ideaz__/axios.js","@tanstack/react-query":"/__ideaz__/tanstack-react-query.js","styled-components":"/__ideaz__/styled-components.js","@emotion/react":"/__ideaz__/emotion-react.js","@emotion/styled":"/__ideaz__/emotion-styled.js"}}</script>
            |<script src="/__ideaz__/react.umd.js"></script>
            |<script src="/__ideaz__/react-dom.umd.js"></script>
            |<script src="/__ideaz__/babel.min.js"></script>
            |<script src="/__ideaz__/ideaz-loader.js"></script>
        """.trimMargin()

        /**
         * Injects the runtime into `<head>` and rewrites `<script type="module">`
         * to `type="ideaz-module"` so the browser does not try to execute raw JSX
         * (the loader picks these up and transpiles them). Idempotent.
         */
        fun injectRuntime(html: String): String {
            if (html.contains("/__ideaz__/ideaz-loader.js")) return html

            val lower = html.lowercase()
            val headIdx = lower.indexOf("<head")
            val withRuntime = when {
                headIdx >= 0 -> {
                    val tagEnd = html.indexOf('>', headIdx)
                    if (tagEnd >= 0) {
                        html.substring(0, tagEnd + 1) + "\n" + INJECTION + html.substring(tagEnd + 1)
                    } else INJECTION + html
                }
                lower.indexOf("<html").let { it >= 0 && html.indexOf('>', it) >= 0 } -> {
                    val htmlIdx = lower.indexOf("<html")
                    val tagEnd = html.indexOf('>', htmlIdx)
                    html.substring(0, tagEnd + 1) + "\n" + INJECTION + html.substring(tagEnd + 1)
                }
                else -> INJECTION + html
            }

            return withRuntime
                .replace("type=\"module\"", "type=\"ideaz-module\"")
                .replace("type='module'", "type='ideaz-module'")
        }

        fun mimeFor(ext: String): String = when (ext) {
            "js", "mjs", "cjs", "jsx", "ts", "tsx" -> "text/javascript"
            "json", "map" -> "application/json"
            "css" -> "text/css"
            "html", "htm" -> "text/html"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "avif" -> "image/avif"
            "ico" -> "image/x-icon"
            "bmp" -> "image/bmp"
            "wasm" -> "application/wasm"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "eot" -> "application/vnd.ms-fontobject"
            "txt" -> "text/plain"
            "xml" -> "application/xml"
            "webmanifest" -> "application/manifest+json"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            else -> "application/octet-stream"
        }
    }
}
