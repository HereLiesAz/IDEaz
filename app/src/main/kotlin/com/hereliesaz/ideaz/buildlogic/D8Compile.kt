package com.hereliesaz.ideaz.buildlogic

import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import com.hereliesaz.ideaz.IBuildCallback
import java.io.File

class D8Compile(
    private val d8Path: String,
    private val javaPath: String,
    private val androidJarPath: String,
    private val classesDir: String,
    private val outputDir: String,
    private val classpath: String
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        val outputDirFile = File(outputDir)
        val classFiles = File(classesDir).walk().filter { it.isFile && it.extension == "class" }.toList()

        val classpathFiles = classpath.split(File.pathSeparator).filter { it.isNotEmpty() }.map { File(it) }
        val allInputs = classFiles + classpathFiles + File(androidJarPath)

        if (BuildCacheManager.shouldSkip("d8", allInputs, outputDirFile, "classes.dex")) {
            callback?.onLog("Skipping D8Compile: Up-to-date.")
            return BuildResult(true, "Up-to-date")
        }

        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs()
        }

        return try {
            val commandBuilder = D8Command.builder()
                .setMinApiLevel(26)
                .setMode(CompilationMode.DEBUG)
                .addLibraryFiles(File(androidJarPath).toPath())
                .addProgramFiles(classFiles.map { it.toPath() })
                .setOutput(outputDirFile.toPath(), OutputMode.DexIndexed)

            if (classpath.isNotEmpty()) {
                val libs = classpath.split(File.pathSeparator)
                    .filter { it.isNotEmpty() }
                    .map { File(it).toPath() }
                commandBuilder.addClasspathFiles(libs)
            }

            D8.run(commandBuilder.build())

            BuildCacheManager.updateSnapshot("d8", allInputs, outputDirFile)
            BuildResult(true, "D8 compilation successful")
        } catch (e: Throwable) {
            callback?.onLog("D8 Error: ${e.message}")
            e.printStackTrace()
            BuildResult(false, e.stackTraceToString())
        }
    }
}
