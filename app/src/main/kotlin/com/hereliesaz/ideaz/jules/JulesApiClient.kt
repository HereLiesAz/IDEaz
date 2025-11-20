package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.api.*
import com.hereliesaz.ideaz.api.AuthInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

object JulesApiClient {

    private const val BASE_URL = "https://jules.googleapis.com/v1alpha/"

    private fun getClient(): JulesApi {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor)
            .build()

        val contentType = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true }

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        return retrofit.create(JulesApi::class.java)
    }

    suspend fun createSession(request: CreateSessionRequest): Session {
        return getClient().createSession(request)
    }

    suspend fun listActivities(sessionId: String): ListActivitiesResponse {
        return getClient().listActivities(sessionId)
    }

    suspend fun sendMessage(sessionId: String, prompt: String) {
        val request = SendMessageRequest(prompt = prompt)
        getClient().sendMessage(sessionId, request)
    }

    suspend fun listSources(): ListSourcesResponse {
        return getClient().listSources()
    }

    suspend fun getSession(sessionId: String): Session {
        return getClient().getSession(sessionId)
    }
}
