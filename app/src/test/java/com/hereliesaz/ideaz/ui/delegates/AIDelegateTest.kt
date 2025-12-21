package com.hereliesaz.ideaz.ui.delegates

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hereliesaz.ideaz.api.*
import com.hereliesaz.ideaz.jules.IJulesApiClient
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AIDelegateTest {

    private lateinit var aiDelegate: AIDelegate
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var mockApiClient: MockJulesApiClient

    class MockJulesApiClient : IJulesApiClient {
        var sessions = listOf<Session>()
        var createdSession: Session? = null
        var lastMessage: SendMessageRequest? = null

        override suspend fun listSessions(pageSize: Int, pageToken: String?): ListSessionsResponse {
            return ListSessionsResponse(sessions, null)
        }

        override suspend fun createSession(request: CreateSessionRequest): Session {
            createdSession = Session(
                id = "new_session_id",
                name = request.title ?: "sessions/new",
                prompt = request.prompt,
                sourceContext = request.sourceContext,
                title = request.title
            )
            return createdSession!!
        }

        override suspend fun sendMessage(sessionId: String, request: SendMessageRequest) {
            lastMessage = request
        }

        override suspend fun listActivities(sessionId: String, pageSize: Int, pageToken: String?): ListActivitiesResponse {
            return ListActivitiesResponse(emptyList(), null)
        }

        override suspend fun listSources(pageSize: Int, pageToken: String?): ListSourcesResponse {
            return ListSourcesResponse(emptyList(), null)
        }
    }

    @Before
    fun setUp() {
        settingsViewModel = SettingsViewModel(ApplicationProvider.getApplicationContext())
        mockApiClient = MockJulesApiClient()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        aiDelegate = AIDelegate(settingsViewModel, scope, {}, { true }, mockApiClient)
    }

    @Test
    fun testResumeSession() {
        aiDelegate.resumeSession("session_123")
        assertEquals("session_123", aiDelegate.currentJulesSessionId.value)
    }

    @Test
    fun testFetchSessionsForRepo() = runBlocking {
        val session1 = Session(
            id = "s1",
            name = "sessions/s1",
            prompt = "p1",
            sourceContext = SourceContext(source = "sources/github/user/repo")
        )
        val session2 = Session(
            id = "s2",
            name = "sessions/s2",
            prompt = "p2",
            sourceContext = SourceContext(source = "sources/github/other/repo")
        )
        mockApiClient.sessions = listOf(session1, session2)

        settingsViewModel.setGithubUser("user")
        aiDelegate.fetchSessionsForRepo("repo")

        val sessions = aiDelegate.sessions.value
        assertEquals(1, sessions.size)
        assertEquals("s1", sessions[0].id)
    }
}
