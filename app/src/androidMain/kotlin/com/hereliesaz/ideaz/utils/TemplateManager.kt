package com.hereliesaz.ideaz.utils

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Scaffolds an empty project directory from the bundled starter.
 *
 * There is one starter: `assets/templates/react`. It is a Vite-shaped React app
 * because that is the shape the preview pipeline is built for — its JSX is what
 * `ideaz-loader.js` transpiles with `jsx-source` on, which is what lets a tap
 * resolve to `src/App.jsx:42`.
 *
 * The `web`, `pwa` and `android` starters are gone, along with the placeholder
 * substitution and package-directory relocation that only the Android one
 * needed. A plain HTML project still opens and previews perfectly well; IDEaz
 * simply no longer offers to generate one, and no longer asks which kind you
 * meant.
 */
object TemplateManager {

    private const val ASSET_PATH = "templates/react"

    /**
     * Copy the bundled starter into [destinationDir] only when it doesn't
     * already contain something previewable.
     *
     * Used by `Save & Initialize` so a brand-new project gets scaffolded instead
     * of failing its first preview because the directory is empty. Returns true
     * when files were copied, false when an existing project was preserved.
     */
    fun ensureTemplate(context: Context, destinationDir: File): Boolean {
        if (ProjectAnalyzer.isPreviewable(destinationDir)) return false
        copyTemplate(context, destinationDir)
        return true
    }

    fun copyTemplate(context: Context, destinationDir: File) {
        try {
            copyAssetFolder(context.assets, ASSET_PATH, destinationDir)
        } catch (e: Exception) {
            android.util.Log.w("TemplateManager", "Template copy failed", e)
        }
    }

    private fun copyAssetFolder(assets: AssetManager, fromPath: String, toPath: File) {
        val list = try { assets.list(fromPath) } catch (e: IOException) { null }

        if (list != null && list.isNotEmpty()) {
            // It is a directory
            if (!toPath.exists()) toPath.mkdirs()
            for (file in list) {
                copyAssetFolder(assets, "$fromPath/$file", File(toPath, file))
            }
        } else {
            // It might be a file or an empty folder.
            try {
                copyAssetFile(assets, fromPath, toPath)
            } catch (e: IOException) {
                // If open fails, it might be an empty directory or not a file we can read
                if (list != null) { // Empty array means empty folder probably
                    toPath.mkdirs()
                }
            }
        }
    }

    private fun copyAssetFile(assets: AssetManager, fromPath: String, toPath: File) {
        toPath.parentFile?.let {
            if (!it.exists()) it.mkdirs()
        }
        assets.open(fromPath).use { input ->
            FileOutputStream(toPath).use { output ->
                input.copyTo(output)
            }
        }
    }
}
