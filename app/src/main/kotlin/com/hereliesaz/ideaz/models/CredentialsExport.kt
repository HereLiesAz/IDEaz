package com.hereliesaz.ideaz.models

import kotlinx.serialization.Serializable

@Serializable
data class CredentialsExport(
    // Security & Auth
    val julesApiKey: String? = null,
    val julesProjectId: String? = null,
    val githubToken: String? = null,
    val googleApiKey: String? = null,

    // Signing Config
    val keystorePath: String? = null,
    val keystorePass: String? = null,
    val keyAlias: String? = null,
    val keyPass: String? = null,

    // Project List
    val projectList: Set<String> = emptySet(),
    val projectPaths: Map<String, String> = emptyMap(),

    // Preferences
    val showCancelWarning: Boolean = true,
    val autoReportBugs: Boolean = true,
    val enableLocalBuilds: Boolean = false,
    val themeMode: String? = null,
    val logLevel: String? = null,

    // AI Assignments
    val aiAssignments: Map<String, String> = emptyMap()
)