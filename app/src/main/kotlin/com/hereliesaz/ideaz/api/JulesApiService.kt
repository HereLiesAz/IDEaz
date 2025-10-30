package com.hereliesaz.ideaz.api

import retrofit2.http.Body
import retrofit2.http.POST

interface JulesApiService {
    @POST("v1/prompt")
    suspend fun sendPrompt(@Body prompt: String): String
}
