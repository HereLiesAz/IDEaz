package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import java.io.File

class ReactNativeBuildStep(
    private val projectDir: File,
    private val outputDir: File
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        callback?.onLog("[RN] Starting React Native Bundling...")

        val bundler = SimpleJsBundler()
        val res = bundler.bundle(projectDir, outputDir)

        if (res.success) {
            callback?.onLog("[RN] ${res.output}")

            // Asset Copying
            val assetsDir = File(projectDir, "assets")
            if (assetsDir.exists() && assetsDir.isDirectory) {
                 // Iterate and copy to flatten structure if needed, or ensure strict copying to output root.
                 assetsDir.walkTopDown().forEach { file ->
                     if (file.isFile) {
                         val relativePath = file.toRelativeString(assetsDir)
                         val targetFile = File(outputDir, relativePath)
                         if (!targetFile.parentFile.exists()) targetFile.parentFile.mkdirs()
                         file.copyTo(targetFile, overwrite = true)
                     }
                 }

                 callback?.onLog("[RN] Copied assets to output directory.")
            }

            return BuildResult(true, res.output)
        } else {
            callback?.onLog("[RN] Bundling failed: ${res.output}")
            return BuildResult(false, res.output)
        }
    }
}
