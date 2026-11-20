package com.hereliesaz.ideaz.azphalt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AzphaltPackageVerifierTest {
    private val pins = KeyValuePublisherPinStore(MemoryKv())
    private val verifier = AzphaltPackageVerifier(pins)
    private val publisher = TestKey()

    private fun fails(bytes: ByteArray, keys: List<AzphaltSigningKey> = emptyList()): String =
        assertThrows(IllegalArgumentException::class.java) { verifier.verify(bytes, keys) }.message.orEmpty()

    @Test
    fun validSignedPackageFromTrustedKeyIsTrusted() {
        val result = verifier.verify(signedPackage(publisher), listOf(AzphaltSigningKey(publisher.publicKey)))
        assertTrue(result.trusted)
        assertTrue(result.packageContents.signed)
        assertFalse(result.publisherChanged)
        assertEquals("example.llm", result.packageContents.manifest.id)
        assertEquals("m-1", result.packageContents.manifest.llm?.endpoint?.defaultModel)
    }

    @Test
    fun counterSignatureChainToTrustedKeyIsTrusted() {
        val store = TestKey()
        val result = verifier.verify(signedPackage(publisher, voucher = store), listOf(AzphaltSigningKey(store.publicKey)))
        assertTrue(result.trusted)
        assertTrue(result.trustReason.contains("vouches"))
    }

    @Test
    fun forgedCounterSignatureIsRejected() {
        val store = TestKey()
        val files = mapOf("LICENSE" to "MIT".toByteArray())
        val manifest = manifestJson(files)
        // The voucher signs the wrong bytes, so the chain must not verify.
        val sig = """{"alg":"ed25519","publicKey":"${publisher.publicKey}","signature":"${publisher.sign(manifest)}",
            "countersignature":{"publicKey":"${store.publicKey}","signature":"${store.sign("other".toByteArray())}"}}"""
        val pkg = zip(files + ("manifest.json" to manifest) + ("signature.json" to sig.toByteArray()))
        assertTrue(fails(pkg, listOf(AzphaltSigningKey(store.publicKey))).contains("Counter-signature invalid"))
    }

    @Test
    fun untrustedSignerVerifiesButIsNotTrusted() {
        val result = verifier.verify(signedPackage(publisher), listOf(AzphaltSigningKey(TestKey().publicKey)))
        assertFalse(result.trusted)
        assertTrue(result.packageContents.signed)
    }

    @Test
    fun unsignedPackageIsNotTrusted() {
        val files = mapOf("LICENSE" to "MIT".toByteArray())
        val result = verifier.verify(zip(files + ("manifest.json" to manifestJson(files))))
        assertFalse(result.trusted)
        assertFalse(result.packageContents.signed)
    }

    @Test
    fun signatureOverDifferentManifestFails() {
        val files = mapOf("LICENSE" to "MIT".toByteArray())
        val manifest = manifestJson(files)
        val other = manifestJson(files, version = "9.9.9")
        val pkg = zip(files + ("manifest.json" to manifest) + ("signature.json" to signatureJson(other, publisher)))
        assertTrue(fails(pkg).contains("signature verification failed"))
    }

    @Test
    fun tamperedFileIsRejected() {
        val files = mapOf("LICENSE" to "MIT".toByteArray(), "README.md" to "hi".toByteArray())
        val manifest = manifestJson(files)
        val tampered = files + ("README.md" to "evil".toByteArray())
        val pkg = zip(tampered + ("manifest.json" to manifest) + ("signature.json" to signatureJson(manifest, publisher)))
        assertTrue(fails(pkg).contains("Digest mismatch: README.md"))
    }

    @Test
    fun unlistedFileIsRejected() {
        val files = mapOf("LICENSE" to "MIT".toByteArray())
        val manifest = manifestJson(files)
        val pkg = zip(files + ("extra.sh" to "rm".toByteArray()) + ("manifest.json" to manifest) +
            ("signature.json" to signatureJson(manifest, publisher)))
        assertTrue(fails(pkg).contains("Unlisted payload entries: extra.sh"))
    }

    @Test
    fun missingLicenseIsRejected() {
        val files = mapOf("README.md" to "hi".toByteArray())
        val manifest = manifestJson(files)
        val pkg = zip(files + ("manifest.json" to manifest) + ("signature.json" to signatureJson(manifest, publisher)))
        assertTrue(fails(pkg).contains("LICENSE is not listed"))
    }

    @Test
    fun missingManifestIsRejected() {
        assertTrue(fails(zip(mapOf("LICENSE" to "MIT".toByteArray()))).contains("manifest.json is missing"))
    }

    @Test
    fun zipSlipPathIsRejected() {
        val files = mapOf("LICENSE" to "MIT".toByteArray())
        val pkg = zip(files + ("../evil" to "x".toByteArray()) + ("manifest.json" to manifestJson(files)))
        assertTrue(fails(pkg).contains("Unsafe package path ../evil"))
        assertFalse(isSafePackagePath("/abs"))
        assertFalse(isSafePackagePath("a\\b"))
        assertFalse(isSafePackagePath("a/./b"))
        assertTrue(isSafePackagePath("dir/file.txt"))
    }

    @Test
    fun oversizedArchiveIsRejected() {
        val small = AzphaltPackageVerifier(pins, ZipAzphaltArchiveReader(maxArchiveBytes = 16))
        assertThrows(IllegalArgumentException::class.java) { small.verify(signedPackage(publisher)) }
    }

    @Test
    fun publisherChangeIsFlaggedAgainstPinnedKey() {
        val first = verifier.verify(signedPackage(publisher), listOf(AzphaltSigningKey(publisher.publicKey)))
        assertNull(first.pinnedPublisherKey)
        verifier.approvePublisher(first)

        val imposter = TestKey()
        val second = verifier.verify(signedPackage(imposter, version = "1.1.0"), listOf(AzphaltSigningKey(imposter.publicKey)))
        assertTrue(second.trusted)
        assertTrue(second.publisherChanged)
        assertEquals(publisher.publicKey, second.pinnedPublisherKey)

        val same = verifier.verify(signedPackage(publisher, version = "1.1.0"))
        assertFalse(same.publisherChanged)
    }
}
