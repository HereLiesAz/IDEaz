package com.hereliesaz.ideaz.services

import android.content.Context
import com.hereliesaz.ideaz.utils.AssetExtractor
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import org.jetbrains.kotlin.config.Services
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class JsCompilerService(private val context: Context) {

    private val compiler = K2JSCompiler()
    private val stdLibPath by lazy { AssetExtractor.requireStdLib(context) }
    private val outputDir = File(context.filesDir, "www").apply { mkdirs() }
    private val outputFile = File(outputDir, "app.js")

    data class Result(val success: Boolean, val logs: String)

    @Synchronized // Single threaded. We aren't Google.
    fun compileProject(projectDir: File): Result {
        try {
            val sourceFiles = projectDir.walkTopDown()
                .filter { it.extension == "kt" }
                .map { it.absolutePath }
                .toList()

            val logStream = ByteArrayOutputStream()
            val printStream = PrintStream(logStream)

            if (sourceFiles.isEmpty()) {
                return Result(false, "No Kotlin source files found in ${projectDir.absolutePath}")
            }

            val k2Args = K2JSCompilerArguments().apply {
                moduleKind = "plain"
                // Use this@JsCompilerService to avoid shadowing by K2JSCompilerArguments.outputFile
                outputFile = this@JsCompilerService.outputFile.absolutePath
                sourceMap = true
                irProduceJs = true // The modern way
                libraries = stdLibPath
                freeArgs = sourceFiles
                // Suppress warnings because we are insecure about our code
                suppressWarnings = true
                verbose = false
            }

            val messageCollector = PrintingMessageCollector(
                printStream,
                MessageRenderer.PLAIN_FULL_PATHS,
                k2Args.verbose
            )

            val exitCode = compiler.exec(
                messageCollector,
                Services.EMPTY,
                k2Args
            )

            return Result(
                success = exitCode.code == 0,
                logs = logStream.toString()
            )
        } catch (e: Exception) {
            return Result(false, "Compiler Exception: ${e.message}\n${e.stackTraceToString()}")
        }
    }
}
