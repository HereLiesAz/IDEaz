package com.hereliesaz.ideaz.azphalt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Client for azphalt.store or any conforming Azphalt Repository API, limited to what IDEaz needs
 * for LLM packages: index, search, detail, download and revocations.
 */
class AzphaltRepositoryClient internal constructor(
    repositoryUrl: String,
    private val httpClient: OkHttpClient,
    private val maxDownloadBytes: Int,
    private val maxTextBytes: Int,
    /** Tests only: MockWebServer speaks plain HTTP. */
    allowPlainHttp: Boolean,
) {
    constructor(
        repositoryUrl: String = AZPHALT_STORE_URL,
        httpClient: OkHttpClient = defaultClient,
    ) : this(repositoryUrl, httpClient, ZipAzphaltArchiveReader.DEFAULT_MAX_ARCHIVE_BYTES, DEFAULT_MAX_TEXT_BYTES, false)

    private val json: Json = Json { ignoreUnknownKeys = true }

    val repositoryUrl: String =
        if (allowPlainHttp) repositoryUrl.trim().trimEnd('/') else normalizeRepositoryUrl(repositoryUrl)

    suspend fun index(): AzphaltRepositoryIndex =
        json.decodeFromString(getText("$repositoryUrl/.well-known/azphalt-repository.json", "read Azphalt repository index"))

    /**
     * Searches `kind=llm` without `?app=`: IDEaz accepts packages for either itself or The Aive, and
     * a single app scope would hide the other. [targetsIdeaz] is applied locally instead.
     */
    suspend fun searchLlms(query: String = ""): AzphaltPackageSearchResponse {
        val url = "$repositoryUrl/packages".toHttpUrl().newBuilder()
            .addQueryParameter("kind", "llm")
            .addQueryParameter("q", query.trim())
            .build()
        val response = json.decodeFromString<AzphaltPackageSearchResponse>(getText(url.toString(), "search Azphalt repository"))
        val packages = response.packages.filter { it.kind == "llm" && targetsIdeaz(it.targetApps) }
        return response.copy(packages = packages, total = packages.size)
    }

    suspend fun detail(packageId: String): AzphaltPackageDetail {
        val id = requirePackageId(packageId)
        return json.decodeFromString(getText("$repositoryUrl/packages/$id", "read Azphalt package $id"))
    }

    suspend fun download(packageId: String, version: String): ByteArray = withContext(Dispatchers.IO) {
        val id = requirePackageId(packageId)
        val safeVersion = requireVersion(version)
        val request = Request.Builder().url("$repositoryUrl/packages/$id/versions/$safeVersion/download").build()
        httpClient.newCall(request).execute().use { response ->
            when (response.code) {
                401 -> throw AzphaltRepositoryException.AuthenticationRequired(
                    response.errorMessage("Authentication required for $id@$safeVersion"),
                )
                402 -> throw AzphaltRepositoryException.PaymentRequired(
                    response.errorMessage("Payment or package entitlement required for $id@$safeVersion"),
                )
                else -> response.requireSuccess("download Azphalt package $id@$safeVersion")
            }
            response.readCapped(maxDownloadBytes, "Azphalt package $id@$safeVersion exceeds the download size limit of $maxDownloadBytes bytes")
        }
    }

    suspend fun revocations(): List<AzphaltRevocation> =
        json.decodeFromString<AzphaltRevocationsResponse>(getText("$repositoryUrl/revocations", "read Azphalt revocations")).revocations

    private suspend fun getText(url: String, action: String): String = withContext(Dispatchers.IO) {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            response.requireSuccess(action)
            response.readCapped(maxTextBytes, "Azphalt response exceeds $maxTextBytes bytes").decodeToString()
        }
    }

    private fun Response.requireSuccess(action: String) {
        if (isSuccessful) return
        throw AzphaltRepositoryException.Http(code, errorMessage("Unable to $action ($code)"))
    }

    private fun Response.errorMessage(fallback: String): String =
        runCatching { readCapped(4096, "").decodeToString().trim() }.getOrNull()?.takeIf(String::isNotEmpty)?.take(500)
            ?: fallback

    // Read under a cap so a hostile repository cannot exhaust memory before the archive limits apply.
    private fun Response.readCapped(limit: Int, tooLarge: String): ByteArray {
        val body = body
        val declared = body.contentLength()
        require(declared < 0 || declared <= limit) { tooLarge }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        body.byteStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                require(read <= limit - out.size()) { tooLarge }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    companion object {
        const val DEFAULT_MAX_TEXT_BYTES: Int = 4 * 1024 * 1024
        private val packageIdPattern = Regex("^[A-Za-z0-9](?:[A-Za-z0-9._-]{0,253}[A-Za-z0-9])?$")
        private val versionPattern = Regex("^[A-Za-z0-9][A-Za-z0-9.+_-]{0,127}$")

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        fun normalizeRepositoryUrl(value: String): String {
            val normalized = value.trim().trimEnd('/')
            require(normalized.startsWith("https://", ignoreCase = true)) { "Azphalt repository URL must use HTTPS" }
            return normalized
        }

        fun requirePackageId(value: String): String {
            val normalized = value.trim()
            require(packageIdPattern.matches(normalized)) { "Invalid Azphalt package id" }
            return normalized
        }

        fun requireVersion(value: String): String {
            val normalized = value.trim()
            require(versionPattern.matches(normalized)) { "Invalid Azphalt package version" }
            return normalized
        }
    }
}
