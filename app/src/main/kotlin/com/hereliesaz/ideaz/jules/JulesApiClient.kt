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
import java.util.concurrent.TimeUnit
import org.jetbrains.annotations.VisibleForTesting

/**
 * A Singleton client for interacting with the Jules API.
 *
 * This client provides a suspend-function based interface to the Jules REST API, handling:
 * - Authentication (via [AuthInterceptor]).
 * - JSON Serialization/Deserialization (via `kotlinx.serialization`).
 * - Logging (via [HttpLoggingInterceptor]).
 * - Automatic Retries (via [RetryInterceptor]).
 *
 * It wraps the Retrofit interface [JulesApi].
 */
object JulesApiClient : IJulesApiClient {

    /** The base URL for the v1alpha Jules API. */
    private const val DEFAULT_BASE_URL = "https://jules.googleapis.com/v1alpha/"

    @VisibleForTesting
    var baseUrl: String = DEFAULT_BASE_URL
        set(value) {
            synchronized(this) {
                field = value
                // Invalidate the client so it gets recreated with the new URL
                _client = null
            }
        }

    @Volatile
    private var _client: JulesApi? = null

    private val client: JulesApi
        get() {
            return _client ?: synchronized(this) {
                _client ?: buildClient().also { _client = it }
            }
        }

    private fun buildClient(): JulesApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("X-Goog-Api-Key")
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor)
            .addInterceptor(RetryInterceptor())
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val contentType = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        return retrofit.create(JulesApi::class.java)
    }

    override suspend fun listSessions(pageSize: Int, pageToken: String?) =
        client.listSessions(pageSize, pageToken)

    override suspend fun createSession(request: CreateSessionRequest): Session =
        client.createSession(request)

    override suspend fun sendMessage(sessionId: String, request: SendMessageRequest) =
        client.sendMessage(sessionId, request)

    override suspend fun listActivities(sessionId: String, pageSize: Int, pageToken: String?) =
        client.listActivities(sessionId, pageSize, pageToken)

    override suspend fun listSources(pageSize: Int, pageToken: String?) =
        client.listSources(pageSize, pageToken)
}
