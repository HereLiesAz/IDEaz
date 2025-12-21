package com.hereliesaz.ideaz.jules

import com.hereliesaz.ideaz.api.CreateSessionRequest
import com.hereliesaz.ideaz.api.GitHubRepoContext
import com.hereliesaz.ideaz.api.SendMessageRequest
import com.hereliesaz.ideaz.api.SourceContext
import com.hereliesaz.ideaz.api.AuthInterceptor
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class JulesApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private var originalApiKey: String? = null
    private var originalBaseUrl: String? = null

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Store original values
        originalBaseUrl = JulesApiClient.baseUrl
        originalApiKey = AuthInterceptor.apiKey

        // Configure JulesApiClient to use the mock server
        JulesApiClient.baseUrl = mockWebServer.url("/").toString()
        AuthInterceptor.apiKey = "TEST_API_KEY"
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()

        // Restore original values to prevent side effects on other tests
        originalBaseUrl?.let { JulesApiClient.baseUrl = it }
        AuthInterceptor.apiKey = originalApiKey
    }

    @Test
    fun `listSessions sends correct request and parses response`() = runBlocking {
        // Prepare Mock Response
        val mockResponseJson = """
            {
              "sessions": [
                {
                  "name": "projects/p1/locations/l1/sessions/s1",
                  "id": "s1",
                  "prompt": "test prompt",
                  "sourceContext": { "source": "s1" }
                }
              ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(mockResponseJson).setResponseCode(200))

        // Execute
        val response = JulesApiClient.listSessions()

        // Verify Request
        val request = mockWebServer.takeRequest()
        assertEquals("/sessions?pageSize=100", request.path)
        assertEquals("TEST_API_KEY", request.getHeader("X-Goog-Api-Key"))

        // Verify Response
        assertNotNull(response.sessions)
        assertEquals(1, response.sessions?.size)
        assertEquals("s1", response.sessions?.get(0)?.id)
    }

    @Test
    fun `createSession sends correct request`() = runBlocking {
        val mockResponseJson = """
            {
              "name": "projects/p1/locations/l1/sessions/new_session",
              "id": "new_session",
              "prompt": "create prompt",
              "sourceContext": { "source": "src1" }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(mockResponseJson).setResponseCode(200))

        val req = CreateSessionRequest(
            prompt = "create prompt",
            sourceContext = SourceContext(
                source = "src1",
                githubRepoContext = GitHubRepoContext("main")
            )
        )

        val session = JulesApiClient.createSession(req)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/sessions", recordedRequest.path)
        assertTrue(recordedRequest.body.readUtf8().contains("create prompt"))
        assertEquals("new_session", session.id)
    }

    @Test
    fun `sendMessage sends correct request`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val req = SendMessageRequest("follow up")
        JulesApiClient.sendMessage("s1", req)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/sessions/s1:sendMessage", recordedRequest.path)
    }
}
