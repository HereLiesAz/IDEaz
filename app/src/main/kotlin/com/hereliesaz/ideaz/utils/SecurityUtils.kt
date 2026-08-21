package com.hereliesaz.ideaz.utils

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SecurityUtils {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12 // 12 bytes is recommended for GCM
    private const val KEY_SIZE_BYTES = 32 // 256 bits

    // OWASP's current recommendation for PBKDF2-HMAC-SHA256 (v2 shipped with
    // 65536, ~9x below this). The iteration count travels in the v3 payload
    // itself so a future bump never breaks decrypting an older export, and a
    // v2 export (fixed at 65536) still decrypts correctly below.
    private const val PBKDF2_ITERATIONS_V3 = 600_000
    private const val PBKDF2_ITERATIONS_V2 = 65_536

    data class EncryptedData(val salt: String, val iv: String, val ciphertext: String)

    fun encrypt(plainText: String, password: String): String {
        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)

        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)

        val key = deriveKey(password, salt, PBKDF2_ITERATIONS_V3)
        val secretKeySpec = SecretKeySpec(key, KEY_ALGORITHM)
        val gcmSpec = GCMParameterSpec(128, iv) // 128-bit authentication tag

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmSpec)

        val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        val saltStr = Base64.encodeToString(salt, Base64.NO_WRAP)
        val ivStr = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherStr = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

        // Format: version:iterations:salt:iv:ciphertext
        return "v3:$PBKDF2_ITERATIONS_V3:$saltStr:$ivStr:$cipherStr"
    }

    fun decrypt(encryptedPayload: String, password: String): String {
        val parts = encryptedPayload.split(":")
        val (iterations, salt, iv, ciphertext) = when {
            parts.size == 5 && parts[0] == "v3" -> {
                val iters = parts[1].toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid encrypted data format")
                Quad(iters, parts[2], parts[3], parts[4])
            }
            parts.size == 4 && parts[0] == "v2" ->
                Quad(PBKDF2_ITERATIONS_V2, parts[1], parts[2], parts[3])
            else -> throw IllegalArgumentException("Invalid encrypted data format")
        }

        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        val ivBytes = Base64.decode(iv, Base64.NO_WRAP)
        val ciphertextBytes = Base64.decode(ciphertext, Base64.NO_WRAP)

        val key = deriveKey(password, saltBytes, iterations)
        val secretKeySpec = SecretKeySpec(key, KEY_ALGORITHM)
        val gcmSpec = GCMParameterSpec(128, ivBytes)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmSpec)

        val decryptedBytes = cipher.doFinal(ciphertextBytes)
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    private data class Quad(val iterations: Int, val salt: String, val iv: String, val ciphertext: String)

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return factory.generateSecret(spec).encoded
    }
}
