package com.hereliesaz.ideaz.api

import android.content.Context
import android.util.Log
import com.hereliesaz.ideaz.utils.ToolManager
import java.io.BufferedReader
import java.io.InputStreamReader

object JulesCliClient {

    private const val TAG = "JulesCliClient"
    // The actual executable will be libjules.so, ToolManager handles the prefix/suffix.
    private const val JULES_TOOL_NAME = "jules"

    private fun executeCommand(context: Context, command: String): String? {
        val julesPath = ToolManager.getToolPath(context, JULES_TOOL_NAME)
        if (julesPath == null) {
            Log.e(TAG, "Jules CLI tool not found. Please install it first.")
            return null
        }

        val fullCommand = "$julesPath $command"
        Log.d(TAG, "Executing command: $fullCommand")

        try {
            val process = Runtime.getRuntime().exec(fullCommand)
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
        // --- FIX: Align with reference document ---
        val command = "remote new --repo \"$source\" --session \"$prompt\""
        // --- END FIX ---
        return executeCommand(context, command)
    }

    fun listActivities(context: Context, sessionId: String): String? {
        // --- FIX: Align with reference document ---
        val command = "remote list --session \"$sessionId\" --format=json"
        // --- END FIX ---
        return executeCommand(context, command)
    }

    fun pullPatch(context: Context, sessionId: String): String? {
        // --- FIX: Align with reference document ---
        val command = "remote pull --session $sessionId"
        // --- END FIX ---
        return executeCommand(context, command)
    }

    // --- NEW: Function to list available GitHub repos ---
    fun listSources(context: Context): String? {
        // --- FIX: Align with reference document ---
        val command = "remote list --repo --format=json"
        // --- END FIX ---
        return executeCommand(context, command)
    }
    // --- END NEW ---

    // --- NEW: Function to list available sessions ---
    fun listSessions(context: Context): String? {
        val command = "remote list --session --format=json"
        return executeCommand(context, command)
    }
    // --- END NEW ---
}