package com.hereliesaz.ideaz.utils

import android.content.Context
import android.util.Base64
import com.hereliesaz.ideaz.models.IdeazProjectConfig
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.models.PromptEntry
import com.hereliesaz.ideaz.models.PromptHistory
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

object ProjectConfigManager {
    private const val CONFIG_DIR = ".ideaz"
    private const val CONFIG_FILE = "config.json"
    private const val HISTORY_FILE = "prompt_history.json"
    private const val SCREENSHOTS_DIR = "screenshots"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun saveConfig(projectDir: File, config: IdeazProjectConfig) {
        try {
            val ideazDir = File(projectDir, CONFIG_DIR)
            if (!ideazDir.exists()) {
                ideazDir.mkdirs()
            }
            val file = File(ideazDir, CONFIG_FILE)
            val jsonString = json.encodeToString(IdeazProjectConfig.serializer(), config)
            file.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadConfig(projectDir: File): IdeazProjectConfig? {
        return try {
            val file = File(projectDir, "$CONFIG_DIR/$CONFIG_FILE")
            if (file.exists()) {
                json.decodeFromString(IdeazProjectConfig.serializer(), file.readText())
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun ensureGitIgnore(projectDir: File) {
        try {
            val gitignore = File(projectDir, ".gitignore")
            val ideazEntry = ".ideaz/"

            if (gitignore.exists()) {
                val content = gitignore.readText()
                if (!content.contains(ideazEntry)) {
                    gitignore.appendText("\n$ideazEntry\n")
                }
            } else {
                gitignore.writeText("$ideazEntry\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun ensureWorkflow(context: Context, projectDir: File, type: ProjectType): Boolean {
        val (assetPath, destName) = when (type) {
            ProjectType.ANDROID -> "project/.github/workflows/android_ci_jules.yml" to "android_ci_jules.yml"
            ProjectType.REACT_NATIVE -> "templates/react_native/.github/workflows/react_native_ci_jules.yml" to "react_native_ci_jules.yml"
            ProjectType.FLUTTER -> "templates/flutter/.github/workflows/flutter_ci_jules.yml" to "flutter_ci_jules.yml"
            else -> return false
        }

        var modified = false
        try {
            val workflowsDir = File(projectDir, ".github/workflows")
            if (!workflowsDir.exists()) {
                workflowsDir.mkdirs()
            }

            val destFile = File(workflowsDir, destName)
            if (!destFile.exists()) {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                modified = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return modified
    }

    fun ensureSetupScript(projectDir: File): Boolean {
        var modified = false
        try {
            val setupFile = File(projectDir, "setup_env.sh")
            if (!setupFile.exists()) {
                setupFile.writeText(EnvironmentSetup.ANDROID_SETUP_SCRIPT)
                setupFile.setExecutable(true)
                modified = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return modified
    }

    fun appendPromptToHistory(projectDir: File, promptText: String, screenshotBase64: String? = null) {
        try {
            val ideazDir = File(projectDir, CONFIG_DIR)
            if (!ideazDir.exists()) ideazDir.mkdirs()

            val historyFile = File(ideazDir, HISTORY_FILE)

            // Load existing
            val currentHistory = if (historyFile.exists()) {
                try {
                    json.decodeFromString(PromptHistory.serializer(), historyFile.readText())
                } catch (e: Exception) {
                    PromptHistory()
                }
            } else {
                PromptHistory()
            }

            // Save screenshot if exists
            var screenshotFilename: String? = null
            if (screenshotBase64 != null) {
                val screenshotsDir = File(ideazDir, SCREENSHOTS_DIR)
                if (!screenshotsDir.exists()) screenshotsDir.mkdirs()

                val timestamp = System.currentTimeMillis()
                val filename = "screen_$timestamp.png"
                val file = File(screenshotsDir, filename)

                try {
                    val imageBytes = Base64.decode(screenshotBase64, Base64.DEFAULT)
                    FileOutputStream(file).use { it.write(imageBytes) }
                    screenshotFilename = filename
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val newEntry = PromptEntry(
                timestamp = System.currentTimeMillis(),
                text = promptText,
                screenshotFilename = screenshotFilename
            )

            val newHistory = currentHistory.copy(entries = currentHistory.entries + newEntry)

            historyFile.writeText(json.encodeToString(PromptHistory.serializer(), newHistory))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}