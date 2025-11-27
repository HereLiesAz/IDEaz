package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import org.jf.smali.Smali
import org.jf.smali.SmaliOptions
import java.io.File
import java.io.OutputStream
import java.io.PrintStream

class SmaliCompile(
    private val srcDir: String,
    private val outputDexPath: String,
    private val apiLevel: Int = 26
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        val srcDirFile = File(srcDir)
        val outputDexFile = File(outputDexPath)

        val smaliFiles = srcDirFile.walk().filter { it.isFile && it.extension == "smali" }.toList()

        if (BuildCacheManager.shouldSkip("smali", smaliFiles, outputDexFile)) {
             callback?.onLog("Skipping SmaliCompile: Up-to-date.")
             return BuildResult(true, "Up-to-date")
        }

        val parent = outputDexFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        if (smaliFiles.isEmpty()) {
            callback?.onLog("No Smali files found.")
            return BuildResult(true, "No sources")
        }

        val options = SmaliOptions()
        options.outputDexFile = outputDexPath
        options.apiLevel = apiLevel
        options.verboseErrors = true

        val originalErr = System.err
        var capturedErrors = StringBuilder()

        try {
            System.setErr(PrintStream(object : OutputStream() {
                override fun write(b: Int) {
                    val c = b.toChar()
                    capturedErrors.append(c)
                    // Optionally stream to callback immediately
                }
            }))

            val success = Smali.assemble(options, smaliFiles.map { it.absolutePath })

            if (capturedErrors.isNotEmpty()) {
                callback?.onLog(capturedErrors.toString())
            }

            if (success) {
                 BuildCacheManager.updateSnapshot("smali", smaliFiles, outputDexFile)
                 return BuildResult(true, "Smali compilation successful")
            } else {
                 return BuildResult(false, "Smali compilation failed")
            }
        } catch (e: Exception) {
            callback?.onLog("Smali error: ${e.message}")
            if (capturedErrors.isNotEmpty()) {
                callback?.onLog(capturedErrors.toString())
            }
            e.printStackTrace()
            return BuildResult(false, e.stackTraceToString())
        } finally {
            System.setErr(originalErr)
        }
    }
}
