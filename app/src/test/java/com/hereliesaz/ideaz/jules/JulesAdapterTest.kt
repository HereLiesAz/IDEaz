package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.ai.TaskEvent
import com.hereliesaz.ideaz.api.Activity
import com.hereliesaz.ideaz.api.AgentMessaged
import com.hereliesaz.ideaz.api.Artifact
import com.hereliesaz.ideaz.api.ChangeSet
import com.hereliesaz.ideaz.api.CreateSessionRequest
import com.hereliesaz.ideaz.api.GitPatch
import com.hereliesaz.ideaz.api.ListActivitiesResponse
import com.hereliesaz.ideaz.api.ListSessionsResponse
import com.hereliesaz.ideaz.api.ListSourcesResponse
import com.hereliesaz.ideaz.api.PullRequest
import com.hereliesaz.ideaz.api.SendMessageRequest
import com.hereliesaz.ideaz.api.Session
import com.hereliesaz.ideaz.api.SessionOutput
import com.hereliesaz.ideaz.api.SourceContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JulesAdapterTest {

    private val sourceContext = SourceContext(source = "sources/github/user/repo")

    private fun activity(id: String, message: String? = null, patch: String? = null) = Activity(
        name = "activities/$id",
        id = id,
        createTime = "2026-01-01T00:00:00Z",
        originator = "AGENT",
        agentMessaged = message?.let { AgentMessaged(it) },
        artifacts = patch?.let { listOf(Artifact(changeSet = ChangeSet(gitPatch = GitPatch(unidiffPatch = it)))) },
    )

    /** Fake that returns the same activity page on every poll (exercises dedup). */
    private class FakeClient(
        private val activities: List<Activity>,
        private val sessionOutputs: List<SessionOutput>? = null,
    ) : IJulesApiClient {
        var created: CreateSessionRequest? = null
        var sentTo: String? = null
        var sentMessage: SendMessageRequest? = null

        override suspend fun listSessions(pageSize: Int, pageToken: String?) =
            ListSessionsResponse(emptyList(), null)

        override suspend fun createSession(request: CreateSessionRequest): Session {
            created = request
            return Session(name = "sessions/s1", id = "s1", prompt = request.prompt, sourceContext = request.sourceContext)
        }

        override suspend fun getSession(sessionId: String): Session =
            Session(name = "sessions/$sessionId", id = sessionId, prompt = "", sourceContext = SourceContext(source = "s"), outputs = sessionOutputs)

        override suspend fun sendMessage(sessionId: String, request: SendMessageRequest) {
            sentTo = sessionId
            sentMessage = request
        }

        override suspend fun listActivities(sessionId: String, pageSize: Int, pageToken: String?) =
            ListActivitiesResponse(activities, null)

        override suspend fun listSources(pageSize: Int, pageToken: String?) =
            ListSourcesResponse(emptyList(), null)
    }

    @Test
    fun `new session emits SessionStarted then message, patch, and TimedOut once`() = runTest {
        val client = FakeClient(listOf(activity("a1", message = "working on it", patch = "diff --git a b")))
        val adapter = JulesAdapter(client, pollDelayMs = 1, maxPollAttempts = 3)

        val events = adapter.dispatchTask("do the thing", sourceContext, existingSessionId = null).toList()

        // Session created with our prompt + context.
        assertEquals("do the thing", client.created?.prompt)
        // First event is the session; message and patch each appear exactly once (deduped across polls).
        assertTrue(events.first() is TaskEvent.SessionStarted)
        assertEquals("s1", (events.first() as TaskEvent.SessionStarted).session.id)
        assertEquals(1, events.count { it is TaskEvent.Message })
        assertEquals(1, events.count { it is TaskEvent.Patch })
        assertEquals("diff --git a b", (events.first { it is TaskEvent.Patch } as TaskEvent.Patch).unidiff)
        assertEquals(TaskEvent.TimedOut, events.last())
    }

    @Test
    fun `existing session sends a message and does not create or emit SessionStarted`() = runTest {
        val client = FakeClient(emptyList())
        val adapter = JulesAdapter(client, pollDelayMs = 1, maxPollAttempts = 1)

        val events = adapter.dispatchTask("follow up", sourceContext, existingSessionId = "s-existing").toList()

        assertNull(client.created)
        assertEquals("s-existing", client.sentTo)
        assertEquals("follow up", client.sentMessage?.prompt)
        assertTrue(events.none { it is TaskEvent.SessionStarted })
        assertEquals(TaskEvent.TimedOut, events.last())
    }

    @Test
    fun `resuming a session does not re-emit or re-apply existing history`() = runTest {
        // The session already has a prior turn (message + patch). Resuming with a
        // new prompt must not replay it.
        val client = FakeClient(listOf(activity("old", message = "earlier turn", patch = "old diff")))
        val adapter = JulesAdapter(client, pollDelayMs = 1, maxPollAttempts = 2)

        val events = adapter.dispatchTask("again", sourceContext, existingSessionId = "s9").toList()

        assertTrue(events.none { it is TaskEvent.Patch })
        assertTrue(events.none { it is TaskEvent.Message })
        assertEquals(TaskEvent.TimedOut, events.last())
    }

    @Test
    fun `emits PullRequest and stops polling once a PR appears in session outputs`() = runTest {
        val pr = PullRequest(url = "https://github.com/o/r/pull/7", title = "Add feature", description = "body")
        val client = FakeClient(emptyList(), sessionOutputs = listOf(SessionOutput(pullRequest = pr)))
        val adapter = JulesAdapter(client, pollDelayMs = 1, maxPollAttempts = 5)

        val events = adapter.dispatchTask("do the thing", sourceContext, existingSessionId = null).toList()

        val prEvent = events.filterIsInstance<TaskEvent.PullRequest>().single()
        assertEquals("https://github.com/o/r/pull/7", prEvent.url)
        assertEquals("Add feature", prEvent.title)
        // The PR is terminal — the loop returns without ever timing out.
        assertTrue(events.none { it is TaskEvent.TimedOut })
    }
}
