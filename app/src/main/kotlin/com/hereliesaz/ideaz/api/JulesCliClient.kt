package com.hereliesaz.ideaz.api

import android.content.Context
import android.util.Log
import com.hereliesaz.ideaz.utils.ToolManager
import java.io.BufferedReader
import java.io.InputStreamReader

object JulesCliClient {

    private const val TAG = "JulesCliClient"
    private const val JULES_TOOL_NAME = "jules"

    // --- FIX: Reworked to use String[] for safe argument passing ---
    private fun executeCommand(context: Context, commandArgs: List<String>): String? {
        val julesPath = ToolManager.getToolPath(context, JULES_TOOL_NAME)
        if (julesPath == null) {
            Log.e(TAG, "NATIVE tool 'jules' not found. Check jniLibs and build.gradle.kts.")
            return null
        }

        val fullCommand = listOf(julesPath) + commandArgs
        Log.d(TAG, "Executing command: ${fullCommand.joinToString(" ")}")

        try {
            // Use ProcessBuilder or exec(String[]) to handle arguments correctly
            val process = ProcessBuilder(fullCommand).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            val exitCode = process.exitValue()

            if (exitCode == 0) {
                Log.d(TAG, "Command succeeded. Output: $output")
                return output.toString()
            } else {
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                val errorOutput = StringBuilder()
                while (errorReader.readLine().also { line = it } != null) {
                    errorOutput.append(line).append("\n")
                }
                Log.e(TAG, "Command failed with exit code $exitCode. Error: $errorOutput")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while executing command", e)
            return null
        }
    }

    fun createSession(context: Context, prompt: String, source: String): String? {
        // --- FIX: Align with reference doc AND pass as List<String> ---
        val commandArgs = listOf(
            "remote", "new",
            "--repo", source,
            "--session", prompt
        )
        return executeCommand(context, commandArgs)
    }

    fun listActivities(context: Context, sessionId: String): String? {
        val commandArgs = listOf(
            "remote", "list",
            "--session", sessionId,
            "--format=json"
        )
        return executeCommand(context, commandArgs)
    }

    fun pullPatch(context: Context, sessionId: String): String? {
        val commandArgs = listOf(
            "remote", "pull",
            "--session", sessionId
        )
        return executeCommand(context, commandArgs)
    }

    fun listSources(context: Context): String? {
        val commandArgs = listOf(
            "remote", "list",
            "--repo",
            "--format=json"
        )
        return executeCommand(context, commandArgs)
    }
}