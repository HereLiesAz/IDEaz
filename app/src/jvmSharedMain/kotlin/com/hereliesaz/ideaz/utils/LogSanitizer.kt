package com.hereliesaz.ideaz.utils

/**
 * Utility for redacting sensitive information (API keys, tokens, passwords) from logs and error reports.
 */
object LogSanitizer {
    private const val REDACTED = "***REDACTED***"

    /** Names whose value is a secret: api_key, accessToken, DB_PASSWORD, client_secret, pwd... */
    private const val SECRET_NAME = "[A-Za-z0-9_.-]*(?:key|token|secret|password|passwd|pwd)"

    private val SECRET_REPLACEMENTS = listOf(
        // Aive pairing codes: `aive1:` + base64url JSON holding the room token and the
        // encryption key, i.e. a bearer secret.
        Regex("aive1:[A-Za-z0-9_-]+") to REDACTED,

        // Private keys, wherever they appear: a PEM block in any file, or one
        // JSON-escaped onto a single line ("-----BEGIN ...-----\nMIIE..."). An
        // unterminated block (a truncated log) is redacted to the end.
        Regex("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\\s\\S]*?(?:-----END [A-Z0-9 ]*PRIVATE KEY-----|\\z)") to REDACTED,
        // Service-account style JSON: the whole private_key string, PEM or not.
        Regex("(\"private_key\"\\s*:\\s*\")[^\"]*(\")") to "$1$REDACTED$2",

        // GitHub Tokens: classic (ghp_, gho_, etc.) and fine-grained PATs
        // (github_pat_...), GitHub's default format since 2022 - the classic
        // pattern alone never matched these.
        Regex("gh[pousr]_[a-zA-Z0-9]+") to REDACTED,
        Regex("github_pat_[a-zA-Z0-9_]+") to REDACTED,

        // Google API Keys (AIza...)
        Regex("AIza[0-9A-Za-z-_]{35}") to REDACTED,

        // OpenAI (sk-..., and the current sk-proj-... project-scoped format,
        // whose embedded hyphens don't match a plain alphanumeric run) and
        // Anthropic (sk-ant-...) keys.
        Regex("sk-ant-[a-zA-Z0-9_-]{20,}") to REDACTED,
        Regex("sk-[a-zA-Z0-9_-]{20,}") to REDACTED,

        // Stripe secret and restricted keys (underscores, so sk- misses them).
        Regex("\\b[sr]k_(?:live|test)_[A-Za-z0-9]{10,}") to REDACTED,

        // AWS access key IDs (long-term AKIA, temporary ASIA).
        Regex("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b") to REDACTED,

        // Hugging Face (hf_...), Groq (gsk_...), and Cerebras (csk-...) keys.
        Regex("hf_[a-zA-Z0-9]{20,}") to REDACTED,
        Regex("gsk_[a-zA-Z0-9]{20,}") to REDACTED,
        Regex("csk-[a-zA-Z0-9]{20,}") to REDACTED,

        // Bearer tokens, any case - keep "Bearer " prefix
        Regex("(?i)(Bearer\\s+)[a-zA-Z0-9_\\-\\.]+") to "$1$REDACTED",
        // `Authorization: Basic <b64>` / `Authorization: token <pat>`
        Regex("(?i)(authorization:\\s*(?:basic|token)\\s+)\\S+") to "$1$REDACTED",
        // `x-api-key: value` header form (a quoted value is handled by the by-name rule below)
        Regex("(?i)(\\bx-api-key\\s*:\\s*)(?![\"'])\\S+") to "$1$REDACTED",

        // Generic secrets by name. This used to redact any value after
        // key/token/password and ':' or '=', which mangled ordinary source
        // sent to the AI: JSX `key={item.id}`, TypeScript `token: string`,
        // `password = input.value`. Now only values that are data:
        // - a quoted literal of 6+ characters: `"password": "hunter22"`,
        //   `const apiKey = 'abc123...'`;
        Regex("(?i)(\\b$SECRET_NAME[\"']?\\s*[:=]\\s*([\"']))[^\"'\\s]{6,}(\\2)") to "$1$REDACTED$3",
        // - an unquoted value written straight after '=' (env files, .properties,
        //   query strings, `key=value` in logs), unless it starts like code;
        Regex("(?i)(\\b$SECRET_NAME=)(?![{(\\[$`\"'])[^\\s'\",&;]+") to "$1$REDACTED",
        // - a .netrc entry's `password <value>` (anchored on `machine`/`default`
        //   so prose like "Password must be..." is left alone).
        Regex("(?im)(^\\s*(?:machine\\s+\\S+|default)\\s+(?:login\\s+\\S+\\s+)?password\\s+)\\S+") to "$1$REDACTED",

        // URLs with credentials: protocol://user:pass@host
        // Group 1: protocol://
        Regex("([a-zA-Z0-9+.-]+://)[^/\\s]*:[^/\\s]*@") to "$1$REDACTED@"
    )

    /**
     * Identifying (not secret) details that logs and error reports should not carry off the
     * device: which GitHub repository a project lives in and what the user named it.
     */
    private val IDENTIFIER_REPLACEMENTS = listOf(
        // github.com/<owner>/<repo> (also the scp-style github.com:<owner>/<repo>)
        Regex("github\\.com[/:][A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") to "github.com/<owner>/<repo>",
        // App-private storage: .../files/<project>/... under /data/user/N/<pkg>, /data/data/<pkg>
        // or /storage/emulated/N/Android/data/<pkg>.
        Regex("(/(?:data/(?:user/\\d+|data)|storage/emulated/\\d+/Android/data)/[^/\\s]+/files/)[^/\\s\"'<>]+") to "$1<project>",
    )

    /** Redacts only secrets. For content that must stay otherwise intact, e.g. source sent to the AI. */
    fun redactSecrets(text: String): String {
        var result = text
        SECRET_REPLACEMENTS.forEach { (regex, replacement) ->
            result = regex.replace(result, replacement)
        }
        return result
    }

    /**
     * Redacts secrets and identifying repository/project names from logs and error reports.
     */
    fun sanitize(text: String): String {
        var result = redactSecrets(text)
        IDENTIFIER_REPLACEMENTS.forEach { (regex, replacement) ->
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
