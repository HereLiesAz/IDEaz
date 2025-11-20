package com.hereliesaz.ideaz.api

import kotlinx.serialization.Serializable

// --- Sources ---

@Serializable
data class ListSourcesResponse(
    val sources: List<Source>? = null,
    val nextPageToken: String? = null
)

@Serializable
data class Source(
    val name: String,
    val id: String,
    val githubRepo: GitHubRepo? = null
)

@Serializable
data class GitHubRepo(
    val owner: String,
    val repo: String,
    val isPrivate: Boolean? = null,
    val defaultBranch: GitHubBranch? = null,
    val branches: List<GitHubBranch>? = null
)

@Serializable
data class GitHubBranch(
    val displayName: String
)


// --- Sessions ---

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
data class SourceContext(
    val source: String,
    val githubRepoContext: GitHubRepoContext? = null
)

@Serializable
data class GitHubRepoContext(
    val startingBranch: String
)

@Serializable
data class Session(
    val name: String,
    val id: String,
    val createTime: String? = null,
    val updateTime: String? = null,
    val state: String? = null,
    val url: String? = null,
    val prompt: String,
    val sourceContext: SourceContext,
    val title: String? = null,
    val requirePlanApproval: Boolean? = null,
    val automationMode: String? = null,
    val outputs: List<SessionOutput>? = null
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


// --- Activities ---

@Serializable
data class ListActivitiesResponse(
    val activities: List<Activity>? = null,
    val nextPageToken: String? = null
)

@Serializable
data class Activity(
    val name: String,
    val id: String,
    val description: String? = null,
    val createTime: String,
    val originator: String,
    val artifacts: List<Artifact>? = null,
    val agentMessaged: AgentMessaged? = null,
    val userMessaged: UserMessaged? = null,
    val planGenerated: PlanGenerated? = null,
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
    val steps: List<PlanStep>? = null,
    val createTime: String? = null
)

@Serializable
data class PlanStep(
    val id: String,
    val title: String,
    val description: String,
    val index: Int? = null
)

@Serializable
data class PlanApproved(
    val planId: String
)

@Serializable
data class ProgressUpdated(
    val title: String? = null,
    val description: String? = null
)

@Serializable
class SessionCompleted

@Serializable
data class SessionFailed(
    val reason: String? = null
)

@Serializable
data class Artifact(
    val changeSet: ChangeSet? = null,
    val media: Media? = null,
    val bashOutput: BashOutput? = null
)

@Serializable
data class ChangeSet(
    val source: String? = null,
    val gitPatch: GitPatch? = null
)

@Serializable
data class GitPatch(
    val unidiffPatch: String? = null,
    val baseCommitId: String? = null,
    val suggestedCommitMessage: String? = null
)

@Serializable
data class Media(
    val data: String? = null,
    val mimeType: String? = null
)

@Serializable
data class BashOutput(
    val command: String? = null,
    val output: String? = null,
    val exitCode: Int? = null
)

@Serializable
data class SendMessageRequest(
    val prompt: String
)
