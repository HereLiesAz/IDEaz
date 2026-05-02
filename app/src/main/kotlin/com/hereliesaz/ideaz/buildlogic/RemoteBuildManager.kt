package com.hereliesaz.ideaz.buildlogic

import android.content.Context
import com.hereliesaz.ideaz.api.GitHubApi
import com.hereliesaz.ideaz.api.GitHubArtifact
import com.hereliesaz.ideaz.api.GitHubWorkflowRun
import com.hereliesaz.ideaz.utils.ApkInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/**
 * Drives the remote half of the "Race to Build" feature: watch GitHub Actions
 * for the workflow run that builds the user's APK, wait for it to finish,
 * download the resulting artifact, and install it.
 *
 * Designed to be cooperative with [kotlinx.coroutines] cancellation: if the
 * coroutine that called [pollAndDownload] is cancelled, the polling loop and
 * downloads abort at the next checkpoint.
 *
 * @param workflowNameHint Optional substring to disambiguate when multiple
 *   workflows run on the same SHA (e.g., "build", "android", "ci"). The first
 *   run whose [GitHubWorkflowRun.name] contains this substring (case-insensitive)
 *   wins. If null, the most recent run for the SHA wins.
 */
class RemoteBuildManager(
    private val context: Context,
    private val api: GitHubApi,
    private val token: String,
    private val user: String,
    private val repo: String,
    private val onLog: (String) -> Unit,
    private val workflowNameHint: String? = null,
) {

    /**
     * Find the workflow run for [headSha], poll until it finishes, download
     * the resulting APK artifact, and install it.
     *
     * @return absolute path to the installed APK, or null if anything failed.
     */
    suspend fun pollAndDownload(headSha: String): String? {
        onLog("Waiting for GitHub Action (HEAD: ${headSha.take(7)})...\n")

        val run = findRun(headSha) ?: run {
            onLog("Remote Build: No workflow run found for $headSha within timeout.\n")
            return null
        }

        val finalRun = pollUntilFinished(run.id) ?: return null

        if (finalRun.conclusion != "success") {
            onLog("Remote Build finished with status: ${finalRun.conclusion}\n")
            dumpFailureLogs(finalRun.id)
            return null
        }

        val apk = findApkArtifact(finalRun.id) ?: run {
            onLog("Remote Build: No APK artifact found.\n")
            return null
        }

        return downloadAndInstall(apk, headSha)
    }

    // -- step 1: find the run --------------------------------------------------

    /**
     * Look up the workflow run for this commit. Retries for ~60 s because the
     * action queue may not have indexed the new run yet. Falls back to the
     * most recent run if the SHA-filtered query keeps coming up empty.
     */
    private suspend fun findRun(headSha: String): GitHubWorkflowRun? {
        val maxAttempts = 20
        val delayMs = 3_000L

        repeat(maxAttempts) {
            if (!coroutineContext.isActive) return null
            val match = try {
                api.listWorkflowRuns(user, repo, headSha = headSha, perPage = 10)
                    .workflowRuns
                    .matchHint()
            } catch (_: Exception) { null }
            if (match != null) {
                onLog("Remote Build Started. Run ID: ${match.id} (${match.name})\n")
                return match
            }
            delay(delayMs)
        }

        // Fallback: the run might exist with a different head_sha (rebase, squash).
        // Pick the most recent that matches our hint.
        onLog("Remote Build: SHA-filtered lookup timed out, falling back to latest run...\n")
        return try {
            val recent = api.listWorkflowRuns(user, repo, headSha = null, perPage = 10).workflowRuns
            recent.matchHint()?.also {
                onLog("Remote Build Fallback: Using Run ID: ${it.id} (${it.name}, status=${it.status})\n")
            }
        } catch (e: Exception) {
            onLog("Remote Build Fallback Failed: ${e.message}\n")
            null
        }
    }

    private fun List<GitHubWorkflowRun>.matchHint(): GitHubWorkflowRun? {
        if (isEmpty()) return null
        val hint = workflowNameHint?.lowercase()
        return if (hint == null) first()
        else firstOrNull { it.name.lowercase().contains(hint) } ?: first()
    }

    // -- step 2: poll the run until it's done ----------------------------------

    /**
     * Poll [GitHubApi.getRun] every 5 s for up to 30 minutes. Returns the
     * final [GitHubWorkflowRun] state, or null on timeout / cancellation.
     *
     * Uses [GitHubApi.getRun] (single-run endpoint) instead of relisting,
     * because the list endpoint can paginate the run off page 1 when other
     * workflows fire concurrently — that bug previously caused this loop to
     * hang indefinitely.
     */
    private suspend fun pollUntilFinished(runId: Long): GitHubWorkflowRun? {
        val pollIntervalMs = 5_000L
        val timeoutMs = 30 * 60 * 1_000L  // 30 minutes
        val deadline = System.currentTimeMillis() + timeoutMs

        var lastStatus = ""
        var firstIteration = true
        while (System.currentTimeMillis() < deadline) {
            if (!coroutineContext.isActive) return null
            // Check status first; only delay between repeats. This way a run
            // that's already 'completed' by the time findRun returns finishes
            // immediately instead of waiting 5 seconds.
            if (!firstIteration) {
                delay(pollIntervalMs)
            }
            firstIteration = false

            val run = try {
                api.getRun(user, repo, runId)
            } catch (e: Exception) {
                if (lastStatus != "ERROR") {
                    onLog("Remote Build: poll error (${e.message}), retrying...\n")
                    lastStatus = "ERROR"
                }
                continue
            }

            if (run.status != lastStatus) {
                onLog("Remote Build status: ${run.status}\n")
                lastStatus = run.status
            }
            if (run.status == "completed") return run
        }

        onLog("Remote Build: poll timeout after ${timeoutMs / 60_000} minutes.\n")
        return null
    }

    // -- step 3: locate the APK artifact ---------------------------------------

    private suspend fun findApkArtifact(runId: Long): GitHubArtifact? {
        return try {
            val artifacts = api.getRunArtifacts(user, repo, runId).artifacts
            artifacts.firstOrNull { it.name.contains("debug") && it.name.endsWith("apk") }
                ?: artifacts.firstOrNull { it.name.endsWith(".apk") }
                ?: artifacts.firstOrNull { it.name == "app-debug" }
        } catch (e: Exception) {
            onLog("Remote Build: failed to list artifacts (${e.message}).\n")
            null
        }
    }

    private suspend fun dumpFailureLogs(runId: Long) {
        try {
            val jobs = api.getRunJobs(user, repo, runId).jobs
            val failed = jobs.find { it.conclusion == "failure" } ?: return
            val resp = api.getJobLogs(user, repo, failed.id)
            if (resp.isSuccessful) {
                onLog("Remote Build Log:\n${resp.body()?.string().orEmpty()}\n")
            }
        } catch (e: Exception) {
            onLog("Could not retrieve remote logs: ${e.message}\n")
        }
    }

    // -- step 4: download, validate, unzip, install ----------------------------

    private suspend fun downloadAndInstall(artifact: GitHubArtifact, headSha: String): String? {
        onLog("Remote Build Successful. Downloading artifact...\n")

        val shaTag = headSha.take(7)
        val zipFile = File(context.filesDir, "remote_build_$shaTag.zip")
        zipFile.delete()  // ensure fresh

        if (!downloadFileWithAuth(artifact.archiveDownloadUrl, zipFile)) {
            onLog("Remote Build: Failed to download artifact.\n")
            return null
        }

        if (zipFile.length() == 0L) {
            onLog("Remote Build: Downloaded artifact is empty.\n")
            zipFile.delete()
            return null
        }

        // Validate the zip header before extracting. ZipFile() throws fast on
        // a malformed or HTML-error-page response.
        try {
            ZipFile(zipFile).close()
        } catch (e: ZipException) {
            onLog("Remote Build: Downloaded artifact is not a valid zip (${e.message}).\n")
            zipFile.delete()
            return null
        }

        onLog("Unzipping artifact...\n")
        val destDir = File(context.filesDir, "remote_extracted_$shaTag")
        destDir.deleteRecursively()
        destDir.mkdirs()

        return try {
            extractZip(zipFile, destDir)
            val apkFile = destDir.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".apk") }
            if (apkFile == null) {
                onLog("Remote Build: APK file not found in artifact zip.\n")
                null
            } else {
                onLog("Installing Remote APK: ${apkFile.name}\n")
                ApkInstaller.installApk(context, apkFile.absolutePath)
                apkFile.absolutePath
            }
        } catch (e: Exception) {
            onLog("Remote Build Error during extraction: ${e.message}\n")
            null
        } finally {
            zipFile.delete()
        }
    }

    /**
     * Extract a zip with proper Zip Slip protection. The previous prefix
     * check was vulnerable to a sibling-directory bypass: a destination of
     * `/foo/abc` and entry name `../abcXXX/evil` would canonicalise to
     * `/foo/abcXXX/evil` whose path *does* startsWith `/foo/abc`. Adding the
     * trailing separator closes that hole.
     */
    private suspend fun extractZip(zipFile: File, destDir: File) = withContext(Dispatchers.IO) {
        val canonicalDestPrefix = destDir.canonicalPath + File.separator
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                val newPath = newFile.canonicalPath
                if (newPath != destDir.canonicalPath && !newPath.startsWith(canonicalDestPrefix)) {
                    throw SecurityException("Zip Entry outside of target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos -> zis.copyTo(fos) }
                }
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Download a file from GitHub Actions, manually following one redirect
     * so we can drop the `Authorization` header before hitting the
     * presigned-URL host. Forwarding the GitHub bearer token to the Azure /
     * S3 backend is a credential-leak vector and was the previous behaviour
     * because [HttpURLConnection.instanceFollowRedirects] forwards headers.
     */
    private suspend fun downloadFileWithAuth(urlStr: String, destination: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val initial = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 60_000
            }

            val finalConn: HttpURLConnection = try {
                when (initial.responseCode) {
                    HttpURLConnection.HTTP_OK -> initial
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307, 308 -> {
                        val redirect = initial.getHeaderField("Location") ?: run {
                            onLog("Download failed: 3xx without Location header\n")
                            initial.disconnect()
                            return@withContext false
                        }
                        initial.disconnect()
                        // Open the redirect target WITHOUT forwarding the
                        // Authorization header. The presigned URL does not
                        // need it and the cross-origin host should never see it.
                        (URL(redirect).openConnection() as HttpURLConnection).apply {
                            instanceFollowRedirects = true
                            connectTimeout = 15_000
                            readTimeout = 5 * 60_000
                        }
                    }
                    else -> {
                        onLog("Download failed: HTTP ${initial.responseCode}\n")
                        initial.disconnect()
                        return@withContext false
                    }
                }
            } catch (e: Exception) {
                initial.disconnect()
                throw e
            }

            try {
                if (finalConn.responseCode != HttpURLConnection.HTTP_OK) {
                    onLog("Download failed at redirect: HTTP ${finalConn.responseCode}\n")
                    return@withContext false
                }
                finalConn.inputStream.use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } finally {
                finalConn.disconnect()
            }
        } catch (e: Exception) {
            onLog("Download error: ${e.message}\n")
            false
        }
    }
}
