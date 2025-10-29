package com.hereliesaz.peridiumide.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface JulesApiService {
    @POST("generate-code")
    suspend fun generateCode(@Body request: GenerateCodeRequest): GenerateCodeResponse
}

@Serializable
data class GenerateCodeRequest(
    val prompt: String,
    val context: String
)

@Serializable
data class GenerateCodeResponse(
    val code: String
)
