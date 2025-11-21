package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.api.*
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JulesApi {
    @GET("sources")
    suspend fun listSources(
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null
    ): ListSourcesResponse

    @GET("sources/{sourceId}")
    suspend fun getSource(@Path("sourceId") sourceId: String): Source

    @POST("sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): Session

    @GET("sessions")
    suspend fun listSessions(
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListSessionsResponse

    @GET("sessions/{sessionId}")
    suspend fun getSession(@Path("sessionId") sessionId: String): Session

    @POST("sessions/{sessionId}:approvePlan")
    suspend fun approvePlan(@Path("sessionId") sessionId: String)

    // Reverted to explicit path structure.
    // If this 404s, MainViewModel will fallback to CLI.
    @GET("sessions/{sessionId}/activities")
    suspend fun listActivities(
        @Path("sessionId") sessionId: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListActivitiesResponse

    @GET("sessions/{sessionId}/activities/{activityId}")
    suspend fun getActivity(
        @Path("sessionId") sessionId: String,
        @Path("activityId") activityId: String
    ): Activity

    @POST("sessions/{sessionId}:sendMessage")
    suspend fun sendMessage(
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    )

    @DELETE("sessions/{sessionId}")
    suspend fun deleteSession(@Path("sessionId") sessionId: String)
}