package com.hereliesaz.ideaz.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.hereliesaz.ideaz.api.AuthInterceptor
import com.hereliesaz.ideaz.api.CreateSessionRequest
import com.hereliesaz.ideaz.api.GitHubRepoContext
import com.hereliesaz.ideaz.api.SendMessageRequest
import com.hereliesaz.ideaz.api.SourceContext
import com.hereliesaz.ideaz.jules.JulesApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * A Background Service responsible for reporting Fatal Crashes (Uncaught Exceptions)
 * and Non-Fatal errors (Handled Exceptions) to external services (GitHub Issues or Jules AI).
 *
 * **Design:**
 * - Runs in a separate process (`:crash_reporter`) defined in AndroidManifest.xml.
 *   This ensures it survives even if the main app process dies due to a native crash or OOM.
 * - Uses [JulesApiClient] to start a session with the AI agent to analyze the stack trace.
 * - Enforces strict data sanitization to prevent leaking API keys or tokens in logs.
 */
class CrashReportingService : Service() {

    // SupervisorJob allows independent child failures. IO dispatcher for network calls.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "CrashReportingService"

        // Actions
        const val ACTION_REPORT_FATAL = "com.hereliesaz.ideaz.REPORT_FATAL"
        const val ACTION_REPORT_NON_FATAL = "com.hereliesaz.ideaz.REPORT_NON_FATAL"

        // Extras
        const val EXTRA_API_KEY = "extra_api_key"
        const val EXTRA_JULES_PROJECT_ID = "extra_jules_project_id"
        const val EXTRA_GITHUB_TOKEN = "extra_github_token"
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
        const val EXTRA_GITHUB_USER = "extra_github_user"
        const val EXTRA_REPORT_TO_GITHUB = "extra_report_to_github"

        /**
         * **CRITICAL INSTRUCTION FOR AI AGENTS:**
         * This string is appended to every crash report sent to the AI.
         * It enforces a strict "No Regressions" policy and high standards for fixes.
         */
        private const val MANDATORY_INSTRUCTION =
            "You are required to get a perfect code review, no blocks or nitpicks allowed in it. " +
            "Then you must get a passing build with tests. " +
            "Once you have it, you must take the time to go get all of the documentation up to date before committing."

        private const val IDEAZ_SOURCE = "sources/github/HereLiesAz/IDEaz"
        private const val DEFAULT_BRANCH = "main"

        /** Minimum delay between batch reports for non-fatal errors to avoid spamming the API. */
        private const val BATCH_DELAY_MS = 5000L
    }

    private var lastReportTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Extract extras
        val apiKey = intent.getStringExtra(EXTRA_API_KEY)
        val projectId = intent.getStringExtra(EXTRA_JULES_PROJECT_ID)
        val githubToken = intent.getStringExtra(EXTRA_GITHUB_TOKEN)
        val rawErrorData = intent.getStringExtra(EXTRA_STACK_TRACE)
        val githubUser = intent.getStringExtra(EXTRA_GITHUB_USER) ?: "Unknown User"
        val reportToGithub = intent.getBooleanExtra(EXTRA_REPORT_TO_GITHUB, true)
        val isFatal = intent.action != ACTION_REPORT_NON_FATAL

        if (apiKey.isNullOrBlank() || rawErrorData.isNullOrBlank() || projectId.isNullOrBlank()) {
            Log.w(TAG, "Missing API key, Project ID, or error data. Aborting report.")
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. Sanitize error data (Remove API Keys/Tokens from stack trace/logs)
        // This is a critical security step.
        val errorData = com.hereliesaz.ideaz.utils.LogSanitizer.sanitize(rawErrorData)

        // Initialize AuthInterceptor for this process (since it's a separate process from MainApp)
        AuthInterceptor.apiKey = apiKey

        serviceScope.launch {
            // Rate limiting for non-fatal errors
            if (!isFatal) {
                val now = System.currentTimeMillis()
                if (now - lastReportTime < BATCH_DELAY_MS) {
                    delay(BATCH_DELAY_MS - (now - lastReportTime))
                }
                lastReportTime = System.currentTimeMillis()
            }

            try {
                val type = if (isFatal) "CRASH" else "NON-FATAL ERROR(S)"
                Log.d(TAG, "Attempting to submit $type report...")

                if (reportToGithub && !githubToken.isNullOrBlank()) {
                    // Option A: Report as GitHub Issue
                    com.hereliesaz.ideaz.utils.GithubIssueReporter.reportError(
                        applicationContext,
                        githubToken,
                        Throwable(type),
                        "Fatal Crash or System Error from $githubUser",
                        errorData
                    )
                    Log.d(TAG, "Report submitted via GitHub Issues.")
                } else {
                    // Option B: Report to Jules Agent directly
                    val promptTitle = if (isFatal) {
                        "CRASH REPORT from $githubUser: \n${errorData.lines().firstOrNull() ?: "Error"}"
                    } else {
                        "NON-FATAL ERRORS from $githubUser"
                    }

                    val title = if (isFatal) {
                        "Crash: ${errorData.lines().firstOrNull()?.take(50) ?: "Unknown"}"
                    } else {
                        "Non-Fatal Errors Batch"
                    }

                    // 1. Create a new Session for this crash
                    val createRequest = CreateSessionRequest(
                        prompt = promptTitle,
                        sourceContext = SourceContext(
                            source = IDEAZ_SOURCE,
                            githubRepoContext = GitHubRepoContext(startingBranch = DEFAULT_BRANCH)
                        ),
                        title = title
                    )

                    val session = JulesApiClient.createSession(request = createRequest)
                    Log.d(TAG, "Session created: ${session.name}")

                    // 2. Send the detailed error log with instructions
                    val messageRequest = SendMessageRequest(
                        prompt = "AUTOMATED REPORT ($type):\n\n$errorData\n\n$MANDATORY_INSTRUCTION"
                    )

                    JulesApiClient.sendMessage(session.id, messageRequest)
                    Log.d(TAG, "Report submitted via Jules Session.")
                }

            } catch (e: Throwable) {
                // If reporting fails, log it locally. We can't do much else if the network/API is down.
                Log.e(TAG, "Failed to submit report", e)
            } finally {
                // Always stop the service after work is done to save resources.
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
