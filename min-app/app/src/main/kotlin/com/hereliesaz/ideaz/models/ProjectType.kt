package com.hereliesaz.ideaz.models

enum class ProjectType(val displayName: String) {
    ANDROID("Android"),
    WEB("Web"),
    REACT_NATIVE("React Native"),
    FLUTTER("Flutter"),
    OTHER("Other");

    companion object {
        fun fromString(value: String): ProjectType {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                OTHER
            }
        }
    }
}
