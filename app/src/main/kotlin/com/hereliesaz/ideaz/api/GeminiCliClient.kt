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
            ?: return "Error: Gemini CLI tool not found."

        // It's important to pass the prompt as a single argument
        val command = listOf(geminiCliPath, "generate", "content", "--prompt", prompt)

        val result = ProcessExecutor.execute(command)

        return if (result.exitCode == 0) {
            result.output
        } else {
            // Attempt to parse a structured error from the JSON output
            val errorMessage = parseError(result.output)
            Log.e(TAG, "Gemini CLI execution failed: $errorMessage")
            "Error: $errorMessage"
        }
    }

    /**
     * Generates content using the Gemini CLI and streams the output.
     *
     * @param context The application context.
     * @param prompt The prompt to send to the Gemini CLI.
     * @param onOutputLine A callback that will be invoked for each line of output.
     * @param onCompletion A callback that will be invoked when the process completes.
     */
    fun generateContentStream(
        context: Context,
        prompt: String,
        onOutputLine: (String) -> Unit,
        onCompletion: (Int) -> Unit
    ) {
        val geminiCliPath = ToolManager.getToolPath(context, "gemini")
        if (geminiCliPath == null) {
            onOutputLine("Error: Gemini CLI tool not found.")
            onCompletion(-1)
            return
        }

        val command = listOf(geminiCliPath, "generate", "content", "--prompt", prompt, "--stream")

        ProcessExecutor.executeAndStream(command, onOutputLine, onCompletion)
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
            // The CLI often outputs a JSON object with an error message
            val jsonObject = JSONObject(errorOutput)
            jsonObject.getString("message")
        } catch (e: JSONException) {
            // If it's not a JSON object, or doesn't have the "message" key,
            // return the raw output as the best available error info.
            Log.w(TAG, "Failed to parse structured error, returning raw output: $errorOutput")
            errorOutput
        }
    }
}