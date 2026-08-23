package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.models.ProjectType
import java.io.File

object ProjectAnalyzer {

    /** Where a web entry point realistically lives, in priority order. */
    private val ENTRY_DIRS = listOf("", "public/", "src/", "app/", "www/", "docs/", "site/")

    /**
     * Classifies a project by what IDEaz can actually do with it.
     *
     * The old rule was "index.html at the repo root, PLUS one of four PWA marker
     * files". That encoded an assumption from the original design - that a "PWA"
     * is hand-written HTML where the DOM you tap *is* the source you edit - which
     * almost no real web project satisfies. Two consequences followed:
     *
     *  - A standard React/Vite app resolved to WEB, which is not selectable, so
     *    it could not be opened at all. That included IDEaz's own bundled React
     *    template, which has index.html, package.json and vite.config.js but no
     *    web manifest.
     *  - The 4.5 MB React/Babel runtime IDEaz ships to preview exactly those
     *    projects was therefore unreachable for them.
     *
     * The question that actually matters is "is there a JS/HTML entry point we
     * can mount and transpile in-browser?" - not "did someone remember to write a
     * webmanifest". Anything web-like that has one is previewable, so it reports
     * PWA, the selectable type. The marker files still matter for nothing but
     * accuracy of the label, so they are gone.
     */
    fun detectProjectType(projectDir: File): ProjectType {
        if (!projectDir.exists()) return ProjectType.OTHER

        if (findWebEntryPoint(projectDir) != null) return ProjectType.PWA

        // A package.json with a web-ish entry is enough on its own: a Vite/Next
        // project may keep index.html somewhere this doesn't look, and the loader
        // resolves the real entry at mount time anyway.
        if (File(projectDir, "package.json").isFile) return ProjectType.PWA

        if (File(projectDir, "build.gradle.kts").exists() ||
            File(projectDir, "build.gradle").exists() ||
            File(projectDir, "app/build.gradle.kts").exists() ||
            File(projectDir, "app/build.gradle").exists()
        ) {
            return ProjectType.ANDROID
        }

        return ProjectType.OTHER
    }

    /**
     * The project-relative path of the HTML entry point, or null.
     *
     * Checked against a short list of conventional locations rather than the repo
     * root alone, and never by walking the whole tree - on a phone, over a large
     * clone, that would be a visible stall on every project open.
     */
    fun findWebEntryPoint(projectDir: File): String? =
        ENTRY_DIRS.firstNotNullOfOrNull { dir ->
            "${dir}index.html".takeIf { File(projectDir, it).isFile }
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
