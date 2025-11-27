package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.sun.tools.javac.api.JavacTool
import java.io.File
import java.io.Writer
import java.util.Locale
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardLocation

class JavaCompile(
    private val srcDir: String,
    private val outputDir: String,
    private val classpath: String,
    private val androidJarPath: String
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        val outputDirFile = File(outputDir)
        val srcDirFile = File(srcDir)

        val javaFiles = srcDirFile.walk().filter { it.isFile && it.extension == "java" }.toList()
        val classpathFiles = classpath.split(File.pathSeparator).filter { it.isNotEmpty() }.map { File(it) }
        val allInputs = javaFiles + classpathFiles + File(androidJarPath)

        if (BuildCacheManager.shouldSkip("javac", allInputs, outputDirFile)) {
            callback?.onLog("Skipping JavaCompile: Up-to-date.")
            return BuildResult(true, "Up-to-date")
        }

        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs()
        }

        if (javaFiles.isEmpty()) {
            callback?.onLog("No Java files found. Skipping compilation.")
             return BuildResult(true, "No sources")
        }

        val tool = JavacTool.create()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val fileManager = tool.getStandardFileManager(diagnostics, null, null)

        return try {
            val systemClasspath = listOf(File(androidJarPath))
            val compilationClasspath = classpathFiles + listOf(outputDirFile)

            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(outputDirFile))
            fileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, systemClasspath)
            fileManager.setLocation(StandardLocation.CLASS_PATH, compilationClasspath)
            fileManager.setLocation(StandardLocation.SOURCE_PATH, listOf(srcDirFile))

            val javaFileObjects = javaFiles.map { file ->
                object : SimpleJavaFileObject(file.toURI(), JavaFileObject.Kind.SOURCE) {
                    override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence {
                        return file.readText()
                    }
                }
            }

            val options = listOf(
                "-proc:none",
                "-source", "1.8",
                "-target", "1.8"
            )

            val writer = object : Writer() {
                private val sb = StringBuilder()
                override fun close() = flush()
                override fun flush() {
                    if (sb.isNotEmpty()) {
                        callback?.onLog(sb.toString())
                        sb.clear()
                    }
                }
                override fun write(cbuf: CharArray, off: Int, len: Int) {
                    sb.appendRange(cbuf, off, off + len)
                    if (sb.contains('\n')) {
                        flush()
                    }
                }
            }

            val task = tool.getTask(
                writer,
                fileManager,
                diagnostics,
                options,
                null,
                javaFileObjects
            )

            val success = task.call()

            for (diagnostic in diagnostics.diagnostics) {
                val message = StringBuilder()
                if (diagnostic.source != null) {
                    message.append("${diagnostic.source.name}:${diagnostic.lineNumber}: ")
                }
                message.append(diagnostic.getMessage(Locale.getDefault()))
                callback?.onLog(message.toString())
            }

            if (success == true) {
                BuildCacheManager.updateSnapshot("javac", allInputs, outputDirFile)
                BuildResult(true, "Java compilation successful")
            } else {
                BuildResult(false, "Java compilation failed")
            }

        } catch (e: Exception) {
            callback?.onLog("Java compilation error: ${e.message}")
            e.printStackTrace()
            BuildResult(false, e.stackTraceToString())
        } finally {
            fileManager.close()
        }
    }
}
