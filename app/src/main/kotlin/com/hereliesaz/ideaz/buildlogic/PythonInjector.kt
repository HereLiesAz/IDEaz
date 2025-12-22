package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import java.io.File
import java.util.zip.ZipFile

class PythonInjector(
    private val artifacts: List<File>,
    private val buildDir: File,
    private val abi: String = "arm64-v8a"
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        val runtimeAar = artifacts.find {
            it.name.contains("chaquopy") && it.extension.equals("aar", ignoreCase = true)
        }

        if (runtimeAar == null) {
            callback?.onLog("[PythonInjector] Chaquopy runtime AAR not found. Skipping Python injection.")
            // Not a failure, just means no Python support in this build unless it was expected.
            // If the user project is Python, this might be an issue, but for now we assume optional.
            return BuildResult(true, "No Python runtime found.")
        }

        callback?.onLog("[PythonInjector] Found runtime: ${runtimeAar.name}")

        try {
            // Define output directories
            val jniLibsDir = File(buildDir, "intermediates/jniLibs/$abi").apply { mkdirs() }
            val assetsDir = File(buildDir, "intermediates/assets").apply { mkdirs() }

            ZipFile(runtimeAar).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory) return@forEach

                    // 1. Extract Native Libraries
                    // Chaquopy stores libs in jni/{abi}/
                    if (entry.name.startsWith("jni/$abi/")) {
                        val fileName = entry.name.substringAfterLast("/")
                        val destFile = File(jniLibsDir, fileName)
                        callback?.onLog("[PythonInjector] Extracting lib: $fileName")
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
                        // callback?.onLog("[PythonInjector] Extracting asset: $relativePath") // Too verbose
                        zip.getInputStream(entry).use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }

            return BuildResult(true, "Python runtime injected successfully.")

        } catch (e: Exception) {
            e.printStackTrace()
            return BuildResult(false, "Python injection failed: ${e.message}")
        }
    }
}
