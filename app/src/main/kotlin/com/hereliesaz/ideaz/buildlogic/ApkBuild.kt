package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkBuild(
    private val finalApkPath: String,
    private val resourcesApkPath: String,
    private val classesDir: String,
    private val jniLibsDir: String? = null,
    private val assetsDir: String? = null
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        try {
            val dexFile = File(classesDir, "classes.dex")
            if (!dexFile.exists()) {
                val error = "classes.dex not found!"
                println(error)
                callback?.onLog(error)
                return BuildResult(false, error)
            }

            File(finalApkPath).delete()

            // Safe Merge Strategy:
            // 1. Open Output Stream (final APK)
            // 2. Read Resources APK (base) -> Copy entries
            // 3. Add classes.dex
            // 4. Add Native Libs (if provided)
            // 5. Add Extra Assets (if provided)

            val zos = ZipOutputStream(FileOutputStream(finalApkPath))
            zos.use { out ->
                // 1. Copy everything from resources.apk (AndroidManifest.xml, resources.arsc, res/**/*)
                val resApk = File(resourcesApkPath)
                if (resApk.exists()) {
                    ZipFile(resApk).use { zip ->
                        zip.entries().asSequence().forEach { entry ->
                            // Avoid duplicates if classes.dex or libs exist in base
                            // We do NOT filter assets/python/ here anymore, to preserve user source code.
                            // Runtime assets (stdlib) are injected separately via assetsDir.
                            if (entry.name == "classes.dex" || entry.name.startsWith("lib/")) {
                                return@forEach
                            }

                            val newEntry = ZipEntry(entry.name)
                            out.putNextEntry(newEntry)
                            zip.getInputStream(entry).use { it.copyTo(out) }
                            out.closeEntry()
                        }
                    }
                }

                // 2. Add classes.dex
                val dexEntry = ZipEntry("classes.dex")
                out.putNextEntry(dexEntry)
                FileInputStream(dexFile).use { it.copyTo(out) }
                out.closeEntry()

                // 3. Add Native Libs (lib/{abi}/*.so)
                if (jniLibsDir != null) {
                    val libDir = File(jniLibsDir)
                    if (libDir.exists()) {
                        libDir.walkTopDown().filter { it.isFile }.forEach { file ->
                            val relativePath = file.relativeTo(libDir).path.replace("\\", "/")
                            val zipPath = "lib/$relativePath"
                            out.putNextEntry(ZipEntry(zipPath))
                            FileInputStream(file).use { it.copyTo(out) }
                            out.closeEntry()
                        }
                    }
                }

                // 4. Add Extra Assets (assets/...)
                if (assetsDir != null) {
                    val assetRoot = File(assetsDir)
                    if (assetRoot.exists()) {
                        assetRoot.walkTopDown().filter { it.isFile }.forEach { file ->
                            val relativePath = file.relativeTo(assetRoot).path.replace("\\", "/")
                            val zipPath = "assets/$relativePath"
                            out.putNextEntry(ZipEntry(zipPath))
                            FileInputStream(file).use { it.copyTo(out) }
                            out.closeEntry()
                        }
                    }
                }
            }

            val successMessage = "APK built successfully with merged components."
            callback?.onLog(successMessage)
            return BuildResult(true, successMessage)

        } catch (e: Exception) {
            e.printStackTrace()
            val errorMessage = "APK Build Failed: ${e.message}"
            callback?.onLog(errorMessage)
            return BuildResult(false, errorMessage)
        }
    }
}
