package com.hereliesaz.ideaz.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface GitHubService {
    @GET("user")
    suspend fun getUser(): GitHubUser

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("branch") branch: String? = null,
        @Query("event") event: String? = "push",
        @Query("per_page") perPage: Int = 1
    ): WorkflowRunsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun getRunArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): ArtifactsResponse

    @POST("user/repos")
    suspend fun createRepo(@Body request: CreateRepoRequest): GitHubRepoResponse

    @POST("repos/{owner}/{repo}/forks")
    suspend fun forkRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: ForkRepoRequest
    ): GitHubRepoResponse

    @GET("user/repos")
    suspend fun listRepos(
        @Query("type") type: String = "owner",
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100
    ): List<GitHubRepoResponse>

    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateIssueRequest
    ): GitHubIssueResponse

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitHubRelease>

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
}

// --- MODELS ---
@Serializable
data class GitHubUser(val login: String)

@Serializable
data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    val private: Boolean = false,
    @SerialName("auto_init") val autoInit: Boolean = true
)

@Serializable
data class ForkRepoRequest(val organization: String? = null)

@Serializable
data class CreateIssueRequest(
    val title: String,
    val body: String?,
    val labels: List<String> = emptyList()
)

@Serializable
data class GitHubIssueResponse(val id: Long, val number: Int, val title: String, @SerialName("html_url") val htmlUrl: String)

@Serializable
data class GitHubRepoResponse(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val private: Boolean,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("default_branch") val defaultBranch: String?
)

@Serializable
data class WorkflowRunsResponse(
    @SerialName("total_count") val totalCount: Int,
    @SerialName("workflow_runs") val workflowRuns: List<WorkflowRun>
)

@Serializable
data class WorkflowRun(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    @SerialName("html_url") val htmlUrl: String
)

@Serializable
data class ArtifactsResponse(
    @SerialName("total_count") val totalCount: Int,
    val artifacts: List<GitHubArtifact>
)

@Serializable
data class GitHubArtifact(
    val id: Long,
    val name: String,
    @SerialName("archive_download_url") val archiveDownloadUrl: String,
    val expired: Boolean
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GitHubAsset>
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String
)

object GitHubApiClient {
    private const val BASE_URL = "https://api.github.com/"

    fun createService(token: String?): GitHubService {
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .apply {
                        if (!token.isNullOrBlank()) {
                            addHeader("Authorization", "token $token")
                        }
                    }
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val json = Json { ignoreUnknownKeys = true }

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubService::class.java)
    }
}
