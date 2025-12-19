package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.api.*
import retrofit2.http.*

interface JulesApi {
    @GET("{parent}/sessions")
    suspend fun listSessions(
        @Path("parent", encoded = true) parent: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): ListSessionsResponse

    @POST("{parent}/sessions")
    suspend fun createSession(
        @Path("parent", encoded = true) parent: String,
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

    @GET("{parent}/sources")
    suspend fun listSources(
        @Path("parent", encoded = true) parent: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): ListSourcesResponse
}
