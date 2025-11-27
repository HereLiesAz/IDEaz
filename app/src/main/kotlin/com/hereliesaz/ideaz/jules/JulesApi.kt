package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.api.*
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JulesApi {
    @GET("projects/{projectId}/sources")
    suspend fun listSources(
        @Path("projectId") projectId: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null
    ): ListSourcesResponse

    @GET("projects/{projectId}/sources/{sourceId}")
    suspend fun getSource(
        @Path("projectId") projectId: String,
        @Path("sourceId") sourceId: String
    ): Source

    @POST("projects/{projectId}/sessions")
    suspend fun createSession(
        @Path("projectId") projectId: String,
        @Body request: CreateSessionRequest
    ): Session

    @GET("projects/{projectId}/sessions")
    suspend fun listSessions(
        @Path("projectId") projectId: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListSessionsResponse

    @GET("projects/{projectId}/sessions/{sessionId}")
    suspend fun getSession(
        @Path("projectId") projectId: String,
        @Path("sessionId") sessionId: String
    ): Session

    @POST("projects/{projectId}/sessions/{sessionId}:approvePlan")
    suspend fun approvePlan(
        @Path("projectId") projectId: String,
        @Path("sessionId") sessionId: String
    )

    // Reverted to explicit path structure.
    // If this 404s, MainViewModel will fallback to CLI.
    @GET("projects/{projectId}/sessions/{sessionId}/activities")
    suspend fun listActivities(
        @Path("projectId") projectId: String,
        @Path("sessionId") sessionId: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListActivitiesResponse

    @GET("projects/{projectId}/sessions/{sessionId}/activities/{activityId}")
    suspend fun getActivity(
        @Path("projectId") projectId: String,
        @Path("sessionId") sessionId: String,
        @Path("activityId") activityId: String
    ): Activity

    @POST("projects/{projectId}/sessions/{sessionId}:sendMessage")
    suspend fun sendMessage(
        @Path("projectId") projectId: String,
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    )

    @DELETE("projects/{projectId}/sessions/{sessionId}")
    suspend fun deleteSession(
        @Path("projectId") projectId: String,
        @Path("sessionId") sessionId: String
    )
}
