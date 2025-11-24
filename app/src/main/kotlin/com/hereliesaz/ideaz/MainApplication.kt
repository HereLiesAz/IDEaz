package com.hereliesaz.ideaz

import android.app.Application
import android.util.Log
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.api.AuthInterceptor
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.GithubIssueReporter
import kotlinx.coroutines.runBlocking

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val apiKey = sharedPreferences.getString(SettingsViewModel.KEY_API_KEY, null)
        if (apiKey != null) {
            AuthInterceptor.apiKey = apiKey
        }

        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val autoReport = sharedPreferences.getBoolean(SettingsViewModel.KEY_AUTO_REPORT_BUGS, true)
            val token = sharedPreferences.getString(SettingsViewModel.KEY_GITHUB_TOKEN, null)

            if (autoReport && !token.isNullOrBlank()) {
                Log.e("MainApplication", "App crashed! Attempting to report to GitHub...", throwable)

                val logcat = try {
                    Runtime.getRuntime().exec("logcat -d -t 500").inputStream.bufferedReader().readText()
                } catch (e: Exception) {
                    "Failed to capture logcat: ${e.message}"
                }

                // Run blocking to ensure the network request completes before the process dies
                runBlocking {
                    GithubIssueReporter.reportCrash(
                        token = token,
                        error = throwable,
                        contextMessage = "Uncaught Exception in thread '${thread.name}'",
                        logContent = logcat
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MainApplication", "Failed to report crash", e)
        }
    }
}