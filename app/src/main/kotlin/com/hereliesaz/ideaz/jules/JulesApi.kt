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

    @POST("{name}:sendMessage")
    suspend fun sendMessage(
        @Path(value = "name", encoded = true) name: String,
        @Body request: SendMessageRequest
    ): Unit

    @GET("{name}/activities")
    suspend fun listActivities(
        @Path(value = "name", encoded = true) name: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): ListActivitiesResponse

    @GET("sources")
    suspend fun listSources(
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): ListSourcesResponse
}
