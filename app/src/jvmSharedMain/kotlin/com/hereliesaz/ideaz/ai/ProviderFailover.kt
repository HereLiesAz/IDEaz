package com.hereliesaz.ideaz.ai

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/**
 * Automatic provider failover.
 *
 * When a provider refuses a call because its quota or rate limit is used up, the adapter throws
 * [ProviderQuotaException]; [FailoverClient] records a cooldown for that provider and retries the
 * same request on the next usable provider. Everything that decides something lives here as plain
 * functions so it can be unit tested without a network or Android.
 */

/**
 * A provider refused the call because of quota or rate limits, and waiting here is not worth it.
 *
 * @param cooldownSeconds how long until the provider should accept requests again.
 * @param message the raw provider error, kept for the "Details" view; never shown as the headline.
 */
class ProviderQuotaException(
    val cooldownSeconds: Long,
    message: String,
) : Exception(message)

/** Every usable provider is cooling down. [userMessage] is the plain line; [details] is the raw error. */
class AllProvidersExhaustedException(
    val userMessage: String,
    val details: String,
) : Exception(userMessage)

/** What a failed HTTP reply says about quota. See [QuotaClassifier.classify]. */
data class QuotaSignal(
    /** Seconds the server asked us to wait, if it said. */
    val retryAfterSeconds: Long?,
    /** The reply names a quota explicitly (insufficient_quota, quota_exceeded, RESOURCE_EXHAUSTED). */
    val hasQuotaMarker: Boolean,
) {
    /** How long to keep this provider out of rotation. */
    val cooldownSeconds: Long
        get() = retryAfterSeconds
            ?: if (hasQuotaMarker) QuotaClassifier.DEFAULT_QUOTA_COOLDOWN_SECONDS
            else QuotaClassifier.DEFAULT_RATE_LIMIT_COOLDOWN_SECONDS

    /**
     * True when waiting in place would take longer than [QuotaClassifier.SHORT_WAIT_SECONDS]:
     * the caller should switch provider instead. A shorter wait keeps the ordinary retry policy.
     */
    val isLongWait: Boolean get() = cooldownSeconds > QuotaClassifier.SHORT_WAIT_SECONDS
}

object QuotaClassifier {
    /** A Retry-After up to this long is waited out with the normal retry policy. */
    const val SHORT_WAIT_SECONDS = 60L

    /** Cooldown when a quota error names no reset time. */
    const val DEFAULT_QUOTA_COOLDOWN_SECONDS = 3_600L

    /** Cooldown when a plain 429 outlasts the retries and names no reset time. */
    const val DEFAULT_RATE_LIMIT_COOLDOWN_SECONDS = 60L

    private val QUOTA_MARKERS = listOf(
        "insufficient_quota",
        "quota_exceeded",
        "quota exceeded",
        "resource_exhausted",
    )

    private val BODY_RETRY_PATTERNS = listOf(
        Regex("\"retry_after\"\\s*:\\s*\"?(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),
        Regex("\"retryDelay\"\\s*:\\s*\"(\\d+(?:\\.\\d+)?)s\"", RegexOption.IGNORE_CASE),
        Regex("retry (?:after|in) (\\d+(?:\\.\\d+)?)\\s*(?:s\\b|sec)", RegexOption.IGNORE_CASE),
    )

    /**
     * Classifies a failed reply. Returns null when it is not a quota or rate-limit failure:
     * HTTP 429 or 402, a named quota error, or any status carrying a Retry-After beyond
     * [SHORT_WAIT_SECONDS].
     */
    fun classify(
        httpCode: Int,
        body: String?,
        retryAfterHeader: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): QuotaSignal? {
        val retryAfter = parseRetryAfterSeconds(retryAfterHeader, nowMs) ?: retryAfterFromBody(body)
        val marker = hasQuotaMarker(body)
        val quotaStatus = httpCode == 429 || httpCode == 402
        val longWait = retryAfter != null && retryAfter > SHORT_WAIT_SECONDS
        if (!quotaStatus && !marker && !longWait) return null
        return QuotaSignal(retryAfter, marker)
    }

    /**
     * Classifies an exception message from an SDK that hides the status line (Gemini). A "429" or
     * RESOURCE_EXHAUSTED anywhere in the text counts as a rate-limit failure.
     */
    fun classifyMessage(message: String?): QuotaSignal? {
        if (message.isNullOrBlank()) return null
        val has429 = Regex("\\b429\\b").containsMatchIn(message)
        return classify(if (has429) 429 else 0, message, null)
    }

    internal fun hasQuotaMarker(body: String?): Boolean {
        if (body.isNullOrEmpty()) return false
        val lower = body.lowercase()
        return QUOTA_MARKERS.any { it in lower }
    }

    internal fun retryAfterFromBody(body: String?): Long? {
        if (body.isNullOrEmpty()) return null
        for (pattern in BODY_RETRY_PATTERNS) {
            val seconds = pattern.find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
            return ceil(seconds).toLong()
        }
        return null
    }

    /** Retry-After header: delta-seconds, or an HTTP-date. Null when absent or unparsable. */
    fun parseRetryAfterSeconds(header: String?, nowMs: Long = System.currentTimeMillis()): Long? {
        val value = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        value.toDoubleOrNull()?.let { return if (it >= 0) ceil(it).toLong() else null }
        val untilMs = runCatching {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowMs
        }.getOrNull() ?: return null
        return if (untilMs > 0) ceil(untilMs / 1000.0).toLong() else 0L
    }

    /** Builds the exception for a failed reply, or null when it is not quota-class. */
    fun toException(httpCode: Int, body: String?, retryAfterHeader: String?, raw: String): ProviderQuotaException? =
        classify(httpCode, body, retryAfterHeader)?.let { ProviderQuotaException(it.cooldownSeconds, raw) }
}

/** Where cooldown expiries are persisted between launches. */
interface CooldownStore {
    fun read(): String?
    fun write(value: String)
}

/**
 * Per-provider cooldown bookkeeping: provider id -> epoch millis when it may be tried again.
 * Lives in memory and, when a [CooldownStore] is attached, survives a restart.
 */
class ProviderCooldowns(private val now: () -> Long = { System.currentTimeMillis() }) {
    private val until = HashMap<String, Long>()
    private var store: CooldownStore? = null

    /** Loads persisted entries (keeping the later expiry) and persists every change from now on. */
    @Synchronized
    fun attachStore(newStore: CooldownStore) {
        store = newStore
        decode(runCatching { newStore.read() }.getOrNull()).forEach { (id, at) ->
            if (at > (until[id] ?: 0L)) until[id] = at
        }
        prune()
    }

    @Synchronized
    fun markCooling(id: String, seconds: Long) {
        val at = now() + seconds.coerceAtLeast(1L) * 1000L
        if (at > (until[id] ?: 0L)) until[id] = at
        persist()
    }

    @Synchronized
    fun isCooling(id: String): Boolean = (until[id] ?: return false) > now()

    /** Milliseconds until the earliest of [ids] that is cooling becomes available; null if none cool. */
    @Synchronized
    fun earliestResetInMs(ids: Collection<String>): Long? {
        val t = now()
        return ids.mapNotNull { until[it] }.filter { it > t }.minOrNull()?.minus(t)
    }

    @Synchronized
    fun clear() {
        until.clear()
        persist()
    }

    private fun prune() {
        val t = now()
        until.entries.removeAll { it.value <= t }
    }

    private fun persist() {
        prune()
        runCatching { store?.write(encode(until)) }
    }

    companion object {
        /** The cooldowns shared by the whole process. */
        val process = ProviderCooldowns()

        fun encode(map: Map<String, Long>): String =
            map.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }

        fun decode(raw: String?): Map<String, Long> {
            if (raw.isNullOrBlank()) return emptyMap()
            val out = LinkedHashMap<String, Long>()
            for (entry in raw.split(';')) {
                val idx = entry.indexOf('=')
                if (idx <= 0) continue
                val at = entry.substring(idx + 1).toLongOrNull() ?: continue
                out[entry.substring(0, idx)] = at
            }
            return out
        }
    }
}

/** One provider the failover may use, in catalog terms (no Android types). */
data class FailoverCandidate(
    val id: String,
    val label: String,
    val keyless: Boolean,
    /** Keyless, or the user has saved a key for it. */
    val usable: Boolean,
    val supportsImages: Boolean,
)

object ProviderFailover {
    /** Upper bound on providers tried for one request; each is tried at most once. */
    const val MAX_PROVIDERS_PER_REQUEST = 6

    /**
     * The order to try providers in: the user's [chosenId] first, then keyless ones in catalog
     * order, then keyed ones the user has a key for, in catalog order. Unusable providers and (when
     * the request carries images) providers that cannot read them are left out, except the chosen one.
     */
    fun order(chosenId: String, catalog: List<FailoverCandidate>, needsImages: Boolean): List<FailoverCandidate> {
        val chosen = catalog.firstOrNull { it.id == chosenId }
        val rest = catalog.filter { it.id != chosenId && it.usable && (!needsImages || it.supportsImages) }
        return listOfNotNull(chosen) + rest.filter { it.keyless } + rest.filter { !it.keyless }
    }

    /** The first provider in [order] not yet [tried] and not cooling down; null when none is left. */
    fun next(order: List<FailoverCandidate>, tried: Set<String>, cooldowns: ProviderCooldowns): FailoverCandidate? =
        order.firstOrNull { it.id !in tried && !cooldowns.isCooling(it.id) }

    /** "6 h 20 min", "45 min", "3 h": a remaining wait, rounded up to whole minutes. */
    fun formatDuration(remainingMs: Long): String {
        val totalMin = ceil(remainingMs.coerceAtLeast(0L) / 60_000.0).toLong().coerceAtLeast(1L)
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }

    /** The one-line chat notice shown when a request moves from [from] to [to]. */
    fun switchNotice(from: String, to: String, cooldownSeconds: Long): String =
        if (cooldownSeconds >= 3_600L) "$from hit its daily limit. Switched to $to."
        else "$from is rate limited. Switched to $to."

    /** The plain line shown when no provider is left. [resetInMs] is the earliest reset, if known. */
    fun exhaustedMessage(resetInMs: Long?, hasOwnKey: Boolean): String {
        val reset = resetInMs?.let { " Resets in about ${formatDuration(it)}." }.orEmpty()
        return if (hasOwnKey) {
            "All available models are at their limit.$reset Add another key in Settings to keep going."
        } else {
            "All free models are at their daily limit.$reset Add your own key in Settings to keep going."
        }
    }
}

/**
 * Wraps the providers of one request. Calls them in [order]; on [ProviderQuotaException] marks the
 * provider as cooling down and moves to the next one. A provider is tried at most once per request,
 * the total is bounded by [maxProviders], and any other failure passes straight through.
 *
 * @param clientFor builds the client for a candidate; null skips it (for example no key).
 * @param onSwitch receives the one-line notice when the request moves to another provider.
 */
class FailoverClient(
    private val order: List<FailoverCandidate>,
    private val clientFor: (FailoverCandidate) -> ConversationalAiClient?,
    private val cooldowns: ProviderCooldowns = ProviderCooldowns.process,
    private val onSwitch: (String) -> Unit = {},
    private val maxProviders: Int = ProviderFailover.MAX_PROVIDERS_PER_REQUEST,
) : ConversationalAiClient {

    /** Label of the provider that produced the last reply; null before the first success. */
    @Volatile
    var activeLabel: String? = null
        private set

    override suspend fun chat(messages: List<ChatMessage>): String {
        val tried = mutableSetOf<String>()
        var failed: FailoverCandidate? = null
        var lastFailure: ProviderQuotaException? = null
        var attempts = 0

        while (attempts < maxProviders) {
            val candidate = ProviderFailover.next(order, tried, cooldowns) ?: break
            tried += candidate.id
            val client = clientFor(candidate) ?: continue
            attempts++
            val previous = failed
            val previousFailure = lastFailure
            if (previous != null && previousFailure != null) {
                onSwitch(ProviderFailover.switchNotice(previous.label, candidate.label, previousFailure.cooldownSeconds))
            }
            try {
                val reply = client.chat(messages)
                activeLabel = candidate.label
                return reply
            } catch (e: ProviderQuotaException) {
                cooldowns.markCooling(candidate.id, e.cooldownSeconds)
                failed = candidate
                lastFailure = e
            }
        }

        val resetIn = cooldowns.earliestResetInMs(order.map { it.id })
        if (resetIn == null && lastFailure == null) {
            throw IllegalStateException("No AI provider is available. Add a key in Settings.")
        }
        throw AllProvidersExhaustedException(
            userMessage = ProviderFailover.exhaustedMessage(resetIn, hasOwnKey = order.any { !it.keyless && it.usable }),
            details = lastFailure?.message.orEmpty().take(MAX_DETAILS_CHARS),
        )
    }

    private companion object {
        const val MAX_DETAILS_CHARS = 500
    }
}
