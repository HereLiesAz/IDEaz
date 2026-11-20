package com.hereliesaz.ideaz.azphalt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** The persistence the store needs; Android backs it with the app's SharedPreferences. */
interface AzphaltKeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/**
 * A hosted language model installed from an Azphalt `kind: "llm"` package. IDEaz calls the signed
 * manifest's endpoint through its OpenAI-compatible adapter; any key lives in the secure
 * credential store under [providerId].
 */
@Serializable
data class InstalledStoreLlm(
    val packageId: String,
    val version: String,
    val repositoryUrl: String,
    val name: String,
    val description: String? = null,
    val baseUrl: String,
    val defaultModel: String,
    /** `none`, `optional-bearer` or `required-bearer`. */
    val auth: String,
    val keyLabel: String? = null,
    /** `not-retained`, `logged`, `may-train` or `unknown`. */
    val prompts: String = "unknown",
    val modelPinned: Boolean = false,
    val operator: String? = null,
    val terms: String? = null,
    val installedAtEpochMillis: Long = 0,
) {
    val providerId: String get() = providerIdFor(packageId)

    /** Usable without a key: the endpoint serves anonymous requests. */
    val keyOptional: Boolean get() = auth != "required-bearer"

    /** Whether the user can supply a key at all. */
    val acceptsKey: Boolean get() = auth != "none"

    val promptHandling: String get() = describePromptHandling(operator, prompts, modelPinned)

    companion object {
        const val PROVIDER_PREFIX = "azphalt:"

        /** Prefixed so a store package can never take a built-in provider's id or key slot. */
        fun providerIdFor(packageId: String): String = PROVIDER_PREFIX + packageId
    }
}

/** A package's `dataHandling`, in words: who receives prompts and what they do with them. */
fun describePromptHandling(operator: String?, prompts: String, modelPinned: Boolean): String =
    (operator?.takeIf(String::isNotBlank) ?: "The operator") + when (prompts) {
        "not-retained" -> " does not keep prompts."
        "logged" -> " logs prompts."
        "may-train" -> " may log prompts and train on them."
        else -> " has not said what it does with prompts."
    } + if (modelPinned) "" else " The model behind it can change."

/** Installed store LLMs, persisted as one JSON list. A corrupt value reads as empty rather than crashing settings. */
class InstalledLlmStore(private val kv: AzphaltKeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(InstalledStoreLlm.serializer())
    private val state = MutableStateFlow(read())

    val installed: StateFlow<List<InstalledStoreLlm>> get() = state

    fun all(): List<InstalledStoreLlm> = state.value

    fun byProviderId(providerId: String): InstalledStoreLlm? = all().firstOrNull { it.providerId == providerId }

    fun put(value: InstalledStoreLlm) {
        state.update { current -> (current.filterNot { it.packageId == value.packageId } + value).sortedBy { it.packageId } }
        kv.putString(STORAGE_KEY, json.encodeToString(serializer, state.value))
    }

    fun remove(packageId: String) {
        state.update { current -> current.filterNot { it.packageId == packageId } }
        kv.putString(STORAGE_KEY, json.encodeToString(serializer, state.value))
    }

    private fun read(): List<InstalledStoreLlm> =
        kv.getString(STORAGE_KEY)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    companion object {
        const val STORAGE_KEY = "azphalt_installed_llms_v1"
    }
}

/** Publisher keys pinned per package id, so a later version cannot silently change signer. */
class KeyValuePublisherPinStore(private val kv: AzphaltKeyValueStore) : AzphaltPublisherPinStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    @Synchronized
    override fun keyFor(packageId: String): String? = read()[packageId]

    @Synchronized
    override fun pin(packageId: String, publicKey: String) {
        kv.putString(STORAGE_KEY, json.encodeToString(serializer, read() + (packageId to publicKey)))
    }

    // Unlike the installed list, a corrupt pin map must not read as "no pins": that would let a
    // changed publisher through unflagged.
    private fun read(): Map<String, String> =
        kv.getString(STORAGE_KEY)?.let { json.decodeFromString(serializer, it) }.orEmpty()

    companion object {
        const val STORAGE_KEY = "azphalt_publisher_pins_v1"
    }
}
