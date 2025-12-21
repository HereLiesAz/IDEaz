package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import java.io.File

class WebBuildStep(
    private val projectDir: File,
    private val outputDir: File
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        callback?.onLog("[Web] Starting Web Build...")

        val indexFile = File(projectDir, "index.html")
        if (!indexFile.exists()) {
            return BuildResult(false, "Error: index.html not found in ${projectDir.absolutePath}")
        }

        callback?.onLog("[Web] Validating HTML...")
        if (!validateHtml(indexFile)) {
            return BuildResult(false, "Error: Invalid HTML in index.html (Missing DOCTYPE or </html>)")
        }

        if (!outputDir.exists()) outputDir.mkdirs()

        val injector = HtmlSourceInjector()

        try {
            projectDir.walkTopDown().forEach { file ->
                val relativePath = file.toRelativeString(projectDir)
                val outFile = File(outputDir, relativePath)

                if (file.isDirectory) {
                    outFile.mkdirs()
                } else {
                    when (file.extension) {
                        "html" -> {
                            val content = file.readLines()
                            // Inject source maps (ARIA)
                            val injected = injector.inject(content, relativePath)
                            outFile.writeText(injected)
                        }
                        "js" -> {
                            val content = file.readText()
                            outFile.writeText(minifyJs(content))
                        }
                        "css" -> {
                            val content = file.readText()
                            outFile.writeText(minifyCss(content))
                        }
                        else -> {
                            file.copyTo(outFile, overwrite = true)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return BuildResult(false, "Web build failed: ${e.message}")
        }

        callback?.onLog("[Web] Build complete. Files ready in ${outputDir.absolutePath}")
        return BuildResult(true, "Web build successful")
    }

    fun validateHtml(file: File): Boolean {
        val content = file.readText()
        val hasDoctype = content.contains("<!DOCTYPE html>", ignoreCase = true)
        val hasEndHtml = content.contains("</html>", ignoreCase = true)
        return hasDoctype && hasEndHtml
    }

    fun minifyJs(content: String): String {
        // Safe minification: Remove blank lines only. Preserves indentation to avoid breaking multiline strings.
        return content.lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    fun minifyCss(content: String): String {
        // CSS Minification: Remove block comments, trim, join to single line.
        val commentRegex = Regex("/\\*[\\s\\S]*?\\*/")
        val noComments = content.replace(commentRegex, "")
        return noComments.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}
