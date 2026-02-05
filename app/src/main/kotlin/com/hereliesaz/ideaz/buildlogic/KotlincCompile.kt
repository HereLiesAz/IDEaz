package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.incremental.makeJvmIncrementally
import java.io.File

/**
 * A [BuildStep] that compiles Kotlin and Java source code into JVM bytecodes (.class files)
 * using the embedded Kotlin compiler (kotlinc).
 *
 * **Key Features:**
 * - **Embedded Compiler:** Uses `org.jetbrains.kotlin.incremental.makeJvmIncrementally` to run the compiler within the same process.
 * - **Mixed Source Support:** Configured to handle mixed Java/Kotlin projects by passing Java roots to the Kotlin compiler.
 * - **Caching:** Checks [BuildCacheManager] to skip compilation if inputs haven't changed.
 *
 * @param kotlincJarPath Path to the kotlin-compiler.jar (mostly unused as we use the embedded dependency, but kept for CLI fallback potential).
 * @param androidJarPath Path to the android.jar SDK platform library.
 * @param sourceDirs List of directories containing source code.
 * @param outputDir Directory where .class files will be generated.
 * @param classpath The classpath string (paths separated by [File.pathSeparator]) including dependencies and R.jar.
 * @param javaBinaryPath Path to the java executable (unused in embedded mode).
 */
class KotlincCompile(
    private val kotlincJarPath: String, // Unused in embedded mode
    private val androidJarPath: String,
    private val sourceDirs: List<File>,
    private val outputDir: File,
    private val classpath: String,
    private val javaBinaryPath: String // Unused in embedded mode
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        // 1. Collect all source files (kt/java) for cache verification
        val sourceFilesList = sourceDirs.flatMap { dir ->
            if (dir.exists()) {
                dir.walk().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
            } else {
                emptyList()
            }
        }

        if (sourceFilesList.isEmpty()) {
            callback?.onLog("Skipping KotlincCompile: No source files found.")
            return BuildResult(true, "No sources")
        }

        // 2. Construct Input List for Cache Check
        val classpathFiles = classpath.split(File.pathSeparator).filter { it.isNotEmpty() }.map { File(it) }
        val allInputs = sourceFilesList + classpathFiles + File(androidJarPath)

        if (BuildCacheManager.shouldSkip("kotlinc", allInputs, outputDir)) {
            callback?.onLog("Skipping KotlincCompile: Up-to-date.")
            return BuildResult(true, "Up-to-date")
        }

        // 3. Prepare Environment
        if (!outputDir.exists()) outputDir.mkdirs()
        // kotlin-data is required by the incremental compiler for internal caches
        val kotlinHomeDir = File(outputDir.parentFile, "kotlin-data").apply { mkdirs() }

        // 4. Configure Compiler Arguments
        val args = K2JVMCompilerArguments().apply {
            noReflect = true
            noStdlib = true // We supply stdlib manually in classpath via DependencyResolver
            noJdk = true // We rely on android.jar
            destination = outputDir.absolutePath

            // Construct full classpath (Android SDK + Dependencies)
            val fullClasspath = "$androidJarPath${File.pathSeparator}$classpath".trim(File.pathSeparatorChar)
            this.classpath = fullClasspath

            moduleName = "app"
            kotlinHome = kotlinHomeDir.absolutePath

            // Important: Mixed Java/Kotlin support.
            // We must tell the Kotlin compiler where the Java sources are so it can resolve calls from Kotlin to Java.
            val javaFiles = sourceFilesList.filter { it.extension == "java" }.map { it.absolutePath }.toTypedArray()
            if (javaFiles.isNotEmpty()) {
                javaSourceRoots = javaFiles
            }
        }

        // 5. Setup Message Collector to capture compiler output
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
             // 6. Execute Compilation
             makeJvmIncrementally(
                kotlinHomeDir,
                sourceDirs.filter { it.exists() },
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
