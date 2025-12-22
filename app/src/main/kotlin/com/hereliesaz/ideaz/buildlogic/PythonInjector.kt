package com.hereliesaz.ideaz.buildlogic

import android.os.Build
import com.hereliesaz.ideaz.IBuildCallback
import java.io.File
import java.util.zip.ZipFile

class PythonInjector(
    private val artifacts: List<File>,
    private val buildDir: File,
    private val targetAbi: String? = null
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        // Chaquopy 15+ splits runtime (bridge) and python-target (engine/stdlib) into separate AARs.
        // We must process ALL Chaquopy AARs.
        val chaquopyAars = artifacts.filter {
            (it.name.contains("chaquopy") || it.name.contains("python-")) &&
            it.extension.equals("aar", ignoreCase = true)
        }

        if (chaquopyAars.isEmpty()) {
            callback?.onLog("[PythonInjector] Chaquopy runtime AARs not found. Skipping Python injection.")
            return BuildResult(true, "No Python runtime found.")
        }

        callback?.onLog("[PythonInjector] Found ${chaquopyAars.size} Python/Chaquopy AARs.")

        try {
            // Determine ABI
            val abi = targetAbi ?: Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            callback?.onLog("[PythonInjector] Target ABI: $abi")

            // Define output directories
            val jniLibsDir = File(buildDir, "intermediates/jniLibs/$abi").apply { mkdirs() }
            val assetsDir = File(buildDir, "intermediates/assets").apply { mkdirs() }

            for (aar in chaquopyAars) {
                // callback?.onLog("[PythonInjector] Processing: ${aar.name}")
                ZipFile(aar).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        if (entry.isDirectory) return@forEach

                        // 1. Extract Native Libraries
                        // Chaquopy stores libs in jni/{abi}/
                        if (entry.name.startsWith("jni/$abi/")) {
                            val fileName = entry.name.substringAfterLast("/")
                            val destFile = File(jniLibsDir, fileName)
                            // callback?.onLog("[PythonInjector] Extracting lib: $fileName")
                            zip.getInputStream(entry).use { input ->
                                destFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }

                        // 2. Extract Assets (Python Stdlib)
                        // Chaquopy stores assets in assets/
                        if (entry.name.startsWith("assets/")) {
                            val relativePath = entry.name.removePrefix("assets/")
                            val destFile = File(assetsDir, relativePath)
                            destFile.parentFile.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                destFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }
            }

            // Verify extraction
            val extractedLibs = jniLibsDir.list()
            if (extractedLibs.isNullOrEmpty()) {
                callback?.onLog("[PythonInjector] WARNING: No native libraries extracted for ABI $abi.")
            } else {
                 callback?.onLog("[PythonInjector] Extracted ${extractedLibs.size} native libraries.")
            }

            return BuildResult(true, "Python runtime injected successfully.")

        } catch (e: Exception) {
            e.printStackTrace()
            return BuildResult(false, "Python injection failed: ${e.message}")
        }
    }
}
