package com.hereliesaz.ideaz.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.services.CrashReportingService
import com.hereliesaz.ideaz.ui.SettingsViewModel
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

object CrashHandler {
    private const val TAG = "CrashHandler"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(context, thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable) ?: exitProcess(1)
        }
    }

    fun report(context: Context, throwable: Throwable) {
        handleCrash(context, Thread.currentThread(), throwable)
    }

    private fun handleCrash(context: Context, thread: Thread, throwable: Throwable) {
        // Sanitize the crash log before printing to Logcat to prevent secret leakage
        val sanitizedMsg = LogSanitizer.sanitize("Fatal Crash on thread ${thread.name}\n" + throwable.stackTraceToString())
        Log.e(TAG, sanitizedMsg)

        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val githubToken = readSecureOrLegacyCredential(context, SettingsViewModel.KEY_GITHUB_TOKEN)
            val githubUser = prefs.getString(SettingsViewModel.KEY_GITHUB_USER, "Unknown")
            val reportToGithub = prefs.getBoolean(SettingsViewModel.KEY_REPORT_IDE_ERRORS, true)

            // Reporting needs the user's first-run consent and a GitHub token to
            // file the issue with. Without both there is nowhere to send it.
            if (reportToGithub && !githubToken.isNullOrBlank()) {
                val intent = Intent(context, CrashReportingService::class.java).apply {
                    putExtra(CrashReportingService.EXTRA_GITHUB_TOKEN, githubToken)
                    putExtra(CrashReportingService.EXTRA_STACK_TRACE, stackTrace)
                    putExtra(CrashReportingService.EXTRA_GITHUB_USER, githubUser)
                }
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start crash reporting service", e)
        }
    }
}