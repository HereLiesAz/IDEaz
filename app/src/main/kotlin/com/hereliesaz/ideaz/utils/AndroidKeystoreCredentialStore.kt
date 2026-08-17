package com.hereliesaz.ideaz.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Minimal credential store whose values are encrypted by a non-exportable Android Keystore key. */
interface CredentialStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/**
 * Read-only, migration-side-effect-free credential lookup for call sites that
 * don't hold a [com.hereliesaz.ideaz.ui.SettingsViewModel] instance (services,
 * static crash handlers). Checks the Keystore-backed store first, falling back
 * to the legacy plaintext preference of the same name for a value that hasn't
 * been migrated yet - [com.hereliesaz.ideaz.ui.SettingsViewModel.getApiKey]
 * still performs the actual one-time migration whenever Settings is opened.
 */
fun readSecureOrLegacyCredential(context: Context, key: String): String? {
    val secure = runCatching { AndroidKeystoreCredentialStore(context).get(key) }.getOrNull()
    if (secure != null) return secure
    return androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).getString(key, null)
}

/** Encrypts one credential into a versioned AES-GCM envelope. */
internal fun encryptCredential(value: String, key: SecretKey, associatedData: String): String {
    val iv = ByteArray(12).also(SecureRandom()::nextBytes)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
    cipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
    val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    return "v1:${Base64.getEncoder().encodeToString(iv)}:${Base64.getEncoder().encodeToString(ciphertext)}"
}

/** Decrypts and authenticates one credential envelope. */
internal fun decryptCredential(envelope: String, key: SecretKey, associatedData: String): String {
    val parts = envelope.split(':', limit = 3)
    require(parts.size == 3 && parts[0] == "v1") { "Invalid credential envelope" }
    val iv = Base64.getDecoder().decode(parts[1])
    require(iv.size == 12) { "Invalid credential IV" }
    val ciphertext = Base64.getDecoder().decode(parts[2])
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
    cipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
    return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
}

/**
 * Stores only authenticated ciphertext in private preferences. The AES key remains
 * non-exportable inside Android Keystore and is deliberately excluded from backup.
 */
class AndroidKeystoreCredentialStore(context: Context) : CredentialStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_FILE.removeSuffix(".xml"),
        Context.MODE_PRIVATE,
    )

    override fun get(key: String): String? = preferences.getString(storageKey(key), null)?.let {
        decryptCredential(it, secretKey(), key)
    }

    override fun put(key: String, value: String) {
        if (value.isBlank()) {
            remove(key)
            return
        }
        check(preferences.edit().putString(storageKey(key), encryptCredential(value, secretKey(), key)).commit()) {
            "Credential persistence failed"
        }
    }

    override fun remove(key: String) {
        check(preferences.edit().remove(storageKey(key)).commit()) { "Credential deletion failed" }
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun storageKey(key: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)))

    companion object {
        const val PREFERENCES_FILE = "ideaz_secure_credentials.xml"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ideaz_credentials_v1"
    }
}
