package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.api.*

interface IJulesApiClient {
    suspend fun listSessions(pageSize: Int = 100, pageToken: String? = null): ListSessionsResponse
    suspend fun createSession(request: CreateSessionRequest): Session
    suspend fun sendMessage(sessionId: String, request: SendMessageRequest)
    suspend fun listActivities(sessionId: String, pageSize: Int = 100, pageToken: String? = null): ListActivitiesResponse
    suspend fun listSources(pageSize: Int = 100, pageToken: String? = null): ListSourcesResponse
}
