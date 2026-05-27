package com.hereliesaz.ideaz.models

enum class ProjectType(val displayName: String) {
    ANDROID("Android"),
    WEB("Web"),
    PWA("PWA"),
    REACT("React"),
    OTHER("Other"),
    UNKNOWN("Unknown");

    /**
     * Web-like project types share the on-device WebView preview/edit loop
     * (served by WebProjectHost) and skip the remote Android build pipeline.
     * REACT is previewed exactly like WEB — its JSX is transpiled in-browser by
     * the bundled runtime.
     */
    fun isWebLike(): Boolean = this == WEB || this == PWA || this == REACT

    companion object {
        fun fromString(value: String?): ProjectType {
            return values().find { it.name == value } ?: UNKNOWN
        }
    }
}
