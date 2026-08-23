package com.example.app.utils

import android.content.Context
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * A lightweight crash reporter for user projects initialized by IDEaz.
 * Uses raw HttpURLConnection to avoid dependencies.
 * Assumes the existence of a server or API that accepts JSON.
 *
 * ADAPTED FOR JULES API.
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    // Configuration
    var API_KEY: String? = Secrets.API_KEY
    var GITHUB_USER: String = Secrets.GITHUB_USER
    var REPO_SOURCE: String = Secrets.REPO_SOURCE

    private const val MANDATORY_INSTRUCTION =
        "You are required to get a perfect code review, no blocks or nitpicks allowed in it. " +
        "Then you must get a passing build with tests. " +
        "Once you have it, you must take the time to go get all of the documentation up to date before committing."

    fun init(context: Context) {
        // No-args init uses baked-in Secrets
        init(context, null, null, null)
    }

    fun init(context: Context, apiKey: String?, user: String?, source: String?) {
        if (!apiKey.isNullOrBlank()) API_KEY = apiKey
        if (!user.isNullOrBlank()) GITHUB_USER = user!!
        if (!source.isNullOrBlank()) REPO_SOURCE = source!!

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            reportCrash(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun reportCrash(t: Throwable) {
        val key = API_KEY
        if (key.isNullOrBlank()) return

        val stackTrace = Log.getStackTraceString(t)

        executor.submit {
            try {
                // 1. Create Session
                val sessionUrl = "https://jules.googleapis.com/v1alpha/sessions"
                val sessionJson = JSONObject().apply {
                    put("prompt", "CRASH REPORT from $GITHUB_USER")
                    put("sourceContext", JSONObject().apply {
                        put("source", REPO_SOURCE)
                        put("githubRepoContext", JSONObject().apply {
                            put("startingBranch", "main")
                        })
                    })
                    put("title", "Crash Report: ${t.javaClass.simpleName}")
                }

                val sessionResp = postJson(sessionUrl, sessionJson.toString(), key)
                val sessionName = JSONObject(sessionResp).getString("name")

                // 2. Send Message
                val messageUrl = "https://jules.googleapis.com/v1alpha/$sessionName:sendMessage"
                val messageJson = JSONObject().apply {
                    put("prompt", "AUTOMATED CRASH REPORT:\n\n$stackTrace\n\n$MANDATORY_INSTRUCTION")
                }

                postJson(messageUrl, messageJson.toString(), key)
                Log.d(TAG, "Crash report submitted.")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit crash report", e)
            }
        }
    }

    private fun postJson(urlString: String, jsonBody: String, apiKey: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setRequestProperty("X-Goog-Api-Key", apiKey)
        conn.doOutput = true

        OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }

        val responseCode = conn.responseCode
        if (responseCode >= 400) {
            val error = conn.errorStream.bufferedReader().use { it.readText() }
            throw RuntimeException("HTTP $responseCode: $error")
        }

        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
