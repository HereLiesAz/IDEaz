package com.hereliesaz.ideaz.models

enum class ProjectType(val displayName: String) {
    ANDROID("Android"),
    REACT_NATIVE("React Native"),
    FLUTTER("Flutter"),
    WEB("Web"),
    OTHER("Other"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(value: String?): ProjectType {
            return values().find { it.name == value } ?: UNKNOWN
        }
    }
}
