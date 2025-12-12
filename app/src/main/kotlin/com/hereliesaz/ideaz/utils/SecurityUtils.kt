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

    data class EncryptedData(val salt: String, val iv: String, val ciphertext: String)

    fun encrypt(plainText: String, password: String): String {
        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)

        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)

        val key = deriveKey(password, salt)
        val secretKeySpec = SecretKeySpec(key, KEY_ALGORITHM)
        val gcmSpec = GCMParameterSpec(128, iv) // 128-bit authentication tag

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmSpec)

        val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        val saltStr = Base64.encodeToString(salt, Base64.NO_WRAP)
        val ivStr = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherStr = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

        // Format: version:salt:iv:ciphertext
        return "v2:$saltStr:$ivStr:$cipherStr"
    }

    fun decrypt(encryptedPayload: String, password: String): String {
        val parts = encryptedPayload.split(":")
        if (parts.size != 4 || parts[0] != "v2") {
            throw IllegalArgumentException("Invalid encrypted data format")
        }

        val salt = Base64.decode(parts[1], Base64.NO_WRAP)
        val iv = Base64.decode(parts[2], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[3], Base64.NO_WRAP)

        val key = deriveKey(password, salt)
        val secretKeySpec = SecretKeySpec(key, KEY_ALGORITHM)
        val gcmSpec = GCMParameterSpec(128, iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmSpec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        return factory.generateSecret(spec).encoded
    }
}
