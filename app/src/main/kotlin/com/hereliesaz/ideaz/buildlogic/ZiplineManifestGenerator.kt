package com.hereliesaz.ideaz.buildlogic

import android.content.Context
import androidx.preference.PreferenceManager
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Key
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.ui.SettingsViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.TreeMap

@Serializable
data class ZiplineManifest(
    val unsigned: ManifestUnsigned,
    val signatures: Map<String, String>
)

@Serializable
data class ManifestUnsigned(
    val baseUrl: String,
    val modules: Map<String, ManifestModule>,
    val version: String
)

@Serializable
data class ManifestModule(
    val url: String,
    val sha256: String,
    val dependsOnIds: List<String> = emptyList()
)

class ZiplineManifestGenerator(
    private val outputDir: File,
    private val context: Context
) : BuildStep {

    private val prettyJson = Json { prettyPrint = true }

    override fun execute(callback: IBuildCallback?): BuildResult {
        callback?.onLog("[Zipline] Generating Manifest...")
        try {
            generate(outputDir, context)
            return BuildResult(true, "Manifest generated successfully.")
        } catch (e: Exception) {
            e.printStackTrace()
            return BuildResult(false, "Manifest generation failed: ${e.message}")
        }
    }

    private fun generate(outputDir: File, context: Context): File {
        val manifestFile = File(outputDir, "manifest.zipline.json")

        val modules = TreeMap<String, ManifestModule>()
        val jsFiles = outputDir.listFiles { _, name -> name.endsWith(".js") }?.sortedBy { it.name } ?: emptyList()

        jsFiles.forEach { file ->
            val sha256 = computeSha256(file)
            val name = file.nameWithoutExtension
            val deps = if (name == "guest" || name == "app") {
                jsFiles.filter { it.name != file.name }.map { it.nameWithoutExtension }
            } else {
                emptyList()
            }

            modules[name] = ManifestModule(
                url = file.name,
                sha256 = sha256,
                dependsOnIds = deps
            )
        }

        val baseUrl = "file://${outputDir.absolutePath}/"

        val unsigned = ManifestUnsigned(
            baseUrl = baseUrl,
            modules = modules,
            version = "1.0.0"
        )

        val signingJson = Json.encodeToString(unsigned)
        val signature = sign(signingJson, context)

        val manifest = ZiplineManifest(
            unsigned = unsigned,
            signatures = mapOf("key1" to signature)
        )

        val finalJson = prettyJson.encodeToString(manifest)
        manifestFile.writeText(finalJson)
        return manifestFile
    }

    private fun computeSha256(file: File): String {
        val bytes = file.readBytes()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun sign(content: String, context: Context): String {
        val ls = LazySodiumAndroid(SodiumAndroid())

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        var secretKeyHex = prefs.getString(SettingsViewModel.KEY_ZIPLINE_SIGNING_KEY_HEX, null)

        if (secretKeyHex == null) {
            val keyPair = ls.cryptoSignKeypair()
            secretKeyHex = keyPair.secretKey.asHexString
            prefs.edit().putString(SettingsViewModel.KEY_ZIPLINE_SIGNING_KEY_HEX, secretKeyHex).apply()
        }

        val secretKey = Key.fromHexString(secretKeyHex)
        return ls.cryptoSignDetached(content, secretKey)
    }
}
