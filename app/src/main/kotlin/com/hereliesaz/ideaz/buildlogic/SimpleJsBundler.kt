package com.hereliesaz.ideaz.buildlogic

import java.io.File
import org.json.JSONObject

/**
 * Basic bundler for React Native projects without Node.js.
 */
class SimpleJsBundler {
    data class Result(val success: Boolean, val output: String)

    fun bundle(projectDir: File, outputDir: File): Result {
        try {
            val appJsonFile = File(projectDir, "app.json")
            if (!appJsonFile.exists()) {
                return Result(false, "app.json not found")
            }

            val appJson = JSONObject(appJsonFile.readText())
            var appName = appJson.optString("name")
            if (appName.isEmpty()) {
                val expo = appJson.optJSONObject("expo")
                if (expo != null) {
                    appName = expo.optString("name")
                }
            }
            if (appName.isEmpty()) {
                appName = appJson.optString("displayName")
            }
            if (appName.isEmpty()) {
                appName = "MyReactNativeApp"
            }

            val appJsFile = File(projectDir, "App.js")
            if (!appJsFile.exists()) {
                return Result(false, "App.js not found")
            }

            var appJsContent = appJsFile.readText()
            appJsContent = processSource(appJsContent.lines(), "App.js")

            val sb = StringBuilder()
            sb.append("import { AppRegistry } from 'react-native';\n")
            sb.append("\n")
            sb.append(appJsContent)
            sb.append("\n\n")
            sb.append("AppRegistry.registerComponent('$appName', () => App);\n")

            if (!outputDir.exists()) outputDir.mkdirs()
            val outFile = File(outputDir, "index.android.bundle")
            outFile.writeText(sb.toString())

            return Result(true, "Bundled successfully to ${outFile.absolutePath}")
        } catch (e: Exception) {
            return Result(false, "Error: ${e.message}")
        }
    }

    fun processSource(lines: List<String>, filename: String): String {
        return lines.mapIndexed { index, line ->
            val lineNumber = index + 1
            val label = "__source:$filename:${lineNumber}__"

            line.replace(Regex("(<[A-Z][a-zA-Z0-9]*)((?:\\s+[^>]*?)?)(/?>)")) { matchResult ->
                 val tag = matchResult.groupValues[1]
                 val props = matchResult.groupValues[2]
                 val end = matchResult.groupValues[3]
                 "$tag$props accessibilityLabel=\"$label\"$end"
            }
        }.joinToString("\n")
    }
}
