package com.hereliesaz.ideaz.ui.web

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import com.hereliesaz.ideaz.BuildConfig
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
            // Unlike project content below, this is bundled with the app itself
            // (not user-editable) and identical for the app's whole lifetime
            // between updates - safe, and worth letting the WebView cache: it's
            // ~4.5MB (the Babel bundle alone is ~3MB) that a no-store project
            // reload has no reason to force a re-transfer of.
            response(
                mimeFor(name.substringAfterLast('.', "").lowercase()),
                context.assets.open("$RUNTIME_ASSET_DIR/$name"),
                cacheable = true,
            )
        } catch (e: IOException) {
            notFound()
        }
    }

    private fun response(mime: String, stream: java.io.InputStream, cacheable: Boolean = false): WebResourceResponse {
        val encoding = if (mime.startsWith("text/") || mime.endsWith("javascript") ||
            mime.endsWith("json") || mime.endsWith("xml") || mime == "image/svg+xml"
        ) "utf-8" else null
        // Previously no response from this handler carried a CSP at all - untrusted
        // AI-generated/imported project JS had unrestricted network egress. This is
        // deliberately not a full lockdown: the injected runtime's Babel-based
        // transpiler (ideaz-loader.js) needs 'unsafe-eval' to work at all, and
        // real projects legitimately call external HTTPS APIs, so script-src and
        // connect-src stay broad. What it does add: no plugins (object-src), no
        // non-HTTPS/non-data schemes for the resource types that matter, and a
        // pinned base-uri/frame-ancestors to close off some injection/embedding
        // vectors that cost nothing to block.
        val cacheHeaders = if (cacheable) RUNTIME_ASSET_CACHE_HEADERS else NO_CACHE_HEADERS
        return WebResourceResponse(
            mime, encoding, 200, "OK",
            mapOf("Content-Security-Policy" to CONTENT_SECURITY_POLICY) + cacheHeaders,
            stream,
        )
    }

    private fun notFound(): WebResourceResponse = WebResourceResponse(
        "text/plain", "utf-8", 404, "Not Found", NO_CACHE_HEADERS,
        ByteArrayInputStream(ByteArray(0))
    )

    companion object {
        // Runtime assets are now cacheable (see NO_CACHE_HEADERS below) for the
        // lifetime of an app version, but the WebView's HTTP cache is keyed
        // purely by URL and knows nothing about app updates - without a version
        // in the path, upgrading IDEaz to a build with different runtime JS
        // would leave old cached bytes serving under the same URL for up to
        // RUNTIME_ASSET_CACHE_HEADERS' max-age, mixing new project/wasm output
        // with a stale runtime. Folding the version code into the path makes an
        // app upgrade a clean cache miss instead: the old cached entries are
        // simply never requested again.
        /** URL prefix for runtime assets (used in injected HTML). */
        val RUNTIME_PREFIX = "/__ideaz__/${BuildConfig.VERSION_CODE}/"
        /** Same prefix as seen by [handle] after WebViewAssetLoader strips "/". */
        private val RUNTIME_DIR = RUNTIME_PREFIX.removePrefix("/")
        // These assets physically live in the :webruntime install-time dynamic
        // feature module (settings.gradle.kts). Because that module is install-time
        // and fused, its assets stay reachable through the base AssetManager, so
        // this lookup is unchanged from when they were bundled directly in :app.
        private const val RUNTIME_ASSET_DIR = "ideaz-runtime"

        // Project responses serve a project's *current* on-disk content, re-read
        // on every request - it is never safe for the WebView's HTTP cache to
        // reuse a prior response for the same URL. Output filenames are stable
        // across recompiles (WasmCompilerService always writes `app.wasm`/
        // `app.js`; a project's own index.html keeps its name too), so without
        // this the WebView previously had to be told to nuke its *entire* cache
        // (WebProjectHost.clearCache(true)) on every reload just to avoid
        // re-instantiating a stale Wasm binary under an unchanged URL. Declaring
        // project responses never-cacheable makes that whole-cache wipe
        // unnecessary - a plain reload always re-fetches fresh bytes.
        private val NO_CACHE_HEADERS = mapOf(
            "Cache-Control" to "no-store, no-cache, must-revalidate",
            "Pragma" to "no-cache",
        )

        // The bundled /__ideaz__/ runtime is the opposite case: static per app
        // version, not project content, and worth actually letting the WebView
        // cache (see serveRuntimeAsset).
        private val RUNTIME_ASSET_CACHE_HEADERS = mapOf(
            "Cache-Control" to "public, max-age=86400",
        )

        /** See the comment on [response] for what this does and doesn't restrict. */
        private const val CONTENT_SECURITY_POLICY =
            "default-src 'self' https: ${WebProjectUrlUtils.ASSET_DOMAIN}; " +
                "script-src 'self' 'unsafe-inline' 'unsafe-eval' https: ${WebProjectUrlUtils.ASSET_DOMAIN}; " +
                "style-src 'self' 'unsafe-inline' https: ${WebProjectUrlUtils.ASSET_DOMAIN}; " +
                "img-src 'self' data: https: ${WebProjectUrlUtils.ASSET_DOMAIN}; " +
                "font-src 'self' data: https: ${WebProjectUrlUtils.ASSET_DOMAIN}; " +
                "connect-src 'self' https: wss: ${WebProjectUrlUtils.ASSET_DOMAIN}; " +
                "object-src 'none'; " +
                "base-uri 'self'; " +
                "frame-ancestors 'self'"

        /**
         * Runtime is injected only for module-based projects (Vite/React/ESM).
         * Plain static pages that use classic `<script>` tags are left untouched,
         * so their load time and behavior are unchanged.
         */
        fun needsRuntime(html: String): Boolean {
            val lower = html.lowercase()
            return lower.contains("type=\"module\"") || lower.contains("type='module'")
        }

        /**
         * The runtime + import map injected into the `<head>` of module-based
         * HTML. Written against the unversioned `/__ideaz__/` prefix and
         * rewritten to the real (version-segmented) [RUNTIME_PREFIX] below -
         * keeps this template readable instead of repeating the version
         * segment at ~20 call sites.
         */
        private val INJECTION = """
            |<script type="importmap">{"imports":{"react":"/__ideaz__/react.js","react-dom":"/__ideaz__/react-dom.js","react-dom/client":"/__ideaz__/react-dom-client.js","react/jsx-runtime":"/__ideaz__/jsx-runtime.js","react/jsx-dev-runtime":"/__ideaz__/jsx-runtime.js","react-router":"/__ideaz__/react-router.js","react-router/dom":"/__ideaz__/react-router-dom-entry.js","react-router-dom":"/__ideaz__/react-router-dom-entry.js","zustand":"/__ideaz__/zustand.js","zustand/middleware":"/__ideaz__/zustand-middleware.js","zustand/shallow":"/__ideaz__/zustand-shallow.js","@reduxjs/toolkit":"/__ideaz__/reduxjs-toolkit.js","react-redux":"/__ideaz__/react-redux.js","axios":"/__ideaz__/axios.js","@tanstack/react-query":"/__ideaz__/tanstack-react-query.js","styled-components":"/__ideaz__/styled-components.js","@emotion/react":"/__ideaz__/emotion-react.js","@emotion/styled":"/__ideaz__/emotion-styled.js"}}</script>
            |<script src="/__ideaz__/react.umd.js"></script>
            |<script src="/__ideaz__/react-dom.umd.js"></script>
            |<script src="/__ideaz__/babel.min.js"></script>
            |<script src="/__ideaz__/ideaz-loader.js"></script>
        """.trimMargin().replace("/__ideaz__/", RUNTIME_PREFIX)

        /**
         * Injects the runtime into `<head>` and rewrites `<script type="module">`
         * to `type="ideaz-module"` so the browser does not try to execute raw JSX
         * (the loader picks these up and transpiles them). Idempotent.
         */
        fun injectRuntime(html: String): String {
            if (html.contains("${RUNTIME_PREFIX}ideaz-loader.js")) return html

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
