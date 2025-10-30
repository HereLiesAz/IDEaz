package com.hereliesaz.ideaz.api

import kotlinx.serialization.Serializable

@Serializable
data class SourceContext(
    val githubRepoContext: GithubRepoContext
)

@Serializable
data class GithubRepoContext(
    val startingBranch: String = "main"
)

@Serializable
data class CreateSessionRequest(
    val prompt: String,
    val sourceContext: SourceContext
)

@Serializable
data class ChangeSet(
    val gitPatch: String
)

@Serializable
data class Activity(
    val changeSet: ChangeSet? = null,
    val status: String
)

@Serializable
data class CreateSessionResponse(
    val id: String
)
