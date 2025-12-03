package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.api.AuthInterceptor
import com.hereliesaz.ideaz.api.RetryInterceptor
import com.hereliesaz.ideaz.api.LoggingInterceptor
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
            .addInterceptor(RetryInterceptor())
            .addInterceptor(LoggingInterceptor)
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
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

    suspend fun generateResponse(prompt: Prompt): GenerateResponseResponse {
        return getClient().generateResponse(prompt)
    }

    suspend fun listSessions(parent: String, pageSize: Int = 100, pageToken: String? = null) =
        getClient().listSessions(parent, pageSize, pageToken)

    suspend fun listSources(parent: String, pageSize: Int = 100, pageToken: String? = null) =
        getClient().listSources(parent, pageSize, pageToken)
}
