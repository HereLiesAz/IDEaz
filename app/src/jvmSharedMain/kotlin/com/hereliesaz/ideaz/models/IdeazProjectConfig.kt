package com.hereliesaz.ideaz.models

import kotlinx.serialization.Serializable

/**
 * Per-project metadata stored at `.ideaz/config.json`.
 *
 * `projectType` and `packageName` used to live here. IDEaz previews exactly one
 * kind of thing — a web project it can mount and transpile — so there is no type
 * to record, and with the Android edit target gone nothing needs a Java package
 * name. Configs written by older versions still carry both keys; the reader sets
 * `ignoreUnknownKeys`, so they are simply ignored rather than failing the load.
 */
@Serializable
data class IdeazProjectConfig(
    val branch: String = "main",
    val owner: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
