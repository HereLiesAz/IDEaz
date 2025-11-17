package com.hereliesaz.ideaz.api

import android.content.Context
import android.util.Log
import com.hereliesaz.ideaz.utils.ProcessExecutor
import com.hereliesaz.ideaz.utils.ToolManager
import org.json.JSONException
import org.json.JSONObject

object GeminiCliClient {

    private const val TAG = "GeminiCliClient"

    /**
     * Generates content using the Gemini CLI.
     *
     * @param context The application context.
     * @param prompt The prompt to send to the Gemini CLI.
     * @return The generated content, or an error message if something went wrong.
     */
    fun generateContent(context: Context, prompt: String): String {
        val geminiCliPath = ToolManager.getToolPath(context, "gemini")

        if (geminiCliPath == null) {
            val errorMessage = "Gemini CLI tool not found."
            Log.e(TAG, errorMessage)
            return errorMessage
        }

        val command: List<String> = listOf(geminiCliPath, "generate", "content", "--prompt", prompt)

        val result = ProcessExecutor.execute(command)

        return if (result.exitCode == 0) {
            result.output
        } else {
            val errorMessage = parseError(result.output)
            Log.e(TAG, "Gemini CLI execution failed: $errorMessage")
            "Error: $errorMessage"
        }
    }

    /**
     * Parses a structured error message from the Gemini CLI's JSON output.
     * If parsing fails, it returns the raw error string.
     *
     * @param errorOutput The raw error output from the CLI process.
     * @return A parsed error message or the original raw output.
     */
    private fun parseError(errorOutput: String): String {
        return try {
            val jsonObject = JSONObject(errorOutput)
            jsonObject.getString("message")
        } catch (e: JSONException) {
            Log.w(TAG, "Failed to parse structured error, returning raw output: $errorOutput")
            errorOutput
        }
    }
}
