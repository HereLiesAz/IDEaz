package com.hereliesaz.ideaz.utils

import android.content.Context
import android.util.Base64
import com.hereliesaz.ideaz.models.IdeazProjectConfig
import com.hereliesaz.ideaz.models.ProjectType
import com.hereliesaz.ideaz.models.PromptEntry
import com.hereliesaz.ideaz.models.PromptHistory
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
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

    // --- WORKFLOW CONTENT ---
    private val ANDROID_CI_JULES_YML = """
name: Android CI (Jules)

on:
  push:
    branches: [ "**" ]
  pull_request:
    branches: [ "**" ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: gradle
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    - name: Build with Gradle
      run: ./gradlew assembleDebug
    - name: Upload APK
      uses: actions/upload-artifact@v3
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk
""".trimIndent()

    private val RELEASE_YML = """
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    - name: Build Release APK
      run: ./gradlew assembleDebug # Should be assembleRelease in real scenario
    - name: Create Release
      uses: softprops/action-gh-release@v1
      with:
        files: app/build/outputs/apk/debug/app-debug.apk
""".trimIndent()

    private val ANDROID_CI_FLUTTER_YML = """
name: Android CI (Flutter)

on:
  push:
    branches: [ "**" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - uses: actions/setup-java@v3
      with:
        distribution: 'zulu'
        java-version: '17'

    - uses: subosito/flutter-action@v2
      with:
        channel: 'stable'

    - name: Get Dependencies
      run: flutter pub get

    - name: Build APK
      run: flutter build apk --debug

    - name: Rename Artifact
      run: |
        VERSION=${'$'}(grep 'version:' pubspec.yaml | cut -d ' ' -f 2 | cut -d '+' -f 1)
        mv build/app/outputs/flutter-apk/app-debug.apk IDEaz-${'$'}{VERSION}-debug.apk

    - name: Upload Artifact
      uses: actions/upload-artifact@v3
      with:
        name: app-debug
        path: IDEaz-*.apk
""".trimIndent()

    fun ensureWorkflow(context: Context, projectDir: File, type: ProjectType): Boolean {
        // We use hardcoded strings for robustness if assets are missing
        val workflows = when (type) {
            ProjectType.ANDROID -> listOf(
                "android_ci_jules.yml" to ANDROID_CI_JULES_YML,
                "release.yml" to RELEASE_YML
            )
            ProjectType.FLUTTER -> listOf(
                "android_ci_flutter.yml" to ANDROID_CI_FLUTTER_YML
            )
            else -> emptyList()
        }

        var modified = false
        try {
            val workflowsDir = File(projectDir, ".github/workflows")
            if (!workflowsDir.exists()) {
                workflowsDir.mkdirs()
            }

            for ((filename, content) in workflows) {
                val destFile = File(workflowsDir, filename)
                if (!destFile.exists() || destFile.readText() != content) {
                    destFile.writeText(content)
                    modified = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return modified
    }

    fun ensureAgentsSetupMd(projectDir: File): Boolean {
        var modified = false
        try {
            val agentsFile = File(projectDir, "AGENTS_SETUP.md")
            val instruction = "\n\n# Environment Setup\nPlease run `./setup_env.sh` to set up the build environment."
            if (!agentsFile.exists()) {
                agentsFile.writeText("# Project Instructions$instruction")
                modified = true
            } else {
                val content = agentsFile.readText()
                if (!content.contains("setup_env.sh")) {
                    agentsFile.appendText(instruction)
                    modified = true
                }
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
            val content = EnvironmentSetup.ANDROID_SETUP_SCRIPT

            if (!setupFile.exists() || setupFile.readText() != content) {
                setupFile.writeText(content)
                setupFile.setExecutable(true)
                modified = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return modified
    }

    fun ensureVersioning(projectDir: File, type: ProjectType): Boolean {
        var modified = false
        val androidRoot = when(type) {
            ProjectType.ANDROID -> projectDir
            else -> null
        }

        if (androidRoot != null && androidRoot.exists()) {
            try {
                // 1. Ensure version.properties
                val versionFile = File(androidRoot, "version.properties")
                if (!versionFile.exists()) {
                    versionFile.writeText("major=1\nminor=0\npatch=0\n")
                    modified = true
                }

                // 2. Check build.gradle or build.gradle.kts
                val appDir = File(androidRoot, "app")
                val ktsFile = File(appDir, "build.gradle.kts")
                if (ktsFile.exists()) {
                    if (injectVersioningKts(ktsFile)) modified = true
                } else {
                    val groovyFile = File(appDir, "build.gradle")
                    if (groovyFile.exists()) {
                        if (injectVersioningGroovy(groovyFile)) modified = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return modified
    }

    private fun injectVersioningKts(file: File): Boolean {
        var content = file.readText()
        var modified = false

        if (!content.contains("import java.util.Properties")) {
            content = "import java.util.Properties\nimport java.io.FileInputStream\n\n" + content
            modified = true
        }

        if (!content.contains("val versionProps = Properties()")) {
            val logic = """
val versionProps = Properties()
val versionPropsFile = rootProject.file("version.properties")
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
}

val major = versionProps.getProperty("major", "1").toInt()
val minor = versionProps.getProperty("minor", "0").toInt()
val patch = versionProps.getProperty("patch", "1").toInt()
val buildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1
""".trimIndent()

            val androidMatch = Regex("""\n\s*android\s*\{""").find(content)
            if (androidMatch != null) {
                val insertPos = androidMatch.range.first
                content = content.substring(0, insertPos) + "\n" + logic + "\n" + content.substring(insertPos)
                modified = true
            }
        }

        if (content.contains("val versionProps")) {
            val vcRegex = Regex("""\bversionCode\s*=?\s*\d+""")
            if (vcRegex.containsMatchIn(content)) {
                if (!content.contains("major * 1000000")) {
                    content = content.replace(vcRegex, "versionCode = major * 1000000 + minor * 10000 + patch * 100 + buildNumber")
                    modified = true
                }
            }

            val vnRegex = Regex("""\bversionName\s*=?\s*".*?"""")
            if (vnRegex.containsMatchIn(content)) {
                if (!content.contains("\$major.\$minor")) {
                    content = content.replace(vnRegex, "versionName = \"\$major.\$minor.\$patch.\$buildNumber\"")
                    modified = true
                }
            }
        }

        if (modified) {
            file.writeText(content)
        }
        return modified
    }

    private fun injectVersioningGroovy(file: File): Boolean {
        var content = file.readText()
        var modified = false

        if (!content.contains("import java.util.Properties")) {
            content = "import java.util.Properties\nimport java.io.FileInputStream\n\n" + content
            modified = true
        }

        if (!content.contains("def versionProps = new Properties()")) {
            val logic = """
def versionProps = new Properties()
def versionPropsFile = rootProject.file("version.properties")
if (versionPropsFile.exists()) {
    versionProps.load(new FileInputStream(versionPropsFile))
}

def major = versionProps.getProperty("major", "1").toInteger()
def minor = versionProps.getProperty("minor", "0").toInteger()
def patch = versionProps.getProperty("patch", "1").toInteger()
def buildNumber = System.getenv("BUILD_NUMBER")?.toInteger() ?: 1
""".trimIndent()

            val androidMatch = Regex("""\n\s*android\s*\{""").find(content)
            if (androidMatch != null) {
                val insertPos = androidMatch.range.first
                content = content.substring(0, insertPos) + "\n" + logic + "\n" + content.substring(insertPos)
                modified = true
            }
        }

        if (content.contains("def versionProps")) {
            val vcRegex = Regex("""\bversionCode\s+(\d+)""")
            if (vcRegex.containsMatchIn(content)) {
                if (!content.contains("major * 1000000")) {
                    content = content.replace(vcRegex, "versionCode major * 1000000 + minor * 10000 + patch * 100 + buildNumber")
                    modified = true
                }
            }

            val vnRegex = Regex("""\bversionName\s+"(.*?)"""")
            if (vnRegex.containsMatchIn(content)) {
                if (!content.contains("\$major.\$minor")) {
                    content = content.replace(vnRegex, "versionName \"\$major.\$minor.\$patch.\$buildNumber\"")
                    modified = true
                }
            }
        }

        if (modified) {
            file.writeText(content)
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