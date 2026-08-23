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
}
