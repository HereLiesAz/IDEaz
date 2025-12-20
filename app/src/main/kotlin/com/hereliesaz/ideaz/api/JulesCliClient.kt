package com.hereliesaz.ideaz.api

import android.content.Context
import android.util.Log
import com.hereliesaz.ideaz.utils.ToolManager
import java.io.BufferedReader
import java.io.InputStreamReader

@Deprecated("Use JulesApiClient instead. This client relies on a CLI binary that is not functional in this environment.")
object JulesCliClient {

    private const val TAG = "JulesCliClient"
    private const val JULES_TOOL_NAME = "jules"

    private fun executeCommand(context: Context, commandArgs: List<String>): String? {
        val julesPath = ToolManager.getToolPath(context, JULES_TOOL_NAME)
        if (julesPath == null) {
            Log.e(TAG, "Jules CLI tool not found. Please install it first.")
            return null
        }

        // 1. Construct the complete CLI command string
        val cliCommandString = listOf(julesPath) + commandArgs
        val fullCommandString = cliCommandString.joinToString(" ")

        // 2. Re-implementing the robust shell wrapper, as this is the only way to execute
        // a binary from a complex path or with complex permissions.
        val shellCommandArray = arrayOf("/system/bin/sh", "-c", fullCommandString)

        Log.d(TAG, "Executing command: ${shellCommandArray.joinToString(" ")}")

        try {
            val process = Runtime.getRuntime().exec(shellCommandArray)

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            val exitCode = process.exitValue()

            if (exitCode == 0) {
                Log.d(TAG, "Command executed successfully. Output: $output")
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
        // Arguments separated, ready for shell execution with quotes for prompt/source
        val args = listOf("session", "create", "--prompt", "\"$prompt\"", "--source", "\"$source\"", "--format=json")
        return executeCommand(context, args)
    }

    fun listActivities(context: Context, sessionId: String): String? {
        // Arguments separated.
        val args = listOf("activity", "list", "--session", "\"$sessionId\"", "--format=json")
        return executeCommand(context, args)
    }

    fun pullPatch(context: Context, sessionId: String): String? {
        // Arguments separated.
        val args = listOf("patch", "pull", sessionId)
        return executeCommand(context, args)
    }

    // --- NEW: Function to list available GitHub repos ---
    fun listSources(context: Context): String? {
        // Arguments separated.
        val args = listOf("source", "list", "--format=json")
        return executeCommand(context, args)
    }
    // --- END NEW ---
}