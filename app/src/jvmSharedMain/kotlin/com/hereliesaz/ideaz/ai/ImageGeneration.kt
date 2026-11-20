package com.hereliesaz.ideaz.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

/** A generated image: its bytes and the file extension they are encoded as (no dot). */
class GeneratedImage(val bytes: ByteArray, val extension: String)

/** Generation failed; [message] is shown to the model (and so to the user) as the tool result. */
class ImageGenerationException(message: String) : Exception(message)

/**
 * Turns a text prompt into an image. Blocking: tool dispatch already runs on an IO dispatcher,
 * and a queued job can take minutes.
 */
interface ImageGenerator {
    /** The format every result is encoded in, so callers can check a target path up front. */
    val extension: String

    fun generate(prompt: String, width: Int, height: Int): GeneratedImage
}

/**
 * AI Horde (aihorde.net): community GPU workers, no key needed. Anonymous jobs use the public
 * anonymous key and get the lowest queue priority. Ported from The Aive's HostedImageGeneration:
 * submit async → poll check → fetch status → download. Results are WebP.
 *
 * Privacy: the prompt goes to AI Horde and to whichever volunteer worker picks the job up.
 */
class AiHordeImageGenerator(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://aihorde.net/api/v2",
    private val apiKey: () -> String? = { null },
    private val pollMillis: Long = 4_000,
    private val timeoutMillis: Long = 5 * 60_000,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) : ImageGenerator {

    override val extension: String = "webp"

    private val json = Json { ignoreUnknownKeys = true }

    override fun generate(prompt: String, width: Int, height: Int): GeneratedImage {
        require(prompt.isNotBlank()) { "prompt is empty" }
        val id = submit(prompt, width.snapTo64(), height.snapTo64())
        val deadline = now() + timeoutMillis
        try {
            while (true) {
                val check = getJson("$baseUrl/generate/check/$id")
                if (check["faulted"]?.jsonPrimitive?.booleanOrNull == true) {
                    throw ImageGenerationException("AI Horde could not generate the image.")
                }
                if (check["is_possible"]?.jsonPrimitive?.booleanOrNull == false) {
                    throw ImageGenerationException("No AI Horde worker can take this job right now.")
                }
                if (check["done"]?.jsonPrimitive?.booleanOrNull == true) break
                if (now() >= deadline) {
                    throw ImageGenerationException("AI Horde did not finish within ${timeoutMillis / 60_000} min.")
                }
                sleep(pollMillis)
            }
            val img = getJson("$baseUrl/generate/status/$id")["generations"]?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("img")?.jsonPrimitive?.contentOrNull
                ?: throw ImageGenerationException("AI Horde finished without an image.")
            return GeneratedImage(fetchImage(img), extension)
        } catch (e: Exception) {
            // Free the queue slot on any failure or timeout; a finished job is already gone.
            runCatching { http.newCall(request("$baseUrl/generate/status/$id").delete().build()).execute().close() }
            throw e
        }
    }

    private fun submit(prompt: String, width: Int, height: Int): String {
        val body = """{"prompt":${JsonPrimitive(prompt)},"params":{"n":1,"width":$width,"height":$height},"nsfw":false,"r2":true}"""
        val response = http.newCall(
            request("$baseUrl/generate/async").post(body.toRequestBody(JSON)).build(),
        ).execute().use { it.objectBody() }
        return response["id"]?.jsonPrimitive?.contentOrNull?.takeIf { ID.matches(it) }
            ?: throw ImageGenerationException(
                "AI Horde refused the job: " + (response["message"]?.jsonPrimitive?.contentOrNull ?: "no id returned"),
            )
    }

    /** With r2 the result is an HTTPS URL on Horde's storage; without it, inline base64. Both capped. */
    private fun fetchImage(img: String): ByteArray {
        if (!img.startsWith("https://")) {
            if (img.length > MAX_IMAGE_BYTES * 4 / 3 + 4) throw ImageGenerationException("The generated image is too large.")
            return runCatching { Base64.getDecoder().decode(img) }
                .getOrElse { throw ImageGenerationException("AI Horde returned an unreadable image.") }
        }
        val host = img.toHttpUrlOrNull()?.host.orEmpty()
        if (IMAGE_HOSTS.none { host == it || host.endsWith(".$it") }) {
            throw ImageGenerationException("AI Horde pointed at an unexpected image host ($host).")
        }
        http.newCall(Request.Builder().url(img).build()).execute().use { response ->
            if (!response.isSuccessful) throw ImageGenerationException("Downloading the image failed (HTTP ${response.code}).")
            if (response.body.contentLength() > MAX_IMAGE_BYTES) throw ImageGenerationException("The generated image is too large.")
            // Read at most one byte past the cap, so a lying or missing Content-Length can't exhaust memory.
            val source = response.body.source()
            source.request(MAX_IMAGE_BYTES + 1L)
            if (source.buffer.size > MAX_IMAGE_BYTES) throw ImageGenerationException("The generated image is too large.")
            return source.buffer.readByteArray()
        }
    }

    private fun getJson(url: String): JsonObject =
        http.newCall(request(url).get().build()).execute().use { it.objectBody() }

    private fun request(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("apikey", apiKey()?.takeIf(String::isNotBlank) ?: ANONYMOUS_KEY)
        .header("Client-Agent", "IDEaz:1.0:github.com/HereLiesAz/IDEaz")

    private fun okhttp3.Response.objectBody(): JsonObject {
        val text = body.string()
        if (!isSuccessful) {
            val msg = runCatching { json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
            throw ImageGenerationException("AI Horde: HTTP $code" + (msg?.let { " – $it" } ?: ""))
        }
        return runCatching { json.parseToJsonElement(text).jsonObject }
            .getOrElse { throw ImageGenerationException("AI Horde sent an unreadable reply.") }
    }

    private companion object {
        const val ANONYMOUS_KEY = "0000000000"
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024 // the edit-review ceiling
        val JSON = "application/json".toMediaType()
        /** Where Horde's r2 results live. */
        val IMAGE_HOSTS = listOf("r2.cloudflarestorage.com", "aihorde.net")
        val ID = Regex("[a-zA-Z0-9-]{1,100}")

        /** Horde wants multiples of 64; larger anonymous jobs queue far longer, so cap at 768. */
        fun Int.snapTo64(): Int = ((coerceIn(64, 768) + 32) / 64) * 64
    }
}
