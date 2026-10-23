package com.hereliesaz.ideaz.git

import java.net.URI

/**
 * Thrown instead of attaching the user's GitHub token to a remote that is not GitHub. A
 * project's `.git/config` is plain data (a cloned or imported repository can point `origin`
 * anywhere), so the token must only ever be offered to `https://github.com/...`.
 */
class UntrustedRemoteException(message: String) : Exception(message)

/** The one place that decides whether a remote URL may receive the GitHub token. */
object GitHubRemote {

    /**
     * True only for `https://github.com/<path>`: https, host exactly `github.com`
     * (case-insensitive), no user-info (so `https://github.com@evil.com/x` is out), the default
     * port, and a non-empty path. Anything unparsable (scp-style `git@github.com:o/r`,
     * backslashes, spaces) is rejected.
     */
    fun isTrustedUrl(url: String): Boolean {
        val uri = try {
            URI(url.trim())
        } catch (e: Exception) {
            return false
        }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return false
        if (uri.rawUserInfo != null) return false
        if (!"github.com".equals(uri.host, ignoreCase = true)) return false
        if (uri.port != -1 && uri.port != 443) return false
        val path = uri.rawPath ?: return false
        return path.length > 1 && path.startsWith("/")
    }

    /** True when [urls] is non-empty and every entry passes [isTrustedUrl]. */
    fun areAllTrusted(urls: Collection<String>): Boolean = urls.isNotEmpty() && urls.all(::isTrustedUrl)
}
