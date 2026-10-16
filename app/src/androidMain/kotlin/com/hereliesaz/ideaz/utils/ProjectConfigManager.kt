package com.hereliesaz.ideaz.utils

import android.util.Base64
import com.hereliesaz.ideaz.models.IdeazProjectConfig
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

    // --- WORKFLOW CONTENT ---

    /**
     * The Pages workflow IDEaz used to write, byte for byte. It published the
     * repository root (`publish_dir: .`) on every push, which is how a `.env`
     * or `.ideaz/` history reached the open web. Publishing now goes through the
     * central Worker (see [com.hereliesaz.ideaz.api.WebDeployClient]) and a
     * project carries no workflow, so this text is kept only to recognise -
     * and remove - an unmodified copy of it.
     */
    private val LEGACY_WEB_CI_PAGES_YML = """
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

    const val LEGACY_WORKFLOW_PATH = ".github/workflows/web_ci_pages.yml"

    /**
     * The exact project-relative paths `RepoDelegate.forceUpdateInitFiles`
     * can touch, computed without touching disk. Used to preview
     * "Regenerate CI Files" before the user confirms it.
     */
    fun initFileRelativePaths(): List<String> = listOf(
        ".gitignore",
        LEGACY_WORKFLOW_PATH,
        "AGENTS_SETUP.md",
        "version.properties",
    )

    /**
     * Writes or completes `.gitignore` (see [DeployIgnore]). Returns
     * `[".gitignore"]` when it changed, empty otherwise.
     */
    fun ensureGitIgnore(projectDir: File): List<String> =
        try {
            if (DeployIgnore.ensure(projectDir)) listOf(".gitignore") else emptyList()
        } catch (e: Exception) {
            android.util.Log.w("ProjectConfigManager", "Project config operation failed", e)
            emptyList()
        }

    /**
     * Deletes the Pages workflow IDEaz used to generate, but only an unmodified
     * copy: a workflow the user edited is theirs. Returns the deleted path (to
     * be staged as a removal), empty if there was nothing to delete.
     *
     * This used to be `ensureWorkflow`, which wrote that file on every Deploy.
     * The `antigravity-*` workflows that once rode along were removed earlier
     * for installing an autonomous agent into the user's repository without
     * consent; the Pages workflow is removed for publishing the whole tree.
     */
    fun retireLegacyWorkflow(projectDir: File): List<String> {
        try {
            val file = File(projectDir, LEGACY_WORKFLOW_PATH)
            if (file.isFile && file.readText().trimEnd() == LEGACY_WEB_CI_PAGES_YML.trimEnd()) {
                if (file.delete()) return listOf(LEGACY_WORKFLOW_PATH)
            }
        } catch (e: Exception) {
            android.util.Log.w("ProjectConfigManager", "Project config operation failed", e)
        }
        return emptyList()
    }

    private const val AGENTS_MARKER = "<!-- ideaz-preview-contract -->"

    private val AGENTS_SETUP_MD = """
# Project Instructions

$AGENTS_MARKER

This project is edited through IDEaz, which previews it by mounting the working
tree in a WebView and transpiling JSX/TS in the browser with Babel. There is no
build step and no dev server: whatever is on disk is what renders.

Practical consequences for an agent editing this repository:

- Edit source files directly. Do not add a bundler step or expect one to run.
- Bare specifiers (`react`, `react-dom`, and the common ecosystem libraries)
  resolve through a bundled import map. Adding a dependency to `package.json`
  does not make it importable in the preview.
- Vite-only features are unsupported: `import.meta.env`, HMR, and glob / `?raw`
  / `?url` imports.
""".trimIndent()

    /** Returns `["AGENTS_SETUP.md"]` if it wrote the file, empty if nothing changed. */
    fun ensureAgentsSetupMd(projectDir: File): List<String> {
        try {
            val agentsFile = File(projectDir, "AGENTS_SETUP.md")
            // This used to instruct the agent to run `./setup_env.sh`, a script
            // that installed a JDK and the Android SDK. IDEaz does not build
            // Android projects, that script is no longer written, and pointing an
            // agent at a file which does not exist is worse than saying nothing.
            if (!agentsFile.exists()) {
                agentsFile.writeText(AGENTS_SETUP_MD + "\n")
                return listOf("AGENTS_SETUP.md")
            }
            if (!agentsFile.readText().contains(AGENTS_MARKER)) {
                agentsFile.appendText("\n\n" + AGENTS_SETUP_MD + "\n")
                return listOf("AGENTS_SETUP.md")
            }
        } catch (e: Exception) {
            android.util.Log.w("ProjectConfigManager", "Project config operation failed", e)
        }
        return emptyList()
    }

    /**
     * Ensures `version.properties` exists and carries all four keys.
     *
     * This used to continue on for an Android project and rewrite the app
     * module's `build.gradle(.kts)` — injecting a `Properties` loader and
     * regex-replacing `versionCode`/`versionName`. Two hundred lines of
     * text-munging someone else's build script, for a target IDEaz no longer
     * edits. Gone.
     *
     * Returns the project-relative paths actually written, empty if nothing
     * changed.
     */
    fun ensureVersioning(projectDir: File): List<String> {
        val written = mutableListOf<String>()
        if (!projectDir.exists()) return written
        try {
            val versionFile = File(projectDir, "version.properties")
            if (!versionFile.exists()) {
                versionFile.writeText("major=1\nminor=0\npatch=0\nbuild=1\n")
                written += "version.properties"
            } else {
                val existing = versionFile.readText()
                // `${'$'}` emits a *literal* dollar sign. It is correct inside the
                // raw workflow string above, and was cargo-culted into these two
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
        } catch (e: Exception) {
            android.util.Log.w("ProjectConfigManager", "Project config operation failed", e)
        }
        return written
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
