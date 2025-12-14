package com.hereliesaz.ideaz.utils

import android.app.Application
import android.content.Context
import android.util.Log
import com.hereliesaz.ideaz.ui.SettingsViewModel
import java.io.File

object ProjectInitializer {

    private const val TAG = "ProjectInitializer"

    /**
     * Injects the crash reporting files into the target project.
     *
     * @param context Application context
     * @param projectDir Root directory of the target project
     * @param packageName Package name of the target project (e.g., com.example.app)
     * @param settingsViewModel To retrieve API keys/User info for configuration
     */
    fun injectCrashReporting(
        context: Context,
        projectDir: File,
        packageName: String,
        settingsViewModel: SettingsViewModel
    ) {
        try {
            Log.d(TAG, "Injecting crash reporting into $projectDir for package $packageName")

            // 1. Determine Source Paths
            val packagePath = packageName.replace('.', '/')
            var targetUtilsDir = File(projectDir, "app/src/main/kotlin/$packagePath/utils")

            if (!targetUtilsDir.exists()) {
                if (!targetUtilsDir.mkdirs()) {
                    // Fallback to java
                     targetUtilsDir = File(projectDir, "app/src/main/java/$packagePath/utils")
                     if (!targetUtilsDir.exists() && !targetUtilsDir.mkdirs()) {
                         Log.e(TAG, "Failed to create utils directory at ${targetUtilsDir.absolutePath}")
                         return
                     }
                }
            }

            // 2. Read Template Assets
            val assetManager = context.assets
            val reporterTemplate = assetManager.open("templates/common/error_handling/CrashReporter.kt").bufferedReader().use { it.readText() }
            val secretsTemplate = assetManager.open("templates/common/error_handling/Secrets.kt").bufferedReader().use { it.readText() }

            // 3. Customize & Write Secrets.kt
            var secretsContent = secretsTemplate.replace("package com.example.app.utils", "package $packageName.utils")

            val apiKey = settingsViewModel.getApiKey() ?: ""
            val user = settingsViewModel.getGithubUser() ?: "Unknown"
            val repoName = settingsViewModel.getAppName() ?: "Unknown"
            val source = "sources/github/$user/$repoName"

            secretsContent = secretsContent.replace("const val API_KEY = \"\"", "const val API_KEY = \"$apiKey\"")
            secretsContent = secretsContent.replace("const val GITHUB_USER = \"Unknown\"", "const val GITHUB_USER = \"$user\"")
            secretsContent = secretsContent.replace("const val REPO_SOURCE = \"sources/github/Unknown/Unknown\"", "const val REPO_SOURCE = \"$source\"")

            val secretsFile = File(targetUtilsDir, "Secrets.kt")
            secretsFile.writeText(secretsContent)
            Log.d(TAG, "Secrets.kt injected.")

            // 4. Customize & Write CrashReporter.kt
            val reporterContent = reporterTemplate.replace("package com.example.app.utils", "package $packageName.utils")
            val reporterFile = File(targetUtilsDir, "CrashReporter.kt")
            reporterFile.writeText(reporterContent)
            Log.d(TAG, "CrashReporter.kt injected.")

            // 5. Add Secrets.kt to .gitignore
            val gitignore = File(projectDir, ".gitignore")
            if (gitignore.exists()) {
                val currentIgnore = gitignore.readText()
                if (!currentIgnore.contains("Secrets.kt")) {
                    gitignore.appendText("\n# IDEaz Secrets\n**/utils/Secrets.kt\n")
                }
            } else {
                 gitignore.writeText("\n# IDEaz Secrets\n**/utils/Secrets.kt\n")
            }

            // 6. Inject Initialization Logic into MainActivity
            injectInitCall(projectDir, packageName)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject crash reporting", e)
        }
    }

    private fun injectInitCall(projectDir: File, packageName: String) {
        val mainSrcDir = File(projectDir, "app/src/main")

        mainSrcDir.walkTopDown().forEach { file ->
            if (file.isFile && (file.name == "MainActivity.kt" || file.name == "MainActivity.java")) {
                try {
                    val content = file.readText()
                    if (!content.contains("CrashReporter.init")) {
                        val hook = "super.onCreate(savedInstanceState)"
                        if (content.contains(hook)) {
                            // Use no-args init which reads from Secrets
                            val injection = "$hook\n        $packageName.utils.CrashReporter.init(this)"
                            val newContent = content.replace(hook, injection)
                            file.writeText(newContent)
                            Log.d(TAG, "Injected initialization into ${file.name}")
                            return
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to modify ${file.name}", e)
                }
            }
        }
    }
}
