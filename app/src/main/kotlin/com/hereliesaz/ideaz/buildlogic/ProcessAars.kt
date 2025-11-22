package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.utils.ProcessExecutor
import java.io.File
import java.util.zip.ZipFile

class ProcessAars(
    private val artifacts: List<File>,
    private val buildDir: File,
    private val aapt2Path: String
) : BuildStep {

    val compiledAars = mutableListOf<String>()
    val jars = mutableListOf<String>()

    override fun execute(callback: IBuildCallback?): BuildResult {
        val explodedDir = File(buildDir, "exploded_aars")
        explodedDir.mkdirs()
        val compiledDir = File(buildDir, "compiled_aars")
        compiledDir.mkdirs()

        val aarFiles = artifacts.filter { it.isFile && it.extension == "aar" }

        if (aarFiles.isEmpty()) {
            return BuildResult(true, "No AARs found to process.")
        }

        callback?.onLog("Processing ${aarFiles.size} AARs...")

        for (aar in aarFiles) {
            val aarName = aar.nameWithoutExtension
            // Using a hash of the path to ensure uniqueness
            val uniqueName = "${aarName}_${aar.absolutePath.hashCode()}"
            val uniqueDestDir = File(explodedDir, uniqueName)

            if (!uniqueDestDir.exists()) {
                try {
                    unzip(aar, uniqueDestDir)
                } catch (e: Exception) {
                     return BuildResult(false, "Failed to extract ${aar.name}: ${e.message}")
                }
            }

            // Check for classes.jar
            val classesJar = File(uniqueDestDir, "classes.jar")
            if (classesJar.exists()) {
                jars.add(classesJar.absolutePath)
            }

            // Check for libs/*.jar (transitive/internal libs in AAR)
            val libsDir = File(uniqueDestDir, "libs")
            if (libsDir.exists() && libsDir.isDirectory) {
                libsDir.walkTopDown()
                    .filter { it.isFile && it.extension == "jar" }
                    .forEach { jars.add(it.absolutePath) }
            }

            // Check for resources
            val resDir = File(uniqueDestDir, "res")
            if (resDir.exists() && resDir.isDirectory && resDir.list()?.isNotEmpty() == true) {
                val outputFlata = File(compiledDir, "$uniqueName.flata")
                // Compile resources
                val command = listOf(
                    aapt2Path,
                    "compile",
                    "--dir", resDir.absolutePath,
                    "-o", outputFlata.absolutePath
                )

                val result = ProcessExecutor.execute(command)
                if (result.exitCode != 0) {
                     return BuildResult(false, "Failed to compile resources for ${aar.name}: ${result.output}")
                }
                compiledAars.add(outputFlata.absolutePath)
            }
        }

        return BuildResult(true, "Processed ${aarFiles.size} AARs. Compiled ${compiledAars.size} resource packages.")
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val file = File(targetDir, entry.name)
                val canonicalPath = file.canonicalPath
                if (!canonicalPath.startsWith(targetDir.canonicalPath + File.separator)) {
                    throw SecurityException("Zip Slip vulnerability detected: ${entry.name}")
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
}
