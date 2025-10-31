package com.hereliesaz.ideaz.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JulesApiService {
    // Sessions
    @POST("v1alpha/sessions")
    suspend fun createSession(@Body session: Session): Session

    @GET("v1alpha/sessions/{name}")
    suspend fun getSession(@Path("name") name: String): Session

    @GET("v1alpha/sessions")
    suspend fun listSessions(): List<Session>

    @POST("v1alpha/{session=sessions/*}:approvePlan")
    suspend fun approvePlan(@Path("session") session: String): Session

    @POST("v1alpha/{session=sessions/*}:sendMessage")
    suspend fun sendMessage(@Path("session") session: String, @Body message: UserMessaged): Session

    // Activities
    @GET("v1alpha/{name=sessions/*/activities/*}")
    suspend fun getActivity(@Path("name") name: String): Activity

    @GET("v1alpha/{parent=sessions/*}/activities")
    suspend fun listActivities(@Path("parent") parent: String): List<Activity>

    // Sources
    @GET("v1alpha/{name=sources/**}")
    suspend fun getSource(@Path("name") name: String): Source

    @GET("v1alpha/sources")
    suspend fun listSources(): List<Source>
}
