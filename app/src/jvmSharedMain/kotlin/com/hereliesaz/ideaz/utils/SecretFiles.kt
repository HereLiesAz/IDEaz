package com.hereliesaz.ideaz.utils

/**
 * The one deny-list of file names that must never leave the device: not in an AI prompt
 * ([RepoSnapshot]), not in a commit and not on the public web ([DeployIgnore]). Two hand-kept
 * copies used to drift apart, so Deploy would publish files the snapshot already withheld.
 *
 * Entries are gitignore-style name globs (`*` only); they are written to `.gitignore` as they
 * are and matched against a file name, case-insensitively, by [isSecretName].
 */
object SecretFiles {

    val GLOBS: List<String> = listOf(
        ".env*",
        "*.jks",
        "*.keystore",
        "*.p12",
        "*.pfx",
        "*.pem",
        "*.key",
        "*.p8",
        "*.ppk",
        "id_rsa",
        "id_ed25519",
        "local.properties",
        "secret.*",
        "secrets.*",
        "credentials.json",
        "client_secret*.json",
        "service-account*.json",
        "service_account*.json",
        "serviceaccount*.json",
        "google-services.json",
        "GoogleService-Info.plist",
        ".npmrc",
        ".netrc",
        "_netrc",
        ".pypirc",
        ".git-credentials",
        ".htpasswd",
    )

    private val regexes: List<Regex> = GLOBS.map { glob ->
        Regex(glob.split('*').joinToString(".*") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    }

    /** True when the bare file [name] (no directory part) matches the deny-list. */
    fun isSecretName(name: String): Boolean = regexes.any { it.matches(name) }
}
