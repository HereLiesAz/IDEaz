package com.hereliesaz.ideaz.utils

import android.content.Context
import android.content.res.AssetManager
import com.hereliesaz.ideaz.models.ProjectType
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object TemplateManager {

    private const val PLACEHOLDER_PACKAGE = "com.example.my_flutter_app"
    private const val PLACEHOLDER_APP_NAME = "my_flutter_app"

    fun copyTemplate(context: Context, type: ProjectType, destinationDir: File, packageName: String, appName: String) {
        val assetPath = when (type) {
            ProjectType.FLUTTER -> "templates/flutter"
            ProjectType.WEB -> "templates/web"
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
            if (type == ProjectType.FLUTTER || type == ProjectType.ANDROID) {
                relocatePackage(destinationDir, packageName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

        val isText = fromPath.endsWith(".xml") || fromPath.endsWith(".gradle") || fromPath.endsWith(".kt") || fromPath.endsWith(".java") || fromPath.endsWith(".yaml") || fromPath.endsWith(".dart") || fromPath.endsWith(".html") || fromPath.endsWith(".css") || fromPath.endsWith(".js") || fromPath.endsWith(".json") || fromPath.endsWith(".properties")

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
