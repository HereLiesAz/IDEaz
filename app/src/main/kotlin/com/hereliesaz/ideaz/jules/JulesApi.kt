package com.hereliesaz.ideaz.jules

import retrofit2.http.Body
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
        @Body prompt: Map<String, String>
    )
}
