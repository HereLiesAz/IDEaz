package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.utils.ProcessExecutor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

class KotlincCompile(
    private val kotlincPath: String,
    private val androidJarPath: String,
    private val javaDir: String,
    private val classesDir: File,
    private val classpath: String
) : BuildStep {

    private val cacheFile: File by lazy {
        File(classesDir, ".kotlinc_cache")
    }

    override fun execute(callback: IBuildCallback?): BuildResult {
        if (!classesDir.exists()) {
            classesDir.mkdirs()
        }

        val sourceFiles = File(javaDir).walk().filter { it.isFile && it.extension == "kt" }.toList()
        val currentTimestamps = sourceFiles.associate { it.absolutePath to it.lastModified().toString() }

        if (isUpToDate(currentTimestamps)) {
            return BuildResult(true, "Source files are up-to-date. Skipping compilation.")
        }

        val fullClasspath = if (classpath.isNotEmpty()) {
            androidJarPath + File.pathSeparator + classpath
        } else {
            androidJarPath
        }

        val command = listOf(
            "java",
            "-jar",
            kotlincPath,
            "-classpath",
            fullClasspath,
            "-d",
            classesDir.absolutePath,
            javaDir
        )

        val processResult = ProcessExecutor.execute(command)

        if (processResult.exitCode == 0) {
            updateCache(currentTimestamps)
        }

        return BuildResult(processResult.exitCode == 0, processResult.output)
    }

    private fun isUpToDate(currentTimestamps: Map<String, String>): Boolean {
        if (!cacheFile.exists()) return false

        val cachedProps = Properties()
        FileInputStream(cacheFile).use { cachedProps.load(it) }

        if (cachedProps.keys.size != currentTimestamps.keys.size) return false

        return currentTimestamps.all { (path, timestamp) ->
            cachedProps.getProperty(path) == timestamp
        }
    }

    private fun updateCache(currentTimestamps: Map<String, String>) {
        val props = Properties()
        currentTimestamps.forEach { (path, timestamp) ->
            props.setProperty(path, timestamp)
        }
        FileOutputStream(cacheFile).use { props.store(it, null) }
    }
}
