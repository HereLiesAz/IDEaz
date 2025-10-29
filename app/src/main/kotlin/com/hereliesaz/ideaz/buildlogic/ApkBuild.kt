package com.hereliesaz.ideaz.buildlogic

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkBuild(
    private val finalApkPath: String,
    private val resourcesApkPath: String,
    private val classesDir: String
) : BuildStep {

    override fun execute(): BuildResult {
        try {
            val dexFile = File(classesDir, "classes.dex")
            if (!dexFile.exists()) {
                val error = "classes.dex not found!"
                println(error)
                return BuildResult(false, error)
            }

            File(finalApkPath).delete()
            File(resourcesApkPath).copyTo(File(finalApkPath))

            val zos = ZipOutputStream(FileOutputStream(finalApkPath, true))
            zos.use {
                val entry = ZipEntry("classes.dex")
                it.putNextEntry(entry)
                FileInputStream(dexFile).use { fis ->
                    fis.copyTo(it)
                }
                it.closeEntry()
            }
            val successMessage = "APK built successfully"
            println(successMessage)
            return BuildResult(true, successMessage)
        } catch (e: Exception) {
            e.printStackTrace()
            return BuildResult(false, e.message ?: "Unknown error")
        }
    }
}

