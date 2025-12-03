package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.api.ListSessionsResponse
import com.hereliesaz.ideaz.api.ListSourcesResponse
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JulesApi {
    @POST("generateresponse")
    suspend fun generateResponse(
        @Body prompt: Prompt
    ): GenerateResponseResponse

    @GET("{parent}/sessions")
    suspend fun listSessions(
        @Path(value = "parent", encoded = true) parent: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): ListSessionsResponse

    @GET("{parent}/sources")
    suspend fun listSources(
        @Path(value = "parent", encoded = true) parent: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): ListSourcesResponse
}

@Serializable
data class Prompt(
    val parent: String, // e.g., projects/my-project
    val session: SessionDetails?, // nullable for initial prompt
    val query: String,
    val sourceContext: SourceContext? = null,
    val history: List<Message>? = null
) {
    @Serializable
    data class SourceContext(
        val name: String,
        val gitHubRepoContext: GitHubRepoContext
    )

    @Serializable
    data class GitHubRepoContext(
        val branch: String
    )
}

@Serializable
data class SessionDetails(
    val id: String,
    val context: String? = null // e.g., "new-session" or a previous session context
)

@Serializable
data class GenerateResponseResponse(
    val message: Message,
    val patch: Patch? = null,
    val session: SessionDetails,
    val activities: List<Activity>? = null
)

@Serializable
data class Patch(
    val actions: List<PatchAction>
)

@Serializable
data class PatchAction(
    val type: String, // e.g., "CREATE_FILE", "UPDATE_FILE", "DELETE_FILE"
    val filePath: String,
    val content: String // For CREATE_FILE and UPDATE_FILE
)

@Serializable
data class Message(
    val role: String, // "user" or "model"
    val content: String
)

@Serializable
data class Activity(
    val name: String,
    val description: String
)
