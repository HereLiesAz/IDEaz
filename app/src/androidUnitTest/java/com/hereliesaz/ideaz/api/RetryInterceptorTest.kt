package com.hereliesaz.ideaz.api

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RetryInterceptorTest {

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

    private fun client(maxRetries: Int) = OkHttpClient.Builder()
        .addInterceptor(RetryInterceptor(maxRetries = maxRetries, initialDelayMs = 1, maxDelayMs = 5))
        .build()

    @Test
    fun exhaustedRetries_returnReadableResponse() {
        // maxRetries = 2 -> 3 attempts; every one is a retryable 503.
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(503).setBody("rate limited"))
        }

        val response = client(maxRetries = 2)
            .newCall(Request.Builder().url(server.url("/")).build())
            .execute()

        // Regression: on exhausted retries the final response must still be OPEN.
        // Previously it was close()d inside the loop before being returned, so
        // reading the body threw IllegalStateException("closed").
        assertEquals(503, response.code)
        assertEquals("rate limited", response.body.string())
        assertEquals(3, server.requestCount)
        response.close()
    }

    @Test
    fun retryableThenSuccess_returnsSuccess() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("try again"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = client(maxRetries = 3)
            .newCall(Request.Builder().url(server.url("/")).build())
            .execute()

        assertEquals(200, response.code)
        assertEquals("ok", response.body.string())
        assertEquals(2, server.requestCount)
        response.close()
    }

    @Test
    fun nonRetryableStatus_returnedImmediately() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))

        val response = client(maxRetries = 3)
            .newCall(Request.Builder().url(server.url("/")).build())
            .execute()

        assertEquals(404, response.code)
        assertEquals("nope", response.body.string())
        assertEquals(1, server.requestCount)
        response.close()
    }
}
