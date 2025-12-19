package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.api.*
import com.hereliesaz.ideaz.api.AuthInterceptor
import com.hereliesaz.ideaz.api.RetryInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/**
 * A Singleton client for interacting with the Jules API.
 *
 * This client provides a suspend-function based interface to the Jules REST API, handling:
 * - Authentication (via [AuthInterceptor]).
 * - JSON Serialization/Deserialization (via `kotlinx.serialization`).
 * - Logging (via `HttpLoggingInterceptor`).
 * - Automatic Retries (via [RetryInterceptor]).
 *
 * It wraps the Retrofit interface [JulesApi].
 */
object JulesApiClient {

    /** The base URL for the v1alpha Jules API. */
    private const val BASE_URL = "https://jules.googleapis.com/v1alpha/"

    private fun getClient(): JulesApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("X-Goog-Api-Key")
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor)
            .addInterceptor(RetryInterceptor())
            .addInterceptor(loggingInterceptor)
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

    suspend fun listSessions(projectId: String, location: String = "us-central1", pageSize: Int = 100, pageToken: String? = null) =
        getClient().listSessions("projects/$projectId/locations/$location", pageSize, pageToken)

    suspend fun createSession(projectId: String, location: String = "us-central1", request: CreateSessionRequest): Session =
        getClient().createSession("projects/$projectId/locations/$location", request)

    suspend fun sendMessage(sessionName: String, request: SendMessageRequest) =
        getClient().sendMessage(sessionName, request)

    suspend fun listActivities(sessionName: String, pageSize: Int = 100, pageToken: String? = null) =
        getClient().listActivities(sessionName, pageSize, pageToken)

    suspend fun listSources(projectId: String, location: String = "us-central1", pageSize: Int = 100, pageToken: String? = null) =
        getClient().listSources("projects/$projectId/locations/$location", pageSize, pageToken)
}
