package com.hereliesaz.ideaz.services

import android.content.Context
import com.hereliesaz.ideaz.utils.AssetExtractor
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
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
    fun compile(sourceCode: String): Result {
        val sourceFile = File(context.cacheDir, "main.kt")
        sourceFile.writeText(sourceCode)

        val logStream = ByteArrayOutputStream()
        val printStream = PrintStream(logStream)

        // Construct K2JSCompilerArguments as requested, but we must manually convert to strings
        // because the embeddable compiler's exec() method only accepts string varargs.
        val k2Args = K2JSCompilerArguments().apply {
            moduleKind = "plain"
            // Use this@JsCompilerService to avoid shadowing by K2JSCompilerArguments.outputFile
            outputFile = this@JsCompilerService.outputFile.absolutePath
            sourceMap = true
            // metaInfo = true // Property 'metaInfo' is unresolved in this compiler version
            irProduceJs = true // The modern way
            libraries = stdLibPath
            freeArgs = listOf(sourceFile.absolutePath)
            // Suppress warnings because we are insecure about our code
            suppressWarnings = true
            verbose = false
        }

        // Manual conversion to arguments list
        val argsList = mutableListOf<String>()
        k2Args.outputFile?.let { argsList.add("-output"); argsList.add(it) }
        k2Args.moduleKind?.let { argsList.add("-module-kind"); argsList.add(it) }
        if (k2Args.sourceMap) argsList.add("-source-map")
        if (k2Args.irProduceJs) argsList.add("-Xir-produce-js")
        k2Args.libraries?.let { argsList.add("-libraries"); argsList.add(it) }
        if (k2Args.suppressWarnings) argsList.add("-nowarn")
        if (k2Args.verbose) argsList.add("-verbose")
        argsList.addAll(k2Args.freeArgs)

        val exitCode = compiler.exec(
            printStream,
            *argsList.toTypedArray()
        )

        return Result(
            success = exitCode.code == 0,
            logs = logStream.toString()
        )
    }
}
