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

    private fun handleCrash(context: Context, thread: Thread, throwable: Throwable) {
        Log.e(TAG, "Fatal Crash on thread ${thread.name}", throwable)

        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val apiKey = prefs.getString(SettingsViewModel.KEY_API_KEY, null)
            val githubUser = prefs.getString(SettingsViewModel.KEY_GITHUB_USER, "Unknown")

            if (!apiKey.isNullOrBlank()) {
                val intent = Intent(context, CrashReportingService::class.java).apply {
                    putExtra(CrashReportingService.EXTRA_API_KEY, apiKey)
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