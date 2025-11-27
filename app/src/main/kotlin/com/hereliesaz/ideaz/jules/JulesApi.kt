package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.api.*
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JulesApi {
    @GET("{parent}/sources")
    suspend fun listSources(
        @Path("parent", encoded = true) parent: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null
    ): ListSourcesResponse

    @GET("{parent}/sources/{sourceId}")
    suspend fun getSource(
        @Path("parent", encoded = true) parent: String,
        @Path("sourceId") sourceId: String
    ): Source

    @POST("{parent}/sessions")
    suspend fun createSession(
        @Path("parent", encoded = true) parent: String,
        @Body request: CreateSessionRequest
    ): Session

    @GET("{parent}/sessions")
    suspend fun listSessions(
        @Path("parent", encoded = true) parent: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListSessionsResponse

    @GET("{parent}/sessions/{sessionId}")
    suspend fun getSession(
        @Path("parent", encoded = true) parent: String,
        @Path("sessionId") sessionId: String
    ): Session

    @POST("{parent}/sessions/{sessionId}:approvePlan")
    suspend fun approvePlan(
        @Path("parent", encoded = true) parent: String,
        @Path("sessionId") sessionId: String
    )

    // Reverted to explicit path structure.
    // If this 404s, MainViewModel will fallback to CLI.
    @GET("{parent}/sessions/{sessionId}/activities")
    suspend fun listActivities(
        @Path("parent", encoded = true) parent: String,
        @Path("sessionId") sessionId: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListActivitiesResponse

    @GET("{parent}/sessions/{sessionId}/activities/{activityId}")
    suspend fun getActivity(
        @Path("parent", encoded = true) parent: String,
        @Path("sessionId") sessionId: String,
        @Path("activityId") activityId: String
    ): Activity

    @POST("{parent}/sessions/{sessionId}:sendMessage")
    suspend fun sendMessage(
        @Path("parent", encoded = true) parent: String,
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    )

    @DELETE("{parent}/sessions/{sessionId}")
    suspend fun deleteSession(
        @Path("parent", encoded = true) parent: String,
        @Path("sessionId") sessionId: String
    )
}
