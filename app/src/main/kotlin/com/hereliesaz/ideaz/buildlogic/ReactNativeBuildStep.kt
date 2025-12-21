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
                 // copyRecursively copies the content of directory to destination if it exists?
                 // No, if destination exists, it might fail or copy INTO it.
                 // Actually, Kotlin's copyRecursively puts 'this' file/dir to 'target'.
                 // If 'this' is a dir, 'target' becomes that dir.
                 // Wait, no. "Copies this file with all its children to the specified destination target path."
                 // If source is dir and target is dir, it copies content OF source to target?

                 // Let's iterate and copy to avoid ambiguity or nesting "assets" folder inside output.
                 // The test expects `output/test.html`.
                 // So we want content of `assets/` to go to `output/`.

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
