package com.hereliesaz.ideaz.buildlogic

import app.cash.zipline.ZiplineManifest
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Key
import okio.ByteString.Companion.toByteString
import java.io.File

object ZiplineManifestGenerator {

    private val sodium by lazy { LazySodiumAndroid(SodiumAndroid()) }

    fun generateManifest(
        modules: Map<String, File>,
        signingKeys: Map<String, String>? = null, // keyId -> hexSecretKey
        signer: ((ByteArray, ByteArray) -> String)? = null
    ): String {
        val ziplineModules = modules.mapValues { (name, file) ->
            val content = file.readBytes()
            val sha256 = content.toByteString().sha256()
            ZiplineManifest.Module(
                url = name,
                sha256 = sha256,
                dependsOnIds = emptyList()
            )
        }

        val unsignedManifest = ZiplineManifest.create(
            ziplineModules
        )

        if (signingKeys.isNullOrEmpty()) {
            return unsignedManifest.encodeJson()
        }

        val manifestJson = unsignedManifest.encodeJson()
        val manifestBytes = manifestJson.encodeToByteArray()

        val signatures = signingKeys.mapValues { (_, hexKey) ->
            val key = Key.fromHexString(hexKey)
            if (signer != null) {
                signer(manifestBytes, key.asBytes)
            } else {
                val signature = ByteArray(64) // Ed25519 signature length
                sodium.cryptoSignDetached(signature, manifestBytes, manifestBytes.size.toLong(), key.asBytes)
                signature.toByteString().hex()
            }
        }

        val signedManifest = unsignedManifest.copy(
            signatures = signatures
        )

        return signedManifest.encodeJson()
    }
}
