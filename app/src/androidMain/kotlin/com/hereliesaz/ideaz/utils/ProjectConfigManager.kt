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
            android.util.Log.w("ProjectConfigManager", "Project config operation failed", e)
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
            android.util.Log.w("ProjectConfigManager", "Project config operation failed", e)
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
            android.util.Log.w("ProjectConfigManager", "Project config operation failed", e)
        }
    }

    // --- WORKFLOW CONTENT ---


    private val WEB_CI_PAGES_YML = """
name: Deploy to GitHub Pages

on:
  push:
  workflow_dispatch:

permissions:
  contents: write

jobs:
  deploy:
    runs-on: ubuntu-latest
    concurrency:
      group: ${'$'}{{ github.workflow }}-${'$'}{{ github.ref }}
    steps:
      - uses: actions/checkout@v7

      - name: Deploy
        uses: peaceiris/actions-gh-pages@v4
        with:
          github_token: ${'$'}{{ secrets.GITHUB_TOKEN }}
          publish_dir: .
""".trimIndent()



    /**
     * The exact project-relative paths [ensureWorkflow]/[ensureSetupScript]/
     * [ensureAgentsSetupMd]/[ensureVersioning] can write for [type], computed
     * without touching disk. Used to preview "Regenerate CI Files" before the
     * user confirms it — this covers every path those four functions can
     * write; it does NOT cover Android's crash-reporter injection
     * ([ProjectInitializer.injectCrashReporting]), whose exact paths (which
     * MainActivity file, if any, gets modified) can only be known by actually
     * walking the project tree, not predicted ahead of time.
     */
    fun initFileRelativePaths(type: ProjectType): List<String> {
        val workflowFilenames = if (type.isWebLike()) listOf("web_ci_pages.yml") else emptyList()
        return workflowFilenames.map { ".github/workflows/$it" } +
            listOf("setup_env.sh", "AGENTS_SETUP.md", "version.properties")
    }

    /** Returns the project-relative paths actually written, empty if nothing changed. */
    fun ensureWorkflow(projectDir: File, type: ProjectType): List<String> {
        // We use hardcoded strings for robustness if assets are missing
        // Keyed on isWebLike(), not on a single enum member. PWA - the only
        // selectable type - used to fall through to `else -> emptyList()` here
        // because the `when` still branched on the pre-split WEB member, so
        // Deploy wrote no workflow at all and then reported success and polled
        // GitHub Pages for ten minutes for a site nothing would ever build.
        //
        // Only the Pages workflow is written. The two `antigravity-*` workflows
        // that used to ride along are deliberately gone: they installed an
        // autonomous agent into the user's repository - `on: push`, with
        // `contents: write` and `pull-requests: write`, running an MCP server
        // whose tool allowlist included create_or_update_file and push_files,
        // authenticated as the user - with no consent prompt anywhere on the
        // path. Whatever that is, it is not part of "initialise my project".
        val workflows = if (type.isWebLike()) {
            listOf("web_ci_pages.yml" to WEB_CI_PAGES_YML)
        } else {
            emptyList()
        }

        val written = mutableListOf<String>()
        try {
            val workflowsDir = File(projectDir, ".github/workflows")
            if (!workflowsDir.exists()) {
                workflowsDir.mkdirs()
            }

            for ((filename, content) in workflows) {
                val destFile = File(workflowsDir, filename)
                if (!destFile.exists() || destFile.readText() != content) {
                    destFile.writeText(content)
                    written += ".github/workflows/$filename"
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ProjectConfigManager", "Project config operation failed", e)
        }
        return written
    }

    /** Returns `["AGENTS_SETUP.md"]` if it wrote the file, empty if nothing changed. */
    fun ensureAgentsSetupMd(projectDir: File): List<String> {
        try {
            val agentsFile = File(projectDir, "AGENTS_SETUP.md")
            val instruction = "\n\n# Environment Setup\nPlease run `./setup_env.sh` to set up the build environment."
            if (!agentsFile.exists()) {
                agentsFile.writeText("# Project Instructions$instruction")
                return listOf("AGENTS_SETUP.md")
            } else {
                val content = agentsFile.readText()
                if (!content.contains("setup_env.sh")) {
                    agentsFile.appendText(instruction)
                    return listOf("AGENTS_SETUP.md")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ProjectConfigManager", "Project config operation failed", e)
        }
        return emptyList()
    }

    /** Returns `["setup_env.sh"]` if it wrote the file, empty if nothing changed. */
    fun ensureSetupScript(projectDir: File): List<String> {
        try {
            val setupFile = File(projectDir, "setup_env.sh")
            val content = EnvironmentSetup.ANDROID_SETUP_SCRIPT

            if (!setupFile.exists() || setupFile.readText() != content) {
                setupFile.writeText(content)
                setupFile.setExecutable(true)
                return listOf("setup_env.sh")
            }
        } catch (e: Exception) {
            android.util.Log.w("ProjectConfigManager", "Project config operation failed", e)
        }
        return emptyList()
    }

    /** Returns the project-relative paths actually written, empty if nothing changed. */
    fun ensureVersioning(projectDir: File, type: ProjectType): List<String> {
        val written = mutableListOf<String>()
        if (projectDir.exists()) {
            try {
                val versionFile = File(projectDir, "version.properties")
                if (!versionFile.exists()) {
                    versionFile.writeText("major=1\nminor=0\npatch=0\nbuild=1\n")
                    written += "version.properties"
                } else {
                    val existing = versionFile.readText()
                    // `${'$'}` emits a *literal* dollar sign. It is correct inside the
                    // raw workflow strings above, and was cargo-culted into these two
                    // lines where it is catastrophic: the guard compiled to the regex
                    // `(?m)^$key=`, which requires an empty line immediately followed
                    // by `key=` and therefore matches nothing at all - so every key
                    // always looked missing - and the appended text was the literal
                    // `$key=$value`. Every clone, init, deploy and fork appended four
                    // junk lines to the user's version.properties, committed them, and
                    // pushed them to their default branch, without bound.
                    val missing = listOf("major" to "1", "minor" to "0", "patch" to "0", "build" to "1")
                        .filterNot { (key, _) -> Regex("(?m)^\\Q$key\\E=").containsMatchIn(existing) }
                    if (missing.isNotEmpty()) {
                        val separator = if (existing.endsWith('\n')) "" else "\n"
                        versionFile.appendText(
                            separator + missing.joinToString("\n") { (key, value) -> "$key=$value" } + "\n"
                        )
                        written += "version.properties"
                    }
                }

                if (type != ProjectType.ANDROID) return written

                // Inject Android version fields when a conventional app module exists.
                val appDir = File(projectDir, "app")
                val ktsFile = File(appDir, "build.gradle.kts")
                if (ktsFile.exists()) {
                    if (injectVersioningKts(ktsFile)) written += "app/build.gradle.kts"
                } else {
                    val groovyFile = File(appDir, "build.gradle")
                    if (groovyFile.exists()) {
                        if (injectVersioningGroovy(groovyFile)) written += "app/build.gradle"
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ProjectConfigManager", "Project config operation failed", e)
            }
        }
        return written
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
    FileInputStream(versionPropsFile).use { versionProps.load(it) }
}

val major = versionProps.getProperty("major", "1").toInt()
val minor = versionProps.getProperty("minor", "0").toInt()
val patch = versionProps.getProperty("patch", "1").toInt()
val buildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: versionProps.getProperty("build", "1").toInt()
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
    versionPropsFile.withInputStream { stream -> versionProps.load(stream) }
}

def major = versionProps.getProperty("major", "1").toInteger()
def minor = versionProps.getProperty("minor", "0").toInteger()
def patch = versionProps.getProperty("patch", "1").toInteger()
def buildNumber = System.getenv("BUILD_NUMBER")?.toInteger() ?: versionProps.getProperty("build", "1").toInteger()
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
                    android.util.Log.w("ProjectConfigManager", "Project config operation failed", e)
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
            android.util.Log.w("ProjectConfigManager", "Project config operation failed", e)
        }
    }
}
