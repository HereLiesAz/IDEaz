package com.hereliesaz.ideaz.utils

import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.SecureRandom

class GithubSecretBoxTest {

    private fun hex(s: String): ByteArray {
        val c = s.replace(" ", "").replace("\n", "")
        return ByteArray(c.length / 2) {
            ((Character.digit(c[it * 2], 16) shl 4) + Character.digit(c[it * 2 + 1], 16)).toByte()
        }
    }

    /**
     * Known-answer test for crypto_core_hsalsa20 from NaCl `tests/core1.c`:
     * HSalsa20(key = X25519 shared secret, input = 0) == the canonical secretbox
     * "firstkey". This pins our implementation to libsodium's exact output, which
     * is what makes the sealed box decryptable by GitHub server-side.
     */
    @Test
    fun hSalsa20_matchesNaClCore1Vector() {
        val key = hex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")
        val input = ByteArray(16)
        val expected = hex("1b27556473e985d462cd51197a9a46c76009549eac6474f206c4ee0844f68389")

        assertArrayEquals(expected, GithubSecretBox.hSalsa20(key, input))
    }

    @Test
    fun secretBox_roundTrips() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(24) { (it + 7).toByte() }
        val message = "the quick brown fox".toByteArray()

        val box = GithubSecretBox.secretBox(message, nonce, key)
        // box = tag(16) || ciphertext(message.size)
        assertEquals(16 + message.size, box.size)
        assertArrayEquals(message, GithubSecretBox.secretBoxOpen(box, nonce, key))
    }

    @Test
    fun secretBox_rejectsTamperedCiphertext() {
        val key = ByteArray(32) { (it * 3).toByte() }
        val nonce = ByteArray(24) { it.toByte() }
        val box = GithubSecretBox.secretBox("hello".toByteArray(), nonce, key)

        box[box.size - 1] = (box[box.size - 1] + 1).toByte() // flip a ciphertext byte
        assertNull(GithubSecretBox.secretBoxOpen(box, nonce, key))
    }

    @Test
    fun seal_roundTripsThroughSealOpen() {
        val recipientSk = X25519PrivateKeyParameters(SecureRandom())
        val recipientPk = recipientSk.generatePublicKey().encoded
        val secret = "ghp_ExampleGitHubTokenValue_1234567890".toByteArray()

        val sealed = GithubSecretBox.seal(secret, recipientPk)

        // ephemeral_pk(32) || tag(16) || ciphertext(secret.size)
        assertEquals(32 + 16 + secret.size, sealed.size)
        assertArrayEquals(secret, GithubSecretBox.sealOpen(sealed, recipientSk))
    }

    @Test
    fun seal_usesFreshEphemeralKeyEachCall() {
        val recipientPk = X25519PrivateKeyParameters(SecureRandom()).generatePublicKey().encoded
        val a = GithubSecretBox.seal("x".toByteArray(), recipientPk)
        val b = GithubSecretBox.seal("x".toByteArray(), recipientPk)

        // Different ephemeral public key (first 32 bytes) -> different output.
        assertNotEquals(
            a.copyOfRange(0, 32).toList(),
            b.copyOfRange(0, 32).toList(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun seal_rejectsWrongKeyLength() {
        GithubSecretBox.seal("x".toByteArray(), ByteArray(31))
    }
}
