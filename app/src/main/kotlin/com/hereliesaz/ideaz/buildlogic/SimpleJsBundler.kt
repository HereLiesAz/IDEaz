package com.hereliesaz.ideaz.buildlogic

import java.io.File
import org.json.JSONObject

class SimpleJsBundler {

    fun bundle(projectDir: File, outputDir: File): BuildResult {
        val appJs = File(projectDir, "App.js")
        if (!appJs.exists()) {
            return BuildResult(false, "App.js not found in ${projectDir.absolutePath}")
        }

        val appName = readAppNameFromConfig(projectDir)
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
        bundleContent.append(processedCode)
        bundleContent.append("\n")

        // 3. Entry Point Registration
        // We assume the user code exports 'App' or defines it.
        // To be safe, we might need to modify the user code to assign to a variable we know.
        // For the template 'export default function App()', parsing is tricky without AST.
        // Hack: Append a register call that assumes 'App' is in scope or default export.
        // If 'App.js' is an ES module, we can't just concat. We need a module system.
        // MVP: We assume 'App.js' is written as a script or we strip 'export default'.

        // This is a complex part. For the MVP to work "conceptually" with the provided template:
        // Template: export default function App() { ... }
        // We replace 'export default function App' with 'function App'
        // Then register it.

        val finalizedCode = processedCode.replace("export default function App", "function App")

        val bundleWithRegistration = """
            $finalizedCode

            // Mock AppRegistry for MVP if not in vendor
            if (typeof AppRegistry !== 'undefined') {
                AppRegistry.registerComponent('$appName', () => App);
            } else {
                console.log("AppRegistry not found, skipping registration");
            }
        """.trimIndent()

        bundleContent.append(bundleWithRegistration)

        val outFile = File(outputDir, "index.android.bundle")
        outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        outFile.writeText(bundleContent.toString())

        return BuildResult(true, "Bundled successfully to ${outFile.absolutePath}")
    }

    private fun readAppNameFromConfig(projectDir: File): String {
        val appJsonFile = File(projectDir, "app.json")
        if (appJsonFile.exists()) {
            try {
                val jsonContent = appJsonFile.readText()
                val jsonObject = JSONObject(jsonContent)

                // Check root level "name" (Standard RN)
                val name = jsonObject.optString("name")
                if (name.isNotEmpty()) {
                    return name
                }

                // Check "expo" -> "name" (Expo RN)
                val expoObject = jsonObject.optJSONObject("expo")
                val expoName = expoObject?.optString("name")
                if (!expoName.isNullOrEmpty()) {
                    return expoName
                }

            } catch (e: Exception) {
                // Log warning or ignore, fallback to default
                println("Error parsing app.json: ${e.message}")
            }
        }
        return "MyReactNativeApp"
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
