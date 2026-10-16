package com.hereliesaz.ideaz.ai.aive

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class AiveRelayCodecTest {
    private val key = ByteArray(32) { it.toByte() }

    private fun code(
        relay: String = "https://relay.example.workers.dev",
        room: String = "room-1",
        key: ByteArray = this.key,
    ) = AivePairingCode.parse(AivePairingCode.encode(relay, room, "client-token", key))

    private fun client(c: AivePairingCode = code(), clock: () -> Long = System::currentTimeMillis) =
        AiveRelayCodec(c, clock)

    private fun host(c: AivePairingCode = code(), clock: () -> Long = System::currentTimeMillis) =
        AiveRelayCodec(c, clock, sendDir = "h2c", recvDir = "c2h")

    private fun msg(text: String = "hi") = buildJsonObject {
        put("t", "ping")
        put("id", 1)
        put("note", text)
    }

    private fun assertRejected(codec: AiveRelayCodec, frame: String) {
        try {
            codec.open(frame)
            fail("frame should have been rejected")
        } catch (_: AiveFrameException) {
        }
    }

    private fun rawCode(json: String) =
        AivePairingCode.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    private fun assertInvalid(code: String) {
        try {
            AivePairingCode.parse(code)
            fail("code should be invalid")
        } catch (_: IllegalArgumentException) {
        }
        assertNull(AivePairingCode.parseOrNull(code))
    }

    @Test
    fun `a valid pairing code parses`() {
        val c = code(relay = "https://relay.example.workers.dev/")
        assertEquals("https://relay.example.workers.dev", c.relay)
        assertEquals("room-1", c.room)
        assertEquals("client-token", c.clientToken)
        assertEquals(32, c.key.size)
    }

    @Test
    fun `toString does not leak the secrets`() {
        val text = code().toString()
        assertFalse(text.contains("client-token"))
        assertFalse(text.contains("room-1"))
    }

    @Test
    fun `invalid pairing codes are rejected`() {
        val b64 = { b: ByteArray -> Base64.getUrlEncoder().withoutPadding().encodeToString(b) }
        val good = """{"v":1,"relay":"https://r.example","room":"r1","clientToken":"t","key":"${b64(key)}"}"""
        assertInvalid("")
        assertInvalid("aive2:" + b64(good.toByteArray()))
        assertInvalid(AivePairingCode.PREFIX + "!!!not-base64!!!")
        assertInvalid(AivePairingCode.PREFIX + b64("not json".toByteArray()))
        assertInvalid(rawCode(good.replace("\"v\":1", "\"v\":2")))
        assertInvalid(rawCode(good.replace("https://r.example", "http://r.example")))
        assertInvalid(rawCode(good.replace("https://r.example", "ftp://r.example")))
        assertInvalid(rawCode(good.replace("https://r.example", "https://user:pw@r.example")))
        assertInvalid(rawCode(good.replace("\"r1\"", "\"a/b\"")))
        assertInvalid(rawCode(good.replace("\"t\"", "\"\"")))
        assertInvalid(rawCode(good.replace(b64(key), b64(ByteArray(16)))))
        assertInvalid(rawCode(good.replace(b64(key), "%%%")))
    }

    @Test
    fun `plain http is only accepted for loopback`() {
        assertEquals("http://127.0.0.1:8787", code(relay = "http://127.0.0.1:8787").relay)
        assertEquals("http://localhost:8787", code(relay = "http://localhost:8787").relay)
        assertInvalid(AivePairingCode.encode("http://relay.example", "r1", "t", key))
    }

    @Test
    fun `seal and open round trip both ways`() {
        val c = client()
        val h = host()
        val opened = h.open(c.seal(msg("hello")))
        assertEquals("hello", opened["note"].toString().trim('"'))
        assertEquals(c.sid, opened["sid"].toString().trim('"'))
        assertEquals("1", opened["seq"].toString())
        assertEquals(16, c.sid.length)
        val back = c.open(h.seal(buildJsonObject { put("t", "pong"); put("id", 1) }))
        assertEquals("pong", back["t"].toString().trim('"'))
    }

    @Test
    fun `frames carry increasing sequence numbers and fresh nonces`() {
        val c = client()
        val a = c.seal(msg())
        val b = c.seal(msg())
        assertTrue(a.startsWith("1."))
        assertTrue(b.startsWith("2."))
        assertFalse(a.substringAfter('.').take(16) == b.substringAfter('.').take(16))
    }

    @Test
    fun `a different key fails authentication`() {
        val frame = client().seal(msg())
        assertRejected(host(code(key = ByteArray(32) { 7 })), frame)
    }

    @Test
    fun `a different room fails the AAD`() {
        val frame = client().seal(msg())
        assertRejected(host(code(room = "room-2")), frame)
    }

    @Test
    fun `a frame cannot be reflected back in its own direction`() {
        val c = client()
        assertRejected(client(), c.seal(msg()))
    }

    @Test
    fun `a swapped seq prefix fails the AAD`() {
        val c = client()
        c.seal(msg())
        val frame = c.seal(msg())
        assertRejected(host(), "1." + frame.substringAfter('.'))
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val frame = client().seal(msg())
        val body = Base64.getUrlDecoder().decode(frame.substringAfter('.'))
        body[body.size - 1] = (body[body.size - 1].toInt() xor 1).toByte()
        val tampered = frame.substringBefore('.') + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(body)
        assertRejected(host(), tampered)
        assertRejected(host(), "garbage")
        assertRejected(host(), "1.AAAA")
        assertRejected(host(), "0." + frame.substringAfter('.'))
        assertRejected(host(), "-1." + frame.substringAfter('.'))
    }

    @Test
    fun `a replayed frame is rejected`() {
        val h = host()
        val frame = client().seal(msg())
        h.open(frame)
        assertRejected(h, frame)
    }

    @Test
    fun `a lower seq than already seen is rejected`() {
        val c = client()
        val h = host()
        val first = c.seal(msg())
        val second = c.seal(msg())
        h.open(second)
        assertRejected(h, first)
    }

    @Test
    fun `sequence is tracked per sender session`() {
        val h = host()
        h.open(client().seal(msg()))
        h.open(client().seal(msg()))
    }

    @Test
    fun `many sender sessions stay bounded`() {
        val h = host()
        repeat(AiveRelayCodec.MAX_TRACKED_SIDS * 2) { h.open(client().seal(msg())) }
    }

    @Test
    fun `timestamps more than ten minutes off are rejected`() {
        val now = 1_700_000_000_000L
        val h = host(clock = { now })
        assertRejected(h, client(clock = { now - 11 * 60_000 }).seal(msg()))
        assertRejected(h, client(clock = { now + 11 * 60_000 }).seal(msg()))
        h.open(client(clock = { now - 9 * 60_000 }).seal(msg()))
        h.open(client(clock = { now + 9 * 60_000 }).seal(msg()))
    }

    @Test
    fun `oversize plaintext is refused on send`() {
        val big = "x".repeat(AiveRelayCodec.MAX_PLAINTEXT_BYTES)
        try {
            client().seal(buildJsonObject { put("prompt", big) })
            fail("should be too large")
        } catch (e: AiveFrameException) {
            assertTrue(e.message!!.contains("too large"))
        }
        // Just under the cap still goes through.
        val ok = "x".repeat(AiveRelayCodec.MAX_PLAINTEXT_BYTES - 200)
        host().open(client().seal(buildJsonObject { put("prompt", ok) }))
    }

    @Test
    fun `oversize frames are refused on receive`() {
        assertRejected(host(), "1." + "A".repeat(1_000_001))
    }

    @Test
    fun `a body that is not a json object is rejected`() {
        // Sealed correctly under the right key and AAD, but with a body the codec must not accept.
        val c = code()
        val forged = forge(c, seq = 1, plaintext = "[1,2,3]")
        assertRejected(host(), forged)
        assertRejected(host(), forge(c, seq = 1, plaintext = """{"sid":"abc","seq":2,"ts":${System.currentTimeMillis()}}"""))
        assertRejected(host(), forge(c, seq = 1, plaintext = """{"sid":"abc","seq":1}"""))
        assertRejected(host(), forge(c, seq = 1, plaintext = """{"seq":1,"ts":${System.currentTimeMillis()}}"""))
    }

    /** Seals [plaintext] verbatim the way the codec does, so receive-side checks can be exercised. */
    private fun forge(c: AivePairingCode, seq: Long, plaintext: String, dir: String = "c2h"): String {
        val nonce = ByteArray(12) { 3 }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(c.key, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, nonce),
        )
        cipher.updateAAD("aive-relay/1|${c.room}|$dir|$seq".toByteArray())
        val sealed = nonce + cipher.doFinal(plaintext.toByteArray())
        return "$seq." + Base64.getUrlEncoder().withoutPadding().encodeToString(sealed)
    }
}
