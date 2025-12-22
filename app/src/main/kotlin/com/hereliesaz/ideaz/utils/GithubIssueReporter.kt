package com.hereliesaz.ideaz.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.hereliesaz.ideaz.api.CreateIssueRequest
import com.hereliesaz.ideaz.api.GitHubApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder
import androidx.core.net.toUri

object GithubIssueReporter {

    private const val TAG = "GithubIssueReporter"
    private const val REPO_OWNER = "HereLiesAz"
    private const val REPO_NAME = "IDEaz"
    private const val ISSUE_URL_BASE = "https://github.com/$REPO_OWNER/$REPO_NAME/issues/new"

    private const val PREFS_NAME = "bug_report_prefs"
    private const val KEY_PREFIX_TIMESTAMP = "report_timestamp_"
    private const val COOLDOWN_MS = 24 * 60 * 60 * 1000L // 24 hours

    private val reportMutex = Mutex()
    private val reportedHashes = mutableSetOf<Int>()

    /**
     * Attempts to report an error to GitHub.
     * 1. Tries to use the GitHub API to auto-post the issue (if token provided).
     * 2. Falls back to opening a browser with pre-filled data.
     */
    suspend fun reportError(context: Context, token: String?, error: Throwable, contextMessage: String, logContent: String? = null): String {
        val stackTrace = error.stackTraceToString()

        // Deduplication Logic
        val errorSignature = "$contextMessage|${error::class.simpleName}|${error.message}"
        val errorHash = errorSignature.hashCode()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestampKey = "$KEY_PREFIX_TIMESTAMP$errorHash"
        val pid = android.os.Process.myPid()

        val shouldReport = reportMutex.withLock {
            if (reportedHashes.contains(errorHash)) {
                false
            } else {
                val lastReportTime = prefs.getLong(timestampKey, 0L)
                if (System.currentTimeMillis() - lastReportTime < COOLDOWN_MS) {
                    reportedHashes.add(errorHash) // Sync memory cache
                    false
                } else {
                    reportedHashes.add(errorHash)
                    prefs.edit().putLong(timestampKey, System.currentTimeMillis()).apply()
                    true
                }
            }
        }

        if (!shouldReport) {
            Log.i(TAG, "Skipping duplicate bug report (Hash: $errorHash, PID: $pid)")
            return "Skipped (Duplicate report within 24h) [Hash: $errorHash, PID: $pid]"
        }

        val logSection = if (logContent != null) {
            """

            **Log Output:**
            ```
            ${logContent.takeLast(2000)}
            ```
            """.trimIndent()
        } else ""

        // Truncate for safety (API limit is roughly 65k chars, URL limit 2k-8k)
        val bodyContent = """
            **Context:** $contextMessage
            **Device:** ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})
            **App Version:** 1.0 (Development)
            
            **Stack Trace:**
            ```
            ${stackTrace.take(3000)}
            ```
            $logSection

            Please debug this, Jules. Make sure you get both a correct code review and a passing build with tests before submitting your solution. 
        """.trimIndent()

        val titleContent = "IDE Error: ${error::class.simpleName} - ${error.message?.take(50) ?: "Unknown"}"

        // 1. Try API Reporting
        if (!token.isNullOrBlank()) {
            try {
                Log.d(TAG, "Attempting to post issue via API...")
                val api = GitHubApiClient.createService(token)
                val response = api.createIssue(
                    owner = REPO_OWNER,
                    repo = REPO_NAME,
                    request = CreateIssueRequest(
                        title = titleContent,
                        body = bodyContent,
                        labels = listOf("jules", "bug")
                    )
                )
                Log.d(TAG, "Issue created successfully: #${response.number}")

                return "Reported automatically: ${response.htmlUrl} [Hash: $errorHash, PID: $pid]"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post issue via API. Falling back to browser.", e)
            }
        }

        // 2. Fallback to Browser
        withContext(Dispatchers.Main) {
            try {
                val encodedTitle = URLEncoder.encode(titleContent, "UTF-8")
                // Browser URLs need stricter length limits than API
                val encodedBody = URLEncoder.encode(bodyContent.take(1500) + "\n...[Truncated for URL]", "UTF-8")

                val fullUrl = "$ISSUE_URL_BASE?title=$encodedTitle&body=$encodedBody"

                val intent = Intent(Intent.ACTION_VIEW, fullUrl.toUri())
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open browser for issue reporting", e)
            }
        }

        return "Opened in browser for manual reporting."
    }
}