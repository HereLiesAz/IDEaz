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
}
