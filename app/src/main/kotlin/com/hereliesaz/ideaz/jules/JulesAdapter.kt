package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.ai.AgenticAiClient
import com.hereliesaz.ideaz.ai.TaskEvent
import com.hereliesaz.ideaz.api.Activity
import com.hereliesaz.ideaz.api.CreateSessionRequest
import com.hereliesaz.ideaz.api.SendMessageRequest
import com.hereliesaz.ideaz.api.SourceContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * [AgenticAiClient] over the Jules REST API ([IJulesApiClient]). Creates or resumes
 * a session, then polls its activities, emitting a [TaskEvent] for each agent
 * message and patch. The session lifecycle and activity de-duplication live here
 * (single source of truth) so callers just collect the flow.
 *
 * Pure of Android dependencies, so it's unit-testable with a fake [IJulesApiClient].
 */
class JulesAdapter(
    private val client: IJulesApiClient = JulesApiClient,
    private val pollDelayMs: Long = POLL_DELAY_MS,
    private val maxPollAttempts: Int = MAX_POLL_ATTEMPTS,
) : AgenticAiClient {

    override fun dispatchTask(
        prompt: String,
        sourceContext: SourceContext,
        existingSessionId: String?,
    ): Flow<TaskEvent> = flow {
        val sessionId = if (existingSessionId == null) {
            val session = client.createSession(
                CreateSessionRequest(
                    prompt = prompt,
                    sourceContext = sourceContext,
                    title = "Session ${System.currentTimeMillis()}",
                )
            )
            emit(TaskEvent.SessionStarted(session))
            session.id
        } else {
            client.sendMessage(existingSessionId, SendMessageRequest(prompt = prompt))
            existingSessionId
        }

        // Mark each activity processed once: records are immutable, so re-applying a
        // patch that already failed only spams the user. Dedup messages by content.
        val processed = mutableSetOf<String>()
        var lastMessage: String? = null
        var attempts = 0
        while (attempts < maxPollAttempts) {
            delay(pollDelayMs)
            val activities = getAllActivities(sessionId)

            activities.firstOrNull { it.agentMessaged != null }
                ?.agentMessaged?.agentMessage
                ?.takeIf { it.isNotBlank() && it != lastMessage }
                ?.let { lastMessage = it; emit(TaskEvent.Message(it)) }

            for (activity in activities) {
                if (!processed.add(activity.id)) continue
                activity.artifacts?.forEach { artifact ->
                    artifact.changeSet?.gitPatch?.unidiffPatch
                        ?.takeIf { it.isNotBlank() }
                        ?.let { emit(TaskEvent.Patch(it)) }
                }
            }
            attempts++
        }
        emit(TaskEvent.TimedOut)
    }

    private suspend fun getAllActivities(sessionId: String): List<Activity> {
        val all = mutableListOf<Activity>()
        var pageToken: String? = null
        do {
            val response = client.listActivities(sessionId, pageToken = pageToken)
            response.activities?.let { all.addAll(it) }
            pageToken = response.nextPageToken
        } while (pageToken != null)
        return all
    }

    companion object {
        const val POLL_DELAY_MS = 3_000L
        const val MAX_POLL_ATTEMPTS = 15 // ~45s total
    }
}
