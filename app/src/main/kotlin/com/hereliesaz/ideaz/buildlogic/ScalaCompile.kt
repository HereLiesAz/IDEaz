package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import dotty.tools.MainGenericCompiler
import java.io.File
import java.io.OutputStream
import java.io.PrintStream

class ScalaCompile(
    private val srcDir: String,
    private val outputDir: String,
    private val classpath: String,
    private val androidJarPath: String
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        val outputDirFile = File(outputDir)
        val srcDirFile = File(srcDir)

        val scalaFiles = srcDirFile.walk().filter { it.isFile && it.extension == "scala" }.toList()
        val classpathFiles = classpath.split(File.pathSeparator).filter { it.isNotEmpty() }.map { File(it) }
        val allInputs = scalaFiles + classpathFiles + File(androidJarPath)

        if (BuildCacheManager.shouldSkip("scalac", allInputs, outputDirFile)) {
            callback?.onLog("Skipping ScalaCompile: Up-to-date.")
            return BuildResult(true, "Up-to-date")
        }

        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs()
        }

        if (scalaFiles.isEmpty()) {
            callback?.onLog("No Scala files found. Skipping compilation.")
             return BuildResult(true, "No sources")
        }

        val originalOut = System.out
        val originalErr = System.err

        try {
             System.setOut(PrintStream(object : OutputStream() {
                val sb = StringBuilder()
                override fun write(b: Int) {
                    val c = b.toChar()
                    sb.append(c)
                    if (c == '\n') flush()
                }
                override fun flush() {
                    if (sb.isNotEmpty()) {
                        callback?.onLog(sb.toString().trim())
                        sb.clear()
                    }
                }
            }))

            System.setErr(PrintStream(object : OutputStream() {
                val sb = StringBuilder()
                override fun write(b: Int) {
                    val c = b.toChar()
                    sb.append(c)
                    if (c == '\n') flush()
                }
                override fun flush() {
                    if (sb.isNotEmpty()) {
                        callback?.onLog("[ERROR] " + sb.toString().trim())
                        sb.clear()
                    }
                }
            }))

            val args = arrayOf(
                "-classpath",
                (classpathFiles + File(androidJarPath)).joinToString(File.pathSeparator),
                "-d",
                outputDir,
                *scalaFiles.map { it.absolutePath }.toTypedArray()
            )

            MainGenericCompiler.main(args)

            BuildCacheManager.updateSnapshot("scalac", allInputs, outputDirFile)
            return BuildResult(true, "Scala compilation completed")

        } catch (e: Exception) {
            callback?.onLog("Scala compilation error: ${e.message}")
            e.printStackTrace()
            return BuildResult(false, e.stackTraceToString())
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
    }
}
