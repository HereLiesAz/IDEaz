package com.hereliesaz.ideaz.buildlogic

import android.content.Context
import com.hereliesaz.ideaz.api.GitHubApi
import com.hereliesaz.ideaz.utils.ApkInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

class RemoteBuildManager(
    private val context: Context,
    private val api: GitHubApi,
    private val token: String,
    private val user: String,
    private val repo: String,
    private val onLog: (String) -> Unit
) {
    suspend fun pollAndDownload(headSha: String): String? {
        onLog("Waiting for GitHub Action (HEAD: ${headSha.take(7)})...\n")

        var runId: Long? = null
        var attempts = 0
        var effectiveSha: String? = headSha

        // 1. Find the Workflow Run
        while (runId == null && attempts < 20 && coroutineContext.isActive) {
            try {
                val runs = api.listWorkflowRuns(user, repo, headSha = headSha)
                val run = runs.workflowRuns.firstOrNull()
                if (run != null) {
                    runId = run.id
                    onLog("Remote Build Started. Run ID: $runId\n")
                } else {
                    delay(3000)
                    attempts++
                }
            } catch (e: Exception) {
                delay(3000)
                attempts++
            }
        }

        if (runId == null) {
            onLog("Remote Build: Workflow run not found for commit $headSha. Falling back to latest workflow run...\n")
            try {
                // Fallback: Fetch the latest workflow run regardless of SHA
                val runs = api.listWorkflowRuns(user, repo, headSha = null)
                val run = runs.workflowRuns.firstOrNull()
                if (run != null) {
                    runId = run.id
                    effectiveSha = null
                    onLog("Remote Build Fallback: Using Run ID: $runId (Status: ${run.status}, Conclusion: ${run.conclusion})\n")
                }
            } catch (e: Exception) {
                onLog("Remote Build Fallback Failed: ${e.message}\n")
            }
        }

        if (runId == null) {
            onLog("Remote Build: No workflow runs found.\n")
            return null
        }

        // 2. Poll Status
        var status = "queued"
        var conclusion: String? = null

        while ((status == "queued" || status == "in_progress") && coroutineContext.isActive) {
            delay(5000)
            try {
                val runs = api.listWorkflowRuns(user, repo, headSha = effectiveSha)
                val run = runs.workflowRuns.find { it.id == runId }
                if (run != null) {
                    status = run.status
                    conclusion = run.conclusion
                    // onLog("Status: $status\n") // Optional verbose logging
                }
            } catch (e: Exception) {
                // Ignore transient errors
            }
        }

        if (!coroutineContext.isActive) return null

        if (conclusion != "success") {
            onLog("Remote Build finished with status: $conclusion\n")
            // Try to get failure logs
            try {
                val jobs = api.getRunJobs(user, repo, runId)
                val failedJob = jobs.jobs.find { it.conclusion == "failure" }
                if (failedJob != null) {
                    val logResp = api.getJobLogs(user, repo, failedJob.id)
                    if (logResp.isSuccessful) {
                        val logText = logResp.body()?.string() ?: "No log content"
                        onLog("Remote Build Log:\n$logText\n")
                    }
                }
            } catch (e: Exception) {
                onLog("Could not retrieve remote logs: ${e.message}\n")
            }
            return null
        }

        // 3. Download Artifact
        onLog("Remote Build Successful. Downloading artifact...\n")

        try {
            val artifactsResp = api.getRunArtifacts(user, repo, runId)
            val apkArtifact = artifactsResp.artifacts.find { it.name.contains("debug") && it.name.endsWith("apk") }
                ?: artifactsResp.artifacts.find { it.name.endsWith(".apk") }
                ?: artifactsResp.artifacts.find { it.name == "app-debug" }

            if (apkArtifact == null) {
                onLog("Remote Build: No APK artifact found.\n")
                return null
            }

            val downloadUrl = apkArtifact.archiveDownloadUrl
            val zipFile = File(context.filesDir, "remote_build_${headSha.take(7)}.zip")

            if (downloadFileWithAuth(downloadUrl, zipFile)) {
                onLog("Unzipping artifact...\n")
                val destDir = File(context.filesDir, "remote_extracted_${headSha.take(7)}")
                destDir.deleteRecursively()
                destDir.mkdirs()

                var resultPath: String? = null
                withContext(Dispatchers.IO) {
                    ZipInputStream(zipFile.inputStream()).use { zis ->
                        var entry = zis.nextEntry
                        val canonicalDest = destDir.canonicalPath
                        while (entry != null) {
                            val newFile = File(destDir, entry.name)
                            if (!newFile.canonicalPath.startsWith(canonicalDest)) {
                                throw SecurityException("Zip Entry outside of target dir: ${entry.name}")
                            }
                            if (entry.isDirectory) {
                                newFile.mkdirs()
                            } else {
                                newFile.parentFile?.mkdirs()
                                FileOutputStream(newFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                            entry = zis.nextEntry
                        }
                    }
                }

                val apkFile = destDir.walkTopDown().find { it.name.endsWith(".apk") }
                if (apkFile != null) {
                    onLog("Installing Remote APK: ${apkFile.name}\n")
                    ApkInstaller.installApk(context, apkFile.absolutePath)
                    resultPath = apkFile.absolutePath
                } else {
                    onLog("Remote Build: APK file not found in artifact zip.\n")
                }
                return resultPath
            } else {
                onLog("Remote Build: Failed to download artifact.\n")
                return null
            }
        } catch (e: Exception) {
            onLog("Remote Build Error: ${e.message}\n")
            return null
        }
    }

    private suspend fun downloadFileWithAuth(urlStr: String, destination: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    onLog("Download failed: HTTP ${connection.responseCode}\n")
                    return@withContext false
                }

                val input = connection.inputStream
                val output = FileOutputStream(destination)
                input.copyTo(output)
                output.close()
                input.close()
                true
            } catch (e: Exception) {
                onLog("Download error: ${e.message}\n")
                false
            }
        }
    }
}
