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

    /** Origin root; the active project is mounted here by [WebProjectPathHandler]. */
    const val ASSET_ROOT_URL = "https://$ASSET_DOMAIN/"

    /**
     * URL for a local project served at the asset-loader **root**.
     *
     * The project directory is mounted at `/` (see [WebProjectPathHandler]) so that
     * root-absolute references like `/src/main.jsx` resolve. The entry document is
     * always `index.html`.
     */
    fun localProjectRootUrl(): String = "${ASSET_ROOT_URL}index.html"

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
     *   ->            = "https://appassets.androidplatform.net/files/myapp/index.html"
     */
    fun toAssetUrl(absolutePath: String, filesDir: File): String? {
        // Normalize separators to '/' so this works on both Android (Unix) and
        // developer workstations running JVM unit tests on Windows.
        val normalizedDir = filesDir.path.replace('\\', '/').trimEnd('/')
        val normalizedPath = absolutePath.replace('\\', '/')
        val prefix = "$normalizedDir/"
        if (!normalizedPath.startsWith(prefix)) return null
        val relative = normalizedPath.removePrefix(prefix)
        return "$ASSET_BASE_URL$relative"
    }

    /** Returns `true` if this URL was produced by [toAssetUrl] or [localProjectUrl]. */
    fun isAssetUrl(url: String): Boolean = url.startsWith(ASSET_BASE_URL)
}
