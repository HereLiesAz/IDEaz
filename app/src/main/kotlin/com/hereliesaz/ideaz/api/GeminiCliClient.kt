package com.hereliesaz.ideaz.api

import android.content.Context
import android.util.Log
import com.hereliesaz.ideaz.utils.ToolManager
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object GeminiCliClient {

    private const val TAG = "GeminiCliClient"
    private const val GEMINI_TOOL_NAME = "gemini"

    private fun executeCommand(context: Context, command: String): String? {
        val geminiPath = ToolManager.getToolPath(context, GEMINI_TOOL_NAME)
        if (geminiPath == null) {
            Log.e(TAG, "Gemini CLI tool not found. Please install it first.")
            return null
        }

        val fullCommand = "$geminiPath $command"
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
                Log.d(TAG, "Command executed successfully.")
                return output.toString()
            } else {
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                val errorOutput = StringBuilder()
                while (errorReader.readLine().also { line = it } != null) {
                    errorOutput.append(line).append("\n")
                }
                Log.e(TAG, "Command failed with exit code $exitCode. Error: $errorOutput")
                return "Error: $errorOutput"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while executing command", e)
            return "Error: ${e.message}"
        }
    }

    /**
     * Calls the Gemini CLI with the given prompt on an IO thread.
     * Returns the response text, including a formatted error message if one occurs.
     */
    fun generateContent(context: Context, prompt: String): String {
        // Using --yolo to auto-approve actions, as there's no interactive user session.
        val command = "--prompt \"$prompt\" --output-format json --yolo"
        val jsonResponse = executeCommand(context, command)

        return if (jsonResponse != null) {
            try {
                val jsonObject = JSONObject(jsonResponse)
                // The main AI-generated content is in the "response" field.
                jsonObject.optString("response", "Error: Could not parse 'response' from JSON.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse JSON response: $jsonResponse", e)
                "Error: Failed to parse CLI response."
            }
        } else {
            "Error: Received null response from Gemini CLI."
        }
    }
}
