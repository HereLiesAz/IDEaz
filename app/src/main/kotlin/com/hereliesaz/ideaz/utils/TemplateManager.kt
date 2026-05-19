package com.hereliesaz.ideaz.utils

import android.content.Context
import android.content.res.AssetManager
import com.hereliesaz.ideaz.models.ProjectType
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object TemplateManager {

    // Must match the actual contents of app/src/main/assets/project/.
    // If you change the bundled template's package, update these too —
    // copyAssetFile substitutes them verbatim.
    private const val PLACEHOLDER_PACKAGE = "com.example.helloworld"
    private const val PLACEHOLDER_APP_NAME = "helloworld"

    /**
     * Derives a valid Android package name from a GitHub username and project name.
     * Strips non-alphanumerics, lowercases each segment, and prefixes any segment
     * that would otherwise start with a digit (or be empty) with an underscore.
     *
     * `derivePackageName("HereLiesAz", "My Cool App")` → `"com.hereliesaz.mycoolapp"`
     * `derivePackageName("", "")`                       → `"com._._"` (avoids invalid output)
     */
    fun derivePackageName(user: String, app: String): String {
        fun sanitize(s: String): String {
            val cleaned = s.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
            return if (cleaned.isEmpty() || cleaned[0].isDigit()) "_$cleaned" else cleaned
        }
        return "com.${sanitize(user)}.${sanitize(app)}"
    }

    fun copyTemplate(context: Context, type: ProjectType, destinationDir: File, packageName: String, appName: String) {
        val assetPath = when (type) {
            ProjectType.WEB -> "templates/web"
            ProjectType.PWA -> "templates/pwa"
            ProjectType.ANDROID -> "project"
            else -> return
        }

        val replacements = mapOf(
            PLACEHOLDER_PACKAGE to packageName,
            PLACEHOLDER_APP_NAME to appName
        )

        try {
            copyAssetFolder(context.assets, assetPath, destinationDir, replacements)

            // Post-processing: Move package directory if needed
            if (type == ProjectType.ANDROID) {
                relocatePackage(destinationDir, packageName)
            }
        } catch (e: Exception) {
            android.util.Log.w("TemplateManager", "Template copy failed", e)
        }
    }

    /**
     * Copy the bundled template into [destinationDir] only when it doesn't
     * already contain a recognisable project. "Recognisable" means:
     *
     *  - Web/PWA: an `index.html` at the project root.
     *  - Android: a top-level `build.gradle.kts` (or legacy `build.gradle`).
     *
     * Used by `Save & Initialize` so a brand-new project gets scaffolded
     * automatically instead of failing the first preview/build because the
     * directory is empty. Returns true when files were copied, false when an
     * existing project was preserved.
     */
    fun ensureTemplate(context: Context, type: ProjectType, destinationDir: File, packageName: String, appName: String): Boolean {
        if (alreadyHasProjectFiles(type, destinationDir)) return false
        copyTemplate(context, type, destinationDir, packageName, appName)
        return true
    }

    private fun alreadyHasProjectFiles(type: ProjectType, dir: File): Boolean {
        if (!dir.exists()) return false
        return when (type) {
            ProjectType.WEB, ProjectType.PWA -> File(dir, "index.html").exists()
            ProjectType.ANDROID -> File(dir, "build.gradle.kts").exists() || File(dir, "build.gradle").exists()
            else -> false
        }
    }

    private fun relocatePackage(projectDir: File, packageName: String) {
        val srcPackagePath = PLACEHOLDER_PACKAGE.replace('.', '/')
        val destPackagePath = packageName.replace('.', '/')

        if (srcPackagePath == destPackagePath) return

        val possibleRoots = listOf(
            "android/app/src/main/kotlin",
            "android/app/src/main/java",
            "app/src/main/kotlin",
            "app/src/main/java"
        )

        for (rootPath in possibleRoots) {
            val srcDir = File(projectDir, "$rootPath/$srcPackagePath")
            if (srcDir.exists()) {
                val destDir = File(projectDir, "$rootPath/$destPackagePath")
                // Ensure parent exists
                destDir.parentFile?.mkdirs()

                if (srcDir.renameTo(destDir)) {
                     cleanEmptyDirs(File(projectDir, rootPath), srcPackagePath)
                } else {
                    // Fallback copy/delete
                    srcDir.copyRecursively(destDir, true)
                    srcDir.deleteRecursively()
                    cleanEmptyDirs(File(projectDir, rootPath), srcPackagePath)
                }
            }
        }
    }

    private fun cleanEmptyDirs(root: File, path: String) {
        // path is like com/example/app
        // we want to delete app, then example, then com if empty
        var currentPath = path
        while (currentPath.isNotEmpty()) {
            val dir = File(root, currentPath)
            if (dir.exists() && dir.listFiles()?.isEmpty() == true) {
                dir.delete()
            }
            val lastSlash = currentPath.lastIndexOf('/')
            if (lastSlash == -1) break
            currentPath = currentPath.substring(0, lastSlash)
        }
        // Check root/first component (e.g. "com")
        val firstComp = if (currentPath.isNotEmpty()) currentPath else path.split("/").firstOrNull() ?: ""
        if (firstComp.isNotEmpty()) {
             val dir = File(root, firstComp)
             if (dir.exists() && dir.listFiles()?.isEmpty() == true) {
                dir.delete()
            }
        }
    }

    private fun copyAssetFolder(assets: AssetManager, fromPath: String, toPath: File, replacements: Map<String, String>) {
        val list = try { assets.list(fromPath) } catch (e: IOException) { null }

        if (list != null && list.isNotEmpty()) {
            // It is a directory
            if (!toPath.exists()) toPath.mkdirs()
            for (file in list) {
                copyAssetFolder(assets, "$fromPath/$file", File(toPath, file), replacements)
            }
        } else {
            // It might be a file or an empty folder.
            try {
                copyAssetFile(assets, fromPath, toPath, replacements)
            } catch (e: IOException) {
                // If open fails, it might be an empty directory or not a file we can read
                if (list != null) { // Empty array means empty folder probably
                     toPath.mkdirs()
                }
            }
        }
    }

    private fun copyAssetFile(assets: AssetManager, fromPath: String, toPath: File, replacements: Map<String, String>) {
        toPath.parentFile?.let {
            if (!it.exists()) it.mkdirs()
        }

        val textExtensions = listOf(
            ".xml", ".gradle", ".kts", ".kt", ".java", ".yaml", ".yml",
            ".html", ".css", ".js", ".json", ".properties", ".toml",
            ".md", ".pro", ".txt", ".sh",
        )
        val fileName = fromPath.substringAfterLast('/')
        val isText = textExtensions.any { fromPath.endsWith(it) } || fileName == ".gitignore" || fileName == "gradlew"

        if (isText) {
             val content = assets.open(fromPath).bufferedReader().use { it.readText() }
             var newContent = content
             replacements.forEach { (k, v) -> newContent = newContent.replace(k, v) }
             toPath.writeText(newContent)
        } else {
            assets.open(fromPath).use { input ->
                FileOutputStream(toPath).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
