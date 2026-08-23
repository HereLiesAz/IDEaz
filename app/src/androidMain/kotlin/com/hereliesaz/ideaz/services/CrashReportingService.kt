package com.hereliesaz.ideaz.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.hereliesaz.ideaz.utils.GithubIssueReporter
import com.hereliesaz.ideaz.utils.LogSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reports fatal crashes and batched non-fatal errors as GitHub Issues.
 *
 * Runs in a separate process (`:crash_reporter`, see AndroidManifest) so it
 * survives the main process dying. Stack traces are sanitized before they leave
 * the device — [LogSanitizer] strips provider keys and tokens — because these
 * reports are filed against a public repository.
 *
 * Reporting is opt-in: [com.hereliesaz.ideaz.utils.CrashHandler] only starts this
 * service once the user has accepted the first-run disclosure.
 */
class CrashReportingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "CrashReportingService"

        const val ACTION_REPORT_FATAL = "com.hereliesaz.ideaz.REPORT_FATAL"
        const val ACTION_REPORT_NON_FATAL = "com.hereliesaz.ideaz.REPORT_NON_FATAL"

        const val EXTRA_GITHUB_TOKEN = "extra_github_token"
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
        const val EXTRA_GITHUB_USER = "extra_github_user"

        /** Minimum delay between batched non-fatal reports, so a loop can't spam the API. */
        private const val BATCH_DELAY_MS = 5000L
    }

    private var lastReportTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val githubToken = intent.getStringExtra(EXTRA_GITHUB_TOKEN)
        val rawErrorData = intent.getStringExtra(EXTRA_STACK_TRACE)
        val githubUser = intent.getStringExtra(EXTRA_GITHUB_USER) ?: "Unknown User"
        val isFatal = intent.action != ACTION_REPORT_NON_FATAL

        if (rawErrorData.isNullOrBlank() || githubToken.isNullOrBlank()) {
            Log.w(TAG, "Missing stack trace or GitHub token. Aborting report.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Critical: strip credentials before anything leaves the device. These
        // issues are filed against a public repo.
        val errorData = LogSanitizer.sanitize(rawErrorData)

        serviceScope.launch {
            if (!isFatal) {
                val now = System.currentTimeMillis()
                if (now - lastReportTime < BATCH_DELAY_MS) {
                    delay(BATCH_DELAY_MS - (now - lastReportTime))
                }
                lastReportTime = System.currentTimeMillis()
            }

            try {
                val type = if (isFatal) "CRASH" else "NON-FATAL ERROR(S)"
                Log.d(TAG, "Submitting $type report...")
                GithubIssueReporter.reportError(
                    context = applicationContext,
                    token = githubToken,
                    error = null,
                    contextMessage = "$type from $githubUser",
                    stackTraceOverride = errorData,
                )
                Log.d(TAG, "Report submitted via GitHub Issues.")
            } catch (e: Throwable) {
                // Nothing useful left to do if the network or API is down.
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
