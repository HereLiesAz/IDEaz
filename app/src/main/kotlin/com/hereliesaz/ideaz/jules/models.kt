package com.hereliesaz.ideaz.jules

import kotlinx.serialization.Serializable

@Serializable
data class GithubRepoContext(
    val startingBranch: String
)

@Serializable
data class SourceContext(
    val source: String,
    val githubRepoContext: GithubRepoContext? = null
)

@Serializable
data class PullRequest(
    val url: String,
    val title: String,
    val description: String
)

@Serializable
data class SessionOutput(
    val pullRequest: PullRequest? = null
)

@Serializable
data class Session(
    val name: String,
    val id: String,
    val createTime: String,
    val updateTime: String,
    val state: String,
    val url: String,
    val prompt: String,
    val sourceContext: SourceContext,
    val title: String? = null,
    val requirePlanApproval: Boolean? = null,
    val automationMode: String? = null,
    val outputs: List<SessionOutput>? = null
)

@Serializable
data class ListSessionsResponse(
    val sessions: List<Session>? = null,
    val nextPageToken: String? = null
)

@Serializable
data class CreateSessionRequest(
    val prompt: String,
    val sourceContext: SourceContext,
    val title: String? = null,
    val requirePlanApproval: Boolean? = null,
    val automationMode: String? = null
)

@Serializable
data class Source(
    val name: String,
    // Add other fields as necessary from the SDK
)

@Serializable
data class ListSourcesResponse(
    val sources: List<Source>? = null,
    val nextPageToken: String? = null
)
