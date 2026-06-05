package com.hereliesaz.ideaz.utils

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.engines.XSalsa20Engine
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

/**
 * libsodium-compatible `crypto_box_seal` (anonymous "sealed box"), implemented
 * with BouncyCastle's lightweight crypto API so GitHub Actions can decrypt the
 * result with the repository's private key.
 *
 * A sealed box is:
 *   1. an ephemeral X25519 key pair `(epk, esk)`,
 *   2. a per-message nonce `Blake2b(epk || recipient_pk)` truncated to 24 bytes,
 *   3. an XSalsa20-Poly1305 secretbox of the message under the key
 *      `crypto_box_beforenm(recipient_pk, esk) = HSalsa20(X25519(esk, recipient_pk), 0)`.
 *
 * Output layout: `epk(32) || secretbox`, where `secretbox = tag(16) || ciphertext`.
 *
 * This replaces the removed `com.goterl.lazysodium` native binding (Phase 0) with
 * a pure-JVM implementation — no native libraries, no extra dependency beyond the
 * BouncyCastle provider already pinned for the build. See [GithubSecretBoxTest]
 * for the HSalsa20 known-answer test and the seal→open round trip.
 */
object GithubSecretBox {

    private const val X25519_KEY_LEN = 32
    private const val NONCE_LEN = 24      // crypto_box_NONCEBYTES
    private const val POLY1305_KEY_LEN = 32
    private const val TAG_LEN = 16        // crypto_secretbox_MACBYTES

    /**
     * Seals [message] for the recipient identified by [recipientPublicKey]
     * (a 32-byte raw X25519 public key, e.g. the base64-decoded value GitHub
     * returns from `GET /repos/{owner}/{repo}/actions/secrets/public-key`).
     *
     * @return `ephemeral_pk(32) || tag(16) || ciphertext` — base64-encode this
     *   for the `encrypted_value` field of a `createSecret` request.
     */
    fun seal(
        message: ByteArray,
        recipientPublicKey: ByteArray,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(recipientPublicKey.size == X25519_KEY_LEN) {
            "recipient public key must be $X25519_KEY_LEN bytes, was ${recipientPublicKey.size}"
        }
        val recipientPk = X25519PublicKeyParameters(recipientPublicKey, 0)
        val ephemeralSk = X25519PrivateKeyParameters(random)
        val ephemeralPk = ephemeralSk.generatePublicKey().encoded

        val key = sharedKey(ephemeralSk, recipientPk, recipientPublicKey)
        val nonce = sealNonce(ephemeralPk, recipientPublicKey)
        return ephemeralPk + secretBox(message, nonce, key)
    }

    /**
     * Opens a sealed box produced by [seal]. Present so the round-trip can be
     * tested; GitHub performs this server-side. Returns null if authentication
     * fails or the input is malformed.
     */
    internal fun sealOpen(sealed: ByteArray, recipientPrivateKey: X25519PrivateKeyParameters): ByteArray? {
        if (sealed.size < X25519_KEY_LEN + TAG_LEN) return null
        val ephemeralPk = sealed.copyOfRange(0, X25519_KEY_LEN)
        val box = sealed.copyOfRange(X25519_KEY_LEN, sealed.size)

        val recipientPublicKey = recipientPrivateKey.generatePublicKey().encoded
        val key = sharedKey(recipientPrivateKey, X25519PublicKeyParameters(ephemeralPk, 0), ephemeralPk)
        val nonce = sealNonce(ephemeralPk, recipientPublicKey)
        return secretBoxOpen(box, nonce, key)
    }

    /** crypto_box_beforenm: `HSalsa20(X25519(sk, pk), 0)`. */
    private fun sharedKey(
        sk: X25519PrivateKeyParameters,
        pk: X25519PublicKeyParameters,
        @Suppress("UNUSED_PARAMETER") pkBytes: ByteArray,
    ): ByteArray {
        val dh = ByteArray(X25519_KEY_LEN)
        X25519Agreement().apply { init(sk) }.calculateAgreement(pk, dh, 0)
        return hSalsa20(dh, ByteArray(16))
    }

    /** nonce = `Blake2b(epk || recipient_pk)` truncated to 24 bytes. */
    private fun sealNonce(ephemeralPk: ByteArray, recipientPk: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_LEN)
        Blake2bDigest(NONCE_LEN * 8).apply {
            update(ephemeralPk, 0, ephemeralPk.size)
            update(recipientPk, 0, recipientPk.size)
            doFinal(nonce, 0)
        }
        return nonce
    }

    /** XSalsa20-Poly1305 secretbox. Returns `tag(16) || ciphertext`. */
    internal fun secretBox(message: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray {
        val engine = XSalsa20Engine().apply { init(true, ParametersWithIV(KeyParameter(key), nonce)) }
        // First 32 keystream bytes are the one-time Poly1305 key; the message is
        // then XOR'd with the keystream continuing from offset 32.
        val polyKey = ByteArray(POLY1305_KEY_LEN)
        engine.processBytes(ByteArray(POLY1305_KEY_LEN), 0, POLY1305_KEY_LEN, polyKey, 0)

        val cipher = ByteArray(message.size)
        engine.processBytes(message, 0, message.size, cipher, 0)

        val tag = ByteArray(TAG_LEN)
        Poly1305().apply {
            init(KeyParameter(polyKey))
            update(cipher, 0, cipher.size)
            doFinal(tag, 0)
        }
        return tag + cipher
    }

    /** Inverse of [secretBox]; returns null on authentication failure. */
    internal fun secretBoxOpen(box: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray? {
        if (box.size < TAG_LEN) return null
        val tag = box.copyOfRange(0, TAG_LEN)
        val cipher = box.copyOfRange(TAG_LEN, box.size)

        val engine = XSalsa20Engine().apply { init(true, ParametersWithIV(KeyParameter(key), nonce)) }
        val polyKey = ByteArray(POLY1305_KEY_LEN)
        engine.processBytes(ByteArray(POLY1305_KEY_LEN), 0, POLY1305_KEY_LEN, polyKey, 0)

        val expectedTag = ByteArray(TAG_LEN)
        Poly1305().apply {
            init(KeyParameter(polyKey))
            update(cipher, 0, cipher.size)
            doFinal(expectedTag, 0)
        }
        if (!constantTimeEquals(tag, expectedTag)) return null

        val plain = ByteArray(cipher.size)
        engine.processBytes(cipher, 0, cipher.size, plain, 0)
        return plain
    }

    /**
     * `crypto_core_hsalsa20`: derives a 32-byte subkey from a 32-byte [key] and a
     * 16-byte [input] using the Salsa20 core function (no final word addition).
     */
    internal fun hSalsa20(key: ByteArray, input: ByteArray): ByteArray {
        require(key.size == 32) { "hsalsa20 key must be 32 bytes" }
        require(input.size == 16) { "hsalsa20 input must be 16 bytes" }
        // Little-endian words of the constant "expand 32-byte k".
        var x0 = 0x61707865
        var x5 = 0x3320646e
        var x10 = 0x79622d32
        var x15 = 0x6b206574
        var x1 = le(key, 0)
        var x2 = le(key, 4)
        var x3 = le(key, 8)
        var x4 = le(key, 12)
        var x11 = le(key, 16)
        var x12 = le(key, 20)
        var x13 = le(key, 24)
        var x14 = le(key, 28)
        var x6 = le(input, 0)
        var x7 = le(input, 4)
        var x8 = le(input, 8)
        var x9 = le(input, 12)

        repeat(10) { // 10 double rounds = 20 rounds
            // column round
            x4 = x4 xor rotl(x0 + x12, 7); x8 = x8 xor rotl(x4 + x0, 9)
            x12 = x12 xor rotl(x8 + x4, 13); x0 = x0 xor rotl(x12 + x8, 18)
            x9 = x9 xor rotl(x5 + x1, 7); x13 = x13 xor rotl(x9 + x5, 9)
            x1 = x1 xor rotl(x13 + x9, 13); x5 = x5 xor rotl(x1 + x13, 18)
            x14 = x14 xor rotl(x10 + x6, 7); x2 = x2 xor rotl(x14 + x10, 9)
            x6 = x6 xor rotl(x2 + x14, 13); x10 = x10 xor rotl(x6 + x2, 18)
            x3 = x3 xor rotl(x15 + x11, 7); x7 = x7 xor rotl(x3 + x15, 9)
            x11 = x11 xor rotl(x7 + x3, 13); x15 = x15 xor rotl(x11 + x7, 18)
            // row round
            x1 = x1 xor rotl(x0 + x3, 7); x2 = x2 xor rotl(x1 + x0, 9)
            x3 = x3 xor rotl(x2 + x1, 13); x0 = x0 xor rotl(x3 + x2, 18)
            x6 = x6 xor rotl(x5 + x4, 7); x7 = x7 xor rotl(x6 + x5, 9)
            x4 = x4 xor rotl(x7 + x6, 13); x5 = x5 xor rotl(x4 + x7, 18)
            x11 = x11 xor rotl(x10 + x9, 7); x8 = x8 xor rotl(x11 + x10, 9)
            x9 = x9 xor rotl(x8 + x11, 13); x10 = x10 xor rotl(x9 + x8, 18)
            x12 = x12 xor rotl(x15 + x14, 7); x13 = x13 xor rotl(x12 + x15, 9)
            x14 = x14 xor rotl(x13 + x12, 13); x15 = x15 xor rotl(x14 + x13, 18)
        }

        val out = ByteArray(32)
        putLe(out, 0, x0); putLe(out, 4, x5); putLe(out, 8, x10); putLe(out, 12, x15)
        putLe(out, 16, x6); putLe(out, 20, x7); putLe(out, 24, x8); putLe(out, 28, x9)
        return out
    }

    private fun rotl(v: Int, c: Int): Int = (v shl c) or (v ushr (32 - c))

    private fun le(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or
            ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or
            ((b[off + 3].toInt() and 0xff) shl 24)

    private fun putLe(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xff).toByte()
        b[off + 1] = ((v ushr 8) and 0xff).toByte()
        b[off + 2] = ((v ushr 16) and 0xff).toByte()
        b[off + 3] = ((v ushr 24) and 0xff).toByte()
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
