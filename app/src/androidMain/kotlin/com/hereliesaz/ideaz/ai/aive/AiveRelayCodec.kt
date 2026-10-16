package com.hereliesaz.ideaz.ai.aive

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A failure the user can act on: a bad pairing code, an unreachable relay, an offline device. */
open class AiveRelayException(message: String, cause: Throwable? = null, val retryable: Boolean = true) :
    IllegalStateException(message, cause)

/** The Aive device is not connected to the relay (or Remote access is off). */
class AiveHostOfflineException : AiveRelayException(OFFLINE_MESSAGE, retryable = false) {
    companion object {
        const val OFFLINE_MESSAGE = "Your Aive device is offline or Remote access is off"
    }
}

/** A frame that failed authentication, replay, freshness or size checks. */
class AiveFrameException(message: String) : IllegalArgumentException(message)

/**
 * A parsed Aive pairing code: `aive1:` + base64url(no padding) of UTF-8 JSON
 * `{"v":1,"relay":"https://...","room":"...","clientToken":"...","key":"<base64url 32 bytes>"}`.
 * The code is a bearer secret (it holds the room token and the encryption key), so [toString]
 * never prints it.
 */
class AivePairingCode private constructor(
    val relay: String,
    val room: String,
    val clientToken: String,
    internal val key: ByteArray,
) {
    override fun toString() = "AivePairingCode(relay=$relay, room=<redacted>)"

    companion object {
        const val PREFIX = "aive1:"
        private const val KEY_BYTES = 32
        private val ROOM_PATTERN = Regex("[A-Za-z0-9._~-]{1,128}")
        private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "[::1]", "::1")

        /** Parses and validates [code]; throws [IllegalArgumentException] with a user-facing reason. */
        fun parse(code: String): AivePairingCode {
            val text = code.trim()
            require(text.startsWith(PREFIX)) { "That is not an Aive pairing code (it should start with $PREFIX)." }
            val json = try {
                val bytes = Base64.getUrlDecoder().decode(text.removePrefix(PREFIX))
                Json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: throw IllegalArgumentException("The pairing code is damaged. Copy it again from The Aive.")
            require(json.lng("v") == 1L) { "This pairing code is from a newer version of The Aive. Update IDEaz." }
            val relay = json.str("relay").orEmpty().trim().trimEnd('/')
            require(isAllowedRelay(relay)) { "The pairing code's relay address must be https." }
            val room = json.str("room").orEmpty()
            require(ROOM_PATTERN.matches(room)) { "The pairing code has no valid room." }
            val token = json.str("clientToken").orEmpty()
            require(token.isNotBlank()) { "The pairing code has no access token." }
            val key = try {
                Base64.getUrlDecoder().decode(json.str("key").orEmpty())
            } catch (_: IllegalArgumentException) {
                ByteArray(0)
            }
            require(key.size == KEY_BYTES) { "The pairing code's encryption key is the wrong size." }
            return AivePairingCode(relay, room, token, key)
        }

        /** [parse] without the exception. */
        fun parseOrNull(code: String?): AivePairingCode? =
            code?.takeIf { it.isNotBlank() }?.let { runCatching { parse(it) }.getOrNull() }

        /** https only; plain http solely for loopback hosts (tests). */
        private fun isAllowedRelay(relay: String): Boolean {
            val uri = try { URI(relay) } catch (_: Exception) { return false }
            val host = uri.host ?: return false
            if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) return false
            return when (uri.scheme?.lowercase()) {
                "https" -> true
                "http" -> host.lowercase() in LOOPBACK_HOSTS
                else -> false
            }
        }

        /** Test helper: builds the code string the way The Aive does. */
        internal fun encode(relay: String, room: String, clientToken: String, key: ByteArray): String {
            val json = buildJsonObject {
                put("v", 1)
                put("relay", relay)
                put("room", room)
                put("clientToken", clientToken)
                put("key", Base64.getUrlEncoder().withoutPadding().encodeToString(key))
            }
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray(Charsets.UTF_8))
        }
    }
}

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonObject.lng(key: String): Long? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

/**
 * Seals and opens relay frames: `<seq>.<base64url(nonce12 || ciphertext || tag16)>`, AES-256-GCM
 * under the pairing key, AAD `aive-relay/1|<room>|<dir>|<seq>`. One instance is one connection:
 * it owns the sender session id and send counter, and the per-sender replay state for what it
 * opens. [sendDir]/[recvDir] default to the client's view (`c2h` out, `h2c` in).
 */
class AiveRelayCodec internal constructor(
    code: AivePairingCode,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sendDir: String = "c2h",
    private val recvDir: String = "h2c",
    private val random: SecureRandom = SecureRandom(),
) {
    private val room = code.room
    private val keySpec = SecretKeySpec(code.key, "AES")

    /** This connection's random 16-hex session id. */
    val sid: String = ByteArray(8).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private var sendSeq = 0L

    /** Last accepted seq per remote sid; oldest sid evicted past [MAX_TRACKED_SIDS]. */
    private val lastSeen = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > MAX_TRACKED_SIDS
    }

    /** Adds sid/seq/ts to [fields] and returns the frame `data` string. */
    @Synchronized
    fun seal(fields: JsonObject): String {
        val seq = sendSeq + 1
        val plain = buildJsonObject {
            fields.forEach { (k, v) -> put(k, v) }
            put("sid", sid)
            put("seq", seq)
            put("ts", clock())
        }.toString().toByteArray(Charsets.UTF_8)
        if (plain.size > MAX_PLAINTEXT_BYTES) {
            throw AiveFrameException("Message too large for the relay (${plain.size} bytes, limit $MAX_PLAINTEXT_BYTES).")
        }
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(sendDir, seq))
        val sealed = nonce + cipher.doFinal(plain)
        sendSeq = seq
        return "$seq." + Base64.getUrlEncoder().withoutPadding().encodeToString(sealed)
    }

    /**
     * Authenticates and decrypts a frame from the other side, enforcing the sequence, freshness and
     * size rules. Throws [AiveFrameException] on any violation; state only advances on success.
     */
    @Synchronized
    fun open(data: String): JsonObject {
        if (data.length > MAX_FRAME_CHARS) throw AiveFrameException("Frame too large.")
        val dot = data.indexOf('.')
        val seq = if (dot > 0 && dot <= 19 && data.substring(0, dot).all { it in '0'..'9' }) {
            data.substring(0, dot).toLongOrNull()
        } else null
        if (seq == null || seq < 1) throw AiveFrameException("Bad frame header.")
        val sealed = try {
            Base64.getUrlDecoder().decode(data.substring(dot + 1))
        } catch (_: IllegalArgumentException) {
            throw AiveFrameException("Bad frame encoding.")
        }
        if (sealed.size < NONCE_BYTES + TAG_BITS / 8) throw AiveFrameException("Frame too short.")
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, sealed.copyOfRange(0, NONCE_BYTES)))
            cipher.updateAAD(aad(recvDir, seq))
            cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
        } catch (_: Exception) {
            throw AiveFrameException("Frame failed authentication.")
        }
        if (plain.size > MAX_PLAINTEXT_BYTES) throw AiveFrameException("Frame too large.")
        val json = try {
            Json.parseToJsonElement(String(plain, Charsets.UTF_8)) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: throw AiveFrameException("Frame is not a JSON object.")
        val sid = json.str("sid")?.takeIf { it.isNotEmpty() && it.length <= 64 }
            ?: throw AiveFrameException("Frame has no session id.")
        if (json.lng("seq") != seq) throw AiveFrameException("Frame sequence mismatch.")
        val ts = json.lng("ts") ?: throw AiveFrameException("Frame has no timestamp.")
        if (kotlin.math.abs(clock() - ts) > MAX_SKEW_MS) throw AiveFrameException("Frame timestamp is out of range.")
        val last = lastSeen[sid]
        if (last != null && seq <= last) throw AiveFrameException("Replayed frame.")
        lastSeen[sid] = seq
        return json
    }

    private fun aad(dir: String, seq: Long) = "aive-relay/1|$room|$dir|$seq".toByteArray(Charsets.UTF_8)

    companion object {
        const val MAX_PLAINTEXT_BYTES = 600_000
        const val MAX_SKEW_MS = 10 * 60 * 1000L
        const val MAX_TRACKED_SIDS = 64
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val MAX_FRAME_CHARS = 1_000_000
    }
}
