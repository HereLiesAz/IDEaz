package com.hereliesaz.ideaz.minapp.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import java.io.File

class WebBuildStep(
    private val projectDir: File,
    private val outputDir: File
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        callback?.onLog("[Web] Starting Web Build...")

        // Placeholder logic for Web build
        // In a real scenario, this might involve bundling, minification, etc.
        // For now, we just verify the index.html exists.

        val indexFile = File(projectDir, "index.html")
        if (!indexFile.exists()) {
            return BuildResult(false, "Error: index.html not found in ${projectDir.absolutePath}")
        }

        // Simulate build process
        callback?.onLog("[Web] Validating HTML...")

        // Copy to output (simulating a 'dist' build)
        if (!outputDir.exists()) outputDir.mkdirs()

        val injector = HtmlSourceInjector()
        projectDir.walkTopDown().forEach { file ->
            val relativePath = file.toRelativeString(projectDir)
            val outFile = File(outputDir, relativePath)

            if (file.isDirectory) {
                outFile.mkdirs()
            } else {
                if (file.extension == "html") {
                    val content = file.readLines()
                    val injected = injector.inject(content, relativePath)
                    outFile.writeText(injected)
                } else {
                    file.copyTo(outFile, overwrite = true)
                }
            }
        }

        callback?.onLog("[Web] Build complete. Files ready in ${outputDir.absolutePath}")
        return BuildResult(true, "Web build successful")
    }
}
