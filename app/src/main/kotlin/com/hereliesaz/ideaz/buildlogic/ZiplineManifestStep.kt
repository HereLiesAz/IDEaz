package com.hereliesaz.ideaz.buildlogic

import android.content.Context
import androidx.preference.PreferenceManager
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.ui.SettingsViewModel
import java.io.File

class ZiplineManifestStep(
    private val guestOutputDir: File,
    private val context: Context
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        callback?.onLog("[Zipline] Generating Manifest...")
        try {
            val guestJs = File(guestOutputDir, "guest.js")
            if (!guestJs.exists()) {
                return BuildResult(false, "guest.js not found for manifest generation.")
            }

            // Key Management (Development only)
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            var keyHex = prefs.getString(SettingsViewModel.KEY_ZIPLINE_SIGNING_KEY_HEX, null)

            if (keyHex == null) {
                callback?.onLog("[Zipline] Generating new signing key (Dev)...")
                val sodium = LazySodiumAndroid(SodiumAndroid())
                keyHex = sodium.cryptoSignKeypair().secretKey.asHexString
                prefs.edit().putString(SettingsViewModel.KEY_ZIPLINE_SIGNING_KEY_HEX, keyHex).apply()
            }

            val manifestJson = ZiplineManifestGenerator.generateManifest(
                modules = mapOf("guest" to guestJs),
                signingKeys = mapOf("key1" to keyHex!!)
            )

            File(guestOutputDir, "manifest.zipline.json").writeText(manifestJson)
            callback?.onLog("[Zipline] Manifest generated.")
            return BuildResult(true, "Manifest generated.")

        } catch (e: Exception) {
            e.printStackTrace()
            return BuildResult(false, "Manifest generation failed: ${e.message}")
        }
    }
}
