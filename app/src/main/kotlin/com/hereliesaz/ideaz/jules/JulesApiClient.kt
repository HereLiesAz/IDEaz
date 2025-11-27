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
    private const val PARENT = "projects/ideaz-336316"

    private fun getProjectId(parent: String): String {
        return parent.substringAfterLast("/")
    }

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

    /**
     * Creates a new Jules session.
     */
    suspend fun createSession(parent: String, request: CreateSessionRequest): Session {
        val projectId = getProjectId(parent)
        return getClient().createSession(projectId, request)
    }

    /**
     * Lists activities for a given session.
     */
    suspend fun listActivities(parent: String, sessionId: String): ListActivitiesResponse {
        val projectId = getProjectId(parent)
        return getClient().listActivities(projectId, sessionId)
    }

    suspend fun sendMessage(sessionId: String, prompt: String) {
        val request = SendMessageRequest(prompt = prompt)
        val projectId = getProjectId(PARENT)
        getClient().sendMessage(projectId, sessionId, request)
    }

    // Used by MainViewModel
    suspend fun listSessions(parent: String): ListSessionsResponse {
        val projectId = getProjectId(parent)
        return getClient().listSessions(projectId)
    }

    suspend fun listSources(parent: String): ListSourcesResponse {
        val projectId = getProjectId(parent)
        return getClient().listSources(projectId)
    }

    suspend fun getSource(parent: String, sourceId: String): Source {
        val projectId = getProjectId(parent)
        return getClient().getSource(projectId, sourceId)
    }

    suspend fun getSession(parent: String, sessionId: String): Session {
        val projectId = getProjectId(parent)
        return getClient().getSession(projectId, sessionId)
    }

    suspend fun deleteSession(parent: String, sessionId: String) {
        val projectId = getProjectId(parent)
        getClient().deleteSession(projectId, sessionId)
    }
}
