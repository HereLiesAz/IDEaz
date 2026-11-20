package com.hereliesaz.ideaz.azphalt

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds real signed `.azp` archives for tests, with in-test Ed25519 keys. */
internal class TestKey {
    private val private = Ed25519PrivateKeyParameters(SecureRandom())
    val spki: ByteArray = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(private.generatePublicKey()).encoded
    val publicKey: String = b64(spki)

    fun sign(message: ByteArray): String = Ed25519Signer().run {
        init(true, private)
        update(message, 0, message.size)
        b64(generateSignature())
    }
}

internal class MemoryKv : AzphaltKeyValueStore {
    val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) {
        values[key] = value
    }
}

internal fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

internal fun sha256(bytes: ByteArray): String =
    "sha256-" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal const val LLM_BLOCK = """
  "llm": {
    "tier": "endpoint",
    "inputs": [{"id": "API_KEY", "password": true, "description": "Example key"}],
    "endpoint": {"protocols": ["openai-chat"], "baseUrl": "https://llm.example.com/v1", "defaultModel": "m-1",
                 "auth": "optional-bearer", "authInput": "API_KEY"},
    "dataHandling": {"prompts": "not-retained", "modelPinned": true, "operator": "Example Co", "terms": "https://example.com/terms"}
  }"""

internal fun manifestJson(
    files: Map<String, ByteArray>,
    id: String = "example.llm",
    version: String = "1.0.0",
    extra: String = "",
    listed: Map<String, String> = files.mapValues { sha256(it.value) },
): ByteArray {
    val filesJson = listed.entries.joinToString(",") { "\"${it.key}\": \"${it.value}\"" }
    return """
        {"azphalt": "0.1", "id": "$id", "name": "Example LLM", "version": "$version", "kind": "llm",
         "license": "MIT", "compat": ">=0.1", "targetApps": ["com.hereliesaz.ideaz"],
         "files": {$filesJson}, $LLM_BLOCK $extra}
    """.trimIndent().toByteArray()
}

internal fun signatureJson(manifest: ByteArray, signer: TestKey, voucher: TestKey? = null): ByteArray {
    val counter = voucher?.let {
        """, "countersignature": {"publicKey": "${it.publicKey}", "signature": "${it.sign(signer.spki)}"}"""
    }.orEmpty()
    return """{"alg": "ed25519", "publicKey": "${signer.publicKey}", "signature": "${signer.sign(manifest)}"$counter}""".toByteArray()
}

internal fun zip(entries: Map<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        entries.forEach { (name, data) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(data)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/** A complete signed package: LICENSE + README, manifest and signature. */
internal fun signedPackage(signer: TestKey, voucher: TestKey? = null, version: String = "1.0.0"): ByteArray {
    val files = mapOf("LICENSE" to "MIT".toByteArray(), "README.md" to "hi".toByteArray())
    val manifest = manifestJson(files, version = version)
    return zip(files + ("manifest.json" to manifest) + ("signature.json" to signatureJson(manifest, signer, voucher)))
}
