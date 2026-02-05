package com.hereliesaz.ideaz.utils

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

    private val WEB_CI_PAGES_YML = """
name: Deploy to GitHub Pages

on:
  push:
    branches: ["main", "master"]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  deploy:
    runs-on: ubuntu-latest
    concurrency:
      group: ${'$'}{{ github.workflow }}-${'$'}{{ github.ref }}
    steps:
      - uses: actions/checkout@v3

      - name: Deploy
        uses: peaceiris/actions-gh-pages@v3
        with:
          github_token: ${'$'}{{ secrets.GITHUB_TOKEN }}
          publish_dir: .
""".trimIndent()

    private val ANDROID_CI_FLUTTER_YML = """
name: Android CI (Flutter)

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
    - name: Set up Flutter
      uses: subosito/flutter-action@v2
      with:
        flutter-version: '3.16.0'
        channel: 'stable'
    - name: Get Dependencies
      run: flutter pub get
    - name: Build APK
      run: flutter build apk --debug
    - name: Rename Artifact
      run: |
        VERSION=${'$'}(grep '^version:' pubspec.yaml | head -n 1 | sed 's/^version:[[:space:]]*//' | tr -d '\r' | tr -d ' ')
        if [ -z "${'$'}VERSION" ]; then VERSION="1.0.0"; fi
        mv build/app/outputs/flutter-apk/app-debug.apk build/app/outputs/flutter-apk/IDEaz-${'$'}VERSION-debug.apk
    - name: Upload APK
      uses: actions/upload-artifact@v3
      with:
        name: app-debug
        path: build/app/outputs/flutter-apk/IDEaz-*-debug.apk
""".trimIndent()

    private val ANDROID_CI_REACT_NATIVE_YML = """
name: Android CI (React Native)

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
    - name: Setup Node
      uses: actions/setup-node@v3
      with:
        node-version: 18
        cache: 'npm'
    - name: Install Dependencies
      run: npm install
    - name: Bundle JS
      run: |
        npx react-native bundle --platform android --dev false --entry-file index.js --bundle-output index.android.bundle --assets-dest assets
    - name: Upload Bundle
      uses: actions/upload-artifact@v3
      with:
        name: js-bundle
        path: |
          index.android.bundle
          assets/
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    - name: Build Android
      run: ./gradlew assembleDebug
    - name: Upload APK
      uses: actions/upload-artifact@v3
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk
""".trimIndent()

    private val JULES_ISSUE_HANDLER_YML = """
name: Jules Issue Handler

on:
  issues:
    types: [opened]

jobs:
  handle_issue:
    runs-on: ubuntu-latest
    permissions:
      contents: write
      issues: write
      pull-requests: write
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: 'Run Gemini CLI'
        uses: 'google-github-actions/run-gemini-cli@v0'
        env:
          TITLE: '${'$'}{{ github.event.pull_request.title || github.event.issue.title }}'
          DESCRIPTION: '${'$'}{{ github.event.pull_request.body || github.event.issue.body }}'
          EVENT_NAME: '${'$'}{{ github.event_name }}'
          GITHUB_TOKEN: '${'$'}{{ secrets.GH_TOKEN || github.token }}'
          IS_PULL_REQUEST: '${'$'}{{ !!github.event.pull_request }}'
          ISSUE_NUMBER: '${'$'}{{ github.event.pull_request.number || github.event.issue.number }}'
          REPOSITORY: '${'$'}{{ github.repository }}'
        with:
          gemini_api_key: '${'$'}{{ secrets.JULES_API_KEY }}'
          gemini_cli_version: '0.24.0'
          workflow_name: 'jules-issue-handler'
          use_gemini_code_assist: false
          use_vertex_ai: false
          settings: |-
            {
              "model": { "maxSessionTurns": 25 },
              "telemetry": { "enabled": true, "target": "local", "outfile": ".gemini/telemetry.log" },
              "mcpServers": {
                "github": {
                  "command": "docker",
                  "args": [ "run", "-i", "--rm", "-e", "GITHUB_PERSONAL_ACCESS_TOKEN", "ghcr.io/github/github-mcp-server:v0.18.0" ],
                  "includeTools": [
                    "add_issue_comment", "get_issue", "get_issue_comments", "list_issues", "search_issues",
                    "create_pull_request", "pull_request_read", "list_pull_requests", "search_pull_requests",
                    "create_branch", "create_or_update_file", "delete_file", "fork_repository", "get_commit",
                    "get_file_contents", "list_commits", "push_files", "search_code", "add_comment_to_pending_review",
                    "create_pending_pull_request_review", "submit_pending_pull_request_review"
                  ],
                  "env": { "GITHUB_PERSONAL_ACCESS_TOKEN": "${'$'}{{ GITHUB_TOKEN }}" }
                }
              },
              "tools": {
                "core": [
                  "run_shell_command(cat)", "run_shell_command(echo)", "run_shell_command(grep)",
                  "run_shell_command(head)", "run_shell_command(tail)", "run_shell_command(git)",
                  "run_shell_command(gh)", "run_shell_command(ls)"
                ]
              }
            }
          prompt: |
            You are an AI agent assigned to handle this issue.

            Issue Title: ${'$'}{{ github.event.issue.title }}
            Issue Body: ${'$'}{{ github.event.issue.body }}

            Your task is to:
            1. Analyze the issue (including security issues if applicable) and identify the necessary changes.
            2. Implement the solution in the codebase.
            3. Verify your changes.
            4. Once the task is complete and verified, close this issue.
""".trimIndent()

    private val JULES_BRANCH_MANAGER_YML = """
name: Jules Branch Manager

on:
  push:
    branches-ignore:
      - 'main'
      - 'master'
  pull_request_review:
    types: [submitted]
  workflow_dispatch:

jobs:
  manage_branch:
    runs-on: ubuntu-latest
    permissions:
      contents: write
      pull-requests: write
      issues: write
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: 'Run Gemini CLI'
        uses: 'google-github-actions/run-gemini-cli@v0'
        env:
          TITLE: '${'$'}{{ github.event.pull_request.title || github.event.issue.title }}'
          DESCRIPTION: '${'$'}{{ github.event.pull_request.body || github.event.issue.body }}'
          EVENT_NAME: '${'$'}{{ github.event_name }}'
          GITHUB_TOKEN: '${'$'}{{ secrets.GH_TOKEN || github.token }}'
          IS_PULL_REQUEST: '${'$'}{{ !!github.event.pull_request }}'
          ISSUE_NUMBER: '${'$'}{{ github.event.pull_request.number || github.event.issue.number }}'
          REPOSITORY: '${'$'}{{ github.repository }}'
        with:
          gemini_api_key: '${'$'}{{ secrets.JULES_API_KEY }}'
          gemini_cli_version: '0.24.0'
          workflow_name: 'jules-branch-manager'
          use_gemini_code_assist: false
          use_vertex_ai: false
          settings: |-
            {
              "model": { "maxSessionTurns": 25 },
              "telemetry": { "enabled": true, "target": "local", "outfile": ".gemini/telemetry.log" },
              "mcpServers": {
                "github": {
                  "command": "docker",
                  "args": [ "run", "-i", "--rm", "-e", "GITHUB_PERSONAL_ACCESS_TOKEN", "ghcr.io/github/github-mcp-server:v0.18.0" ],
                  "includeTools": [
                    "add_issue_comment", "get_issue", "get_issue_comments", "list_issues", "search_issues",
                    "create_pull_request", "pull_request_read", "list_pull_requests", "search_pull_requests",
                    "create_branch", "create_or_update_file", "delete_file", "fork_repository", "get_commit",
                    "get_file_contents", "list_commits", "push_files", "search_code", "add_comment_to_pending_review",
                    "create_pending_pull_request_review", "submit_pending_pull_request_review"
                  ],
                  "env": { "GITHUB_PERSONAL_ACCESS_TOKEN": "${'$'}{{ GITHUB_TOKEN }}" }
                }
              },
              "tools": {
                "core": [
                  "run_shell_command(cat)", "run_shell_command(echo)", "run_shell_command(grep)",
                  "run_shell_command(head)", "run_shell_command(tail)", "run_shell_command(git)",
                  "run_shell_command(gh)", "run_shell_command(ls)"
                ]
              }
            }
          prompt: |
            You are an autonomous Pull Request Manager Agent.

            Context:
            - Current Branch: ${'$'}{{ github.ref_name }}
            - Repository: ${'$'}{{ github.repository }}
            - Event: ${'$'}{{ github.event_name }}

            Your mission is to shepherd this branch from creation to merge.

            Step 1: PR Creation
            Check if a Pull Request exists for this branch. If not, create one targeting the repository's default branch.

            Step 2: Evaluation
            Analyze the code changes.
            - Are they valid, safe, and necessary?
            - If the changes are clearly garbage, malicious, or unnecessary, close the PR and delete the branch. Stop execution.

            Step 3: Review & Fix
            - Perform a self-review of the code. If you find bugs or style issues, fix them.
            - Check for review comments from other users. If there are requests for changes or suggestions, implement fixes for them.

            Step 4: Conflict Resolution
            Check if there are merge conflicts with the default branch. If yes, merge the default branch into this one and resolve the conflicts effectively.

            Step 5: Merge & Cleanup
            If the PR is valid, clean, passes your review, and has no unresolved conflicts:
            - Merge the Pull Request into the default branch.
            - Close the Pull Request (if not closed by merge).
            - Delete the source branch (${'$'}{{ github.ref_name }}) to clean up.

            If you pushed new changes in Steps 3 or 4, stop here and let the next workflow run handle the rest.
""".trimIndent()

    fun ensureWorkflow(projectDir: File, type: ProjectType): Boolean {
        // We use hardcoded strings for robustness if assets are missing
        val workflows = when (type) {
            ProjectType.ANDROID -> listOf(
                "android_ci_jules.yml" to ANDROID_CI_JULES_YML,
                "release.yml" to RELEASE_YML,
                "jules-issue-handler.yml" to JULES_ISSUE_HANDLER_YML,
                "jules-branch-manager.yml" to JULES_BRANCH_MANAGER_YML
            )
            ProjectType.FLUTTER -> listOf(
                "android_ci_flutter.yml" to ANDROID_CI_FLUTTER_YML,
                "jules-issue-handler.yml" to JULES_ISSUE_HANDLER_YML,
                "jules-branch-manager.yml" to JULES_BRANCH_MANAGER_YML
            )
            ProjectType.REACT_NATIVE -> listOf(
                "android_ci_react_native.yml" to ANDROID_CI_REACT_NATIVE_YML,
                "jules-issue-handler.yml" to JULES_ISSUE_HANDLER_YML,
                "jules-branch-manager.yml" to JULES_BRANCH_MANAGER_YML
            )
            ProjectType.WEB -> listOf(
                "web_ci_pages.yml" to WEB_CI_PAGES_YML,
                "jules-issue-handler.yml" to JULES_ISSUE_HANDLER_YML,
                "jules-branch-manager.yml" to JULES_BRANCH_MANAGER_YML
            )
            ProjectType.PYTHON -> listOf(
                "android_ci_jules.yml" to ANDROID_CI_JULES_YML,
                "release.yml" to RELEASE_YML,
                "jules-issue-handler.yml" to JULES_ISSUE_HANDLER_YML,
                "jules-branch-manager.yml" to JULES_BRANCH_MANAGER_YML
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
            ProjectType.ANDROID, ProjectType.PYTHON -> projectDir
            ProjectType.FLUTTER, ProjectType.REACT_NATIVE -> File(projectDir, "android")
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
