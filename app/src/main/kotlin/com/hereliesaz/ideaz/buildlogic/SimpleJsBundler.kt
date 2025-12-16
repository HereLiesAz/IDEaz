package com.hereliesaz.ideaz.buildlogic

import java.io.File
import org.json.JSONObject

class SimpleJsBundler {

    fun bundle(projectDir: File, outputDir: File): BuildResult {
        val appJs = File(projectDir, "App.js")
        if (!appJs.exists()) {
            return BuildResult(false, "App.js not found in ${projectDir.absolutePath}")
        }

        // 1. Get App Name
        val appName = getAppName(projectDir)
        val bundleContent = StringBuilder()

        // 1. Vendor Bundle (React + React Native Core)
        // Ideally this comes from a pre-built asset. For this MVP/Simulation, we'll assume
        // the environment or a mock provides it, or we just append a comment.
        // In a real implementation, we would copy a 'vendor.js' here.
        bundleContent.append("/** Vendor Bundle (React, RN) would go here **/\n")
        bundleContent.append("var global = this;\n") // Polyfill

        // 2. Process User Code
        val lines = appJs.readLines()
        val processedCode = processSource(lines, "App.js")

        // 3. Entry Point Registration
        // Replace 'export default' with a variable assignment to capture the component
        val entryPointVar = "__IdeazApp"
        val (finalizedCode, foundExport) = rewriteExportDefault(processedCode, entryPointVar)

        bundleContent.append(finalizedCode)
        bundleContent.append("\n")

        val registerCode = if (foundExport) {
             """
            // Register the captured default export
            if (typeof AppRegistry !== 'undefined') {
                AppRegistry.registerComponent('$appName', () => $entryPointVar);
            } else {
                console.log("AppRegistry not found, skipping registration");
            }
            """.trimIndent()
        } else {
            // Fallback: Assume 'App' is global (legacy behavior)
             """
            if (typeof AppRegistry !== 'undefined' && typeof App !== 'undefined') {
                AppRegistry.registerComponent('$appName', () => App);
            } else {
                console.log("AppRegistry not found or App global missing, skipping registration");
            }
            """.trimIndent()
        }

        bundleContent.append(registerCode)

        val outFile = File(outputDir, "index.android.bundle")
        outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        outFile.writeText(bundleContent.toString())

        return BuildResult(true, "Bundled successfully to ${outFile.absolutePath}")
    }

    private fun getAppName(projectDir: File): String {
        val appJson = File(projectDir, "app.json")
        if (appJson.exists()) {
            try {
                val json = JSONObject(appJson.readText())
                // Check root name first (standard)
                if (json.has("name")) {
                    return json.getString("name")
                }
                // Then check Expo
                val expo = json.optJSONObject("expo")
                if (expo != null && expo.has("name")) {
                    return expo.getString("name")
                }
                // Fallback to displayName
                if (json.has("displayName")) {
                    return json.getString("displayName")
                }
            } catch (e: Exception) {
                // Ignore parse errors, fallback
            }
        }
        return "MyReactNativeApp"
    }

    private fun rewriteExportDefault(code: String, varName: String): Pair<String, Boolean> {
        // Strategy: Replace "export default" with "const varName ="
        // This works for:
        // export default function App() {}  -> const varName = function App() {}
        // export default class App {}       -> const varName = class App {}
        // export default () => {}           -> const varName = () => {}
        // export default Something;         -> const varName = Something;

        // Regex looks for "export default" at the start of a line (ignoring whitespace)
        // We use MULTILINE mode to match ^ at start of lines.
        val regex = Regex("^\\s*export\\s+default\\s+", RegexOption.MULTILINE)

        if (regex.containsMatchIn(code)) {
            // Replace only the first occurrence to be safe
            val newCode = regex.replaceFirst(code, "const $varName = ")
            return Pair(newCode, true)
        }

        // Handle "export { Name as default }"
        val namedDefaultRegex = Regex("export\\s+\\{\\s*([a-zA-Z0-9_\\$]+)\\s+as\\s+default\\s*\\};?")
        if (namedDefaultRegex.containsMatchIn(code)) {
            val newCode = namedDefaultRegex.replace(code) { matchResult ->
                val exportedName = matchResult.groupValues[1]
                "const $varName = $exportedName;"
            }
            return Pair(newCode, true)
        }

        return Pair(code, false)
    }

    fun processSource(lines: List<String>, fileName: String): String {
        return lines.mapIndexed { index, line ->
            // Regex to match opening JSX tags (e.g., <View, <Text)
            // Exclude closing tags </View>
            // <([A-Z][a-zA-Z0-9\.]*)  Matches <View or <React.Fragment
            // ([^>]*?)                 Matches attributes (non-greedy)
            // (?<!/)>                  Matches closing > but ensures it's not /> (self-closing check handled separately?)
            // Actually, we want to inject into both <View> and <View />

            // Regex: <(TagName)(Attributes)>
            // replacement: <TagName Attributes accessibilityLabel="...">>

            // Note: This regex is simple and brittle. It fails on multi-line tags or tags with > in strings.
            // But it serves the MVP "Lay the groundwork" goal.
            val regex = Regex("<([A-Z][a-zA-Z0-9\\.]*)([^>]*)>")

            regex.replace(line) { match ->
                val tagName = match.groupValues[1]
                val attributes = match.groupValues[2]
                val lineNumber = index + 1

                // Don't inject if already has accessibilityLabel to avoid duplicates
                if (attributes.contains("accessibilityLabel")) {
                    match.value
                } else {
                    // Check if self-closing
                    if (attributes.trim().endsWith("/")) {
                        val attrsWithoutSlash = attributes.trim().dropLast(1)
                        "<$tagName$attrsWithoutSlash accessibilityLabel=\"__source:${fileName}:${lineNumber}__\" />"
                    } else {
                        "<$tagName$attributes accessibilityLabel=\"__source:${fileName}:${lineNumber}__\">"
                    }
                }
            }
        }.joinToString("\n")
    }
}
