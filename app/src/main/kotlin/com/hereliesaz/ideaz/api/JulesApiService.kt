package com.hereliesaz.ideaz.api

import com.hereliesaz.ideaz.models.DebugResult
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface JulesApiService {
    @POST("v1/prompt")
    suspend fun sendPrompt(@Body prompt: String): String

    @POST("v1/debug")
    suspend fun debugBuild(@Body buildLog: String): DebugResult
}
