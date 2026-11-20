package com.hereliesaz.ideaz.azphalt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AzphaltLlmRulesTest {
    private val handling = AzphaltLlmDataHandling(prompts = "logged", operator = "Op")
    private val key = AzphaltLlmInput(id = "KEY", password = true)

    private fun llm(
        tier: String = "endpoint",
        protocols: List<String> = listOf("openai-chat"),
        baseUrl: String? = "https://x.example/v1",
        defaultModel: String? = "m",
        auth: String = "none",
        authInput: String? = null,
        inputs: List<AzphaltLlmInput> = emptyList(),
        dataHandling: AzphaltLlmDataHandling? = handling,
    ) = AzphaltLlm(tier, inputs, AzphaltLlmEndpoint(protocols, baseUrl, defaultModel, auth, authInput), dataHandling)

    @Test
    fun directUseProblemCases() {
        assertNull(llm().directUseProblem())
        assertNull(llm(auth = "required-bearer", authInput = "KEY", inputs = listOf(key)).directUseProblem())
        assertEquals("declares no endpoint", AzphaltLlm(tier = "endpoint", dataHandling = handling).directUseProblem())
        assertTrue(llm(tier = "sandbox").directUseProblem()!!.contains("sandbox tier"))
        assertTrue(llm(protocols = listOf("moyai-session")).directUseProblem()!!.contains("openai-chat"))
        assertTrue(llm(baseUrl = "http://x.example").directUseProblem()!!.contains("https"))
        assertTrue(llm(defaultModel = " ").directUseProblem()!!.contains("default model"))
        assertTrue(llm(auth = "oauth").directUseProblem()!!.contains("unknown auth"))
        assertTrue(llm(auth = "optional-bearer", authInput = "MISSING").directUseProblem()!!.contains("undeclared key input"))
        assertTrue(llm(dataHandling = null).directUseProblem()!!.contains("prompts"))
    }

    @Test
    fun templatedBaseUrlNeedsADeclaredNonPasswordInput() {
        val urlInput = AzphaltLlmInput(id = "URL", description = "Server URL")
        assertNull(llm(baseUrl = "\${input:URL}", inputs = listOf(urlInput)).directUseProblem())
        assertEquals(urlInput, llm(baseUrl = "\${input:URL}", inputs = listOf(urlInput)).endpointUrlInput())
        // A password input must never become the URL.
        val secret = AzphaltLlmInput(id = "URL", password = true)
        assertNull(llm(baseUrl = "\${input:URL}", inputs = listOf(secret)).endpointUrlInput())
        assertTrue(llm(baseUrl = "\${input:URL}", inputs = listOf(secret)).directUseProblem()!!.contains("https"))
        assertTrue(llm(baseUrl = "\${input:OTHER}", inputs = listOf(urlInput)).directUseProblem()!!.contains("https"))
    }

    private fun prepared(l: AzphaltLlm): AzphaltPreparedLlmInstall {
        val files = mapOf("LICENSE" to "MIT".toByteArray())
        val verification = AzphaltPackageVerifier(KeyValuePublisherPinStore(MemoryKv()))
            .verify(zip(files + ("manifest.json" to manifestJson(files))))
        return AzphaltPreparedLlmInstall(
            "https://azphalt.store",
            AzphaltPackageDetail(id = "example.llm", name = "Example", version = "1.0.0", kind = "llm"),
            "1.0.0",
            verification,
            l,
        )
    }

    @Test
    fun endpointUrlResolution() {
        val resolve = AzphaltLlmStoreService.Companion::resolveDirectEndpointUrl
        assertEquals("https://x.example/v1", resolve(prepared(llm(baseUrl = "https://x.example/v1/")), emptyMap()))

        val urlInput = AzphaltLlmInput(id = "URL", description = "Server URL")
        val templated = prepared(llm(baseUrl = "\${input:URL}", inputs = listOf(urlInput)))
        assertEquals("https://mine.example", resolve(templated, mapOf("URL" to " https://mine.example/ ")))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { resolve(templated, emptyMap()) }.message!!.contains("Server URL is required"))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { resolve(templated, mapOf("URL" to "http://mine.example")) }.message!!.contains("HTTPS"))
        assertTrue(
            assertThrows(IllegalArgumentException::class.java) { resolve(templated, mapOf("URL" to "https://user:pw@mine.example/v1")) }
                .message!!.contains("embedded credentials"),
        )
    }

    @Test
    fun compatGrammar() {
        assertEquals(true, azphaltCompatSatisfies(">=0.1"))
        assertEquals(true, azphaltCompatSatisfies("0.1.0"))
        assertEquals(false, azphaltCompatSatisfies(">=1"))
        assertEquals(false, azphaltCompatSatisfies("<0.1"))
        assertNull(azphaltCompatSatisfies("^0.1"))
        assertNull(azphaltCompatSatisfies(">=0.1 <1"))
    }

    @Test
    fun downgradeIsRejected() {
        requireNotDowngrade("p", null, "1.0.0")
        requireNotDowngrade("p", "1.0.0", "1.0.1")
        requireNotDowngrade("p", "1.0.0-rc1", "1.0.0")
        assertThrows(IllegalArgumentException::class.java) { requireNotDowngrade("p", "1.2.0", "1.1.9") }
        assertThrows(IllegalArgumentException::class.java) { requireNotDowngrade("p", "1.0.0", "1.0.0-rc1") }
    }

    @Test
    fun targetApps() {
        assertTrue(targetsIdeaz(emptyList()))
        assertTrue(targetsIdeaz(listOf("com.hereliesaz.ideaz")))
        assertTrue(targetsIdeaz(listOf("com.hereliesaz.aive")))
        assertFalse(targetsIdeaz(listOf("org.other")))
    }

    @Test
    fun installedLlmStorePersistsAndToleratesCorruption() {
        val kv = MemoryKv()
        val store = InstalledLlmStore(kv)
        val llm = InstalledStoreLlm("p", "1", "https://r", "P", baseUrl = "https://x", defaultModel = "m", auth = "none")
        store.put(llm)
        assertEquals(listOf(llm), InstalledLlmStore(kv).all())
        assertEquals("azphalt:p", llm.providerId)
        store.remove("p")
        assertTrue(InstalledLlmStore(kv).all().isEmpty())
        kv.values[InstalledLlmStore.STORAGE_KEY] = "{not json"
        assertTrue(InstalledLlmStore(kv).all().isEmpty())
    }
}
