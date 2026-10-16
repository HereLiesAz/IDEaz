package com.hereliesaz.ideaz.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WebDeployClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = WebDeployClient(server.url("/").toString(), OkHttpClient())

    @Test
    fun acceptedResponseCarriesSiteUrlAndStatusContext() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(202).setBody(
                """{"ok":true,"repository":"HereLiesAz/site","sha":"${"a".repeat(40)}",""" +
                    """"status_context":"ideaz/web-deploy","site_url":"https://ideaz-site-1.pages.dev"}"""
            )
        )
        val result = client().requestDeploy("tok", "HereLiesAz/site", "a".repeat(40))
        assertEquals(WebDeployClient.Result.Accepted("https://ideaz-site-1.pages.dev", "ideaz/web-deploy"), result)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/web-deploy", request.path)
        assertEquals("Bearer tok", request.getHeader("Authorization"))
        assertEquals("""{"repository":"HereLiesAz/site","sha":"${"a".repeat(40)}"}""", request.body.readUtf8())
    }

    @Test
    fun refusalCarriesTheWorkersReason() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"ok":false,"error":"Central web deploy serves only repositories owned by HereLiesAz"}""")
        )
        val result = client().requestDeploy("tok", "someone/site", "b".repeat(40))
        assertEquals(
            WebDeployClient.Result.Refused(403, "Central web deploy serves only repositories owned by HereLiesAz"),
            result,
        )
    }

    @Test
    fun nonJsonFailureIsStillARefusal() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("Bad gateway"))
        assertEquals(
            WebDeployClient.Result.Refused(502, "HTTP 502"),
            client().requestDeploy("tok", "HereLiesAz/site", "c".repeat(40)),
        )
    }
}
