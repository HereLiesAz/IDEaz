package com.hereliesaz.ideaz.azphalt

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AzphaltRepositoryClientTest {
    private val server = MockWebServer()
    private val store = TestKey()
    private val publisher = TestKey()
    private var revocations = """{"revocations": []}"""
    private var packageBytes: ByteArray = ByteArray(0)

    private val summaryLlm = LLM_BLOCK.trim().removePrefix("\"llm\":").trim()

    @Before
    fun setUp() {
        packageBytes = signedPackage(publisher, voucher = store)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/.well-known/azphalt-repository.json" -> json(
                        """{"name":"Test","version":"0.1","signingKeys":[{"publicKey":"${store.publicKey}","label":"store"}],"extra":1}""",
                    )
                    path.startsWith("/packages?") -> json(
                        """{"packages":[
                          {"id":"example.llm","name":"Example LLM","version":"1.0.0","kind":"llm","targetApps":["com.hereliesaz.aive"],"llm":$summaryLlm},
                          {"id":"sandbox.llm","name":"Sandbox","version":"1.0.0","kind":"llm","llm":{"tier":"sandbox"}},
                          {"id":"other.app","name":"Other","version":"1.0.0","kind":"llm","targetApps":["org.other"],"llm":$summaryLlm}
                        ],"total":3}""",
                    )
                    path == "/packages/example.llm" -> json(
                        """{"id":"example.llm","name":"Example LLM","version":"1.0.0","latest":"1.0.0","kind":"llm","targetApps":["com.hereliesaz.ideaz"]}""",
                    )
                    path == "/packages/example.llm/versions/1.0.0/download" ->
                        MockResponse().setBody(Buffer().write(packageBytes))
                    path == "/packages/paid.llm/versions/1.0.0/download" -> MockResponse().setResponseCode(402)
                    path == "/revocations" -> json(revocations)
                    else -> MockResponse().setResponseCode(404).setBody("not found")
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun client(maxDownload: Int = 1 shl 20) = AzphaltRepositoryClient(
        server.url("/").toString(), OkHttpClient(), maxDownload, 1 shl 20, allowPlainHttp = true,
    )

    @Test
    fun httpRepositoryUrlIsRefusedOutsideTests() {
        assertThrows(IllegalArgumentException::class.java) { AzphaltRepositoryClient("http://azphalt.store") }
    }

    @Test
    fun parsesIndexSearchDetailAndRevocations() = runBlocking {
        val c = client()
        assertEquals(store.publicKey, c.index().signingKeys.single().publicKey)
        val search = c.searchLlms("example")
        // org.other is filtered locally; the sandbox one stays in the raw search result.
        assertEquals(listOf("example.llm", "sandbox.llm"), search.packages.map { it.id })
        val request = server.takeRequest().let { server.takeRequest() }
        assertTrue(request.path!!.contains("kind=llm"))
        assertTrue(request.path!!.contains("q=example"))
        assertEquals("1.0.0", c.detail("example.llm").latest)
        assertTrue(c.revocations().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { c.detail("../etc") } }
        Unit
    }

    @Test
    fun downloadErrorsAndSizeCap() = runBlocking {
        assertThrows(AzphaltRepositoryException.PaymentRequired::class.java) {
            runBlocking { client().download("paid.llm", "1.0.0") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { client(maxDownload = 64).download("example.llm", "1.0.0") }
        }
        assertThrows(AzphaltRepositoryException.Http::class.java) { runBlocking { client().detail("missing") } }
        Unit
    }

    private fun service(kv: MemoryKv = MemoryKv()) = AzphaltLlmStoreService(
        client(),
        AzphaltPackageVerifier(KeyValuePublisherPinStore(kv)),
        InstalledLlmStore(kv),
    )

    @Test
    fun searchKeepsOnlyDirectlyUsableLlms() = runBlocking {
        assertEquals(listOf("example.llm"), service().search("").map { it.id })
    }

    @Test
    fun prepareAndInstallEndToEnd() = runBlocking {
        val kv = MemoryKv()
        val svc = service(kv)
        val prepared = svc.prepareLlmInstall("example.llm")
        assertTrue(prepared.verification.trusted)
        assertEquals("API_KEY", prepared.keyInput?.id)
        val installed = svc.installLlm(prepared, nowEpochMillis = 42)
        assertEquals("https://llm.example.com/v1", installed.baseUrl)
        assertEquals("m-1", installed.defaultModel)
        assertEquals("optional-bearer", installed.auth)
        assertEquals("azphalt:example.llm", installed.providerId)
        assertEquals(listOf(installed), InstalledLlmStore(kv).all())
        assertEquals(publisher.publicKey, KeyValuePublisherPinStore(kv).keyFor("example.llm"))
    }

    @Test
    fun untrustedAndPublisherChangeNeedExplicitApproval() = runBlocking {
        val kv = MemoryKv()
        // Not counter-signed by the store key: genuine but untrusted.
        packageBytes = signedPackage(publisher)
        val svc = service(kv)
        val untrusted = svc.prepareLlmInstall("example.llm")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { svc.installLlm(untrusted, 0) } }
        svc.installLlm(untrusted, 0, allowUntrustedSigner = true)

        packageBytes = signedPackage(TestKey(), voucher = store)
        val changed = svc.prepareLlmInstall("example.llm")
        assertTrue(changed.verification.publisherChanged)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { svc.installLlm(changed, 0) } }
        svc.installLlm(changed, 0, allowPublisherChange = true)
        Unit
    }

    @Test
    fun revokedVersionIsRefused() = runBlocking {
        revocations = """{"revocations":[{"id":"example.llm","version":"1.0.0","reason":"leaked key"}]}"""
        val message = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service().prepareLlmInstall("example.llm") }
        }.message.orEmpty()
        assertTrue(message.contains("revoked: leaked key"))
    }

    @Test
    fun forbiddenBlocksAreRefused() = runBlocking {
        val files = mapOf("LICENSE" to "MIT".toByteArray())
        val manifest = manifestJson(files, extra = """, "mcp": {"command": "x"}""")
        packageBytes = zip(files + ("manifest.json" to manifest) + ("signature.json" to signatureJson(manifest, publisher, store)))
        val message = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service().prepareLlmInstall("example.llm") }
        }.message.orEmpty()
        assertTrue(message.contains("must not"))
    }
}
