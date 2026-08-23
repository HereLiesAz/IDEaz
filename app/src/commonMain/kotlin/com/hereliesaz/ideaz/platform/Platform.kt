package com.hereliesaz.ideaz.platform

/**
 * The small set of things shared code needs that differ per target.
 *
 * Deliberately tiny. Every entry here is a place the codebase used to reach
 * straight for an Android API from logic that has nothing to do with Android -
 * `android.util.Log` inside an HTTP adapter, `android.util.Base64` inside a
 * message encoder, `BuildConfig.DEBUG` inside an API client. Those calls are
 * what pinned otherwise portable code to one platform.
 */
expect object Platform {
    /** True in a debug build. Gates verbose HTTP logging, nothing else. */
    val isDebugBuild: Boolean

    fun logDebug(tag: String, message: String)
    fun logWarn(tag: String, message: String, error: Throwable? = null)

    /** Base64, no line wrapping. */
    fun base64Encode(bytes: ByteArray): String
}
