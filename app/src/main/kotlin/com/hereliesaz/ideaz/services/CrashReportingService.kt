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

class CrashReportingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "CrashReportingService"
        const val ACTION_REPORT_FATAL = "com.hereliesaz.ideaz.REPORT_FATAL"
        const val ACTION_REPORT_NON_FATAL = "com.hereliesaz.ideaz.REPORT_NON_FATAL"

        const val EXTRA_API_KEY = "extra_api_key"
        const val EXTRA_JULES_PROJECT_ID = "extra_jules_project_id"
        const val EXTRA_GITHUB_TOKEN = "extra_github_token"
        const val EXTRA_STACK_TRACE = "extra_stack_trace" // Used for both Fatal and Batch errors
        const val EXTRA_GITHUB_USER = "extra_github_user"
        const val EXTRA_REPORT_TO_GITHUB = "extra_report_to_github"

        private const val MANDATORY_INSTRUCTION =
            "You are required to get a perfect code review, no blocks or nitpicks allowed in it. " +
            "Then you must get a passing build with tests. " +
            "Once you have it, you must take the time to go get all of the documentation up to date before committing."

        private const val IDEAZ_SOURCE = "sources/github/HereLiesAz/IDEaz"
        private const val DEFAULT_BRANCH = "main"
        private const val BATCH_DELAY_MS = 5000L
    }

    private var lastReportTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

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

        // Sanitize error data to prevent credential leakage
        val errorData = com.hereliesaz.ideaz.utils.LogSanitizer.sanitize(rawErrorData)

        // Initialize AuthInterceptor for this process
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
                    com.hereliesaz.ideaz.utils.GithubIssueReporter.reportError(
                        applicationContext,
                        githubToken,
                        Throwable(type),
                        "Fatal Crash or System Error from $githubUser",
                        errorData
                    )
                    Log.d(TAG, "Report submitted via GitHub Issues.")
                } else {
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

                    // 1. Create Session
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

                    // 2. Send Message with Mandatory Instruction
                    val messageRequest = SendMessageRequest(
                        prompt = "AUTOMATED REPORT ($type):\n\n$errorData\n\n$MANDATORY_INSTRUCTION"
                    )

                    JulesApiClient.sendMessage(session.id, messageRequest)
                    Log.d(TAG, "Report submitted via Jules Session.")
                }

            } catch (e: Throwable) {
                Log.e(TAG, "Failed to submit report", e)
            } finally {
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
