package com.hereliesaz.ideaz.api

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class RetryInterceptor(
    private val maxRetries: Int = 5,
    private val initialDelayMs: Long = 500,
    private val maxDelayMs: Long = 30000
) : Interceptor {

    private val TAG = "RetryInterceptor"
    private val retryableStatuses = listOf(408, 429, 500, 502, 503, 504)

    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var response: Response? = null
        var exception: IOException? = null

        while (attempt <= maxRetries) {
            try {
                response = chain.proceed(chain.request())

                if (!retryableStatuses.contains(response.code)) {
                    return response
                }

                // Retryable status. Only close (and discard) this response if we are
                // going to retry. On the final attempt we must fall through and return
                // it OPEN — closing it here would hand the caller a closed body that
                // throws IllegalStateException when read.
                if (attempt < maxRetries) {
                    Log.w(TAG, "Request failed with code ${response.code}. Closing and retrying...")
                    response.close()
                }

            } catch (e: IOException) {
                Log.w(TAG, "Request failed with exception: ${e.message}. Retrying...", e)
                exception = e
                // Network error, retry
            }

            attempt++
            if (attempt > maxRetries) break

            val delay = calculateDelay(attempt, response)
            Log.d(TAG, "Retrying attempt $attempt in ${delay}ms...")
            try {
                TimeUnit.MILLISECONDS.sleep(delay)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted during retry delay", e)
            }
        }

        // If we exhausted retries
        if (exception != null) {
            throw exception
        }
        // If we have a response (even if it was a failure code that exhausted retries)
        return response!!
    }

    private fun calculateDelay(attempt: Int, response: Response?): Long {
        // Check for Retry-After header
        val retryAfter = response?.header("Retry-After")
        if (retryAfter != null) {
            try {
                // Try seconds
                val seconds = retryAfter.toLong()
                return (seconds * 1000).coerceAtMost(maxDelayMs)
            } catch (e: NumberFormatException) {
                // Not numeric seconds — try the RFC 7231 HTTP-date form
                // (e.g. "Wed, 21 Oct 2025 07:28:00 GMT").
                val untilMs = runCatching {
                    ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli() - System.currentTimeMillis()
                }.getOrNull()
                if (untilMs != null && untilMs > 0) {
                    return untilMs.coerceAtMost(maxDelayMs)
                }
            }
        }

        // Exponential backoff: initial * 2^(attempt-1)
        // attempt starts at 1 here (after increment)
        val delay = initialDelayMs * Math.pow(2.0, (attempt - 1).toDouble()).toLong()
        return Math.min(delay, maxDelayMs)
    }
}
