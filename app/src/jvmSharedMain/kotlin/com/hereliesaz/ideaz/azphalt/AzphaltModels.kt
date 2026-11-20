package com.hereliesaz.ideaz.azphalt

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Ported from The Aive's Azphalt store (LLM packages only). IDEaz hosts `kind: "llm"`,
// endpoint-tier packages over `openai-chat`; workflows, roles, model assets, Moyai sessions and
// setup scripts are out of scope.

const val AZPHALT_STORE_URL: String = "https://azphalt.store"
const val IDEAZ_AZPHALT_HOST_ID: String = "com.hereliesaz.ideaz"

/** The Aive's id: its LLM packages are plain HTTPS endpoints, so IDEaz accepts them too. */
const val AIVE_AZPHALT_HOST_ID: String = "com.hereliesaz.aive"
val IDEAZ_ACCEPTED_TARGET_APPS: Set<String> = setOf(IDEAZ_AZPHALT_HOST_ID, AIVE_AZPHALT_HOST_ID)

/** Azphalt host API version this integration implements. */
const val IDEAZ_AZPHALT_API_VERSION: String = "0.1.0"

@Serializable
data class AzphaltRepositoryIndex(
    val name: String,
    val version: String,
    val description: String? = null,
    val signingKeys: List<AzphaltSigningKey> = emptyList(),
)

@Serializable
data class AzphaltSigningKey(
    val publicKey: String,
    val keyId: String? = null,
    val label: String? = null,
)

@Serializable
data class AzphaltPackageSearchResponse(
    val packages: List<AzphaltPackageSummary> = emptyList(),
    val total: Int = packages.size,
    val page: Int = 1,
    val pages: Int = 1,
)

@Serializable
data class AzphaltPackageSummary(
    val id: String,
    val name: String,
    val description: String? = null,
    val author: String? = null,
    val version: String,
    val latest: String = version,
    val kind: String,
    val targetApps: List<String> = emptyList(),
    val llm: AzphaltLlm? = null,
)

/** The `llm` block of a `kind: "llm"` package (azphalt spec/llm.md). */
@Serializable
data class AzphaltLlm(
    val tier: String,
    val inputs: List<AzphaltLlmInput> = emptyList(),
    val endpoint: AzphaltLlmEndpoint? = null,
    val dataHandling: AzphaltLlmDataHandling? = null,
)

@Serializable
data class AzphaltLlmInput(
    val id: String,
    val type: String? = null,
    val password: Boolean = false,
    val optional: Boolean = false,
    val description: String? = null,
)

@Serializable
data class AzphaltLlmEndpoint(
    val protocols: List<String> = emptyList(),
    val baseUrl: String? = null,
    val defaultModel: String? = null,
    val auth: String = "none",
    val authInput: String? = null,
)

@Serializable
data class AzphaltLlmDataHandling(
    val prompts: String = "unknown",
    val modelPinned: Boolean = false,
    val operator: String? = null,
    val terms: String? = null,
)

private val INPUT_REF: Regex = Regex("""^\$\{input:([^}]{1,128})\}$""")

/** The non-secret install input that supplies a templated endpoint URL, when one is declared. */
fun AzphaltLlm.endpointUrlInput(): AzphaltLlmInput? {
    val id = endpoint?.baseUrl?.let(INPUT_REF::matchEntire)?.groupValues?.getOrNull(1) ?: return null
    return inputs.firstOrNull { it.id == id && !it.password }
}

/** Why IDEaz cannot call this `llm` block directly, or null when it can. */
fun AzphaltLlm.directUseProblem(): String? {
    val endpoint = endpoint ?: return "declares no endpoint"
    val literalHttps = endpoint.baseUrl?.startsWith("https://", ignoreCase = true) == true
    return when {
        tier != "endpoint" -> "runs only in a GitHub sandbox ($tier tier), which IDEaz does not provide"
        "openai-chat" !in endpoint.protocols -> "does not offer the openai-chat protocol IDEaz supports"
        !literalHttps && endpointUrlInput() == null -> "endpoint is not an https:// URL or declared endpoint input"
        endpoint.defaultModel.isNullOrBlank() -> "names no default model for openai-chat"
        endpoint.auth !in KNOWN_AUTH -> "uses unknown auth ${endpoint.auth}"
        endpoint.auth != "none" && inputs.none { it.id == endpoint.authInput } -> "names an undeclared key input"
        dataHandling == null -> "does not say what the operator does with prompts"
        else -> null
    }
}

private val KNOWN_AUTH = setOf("none", "optional-bearer", "required-bearer")

fun AzphaltPackageSummary.isDirectLlmPackage(): Boolean =
    kind == "llm" && llm?.directUseProblem() == null

fun targetsIdeaz(targetApps: List<String>): Boolean =
    targetApps.isEmpty() || targetApps.any { it in IDEAZ_ACCEPTED_TARGET_APPS }

@Serializable
data class AzphaltPackageDetail(
    val id: String,
    val name: String,
    val author: String? = null,
    val description: String? = null,
    val version: String,
    val latest: String? = null,
    val kind: String,
    val targetApps: List<String> = emptyList(),
    val llm: AzphaltLlm? = null,
)

/**
 * The manifest fields an llm package is checked against. Executable/editor blocks are kept as raw
 * JSON only so their presence can be rejected; IDEaz never interprets them.
 */
@Serializable
data class AzphaltManifest(
    val azphalt: String,
    val id: String,
    val name: String,
    val version: String,
    val kind: String,
    val license: String,
    val compat: String,
    val description: String? = null,
    val author: String? = null,
    val entry: JsonElement? = null,
    val runtime: JsonElement? = null,
    val capabilities: JsonElement? = null,
    val assets: List<JsonElement>? = null,
    val app: JsonElement? = null,
    val mcp: JsonElement? = null,
    val targetApps: List<String> = emptyList(),
    val files: Map<String, String> = emptyMap(),
    val llm: AzphaltLlm? = null,
)

@Serializable
data class AzphaltRevocationsResponse(
    val revocations: List<AzphaltRevocation> = emptyList(),
)

@Serializable
data class AzphaltRevocation(
    val id: String,
    val version: String,
    val reason: String? = null,
    val revokedAt: String? = null,
)

sealed class AzphaltRepositoryException(message: String) : RuntimeException(message) {
    class Http(val statusCode: Int, message: String) : AzphaltRepositoryException(message)
    class AuthenticationRequired(message: String) : AzphaltRepositoryException(message)
    class PaymentRequired(message: String) : AzphaltRepositoryException(message)
}

private val SHA256_HEX = Regex("[0-9a-f]{64}")

/** The lower-case hex digest in a `sha256-<hex>` or `sha256:<hex>` checksum, or null. */
fun azphaltSha256Hex(value: String?): String? {
    val text = value?.trim()?.lowercase() ?: return null
    val hex = when {
        text.startsWith("sha256-") -> text.removePrefix("sha256-")
        text.startsWith("sha256:") -> text.removePrefix("sha256:")
        else -> return null
    }
    return hex.takeIf { SHA256_HEX.matches(it) }
}

/**
 * The exact Azphalt 0.1 `compat` grammar: one optional comparator then MAJOR[.MINOR[.PATCH]].
 * Null when the expression is not in that grammar.
 */
fun azphaltCompatSatisfies(compat: String, hostVersion: String = IDEAZ_AZPHALT_API_VERSION): Boolean? {
    val match = Regex("^(>=|>|<=|<|=)?\\s*([0-9]+(?:\\.[0-9]+){0,2})$").matchEntire(compat.trim()) ?: return null
    val required = parseApiVersion(match.groupValues[2]) ?: return null
    val host = parseApiVersion(hostVersion) ?: return null
    val c = compareIntLists(host, required)
    return when (match.groupValues[1].ifEmpty { ">=" }) {
        ">=" -> c >= 0
        ">" -> c > 0
        "<=" -> c <= 0
        "<" -> c < 0
        else -> c == 0
    }
}

private fun parseApiVersion(raw: String): List<Int>? {
    val parts = raw.trim().split('.')
    if (parts.size > 3) return null
    val values = parts.map { p -> if (p.isEmpty() || p.any { !it.isDigit() }) return null else p.toIntOrNull() ?: return null }
    return List(3) { values.getOrElse(it) { 0 } }
}

private fun compareIntLists(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
        val c = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

/** Dotted numeric core of a version ("1.2.3-rc1+b" -> [1,2,3]), or null when not numeric. */
internal fun azphaltVersionCore(version: String): List<Int>? =
    version.substringBefore('+').substringBefore('-').split('.').map { it.toIntOrNull() ?: return null }

/** Orders two versions by numeric core, then pre-release (which ranks below the plain release). */
internal fun compareAzphaltVersions(a: String, b: String): Int? {
    val core = compareIntLists(azphaltVersionCore(a) ?: return null, azphaltVersionCore(b) ?: return null)
    if (core != 0) return core
    val preA = a.substringBefore('+').substringAfter('-', "")
    val preB = b.substringBefore('+').substringAfter('-', "")
    if (preA.isEmpty() || preB.isEmpty()) return preA.isEmpty().compareTo(preB.isEmpty())
    val ia = preA.split('.')
    val ib = preB.split('.')
    for (i in 0 until minOf(ia.size, ib.size)) {
        val nx = ia[i].toIntOrNull()
        val ny = ib[i].toIntOrNull()
        val c = when {
            nx != null && ny != null -> nx.compareTo(ny)
            nx != null -> -1
            ny != null -> 1
            else -> ia[i].compareTo(ib[i])
        }
        if (c != 0) return c
    }
    return ia.size.compareTo(ib.size)
}

/** A replayed older signed package must not silently replace a newer installed one. */
internal fun requireNotDowngrade(packageId: String, installed: String?, incoming: String) {
    if (installed == null || azphaltVersionCore(installed) == null) return
    val order = compareAzphaltVersions(installed, incoming)
        ?: throw IllegalArgumentException("$packageId $incoming is not a readable version, so it cannot replace the installed $installed")
    require(order <= 0) { "$packageId $incoming is older than the installed $installed" }
}
