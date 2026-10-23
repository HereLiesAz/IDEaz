package com.hereliesaz.ideaz.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Asks the central HereLiesAz/workflows Worker to build and publish one pushed
 * commit (`POST /web-deploy`).
 *
 * The project carries no workflow of its own. The Worker checks that [token]
 * can push to the repository and that the repository belongs to the Worker's
 * owner, then dispatches its central `web-deploy.yml`, which builds the commit
 * and publishes only the build output to Cloudflare Pages. The outcome arrives
 * as the commit status named by [Result.Accepted.statusContext] on that SHA.
 * See the workflows repository's README, "Web deploy (IDEaz)".
 *
 * The token is sent once, in the Authorization header, over HTTPS; the Worker
 * uses it for a single read and does not keep it.
 */
class WebDeployClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: OkHttpClient = defaultClient,
) {

    sealed interface Result {
        /** The build was dispatched; [siteUrl] is where it will be served. */
        data class Accepted(val siteUrl: String, val statusContext: String) : Result

        /** The Worker refused (403: not the owner's repository, 401: bad token, ...). */
        data class Refused(val httpCode: Int, val message: String) : Result
    }

    @Serializable
    private data class DeployRequest(val repository: String, val sha: String)

    @Serializable
    private data class DeployResponse(
        val ok: Boolean = false,
        val error: String? = null,
        @SerialName("site_url") val siteUrl: String? = null,
        @SerialName("status_context") val statusContext: String? = null,
    )

    /**
     * @param repository `owner/name`
     * @param sha the full 40-character SHA that was just pushed
     * @throws java.io.IOException when the Worker cannot be reached
     */
    suspend fun requestDeploy(token: String, repository: String, sha: String): Result =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/web-deploy")
                .header("Authorization", "Bearer $token")
                .post(
                    json.encodeToString(DeployRequest.serializer(), DeployRequest(repository, sha))
                        .toRequestBody("application/json".toMediaType())
                )
                .build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body.string()
                val parsed = runCatching { json.decodeFromString(DeployResponse.serializer(), text) }.getOrNull()
                val siteUrl = parsed?.siteUrl
                val context = parsed?.statusContext
                if (response.isSuccessful && parsed?.ok == true && siteUrl != null && context != null) {
                    Result.Accepted(siteUrl, context)
                } else {
                    Result.Refused(response.code, parsed?.error ?: "HTTP ${response.code}")
                }
            }
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://workflows.hereliesaz.workers.dev"

        private val json = Json { ignoreUnknownKeys = true }

        // Not RetryInterceptor: a POST that times out after the Worker already
        // dispatched would start a second build.
        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
