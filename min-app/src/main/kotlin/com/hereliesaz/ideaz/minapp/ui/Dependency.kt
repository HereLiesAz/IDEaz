package com.hereliesaz.ideaz.minapp.ui

data class Dependency(
    val group: String,
    val artifact: String,
    val version: String,
    val isUpdating: Boolean = false,
    val availableUpdate: String? = null,
    val error: String? = null
) {
    companion object {
        fun fromString(dep: String): Dependency {
            val parts = dep.split(":")
            return Dependency(
                group = parts.getOrElse(0) { "" },
                artifact = parts.getOrElse(1) { "" },
                version = parts.getOrElse(2) { "" }
            )
        }
    }

    override fun toString(): String {
        return "$group:$artifact:$version"
    }
}
