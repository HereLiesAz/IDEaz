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
import java.net.URLEncoder

object GithubIssueReporter {

    private const val TAG = "GithubIssueReporter"
    private const val REPO_OWNER = "HereLiesAz"
    private const val REPO_NAME = "IDEaz"
    private const val ISSUE_URL_BASE = "https://github.com/$REPO_OWNER/$REPO_NAME/issues/new"

    /**
     * Attempts to report an error to GitHub.
     * 1. Tries to use the GitHub API to auto-post the issue (if token provided).
     * 2. Falls back to opening a browser with pre-filled data.
     */
    suspend fun reportError(context: Context, token: String?, error: Throwable, contextMessage: String, logContent: String? = null): String {
        val stackTrace = error.stackTraceToString()

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
                    request = CreateIssueRequest(title = titleContent, body = bodyContent)
                )
                Log.d(TAG, "Issue created successfully: #${response.number}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Bug report sent (Issue #${response.number})", Toast.LENGTH_LONG).show()
                }
                return "Reported automatically: ${response.htmlUrl}"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post issue via API. Falling back to browser.", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Auto-report failed. Opening browser...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 2. Fallback to Browser
        withContext(Dispatchers.Main) {
            try {
                val encodedTitle = URLEncoder.encode(titleContent, "UTF-8")
                // Browser URLs need stricter length limits than API
                val encodedBody = URLEncoder.encode(bodyContent.take(1500) + "\n...[Truncated for URL]", "UTF-8")

                val fullUrl = "$ISSUE_URL_BASE?title=$encodedTitle&body=$encodedBody"

                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open browser for issue reporting", e)
            }
        }

        return "Opened in browser for manual reporting."
    }
}