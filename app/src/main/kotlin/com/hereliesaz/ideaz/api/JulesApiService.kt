package com.hereliesaz.ideaz.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface JulesApiService {

    @POST("v1alpha/sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): CreateSessionResponse

    @GET("v1alpha/sessions/{id}/activities")
    suspend fun getActivities(@Path("id") sessionId: String): List<Activity>
}
