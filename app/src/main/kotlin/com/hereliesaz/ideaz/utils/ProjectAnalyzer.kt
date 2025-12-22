package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.models.ProjectType
import java.io.File

object ProjectAnalyzer {

    fun detectProjectType(projectDir: File): ProjectType {
        if (!projectDir.exists()) return ProjectType.OTHER

        // Check for Web
        if (File(projectDir, "index.html").exists()) return ProjectType.WEB

        // Check for Python (must be checked before Android as it shares structure)
        if (File(projectDir, "app/src/main/assets/python/main.py").exists()) {
            return ProjectType.PYTHON
        }

        // Check for Flutter
        // Confirmed: pubspec.yaml triggers Flutter project detection
        if (File(projectDir, "pubspec.yaml").exists()) return ProjectType.FLUTTER

        // Check for React Native
        if (File(projectDir, "package.json").exists() && File(projectDir, "app.json").exists()) {
            return ProjectType.REACT_NATIVE
        }

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
            "src/main/AndroidManifest.xml",
            "android/app/src/main/AndroidManifest.xml"
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
            "build.gradle",
            "android/app/build.gradle",
            "android/app/build.gradle.kts"
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

        // 3. Fallback: Infer from source directory structure
        val sourceRoots = listOf(
            "app/src/main/java",
            "app/src/main/kotlin",
            "src/main/java",
            "src/main/kotlin"
        )

        for (rootPath in sourceRoots) {
            val rootDir = File(projectDir, rootPath)
            if (rootDir.exists()) {
                val firstSourceFile = rootDir.walk()
                    .filter { it.isFile && (it.extension == "java" || it.extension == "kt") }
                    .firstOrNull()

                if (firstSourceFile != null) {
                    // path: .../app/src/main/java/com/example/MyClass.kt
                    // relative: com/example/MyClass.kt
                    // parent: com/example
                    // package: com.example
                    val relativePath = firstSourceFile.parentFile?.relativeTo(rootDir)?.path
                    if (!relativePath.isNullOrBlank()) {
                        return relativePath.replace(File.separatorChar, '.')
                    }
                }
            }
        }

        // 4. Final Fallback: Generate based on project folder name
        val sanitizedName = projectDir.name.filter { it.isLetterOrDigit() }.lowercase()
        return "com.ideaz.generated.$sanitizedName"
    }
}
