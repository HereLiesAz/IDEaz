package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class ZiplineCompile(
    private val sourceDirs: List<File>,
    private val outputDir: File,
    private val classpath: List<File>,
    private val ziplinePluginJars: List<File>
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        callback?.onLog("[Zipline] Compiling Guest logic to JS...")

        try {
            outputDir.mkdirs()
            val outputFile = File(outputDir, "guest.js")

            // Collect source files
            val sourceFiles = sourceDirs.flatMap { dir ->
                if (dir.exists()) {
                    dir.walk().filter { it.isFile && it.extension == "kt" }.map { it.absolutePath }.toList()
                } else {
                    emptyList()
                }
            }

            if (sourceFiles.isEmpty()) {
                callback?.onLog("[Zipline] No guest source files found, skipping compilation.")
                return BuildResult(true, "No guest source files found.")
            }

            val cpString = classpath.joinToString(File.pathSeparator) { it.absolutePath }

            val args = mutableListOf<String>()
            args.add("-Xir-produce-js")
            args.add("-Xir-per-module")
            args.add("-module-kind")
            args.add("commonjs")
            args.add("-libraries")
            args.add(cpString)
            args.add("-output")
            args.add(outputFile.absolutePath)

            // Plugins
            ziplinePluginJars.forEach { jar ->
                args.add("-Xplugin=${jar.absolutePath}")
            }
            args.add("-P")
            args.add("plugin:app.cash.zipline:zipline-api-validation=enabled")

            // Sources
            args.addAll(sourceFiles)

            val compiler = K2JSCompiler()
            val stream = ByteArrayOutputStream()
            val printStream = PrintStream(stream)

            val exitCode = compiler.exec(printStream, *args.toTypedArray())

            val output = stream.toString()
            callback?.onLog("[Zipline] Compiler Output:\n$output")

            if (exitCode.code != 0) {
                return BuildResult(false, "Zipline compilation failed (Exit code ${exitCode.code}).")
            }

            return BuildResult(true, "Zipline compilation successful. Output: ${outputFile.absolutePath}")

        } catch (e: Exception) {
            e.printStackTrace()
            return BuildResult(false, "Zipline compiler internal error: ${e.message}")
        }
    }
}
