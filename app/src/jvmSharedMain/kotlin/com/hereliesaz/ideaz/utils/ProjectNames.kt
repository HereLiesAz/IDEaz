package com.hereliesaz.ideaz.utils

/**
 * Project names become directory names under the app's files dir and GitHub repository names,
 * so only the characters GitHub keeps verbatim are accepted. Anything else ("My App" becomes
 * "My-App" on GitHub, "../x" escapes the files dir) used to break Deploy or leave the sandbox.
 */
object ProjectNames {
    private val VALID = Regex("[A-Za-z0-9._-]+")
    private const val MAX_LENGTH = 100

    /** A user-visible reason [name] is not acceptable, or null when it is. */
    fun validate(name: String): String? = when {
        name.isEmpty() -> "Enter a project name."
        name.length > MAX_LENGTH -> "Project names can be at most $MAX_LENGTH characters."
        !VALID.matches(name) ->
            "Use only letters, digits, '.', '_' and '-' in the project name (no spaces)."
        name.startsWith(".") || name.startsWith("-") ->
            "A project name cannot start with '.' or '-'."
        name.contains("..") -> "A project name cannot contain '..'."
        else -> null
    }

    fun isValid(name: String): Boolean = validate(name) == null

    /**
     * Turns an arbitrary provider-supplied name into a valid project name: invalid characters
     * become '_', leading '.'/'-' are stripped and '..' collapses. Falls back to [fallback].
     */
    fun sanitize(raw: String?, fallback: String = "Imported"): String {
        val cleaned = (raw ?: "")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trimStart('.', '-')
            .replace(Regex("\\.{2,}"), "_")
            .take(MAX_LENGTH)
        return if (isValid(cleaned)) cleaned else fallback
    }

    /**
     * A single safe file name for an imported file, or null when [raw] cannot be one
     * (blank, '.', '..', or containing a path separator or NUL).
     */
    fun safeFileName(raw: String?): String? {
        val name = raw ?: return null
        if (name.isBlank() || name == "." || name == "..") return null
        if (name.any { it == '/' || it == '\\' || it == '\u0000' }) return null
        return name
    }
}
