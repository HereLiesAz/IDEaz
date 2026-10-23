package com.hereliesaz.ideaz.ui.web

/**
 * URL helpers for [WebProjectHost]'s WebViewAssetLoader origin.
 *
 * All local PWA/Web project content is served from the virtual HTTPS origin
 * `https://appassets.androidplatform.net/`, with the active project mounted at
 * the origin **root** (see [WebProjectPathHandler] and the `addPathHandler("/", ...)`
 * call in [WebProjectHost]) so that root-absolute references like `/src/main.jsx`
 * resolve. This keeps the WebView under a consistent same-origin policy and
 * eliminates the need for the deprecated `allowFileAccessFromFileURLs` /
 * `allowUniversalAccessFromFileURLs` settings.
 */
object WebProjectUrlUtils {

    /** The virtual HTTPS domain used by [WebViewAssetLoader]. */
    const val ASSET_DOMAIN = "appassets.androidplatform.net"

    /** Origin root; the active project is mounted here by [WebProjectPathHandler]. */
    const val ASSET_ROOT_URL = "https://$ASSET_DOMAIN/"

    /** URL for the active local project's `index.html`, served at the origin root. */
    fun localProjectRootUrl(): String = "${ASSET_ROOT_URL}index.html"

    /**
     * URL for a specific project-relative entry point, e.g. the path
     * [com.hereliesaz.ideaz.utils.ProjectAnalyzer.findWebEntryPoint] returns.
     *
     * Callers used to gate on that function's result (null-check only) and
     * then always navigate to [localProjectRootUrl] regardless of what was
     * actually found. For a project whose entry lives at `public/index.html`
     * or `src/index.html` - locations that gate explicitly admits - that
     * left WebProjectPathHandler asked for the *root's own* index.html,
     * which does not exist, and it served its "no index.html" diagnostic:
     * a project could pass the previewability check and still get refused.
     */
    fun localProjectUrl(entryPointPath: String): String = "$ASSET_ROOT_URL$entryPointPath"

    /**
     * Whether the preview WebView may navigate its top-level frame to [target]: only within
     * the asset-loader origin, or - when a remote page is being previewed - within the origin
     * of [remoteBaseUrl] (null for a local project). A page cannot send the WebView, and the
     * privileged bridge it was given, to any other site; non-http(s) schemes never pass.
     */
    fun isAllowedNavigation(target: String, remoteBaseUrl: String?): Boolean {
        val targetOrigin = originOf(target) ?: return false
        if (targetOrigin == originOf(ASSET_ROOT_URL)) return true
        val base = remoteBaseUrl?.let(::originOf) ?: return false
        return targetOrigin == base
    }

    private fun originOf(url: String): Triple<String, String, Int>? {
        val uri = try { java.net.URI(url.trim()) } catch (e: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host?.lowercase() ?: return null
        val port = if (uri.port != -1) uri.port else if (scheme == "https") 443 else 80
        return Triple(scheme, host, port)
    }
}
