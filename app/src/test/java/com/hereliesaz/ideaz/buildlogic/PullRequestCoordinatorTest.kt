package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.api.GitHubApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class PullRequestCoordinatorTest {

    private lateinit var server: MockWebServer
    private lateinit var api: GitHubApi

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubApi::class.java)
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun parsesHtmlAndApiPullRequestUrls() {
        val html = PullRequestCoordinator.parsePullRequestUrl("https://github.com/octocat/hello/pull/42")!!
        assertEquals("octocat", html.owner)
        assertEquals("hello", html.repo)
        assertEquals(42, html.number)

        val apiUrl = PullRequestCoordinator.parsePullRequestUrl("https://api.github.com/repos/octocat/hello/pulls/7")!!
        assertEquals("octocat", apiUrl.owner)
        assertEquals("hello", apiUrl.repo)
        assertEquals(7, apiUrl.number)

        assertNull(PullRequestCoordinator.parsePullRequestUrl("https://example.com/not/a/pr"))
    }

    @Test
    fun mergesAnOpenPrAndReturnsTheMergeSha() = runBlocking {
        // getPullRequest (open, not merged) then PUT merge → 200 with sha.
        server.enqueue(MockResponse().setBody("""{"number":42,"state":"open","merged":false,"head":{"ref":"jules","sha":"abc"},"html_url":"u"}"""))
        server.enqueue(MockResponse().setBody("""{"sha":"mergesha123","merged":true,"message":"Merged"}"""))

        val sha = PullRequestCoordinator(api).mergeAndGetSha("https://github.com/o/r/pull/42")

        assertEquals("mergesha123", sha)
    }

    @Test
    fun returnsExistingMergeShaWhenAlreadyMerged() = runBlocking {
        // Idempotent retry path: PR already merged → reuse its commit, no PUT issued.
        server.enqueue(MockResponse().setBody("""{"number":42,"state":"closed","merged":true,"merge_commit_sha":"already","head":{"ref":"j","sha":"a"},"html_url":"u"}"""))

        val sha = PullRequestCoordinator(api).mergeAndGetSha("https://github.com/o/r/pull/42")

        assertEquals("already", sha)
    }

    @Test
    fun returnsNullOnUnparseableUrl() = runBlocking {
        assertNull(PullRequestCoordinator(api).mergeAndGetSha("not a url"))
    }

    @Test
    fun returnsNullWhenMergeFails() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"number":1,"state":"open","merged":false,"head":{"ref":"j","sha":"a"},"html_url":"u"}"""))
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"message":"conflict"}"""))

        val sha = PullRequestCoordinator(api).mergeAndGetSha("https://github.com/o/r/pull/1")

        assertNull(sha)
    }
}
