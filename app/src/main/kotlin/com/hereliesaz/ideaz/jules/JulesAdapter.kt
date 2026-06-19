package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.ai.AgenticAiClient
import com.hereliesaz.ideaz.ai.TaskEvent
import com.hereliesaz.ideaz.api.Activity
import com.hereliesaz.ideaz.api.CreateSessionRequest
import com.hereliesaz.ideaz.api.SendMessageRequest
import com.hereliesaz.ideaz.api.SourceContext
import kotlinx.coroutines.CancellationException
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
        // Each activity is emitted at most once. For a resumed session we seed this
        // with the session's existing history first, so we don't re-emit (and
        // re-apply) past turns' messages and patches.
        val processed = mutableSetOf<String>()

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
            runCatching { getAllActivities(existingSessionId) }
                .getOrDefault(emptyList())
                .forEach { processed.add(it.id) }
            client.sendMessage(existingSessionId, SendMessageRequest(prompt = prompt))
            existingSessionId
        }

        var attempts = 0
        while (attempts < maxPollAttempts) {
            delay(pollDelayMs)
            // Tolerate a transient poll failure: skip this round, keep waiting,
            // rather than killing the whole session on one network hiccup.
            val activities = try {
                getAllActivities(sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempts++
                continue
            }

            // Process in chronological order so patches apply oldest-first.
            for (activity in activities.sortedBy { it.createTime }) {
                if (!processed.add(activity.id)) continue
                activity.agentMessaged?.agentMessage
                    ?.takeIf { it.isNotBlank() }
                    ?.let { emit(TaskEvent.Message(it)) }
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
