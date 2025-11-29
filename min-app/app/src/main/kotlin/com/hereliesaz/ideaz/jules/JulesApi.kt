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
        @Path(value = "parent", encoded = true) parent: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null
    ): ListSourcesResponse

    @GET("{parent}/sources/{sourceId}")
    suspend fun getSource(
        @Path(value = "parent", encoded = true) parent: String,
        @Path("sourceId") sourceId: String
    ): Source

    @POST("{parent}/sessions")
    suspend fun createSession(
        @Path(value = "parent", encoded = true) parent: String,
        @Body request: CreateSessionRequest
    ): Session

    @GET("{parent}/sessions")
    suspend fun listSessions(
        @Path(value = "parent", encoded = true) parent: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListSessionsResponse

    @GET("{sessionName}")
    suspend fun getSession(
        @Path(value = "sessionName", encoded = true) sessionName: String
    ): Session

    @POST("{sessionName}:approvePlan")
    suspend fun approvePlan(
        @Path(value = "sessionName", encoded = true) sessionName: String
    )

    @GET("{sessionName}/activities")
    suspend fun listActivities(
        @Path(value = "sessionName", encoded = true) sessionName: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListActivitiesResponse

    @GET("{activityName}")
    suspend fun getActivity(
        @Path(value = "activityName", encoded = true) activityName: String
    ): Activity

    @POST("{sessionName}:sendMessage")
    suspend fun sendMessage(
        @Path(value = "sessionName", encoded = true) sessionName: String,
        @Body request: SendMessageRequest
    )

    @DELETE("{sessionName}")
    suspend fun deleteSession(
        @Path(value = "sessionName", encoded = true) sessionName: String
    )
}
