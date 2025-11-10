package com.hereliesaz.ideaz.api

import kotlinx.serialization.Serializable

@Serializable
data class ListSourcesResponse(
    val sources: List<Source>
)

@Serializable
data class ListSessionsResponse(
    val sessions: List<Session>
)

@Serializable
data class CreateSessionRequest(
    val prompt: String?,
    val sourceContext: SourceContext,
    val title: String,
    val requirePlanApproval: Boolean? = null
)

@Serializable
data class Session(
    val name: String,
    val id: String,

    val prompt: String,
    val sourceContext: SourceContext,
    val title: String,
    // Made nullable
    val requirePlanApproval: Boolean? = null,
    val automationMode: String? = null,
    val createTime: String,
    val updateTime: String,
    val state: String,
    val url: String,
    // Made nullable
    val outputs: List<SessionOutput>? = null
)

@Serializable
data class SourceContext(
    val source: String,
    val githubRepoContext: GitHubRepoContext?
    = null
)

@Serializable
data class GitHubRepoContext(
    val startingBranch: String
)

@Serializable
data class SessionOutput(
    val pullRequest: PullRequest? = null
)

@Serializable
data class PullRequest(
    val url: String,
    val title: String,
    val description: String
)

@Serializable
data class Activity(
    val name: String,
    val id: String,
    val description: String,
    val createTime: String,
    val originator: String,
    val artifacts: List<Artifact>,
    val agentMessaged: AgentMessaged? = null,
    val userMessaged: UserMessaged? = null,
    val planGenerated: PlanGenerated?
    = null,
    val planApproved: PlanApproved? = null,
    val progressUpdated: ProgressUpdated? = null,
    val sessionCompleted: SessionCompleted? = null,
    val sessionFailed: SessionFailed? = null
)

@Serializable
data class AgentMessaged(
    val agentMessage: String
)

@Serializable
data class UserMessaged(
    val userMessage: String
)

@Serializable
data class PlanGenerated(
    val plan: Plan
)

@Serializable
data class Plan(
    val id: String,
    val steps: List<PlanStep>,
    val createTime: String
)

@Serializable
data class PlanStep(
    val id: String,
    val title: String,
    val description: String,

    val index: Int
)

@Serializable
data class PlanApproved(
    val planId: String
)

@Serializable
data class ProgressUpdated(
    val title: String,
    val description: String
)

@Serializable
class SessionCompleted

@Serializable
data class SessionFailed(
    val reason: String
)

@Serializable
data class Artifact(
    val changeSet: ChangeSet?
    = null,
    val media: Media? = null,
    val bashOutput: BashOutput?
    = null
)

@Serializable
data class ChangeSet(
    val source: String,
    val gitPatch: GitPatch? = null
)

@Serializable
data class GitPatch(
    val unidiffPatch: String,
    val baseCommitId: String,
    val suggestedCommitMessage: String
)

@Serializable
data class Media(
    val data: String,
    val mimeType: String
)

@Serializable
data class BashOutput(
    val command: String,
    val output: String,
    val exitCode: Int
)

@Serializable
data class Source(
    val name: String,
    val id: String,
    val githubRepo: GitHubRepo? = null
)

@Serializable
data class GitHubRepo(
    val
    owner: String,
    val repo: String,
    // Made nullable
    val isPrivate: Boolean? = null,
    val defaultBranch: GitHubBranch,
    // Made nullable
    val branches: List<GitHubBranch>? = null
)

@Serializable
data class GitHubBranch(
    val displayName: String
)