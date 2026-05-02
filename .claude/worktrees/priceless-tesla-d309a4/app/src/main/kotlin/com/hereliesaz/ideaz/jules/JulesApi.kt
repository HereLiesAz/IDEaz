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

    @POST("sessions/{id}:sendMessage")
    suspend fun sendMessage(
        @Path("id") id: String,
        @Body request: SendMessageRequest
    ): Unit

    @GET("sessions/{id}/activities")
    suspend fun listActivities(
        @Path("id") id: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): ListActivitiesResponse

    @GET("sources")
    suspend fun listSources(
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): ListSourcesResponse
}
