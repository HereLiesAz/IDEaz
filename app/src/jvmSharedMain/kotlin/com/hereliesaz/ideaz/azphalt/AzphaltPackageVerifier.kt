package com.hereliesaz.ideaz.azphalt

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.util.PublicKeyFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipInputStream

fun interface AzphaltArchiveReader {
    fun read(bytes: ByteArray): Map<String, ByteArray>
}

fun interface AzphaltEd25519Verifier {
    fun verify(publicKeySpki: ByteArray, message: ByteArray, signature: ByteArray): Boolean
}

/**
 * `.azp` reader with explicit limits for untrusted input. Every entry is read to EOF so
 * [ZipInputStream] checks its CRC; duplicate paths are rejected so a later entry cannot shadow
 * the manifest or a payload.
 */
class ZipAzphaltArchiveReader(
    private val maxArchiveBytes: Int = DEFAULT_MAX_ARCHIVE_BYTES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxEntryBytes: Long = DEFAULT_MAX_ENTRY_BYTES,
    private val maxTotalInflatedBytes: Long = DEFAULT_MAX_TOTAL_INFLATED_BYTES,
    private val maxPathLength: Int = DEFAULT_MAX_PATH_LENGTH,
) : AzphaltArchiveReader {
    override fun read(bytes: ByteArray): Map<String, ByteArray> {
        require(bytes.size <= maxArchiveBytes) { "Azphalt package exceeds compressed size limit of $maxArchiveBytes bytes" }
        val entries = linkedMapOf<String, ByteArray>()
        var count = 0
        var totalInflated = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val buffer = ByteArray(8192)
            while (true) {
                val entry = zip.nextEntry ?: break
                count += 1
                require(count <= maxEntries) { "Azphalt package exceeds entry limit of $maxEntries" }
                val path = entry.name
                require(path.length <= maxPathLength) { "Azphalt package path exceeds $maxPathLength characters" }
                if (!entry.isDirectory) {
                    require(isSafePackagePath(path)) { "Unsafe package path $path" }
                    require(path !in entries) { "Duplicate package path $path" }
                }
                val out = ByteArrayOutputStream()
                var entryInflated = 0L
                while (true) {
                    val read = zip.read(buffer)
                    if (read == -1) break
                    entryInflated += read
                    totalInflated += read
                    require(entryInflated <= maxEntryBytes) { "Azphalt package entry $path exceeds size limit of $maxEntryBytes bytes" }
                    require(totalInflated <= maxTotalInflatedBytes) {
                        "Azphalt package exceeds total inflated size limit of $maxTotalInflatedBytes bytes"
                    }
                    out.write(buffer, 0, read)
                }
                if (entry.isDirectory) {
                    require(entryInflated == 0L) { "ZIP directory entry $path contains data" }
                } else {
                    entries[path] = out.toByteArray()
                }
            }
        }
        return entries
    }

    companion object {
        const val DEFAULT_MAX_ARCHIVE_BYTES: Int = 64 * 1024 * 1024
        const val DEFAULT_MAX_ENTRIES: Int = 512
        const val DEFAULT_MAX_ENTRY_BYTES: Long = 16L * 1024 * 1024
        const val DEFAULT_MAX_TOTAL_INFLATED_BYTES: Long = 48L * 1024 * 1024
        const val DEFAULT_MAX_PATH_LENGTH: Int = 1024
    }
}

fun isSafePackagePath(path: String): Boolean =
    path.isNotBlank() &&
        !path.startsWith('/') &&
        '\\' !in path &&
        path.split('/').none { it.isBlank() || it == "." || it == ".." }

/**
 * Ed25519 over BouncyCastle's lightweight API: Android's platform providers do not reliably
 * offer Ed25519, and the lightweight classes survive R8 without keep rules. Keys are DER SPKI.
 * A malformed key is a failed verification, not a crash.
 */
object BouncyCastleEd25519Verifier : AzphaltEd25519Verifier {
    override fun verify(publicKeySpki: ByteArray, message: ByteArray, signature: ByteArray): Boolean = runCatching {
        val key = PublicKeyFactory.createKey(publicKeySpki) as? Ed25519PublicKeyParameters ?: return false
        Ed25519Signer().run {
            init(false, key)
            update(message, 0, message.size)
            verifySignature(signature)
        }
    }.getOrDefault(false)
}

@Serializable
data class AzphaltSignature(
    val alg: String,
    val publicKey: String,
    val keyId: String? = null,
    val signature: String,
    val countersignature: AzphaltCountersignature? = null,
)

@Serializable
data class AzphaltCountersignature(
    val publicKey: String,
    val signature: String,
    val keyId: String? = null,
    val countersignature: AzphaltCountersignature? = null,
)

class VerifiedAzphaltPackage internal constructor(
    val manifest: AzphaltManifest,
    val payload: Map<String, ByteArray>,
    val signed: Boolean,
    val signerPublicKey: String? = null,
)

data class AzphaltPackageVerification(
    val packageContents: VerifiedAzphaltPackage,
    val trusted: Boolean,
    val trustReason: String,
    val publisherChanged: Boolean,
    val pinnedPublisherKey: String? = null,
)

interface AzphaltPublisherPinStore {
    fun keyFor(packageId: String): String?
    fun pin(packageId: String, publicKey: String)
}

/**
 * The boundary azphalt's reference `verifyAzp` + `verifyTrust` enforce:
 * 1. archive paths are safe and unique,
 * 2. every manifest payload digest matches SHA-256, and LICENSE is listed,
 * 3. no undeclared payload exists except the detached signature.json,
 * 4. an embedded Ed25519 signature over the raw manifest bytes must verify,
 * 5. trust is direct or through an intact counter-signature chain to a repository key,
 * 6. an installed package id may not silently change signer key.
 */
class AzphaltPackageVerifier(
    private val publisherPins: AzphaltPublisherPinStore,
    private val archiveReader: AzphaltArchiveReader = ZipAzphaltArchiveReader(),
    private val ed25519: AzphaltEd25519Verifier = BouncyCastleEd25519Verifier,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun verify(bytes: ByteArray, trustedRepositoryKeys: Collection<AzphaltSigningKey> = emptyList()): AzphaltPackageVerification {
        require(bytes.isNotEmpty()) { "Azphalt package is empty" }
        val entries = archiveReader.read(bytes)
        require(entries.isNotEmpty()) { "Azphalt package contains no entries" }
        entries.keys.forEach { require(isSafePackagePath(it)) { "Unsafe package path $it" } }
        val manifestBytes = requireNotNull(entries["manifest.json"]) { "azp: manifest.json is missing" }
        val manifest = try {
            json.decodeFromString<AzphaltManifest>(manifestBytes.decodeToString())
        } catch (failure: Exception) {
            throw IllegalArgumentException("azp: manifest.json is not valid Azphalt JSON: ${failure.message}", failure)
        }
        AzphaltRepositoryClient.requirePackageId(manifest.id)
        AzphaltRepositoryClient.requireVersion(manifest.version)
        require(manifest.files.isNotEmpty()) { "azp: manifest.files is missing or empty" }

        val payload = entries.filterKeys { it != "manifest.json" && it != "signature.json" }
        require("LICENSE" in manifest.files) { "azp: LICENSE is not listed in manifest.files" }
        require("LICENSE" in payload) { "azp: LICENSE payload is missing" }
        require("signature.json" !in manifest.files) { "azp: detached signature.json must not be listed in manifest.files" }
        manifest.files.forEach { (path, expected) ->
            require(isSafePackagePath(path)) { "Unsafe manifest path $path" }
            val data = requireNotNull(payload[path]) { "Missing payload for $path" }
            val expectedHex = requireNotNull(azphaltSha256Hex(expected)?.takeIf { expected.trim().startsWith("sha256-", true) }) {
                "Malformed digest for $path"
            }
            require(sha256Hex(data) == expectedHex) { "Digest mismatch: $path" }
        }
        val undeclared = payload.keys - manifest.files.keys
        require(undeclared.isEmpty()) { "Unlisted payload entries: ${undeclared.sorted().joinToString()}" }

        val signature = entries["signature.json"]?.let { raw ->
            try {
                json.decodeFromString<AzphaltSignature>(raw.decodeToString())
            } catch (failure: Exception) {
                throw IllegalArgumentException("signature.json is malformed: ${failure.message}", failure)
            }
        }
        val trust = if (signature == null) {
            TrustVerdict(false, "This package is not signed, so there is no publisher to trust")
        } else {
            require(signature.alg.equals("ed25519", ignoreCase = true)) { "signature algorithm must be ed25519" }
            require(ed25519.verify(decodeBase64(signature.publicKey), manifestBytes, decodeBase64(signature.signature))) {
                "Package signature verification failed"
            }
            verifyTrustChain(signature, trustedRepositoryKeys.map { it.publicKey }.toSet())
        }

        val pinnedKey = publisherPins.keyFor(manifest.id)
        return AzphaltPackageVerification(
            packageContents = VerifiedAzphaltPackage(manifest, payload, signature != null, signature?.publicKey),
            trusted = trust.trusted,
            trustReason = trust.reason,
            publisherChanged = pinnedKey != null && signature?.publicKey != pinnedKey,
            pinnedPublisherKey = pinnedKey,
        )
    }

    fun approvePublisher(verification: AzphaltPackageVerification) {
        val signer = verification.packageContents.signerPublicKey ?: return
        publisherPins.pin(verification.packageContents.manifest.id, signer)
    }

    private fun verifyTrustChain(signature: AzphaltSignature, trustedKeys: Set<String>): TrustVerdict {
        if (signature.publicKey in trustedKeys) return TrustVerdict(true, "Signed by a publisher the store trusts")
        var vouchedKey = signature.publicKey
        var counter = signature.countersignature
        var depth = 0
        while (counter != null) {
            depth += 1
            require(depth <= MAX_CHAIN_DEPTH) { "Counter-signature chain exceeds $MAX_CHAIN_DEPTH hops" }
            // Each hop signs the decoded SPKI bytes of the key it vouches for.
            require(ed25519.verify(decodeBase64(counter.publicKey), decodeBase64(vouchedKey), decodeBase64(counter.signature))) {
                "Counter-signature invalid at hop $depth"
            }
            if (counter.publicKey in trustedKeys) {
                return TrustVerdict(
                    true,
                    if (depth == 1) "Signed by a publisher the store vouches for"
                    else "Signed by a publisher the store vouches for, through $depth steps",
                )
            }
            vouchedKey = counter.publicKey
            counter = counter.countersignature
        }
        return TrustVerdict(false, "The signature is genuine, but the store does not vouch for this publisher")
    }

    private data class TrustVerdict(val trusted: Boolean, val reason: String)

    private companion object {
        const val MAX_CHAIN_DEPTH = 10

        fun decodeBase64(value: String): ByteArray = try {
            Base64.getDecoder().decode(value.filterNot(Char::isWhitespace))
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid base64 in signature.json", e)
        }

        fun sha256Hex(data: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    }
}
