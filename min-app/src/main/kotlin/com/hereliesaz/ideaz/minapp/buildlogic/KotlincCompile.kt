package com.hereliesaz.ideaz.minapp.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.incremental.makeJvmIncrementally
import java.io.File

class KotlincCompile(
    private val kotlincJarPath: String, // Unused
    private val androidJarPath: String,
    private val srcDir: String,
    private val outputDir: File,
    private val classpath: String,
    private val javaBinaryPath: String // Unused
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        // Collect files for cache check
        val srcDirFile = File(srcDir)
        val sourceFilesList = srcDirFile.walk().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
        val classpathFiles = classpath.split(File.pathSeparator).filter { it.isNotEmpty() }.map { File(it) }
        val allInputs = sourceFilesList + classpathFiles + File(androidJarPath)

        if (BuildCacheManager.shouldSkip("kotlinc", allInputs, outputDir)) {
            callback?.onLog("Skipping KotlincCompile: Up-to-date.")
            return BuildResult(true, "Up-to-date")
        }

        if (!outputDir.exists()) outputDir.mkdirs()

        val kotlinHomeDir = File(outputDir.parentFile, "kotlin-data").apply { mkdirs() }

        val args = K2JVMCompilerArguments().apply {
            noReflect = true
            noStdlib = true
            noJdk = true
            destination = outputDir.absolutePath
            val fullClasspath = "$androidJarPath${File.pathSeparator}$classpath".trim(File.pathSeparatorChar)
            this.classpath = fullClasspath
            moduleName = "app"
            kotlinHome = kotlinHomeDir.absolutePath

            // Populate Java source roots to allow resolution of Java classes
            val javaFiles = sourceFilesList.filter { it.extension == "java" }.map { it.absolutePath }.toTypedArray()
            if (javaFiles.isNotEmpty()) {
                javaSourceRoots = javaFiles
            }
        }

        val collector = object : MessageCollector {
            var hasErrors = false
            override fun clear() { hasErrors = false }
            override fun hasErrors(): Boolean = hasErrors
            override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
                val loc = if (location != null) "${location.path}:${location.line}: " else ""
                val msg = "$loc$message"
                callback?.onLog(msg)
                if (severity.isError) {
                    hasErrors = true
                }
            }
        }

        return try {
             makeJvmIncrementally(
                kotlinHomeDir,
                listOf(srcDirFile),
                args,
                collector
            )

            if (collector.hasErrors()) {
                 BuildResult(false, "Compilation failed with errors.")
            } else {
                 BuildCacheManager.updateSnapshot("kotlinc", allInputs, outputDir)
                 BuildResult(true, "Compilation successful")
            }
        } catch (e: Throwable) {
             callback?.onLog("Kotlinc internal error: ${e.message}")
             e.printStackTrace()
             BuildResult(false, e.stackTraceToString())
        }
    }
}
