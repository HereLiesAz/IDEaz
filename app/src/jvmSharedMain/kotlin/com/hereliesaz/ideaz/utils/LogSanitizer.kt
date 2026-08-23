package com.hereliesaz.ideaz.utils

/**
 * Utility for redacting sensitive information (API keys, tokens, passwords) from logs and error reports.
 */
object LogSanitizer {
    private const val REDACTED = "***REDACTED***"

    private val REPLACEMENTS = listOf(
        // GitHub Tokens: classic (ghp_, gho_, etc.) and fine-grained PATs
        // (github_pat_...), GitHub's default format since 2022 - the classic
        // pattern alone never matched these.
        Regex("gh[pousr]_[a-zA-Z0-9]+") to REDACTED,
        Regex("github_pat_[a-zA-Z0-9_]+") to REDACTED,

        // Google API Keys (AIza...)
        Regex("AIza[0-9A-Za-z-_]{35}") to REDACTED,

        // OpenAI (sk-...) and Anthropic (sk-ant-...) keys.
        Regex("sk-ant-[a-zA-Z0-9_-]{20,}") to REDACTED,
        Regex("sk-[a-zA-Z0-9]{20,}") to REDACTED,

        // Hugging Face (hf_...) and Groq (gsk_...) keys.
        Regex("hf_[a-zA-Z0-9]{20,}") to REDACTED,
        Regex("gsk_[a-zA-Z0-9]{20,}") to REDACTED,

        // Bearer tokens - keep "Bearer " prefix
        Regex("(Bearer\\s+)[a-zA-Z0-9_\\-\\.]+") to "$1$REDACTED",

        // Generic "key=", "token=", "password=", "secret="
        // Captures: key=value, "key": "value", key:value
        // Group 1: key + separator
        // Group 2: value (implicitly matched by rest of regex, but we replace the whole match with Group 1 + REDACTED)
        // Note: We exclude '&' from the value to correctly handle URL parameters
        Regex("(?i)((?:key|token|api_?key|access_?token|secret|password|passwd|pwd)['\"]?\\s*[:=]\\s*['\"]?)([^\\s'\",&]+)") to "$1$REDACTED",

        // URLs with credentials: protocol://user:pass@host
        // Group 1: protocol://
        Regex("([a-zA-Z0-9+.-]+://)[^/\\s]*:[^/\\s]*@") to "$1$REDACTED@"
    )

    /**
     * Redacts sensitive patterns from the input string.
     */
    fun sanitize(text: String): String {
        var result = text
        REPLACEMENTS.forEach { (regex, replacement) ->
            result = regex.replace(result, replacement)
        }
        return result
    }

    /**
     * Redacts sensitive patterns from a Throwable's stack trace.
     */
    fun sanitize(throwable: Throwable): String {
        return sanitize(throwable.stackTraceToString())
    }
}
