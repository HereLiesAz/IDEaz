package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.api.*
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JulesApi {
    @GET("v1alpha/{parent}/sources")
    suspend fun listSources(
        @Path("parent") parent: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null
    ): ListSourcesResponse

    @GET("v1alpha/sources/{sourceId}")
    suspend fun getSource(@Path("sourceId") sourceId: String): Source

    @POST("v1alpha/{parent}/sessions")
    suspend fun createSession(
        @Path("parent") parent: String,
        @Body request: CreateSessionRequest
    ): Session

    @GET("v1alpha/{parent}/sessions")
    suspend fun listSessions(
        @Path("parent") parent: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListSessionsResponse

    @GET("v1alpha/{parent}/sessions/{sessionId}")
    suspend fun getSession(
        @Path("parent") parent: String,
        @Path("sessionId") sessionId: String
    ): Session

    @POST("v1alpha/{parent}/sessions/{sessionId}:approvePlan")
    suspend fun approvePlan(
        @Path("parent") parent: String,
        @Path("sessionId") sessionId: String
    )

    @GET("v1alpha/{parent}/sessions/{sessionId}/activities")
    suspend fun listActivities(
        @Path("parent") parent: String,
        @Path("sessionId") sessionId: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListActivitiesResponse

    @GET("v1alpha/{parent}/sessions/{sessionId}/activities/{activityId}")
    suspend fun getActivity(
        @Path("parent") parent: String,
        @Path("sessionId") sessionId: String,
        @Path("activityId") activityId: String
    ): Activity

    @POST("v1alpha/{parent}/sessions/{sessionId}:sendMessage")
    suspend fun sendMessage(
        @Path("parent") parent: String,
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    )

    @DELETE("v1alpha/{parent}/sessions/{sessionId}")
    suspend fun deleteSession(
        @Path("parent") parent: String,
        @Path("sessionId") sessionId: String
    )
}
