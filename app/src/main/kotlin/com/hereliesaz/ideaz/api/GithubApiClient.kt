package com.hereliesaz.ideaz.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    val private: Boolean = false,
    @SerialName("auto_init") val autoInit: Boolean = true
)

@Serializable
data class GitHubRepoResponse(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("clone_url") val cloneUrl: String,
    @SerialName("default_branch") val defaultBranch: String? = "main",
    val permissions: GitHubPermissions? = null
)

@Serializable
data class GitHubPermissions(
    val admin: Boolean,
    val maintain: Boolean? = false,
    val push: Boolean,
    val triage: Boolean? = false,
    val pull: Boolean
)

@Serializable
data class GitHubBranchProtection(
    @SerialName("required_status_checks") val requiredStatusChecks: RequiredStatusChecks? = null,
    @SerialName("enforce_admins") val enforceAdmins: EnforceAdmins? = null,
    @SerialName("required_pull_request_reviews") val requiredPullRequestReviews: RequiredPullRequestReviews? = null
)

@Serializable
data class RequiredStatusChecks(
    val strict: Boolean,
    val contexts: List<String>
)

@Serializable
data class EnforceAdmins(
    val enabled: Boolean
)

@Serializable
data class RequiredPullRequestReviews(
    @SerialName("dismiss_stale_reviews") val dismissStaleReviews: Boolean? = false,
    @SerialName("require_code_owner_reviews") val requireCodeOwnerReviews: Boolean? = false,
    @SerialName("required_approving_review_count") val requiredApprovingReviewCount: Int? = 0
)

// --- NEW: Issue Data Classes ---
@Serializable
data class CreateIssueRequest(
    val title: String,
    val body: String,
    val labels: List<String> = listOf("bug", "automated-report")
)

@Serializable
data class GitHubIssueResponse(
    val number: Int,
    @SerialName("html_url") val htmlUrl: String
)
// --- END NEW ---

interface GitHubApi {
    @POST("user/repos")
    suspend fun createRepo(@Body request: CreateRepoRequest): GitHubRepoResponse

    // --- NEW: Create Issue Endpoint ---
    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateIssueRequest
    ): GitHubIssueResponse
    // --- END NEW ---

    @retrofit2.http.GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRepoResponse

    @retrofit2.http.GET("repos/{owner}/{repo}/branches/{branch}/protection")
    suspend fun getBranchProtection(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String
    ): GitHubBranchProtection
}

object GitHubApiClient {
    private const val BASE_URL = "https://api.github.com/"

    fun createService(token: String): GitHubApi {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "token $token")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        val json = Json { ignoreUnknownKeys = true }

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubApi::class.java)
    }
}