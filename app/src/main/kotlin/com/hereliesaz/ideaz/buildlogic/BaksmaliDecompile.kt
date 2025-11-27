package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import org.jf.baksmali.Baksmali
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import java.io.File
import java.io.OutputStream
import java.io.PrintStream

class BaksmaliDecompile(
    private val dexFile: String,
    private val outputDir: String
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        val dexFileObj = File(dexFile)
        val outputDirObj = File(outputDir)

        if (!dexFileObj.exists()) {
            return BuildResult(false, "Dex file not found: $dexFile")
        }

        if (!outputDirObj.exists()) outputDirObj.mkdirs()

        val options = BaksmaliOptions()

        val originalErr = System.err
        var capturedErrors = StringBuilder()

        try {
            System.setErr(PrintStream(object : OutputStream() {
                override fun write(b: Int) {
                    capturedErrors.append(b.toChar())
                }
            }))

            // Load DexFile using default Opcodes (usually supports latest)
            val dexBackedDexFile = DexFileFactory.loadDexFile(dexFileObj, Opcodes.getDefault())

            val success = Baksmali.disassembleDexFile(dexBackedDexFile, outputDirObj, 4, options)

            if (capturedErrors.isNotEmpty()) {
                callback?.onLog(capturedErrors.toString())
            }

            if (success) {
                 return BuildResult(true, "Baksmali decompilation successful")
            } else {
                 return BuildResult(false, "Baksmali decompilation failed")
            }
        } catch (e: Exception) {
             callback?.onLog("Baksmali error: ${e.message}")
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
