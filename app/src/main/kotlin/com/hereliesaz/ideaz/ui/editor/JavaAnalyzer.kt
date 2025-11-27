package com.hereliesaz.ideaz.ui.editor

import com.sun.tools.javac.api.JavacTool
import com.sun.tools.javac.file.JavacFileManager
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File
import java.nio.charset.Charset
import java.util.Locale
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardLocation

class JavaAnalyzer(
    val editor: CodeEditor,
    val srcDir: File,
    val binDir: File,
    val libDir: File,
    val androidJar: File
) {
    private val args = listOf(
            "-XDstringConcat=inline",
            "-XDcompilePolicy=byfile",
            "-XD-Xprefer=source",
            "-XDide",
            "-XDsuppressAbortOnBadClassFile",
            "-XDshould-stop.at=GENERATE",
            "-XDdiags.formatterOptions=-source",
            "-XDdiags.layout=%L%m|%L%m|%L%m",
            "-XDbreakDocCommentParsingOnError=false",
            "-Xlint:cast",
            "-Xlint:deprecation",
            "-Xlint:empty",
            "-Xlint:fallthrough",
            "-Xlint:finally",
            "-Xlint:path",
            "-Xlint:unchecked",
            "-Xlint:varargs",
            "-Xlint:static",
            "-proc:none"
        )

    private var diagnostics = DiagnosticCollector<JavaFileObject>()
    private val tool: JavacTool by lazy { JavacTool.create() }
    private val standardFileManager: JavacFileManager by lazy {
        tool.getStandardFileManager(
            diagnostics, Locale.getDefault(), Charset.defaultCharset()
        )
    }

    fun analyze() {
        diagnostics = DiagnosticCollector<JavaFileObject>()

        val toCompile = getSourceFiles()
        val classpath = getClasspath()

        try {
            standardFileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, listOf(androidJar))
            standardFileManager.setLocation(StandardLocation.CLASS_PATH, classpath)
            standardFileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(binDir))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val options = args.toMutableList()
        options.add("-source")
        options.add("1.8")
        options.add("-target")
        options.add("1.8")

        try {
            tool.getTask(null, standardFileManager, diagnostics, options, null, toCompile)
                .call()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDiagnostics(): List<DiagnosticRegion> {
        val problems = mutableListOf<DiagnosticRegion>()
        try {
            for (d in diagnostics.diagnostics) {
                if (d.source == null) continue

                val severity = if (d.kind == Diagnostic.Kind.ERROR) DiagnosticRegion.SEVERITY_ERROR else DiagnosticRegion.SEVERITY_WARNING
                val message = d.getMessage(Locale.getDefault())

                problems.add(
                    DiagnosticRegion(
                        d.startPosition.toInt(),
                        d.endPosition.toInt(),
                        severity,
                        0,
                        DiagnosticDetail(message, quickfixes = emptyList())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return problems
    }

    private fun getClasspath(): List<File> {
        val classpath = mutableListOf<File>()
        if (binDir.exists()) classpath.add(binDir)
        if (libDir.exists()) {
            libDir.walk().filter { it.extension == "jar" }.forEach { classpath.add(it) }
        }
        return classpath
    }

    private fun getSourceFiles(): List<JavaFileObject> {
        return srcDir.walk()
            .filter { it.extension == "java" }
            .map { file ->
                object : SimpleJavaFileObject(file.toURI(), JavaFileObject.Kind.SOURCE) {
                    override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence {
                        return file.readText()
                    }
                }
            }
            .toList()
    }
}
