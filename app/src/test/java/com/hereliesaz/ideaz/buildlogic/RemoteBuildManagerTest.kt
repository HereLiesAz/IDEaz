package com.hereliesaz.ideaz.buildlogic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hereliesaz.ideaz.api.*
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class RemoteBuildManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    class FakeGitHubApi(
        private val runs: List<GitHubWorkflowRun>,
        private val artifacts: List<GitHubArtifact>
    ) : GitHubApi {
        override suspend fun createRepo(request: CreateRepoRequest) = throw NotImplementedError()
        override suspend fun forkRepo(owner: String, repo: String, request: ForkRepoRequest) = throw NotImplementedError()
        override suspend fun getRepoPublicKey(owner: String, repo: String) = throw NotImplementedError()
        override suspend fun createSecret(owner: String, repo: String, secretName: String, request: CreateSecretRequest) = Response.success(Unit)
        override suspend fun createIssue(owner: String, repo: String, request: CreateIssueRequest) = throw NotImplementedError()
        override suspend fun getRepo(owner: String, repo: String) = throw NotImplementedError()
        override suspend fun getBranchProtection(owner: String, repo: String, branch: String) = throw NotImplementedError()
        override suspend fun getReleases(owner: String, repo: String) = throw NotImplementedError()
        override suspend fun getArtifacts(owner: String, repo: String) = throw NotImplementedError()
        override suspend fun getPages(owner: String, repo: String) = Response.success(GitHubPagesResponse("built", "https://example.com"))

        override suspend fun listWorkflowRuns(owner: String, repo: String, headSha: String?, perPage: Int): GitHubWorkflowRunsResponse {
            return GitHubWorkflowRunsResponse(runs.size, runs)
        }

        override suspend fun getRun(owner: String, repo: String, runId: Long): GitHubWorkflowRun {
            return runs.firstOrNull { it.id == runId }
                ?: throw NoSuchElementException("Run $runId not in fake")
        }

        override suspend fun getRunArtifacts(owner: String, repo: String, runId: Long): GitHubArtifactsResponse {
            return GitHubArtifactsResponse(artifacts.size, artifacts)
        }

        override suspend fun getRunJobs(owner: String, repo: String, runId: Long) = GitHubJobsResponse(emptyList())
        override suspend fun getJobLogs(owner: String, repo: String, jobId: Long) = Response.success(ResponseBody.create(null, ""))
        override suspend fun listRepos() = emptyList<GitHubRepoResponse>()
    }

    @Test
    fun testPollAndDownload_success() = runBlocking {
        // Prepare Mock Web Server to serve a ZIP file containing an APK
        val zipContent = createZipWithApk("app-debug.apk")
        mockWebServer.enqueue(MockResponse().setBody(okio.Buffer().write(zipContent)))
        val downloadUrl = mockWebServer.url("/artifact.zip").toString()

        val runs = listOf(
            GitHubWorkflowRun(1, "Build", "sha123", "completed", "success", "url")
        )
        val artifacts = listOf(
            GitHubArtifact(100, "app-debug.apk", downloadUrl, "now", 1024, false)
        )

        val fakeApi = FakeGitHubApi(runs, artifacts)
        val manager = RemoteBuildManager(context, fakeApi, "token", "user", "repo") { println(it) }

        val resultPath = manager.pollAndDownload("sha123")

        assertNotNull("Result path should not be null", resultPath)
        assertTrue("Path should end with apk", resultPath!!.endsWith(".apk"))
        assertTrue("File should exist", File(resultPath).exists())
    }

    /**
     * Regression test for the credential-leak fix in [RemoteBuildManager.downloadFileWithAuth].
     * The first request (to GitHub) MUST carry the Authorization bearer token.
     * The second request (to the redirected presigned URL) MUST NOT.
     */
    @Test
    fun testPollAndDownload_dropsAuthOnRedirect() = runBlocking {
        // First response: 302 to a second mockwebserver path.
        // Second response: the actual zip body.
        val zipContent = createZipWithApk("app-debug.apk")
        val redirectTarget = mockWebServer.url("/presigned").toString()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", redirectTarget)
        )
        mockWebServer.enqueue(MockResponse().setBody(okio.Buffer().write(zipContent)))

        val downloadUrl = mockWebServer.url("/artifact.zip").toString()
        val runs = listOf(
            GitHubWorkflowRun(2, "Build", "sha456", "completed", "success", "url")
        )
        val artifacts = listOf(
            GitHubArtifact(101, "app-debug.apk", downloadUrl, "now", 2048, false)
        )

        val fakeApi = FakeGitHubApi(runs, artifacts)
        val manager = RemoteBuildManager(context, fakeApi, "secret-token", "user", "repo") { println(it) }

        val resultPath = manager.pollAndDownload("sha456")
        assertNotNull("Result path should not be null", resultPath)

        // Inspect what the server actually saw.
        val firstReq = mockWebServer.takeRequest()
        val secondReq = mockWebServer.takeRequest()

        assertEquals("First request hits the artifact URL", "/artifact.zip", firstReq.path)
        assertEquals(
            "First request includes the GitHub bearer token",
            "Bearer secret-token",
            firstReq.getHeader("Authorization")
        )

        assertEquals("Second request hits the presigned URL", "/presigned", secondReq.path)
        assertNull(
            "Second request MUST NOT include the bearer token (credential leak)",
            secondReq.getHeader("Authorization")
        )
    }

    private fun createZipWithApk(entryName: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(entryName))
            zos.write("fake apk content".toByteArray())
            zos.closeEntry()
        }
        return baos.toByteArray()
    }
}
