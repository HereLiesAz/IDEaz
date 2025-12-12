package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.api.*
import retrofit2.http.*

interface JulesApi {
    @GET("sessions")
    suspend fun listSessions(
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): ListSessionsResponse

    @POST("sessions")
    suspend fun createSession(
        @Body request: CreateSessionRequest
    ): Session

    @POST("sessions/{sessionId}:sendMessage")
    suspend fun sendMessage(
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    ): Unit

    @GET("sessions/{sessionId}/activities")
    suspend fun listActivities(
        @Path("sessionId") sessionId: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): ListActivitiesResponse

    @GET("sources")
    suspend fun listSources(
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): ListSourcesResponse
}
