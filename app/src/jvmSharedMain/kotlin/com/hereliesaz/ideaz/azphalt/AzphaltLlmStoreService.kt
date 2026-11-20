package com.hereliesaz.ideaz.azphalt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A verified `kind: "llm"` package IDEaz can call directly, ready for its disclosure panel. */
data class AzphaltPreparedLlmInstall(
    val repositoryUrl: String,
    val detail: AzphaltPackageDetail,
    val version: String,
    val verification: AzphaltPackageVerification,
    val llm: AzphaltLlm,
) {
    val endpoint: AzphaltLlmEndpoint get() = requireNotNull(llm.endpoint)
    val keyInput: AzphaltLlmInput? get() = llm.inputs.firstOrNull { it.id == endpoint.authInput }
    val endpointUrlInput: AzphaltLlmInput? get() = llm.endpointUrlInput()
    val connectionCredentialInput: AzphaltLlmInput? get() = keyInput ?: llm.inputs.firstOrNull { it.password }

    /** Non-secret inputs the user fills in before install (keys go in Settings afterwards). */
    val configurationInputs: List<AzphaltLlmInput>
        get() = llm.inputs.filter { !it.password && it.id != endpoint.authInput }
}

/**
 * The LLM half of The Aive's Azphalt store service: search, prepare (download + verify + policy
 * checks) and install/remove. A package's setup script is never run.
 */
class AzphaltLlmStoreService(
    private val repository: AzphaltRepositoryClient,
    private val verifier: AzphaltPackageVerifier,
    private val llms: InstalledLlmStore,
    /** The trust root per repository URL; compiled in, never read from the repository itself. */
    private val trustedKeysFor: (String) -> List<AzphaltSigningKey> = ::trustedKeys,
) {
    private val indexMutex = Mutex()
    private var cachedIndex: AzphaltRepositoryIndex? = null

    suspend fun repositoryIndex(refresh: Boolean = false): AzphaltRepositoryIndex = indexMutex.withLock {
        if (!refresh) cachedIndex?.let { return@withLock it }
        repository.index().also { cachedIndex = it }
    }

    /** Only packages IDEaz can call directly; sandbox-only and other-protocol ones are left out. */
    suspend fun search(query: String = ""): List<AzphaltPackageSummary> =
        repository.searchLlms(query).packages.filter { it.isDirectLlmPackage() }.distinctBy { it.id }

    suspend fun revocations(): List<AzphaltRevocation> = repository.revocations()

    fun installed(): List<InstalledStoreLlm> = llms.all()

    suspend fun prepareLlmInstall(packageId: String, requestedVersion: String? = null): AzphaltPreparedLlmInstall {
        val detail = repository.detail(packageId)
        require(detail.kind == "llm") { "Azphalt language model package ${detail.id} must be kind llm" }
        requireTargetsIdeaz(detail.id, detail.targetApps)
        val version = requestedVersion?.let(AzphaltRepositoryClient::requireVersion) ?: detail.latest ?: detail.version
        requireNotRevoked(detail.id, version)
        val bytes = repository.download(detail.id, version)
        val keys = trustedKeysFor(repository.repositoryUrl)
        val verification = withContext(Dispatchers.Default) { verifier.verify(bytes, keys) }
        val manifest = verification.packageContents.manifest
        require(manifest.id == detail.id) { "Downloaded package id ${manifest.id} does not match repository package ${detail.id}" }
        require(manifest.kind == "llm") { "Downloaded package ${manifest.id} is kind ${manifest.kind}" }
        // Once a package was installed signed, an unsigned version is refused outright rather than
        // offered behind the same checkboxes as an ordinary untrusted install.
        require(verification.packageContents.signed || verification.pinnedPublisherKey == null) {
            "${manifest.id} was signed by its publisher before; this unsigned version is refused"
        }
        requireNotDowngrade(manifest.id, llms.all().firstOrNull { it.packageId == manifest.id }?.version, manifest.version)
        require(manifest.version == version) { "Downloaded package version ${manifest.version} does not match requested version $version" }
        requireTargetsIdeaz(manifest.id, manifest.targetApps)
        val compat = azphaltCompatSatisfies(manifest.compat)
        require(compat != null) { "Package ${manifest.id} has invalid Azphalt compat expression ${manifest.compat}" }
        require(compat) { "Package ${manifest.id} requires Azphalt host ${manifest.compat}; IDEaz implements $IDEAZ_AZPHALT_API_VERSION" }
        require(
            listOf(manifest.entry, manifest.runtime, manifest.capabilities, manifest.app, manifest.mcp).all { it == null } &&
                manifest.assets.isNullOrEmpty(),
        ) { "Language model package ${manifest.id} carries blocks an llm package must not" }
        // The signed manifest decides, not the repository's summary of it.
        val llm = requireNotNull(manifest.llm) { "Package ${manifest.id} has no llm block" }
        llm.directUseProblem()?.let { throw IllegalArgumentException("${manifest.name} $it") }
        return AzphaltPreparedLlmInstall(repository.repositoryUrl, detail, version, verification, llm)
    }

    suspend fun installLlm(
        prepared: AzphaltPreparedLlmInstall,
        nowEpochMillis: Long,
        inputValues: Map<String, String> = emptyMap(),
        allowUntrustedSigner: Boolean = false,
        allowPublisherChange: Boolean = false,
    ): InstalledStoreLlm {
        requireNotRevoked(prepared.detail.id, prepared.version)
        val verification = prepared.verification
        require(!verification.publisherChanged || allowPublisherChange) {
            "Publisher key changed for ${prepared.detail.id}; explicit publisher-change approval is required"
        }
        // The baseUrl later receives the user's API key: an unsigned or untrusted manifest must
        // never get there without explicit approval.
        require(verification.trusted || allowUntrustedSigner) {
            if (verification.packageContents.signed) "Signed package publisher is not trusted: ${verification.trustReason}"
            else "Package ${prepared.detail.id} is unsigned; explicit untrusted-install approval is required"
        }
        val manifest = verification.packageContents.manifest
        val endpoint = prepared.endpoint
        val handling = requireNotNull(prepared.llm.dataHandling)
        val installed = InstalledStoreLlm(
            packageId = manifest.id,
            version = manifest.version,
            repositoryUrl = prepared.repositoryUrl,
            name = manifest.name,
            description = manifest.description,
            baseUrl = resolveDirectEndpointUrl(prepared, inputValues),
            defaultModel = endpoint.defaultModel.orEmpty(),
            auth = endpoint.auth,
            keyLabel = prepared.connectionCredentialInput?.description,
            prompts = handling.prompts,
            modelPinned = handling.modelPinned,
            operator = handling.operator,
            terms = handling.terms,
            installedAtEpochMillis = nowEpochMillis,
        )
        llms.put(installed)
        if (verification.packageContents.signerPublicKey != null &&
            (verification.pinnedPublisherKey == null || allowPublisherChange)
        ) {
            verifier.approvePublisher(verification)
        }
        return installed
    }

    fun removeLlm(packageId: String) = llms.remove(packageId)

    /**
     * Checks every installed model against the repository's current revocation list and switches
     * revoked ones off (they stay listed so the user sees why). A version un-revoked later is
     * switched back on. Run at start-up and whenever the store opens; a failed fetch changes
     * nothing. The list is not signed by the store, so a tampered response can hide a revocation
     * but cannot enable anything.
     */
    suspend fun recheckRevocations(): List<InstalledStoreLlm> {
        val revoked = revocations().associateBy { it.id to it.version }
        llms.all().filter { it.repositoryUrl.trimEnd('/') == repository.repositoryUrl.trimEnd('/') }.forEach { llm ->
            val hit = revoked[llm.packageId to llm.version]
            llms.setRevoked(llm.packageId, hit?.let { it.reason?.takeIf(String::isNotBlank) ?: "revoked by the repository" })
        }
        return llms.all().filter { it.revoked }
    }

    private suspend fun requireNotRevoked(packageId: String, version: String) {
        val revocation = revocations().firstOrNull { it.id == packageId && it.version == version }
        require(revocation == null) {
            "Azphalt package $packageId@$version is revoked" +
                (revocation?.reason?.takeIf(String::isNotBlank)?.let { ": $it" } ?: "")
        }
    }

    private fun requireTargetsIdeaz(packageId: String, targetApps: List<String>) {
        require(targetsIdeaz(targetApps)) { "Package $packageId does not target IDEaz" }
    }

    companion object {
        /** Compiled-in keys for [repositoryUrl] (trailing slash ignored); none for unknown repositories. */
        fun trustedKeys(repositoryUrl: String): List<AzphaltSigningKey> =
            AZPHALT_PINNED_SIGNING_KEYS[repositoryUrl.trimEnd('/')].orEmpty()

        /**
         * The endpoint URL: the declared `${input:ID}` value when the package templates it, else the
         * literal baseUrl. Must be HTTPS without userinfo, since the user's key is sent there.
         */
        fun resolveDirectEndpointUrl(prepared: AzphaltPreparedLlmInstall, inputValues: Map<String, String>): String {
            val raw = prepared.endpointUrlInput?.let { input ->
                inputValues[input.id]?.trim().orEmpty().also { value ->
                    require(value.isNotBlank() || input.optional) { "${input.description ?: input.id} is required" }
                }
            }?.takeIf(String::isNotBlank) ?: prepared.endpoint.baseUrl.orEmpty().trim()
            require(raw.startsWith("https://", ignoreCase = true)) { "Language model endpoint must use HTTPS" }
            require('@' !in raw.substring("https://".length).substringBefore('/')) {
                "Language model endpoint URL must not contain embedded credentials"
            }
            return raw.trimEnd('/')
        }
    }
}
