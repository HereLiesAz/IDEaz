package com.hereliesaz.ideaz.models

import kotlinx.serialization.Serializable


data class ProjectItem(
    val name: String,
    val path: String,
    val type: ProjectType
)

@Serializable
data class GitHubRepoResponse(
    val id: Long,
    val name: String,
    val full_name: String,
    val html_url: String,
    val description: String?,
    val private: Boolean,
    val clone_url: String
)

data class DependencyItem(
    val group: String,
    val artifact: String,
    val version: String
)