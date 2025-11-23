package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.models.ProjectType
import java.io.File

object ProjectAnalyzer {

    fun detectProjectType(projectDir: File): ProjectType {
        if (!projectDir.exists()) return ProjectType.OTHER

        // Check for Flutter
        if (File(projectDir, "pubspec.yaml").exists()) return ProjectType.FLUTTER

        // Check for React Native vs Web
        val packageJson = File(projectDir, "package.json")
        if (packageJson.exists()) {
            // Simple heuristic: check for android/ios folders or app.json which are typical in RN
            if (File(projectDir, "android").exists() ||
                File(projectDir, "ios").exists() ||
                File(projectDir, "app.json").exists()) {
                return ProjectType.REACT_NATIVE
            }
            // If index.html exists, it's likely Web
            if (File(projectDir, "index.html").exists()) {
                return ProjectType.WEB
            }
        }

        if (File(projectDir, "index.html").exists()) return ProjectType.WEB

        // Check for Android
        if (File(projectDir, "build.gradle.kts").exists() ||
            File(projectDir, "build.gradle").exists() ||
            File(projectDir, "app/build.gradle.kts").exists() ||
            File(projectDir, "app/build.gradle").exists()) {
            return ProjectType.ANDROID
        }

        return ProjectType.OTHER
    }

    fun detectPackageName(projectDir: File): String? {
        // 1. Check AndroidManifest.xml in standard locations
        val locations = listOf(
            "app/src/main/AndroidManifest.xml",
            "src/main/AndroidManifest.xml"
        )

        for (path in locations) {
            val manifest = File(projectDir, path)
            if (manifest.exists()) {
                val content = manifest.readText()
                // Regex to find package="com.example"
                val regex = """package\s*=\s*"([^"]+)"""".toRegex()
                val match = regex.find(content)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
        }

        // 2. Check Gradle files for namespace
        val gradleLocations = listOf(
            "app/build.gradle.kts",
            "app/build.gradle",
            "build.gradle.kts",
            "build.gradle"
        )

        for (path in gradleLocations) {
            val file = File(projectDir, path)
            if (file.exists()) {
                val content = file.readText()

                // Check applicationId first (Install ID)
                val appIdRegex = """applicationId\s*[=]?\s*["']([^"']+)["']""".toRegex()
                val appIdMatch = appIdRegex.find(content)
                if (appIdMatch != null) {
                    return appIdMatch.groupValues[1]
                }

                // Fallback to namespace (R class package)
                val namespaceRegex = """namespace\s*[=]?\s*["']([^"']+)["']""".toRegex()
                val match = namespaceRegex.find(content)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
        }

        return null
    }
}
