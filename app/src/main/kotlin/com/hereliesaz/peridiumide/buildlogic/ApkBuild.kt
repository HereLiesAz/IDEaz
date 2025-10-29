package com.hereliesaz.peridiumide.buildlogic

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

    override fun execute(): Boolean {
        try {
            val dexFile = File(classesDir, "classes.dex")
            if (!dexFile.exists()) {
                println("classes.dex not found!")
                return false
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
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
