package com.hereliesaz.ideaz.ai

import com.hereliesaz.ideaz.ai.local.LocalModelReply
import com.hereliesaz.ideaz.ai.local.LocalProviderDiagnostics
import com.hereliesaz.ideaz.ai.local.LocalProviderFailure
import com.hereliesaz.ideaz.ai.local.LocalProviderFailureKind
import com.hereliesaz.ideaz.ai.local.LocalRecoveryAction
import com.hereliesaz.ideaz.ai.local.LocalToolProtocol
import com.hereliesaz.ideaz.ai.local.LocalToolRoundLimitExceeded
import com.hereliesaz.ideaz.ai.local.boundedLocalPrompt
import com.hereliesaz.ideaz.ai.local.localInferenceLimits
import com.hereliesaz.ideaz.ai.local.runBoundedLocalToolLoop
import com.hereliesaz.ideaz.ai.local.createLocalCloudConsultRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ConversationalAiClientTest {

    @Test
    fun `ChatMessage stores role and content`() {
        val msg = ChatMessage(role = "user", content = "hello")
        assertEquals("user", msg.role)
        assertEquals("hello", msg.content)
    }

    @Test
    fun `anonymous implementation satisfies ConversationalAiClient interface`() = runTest {
        val fake = object : ConversationalAiClient {
            override suspend fun chat(messages: List<ChatMessage>): String = "pong"
        }
        val result = fake.chat(listOf(ChatMessage("user", "ping")))
        assertEquals("pong", result)
    }

    @Test
    fun `local protocol parses fenced tool call arguments`() {
        val reply = LocalToolProtocol.parse(
            """```json
                {"type":"tool","name":"read_file","arguments":{"path":"src/App.jsx"}}
                ```""".trimIndent()
        )

        assertEquals(
            LocalModelReply.ToolCall("read_file", mapOf("path" to "src/App.jsx")),
            reply,
        )
    }

    @Test
    fun `local protocol parses final response`() {
        val reply = LocalToolProtocol.parse(
            """{"type":"final","content":"Updated the button."}"""
        )

        assertEquals(LocalModelReply.Final("Updated the button."), reply)
    }

    @Test
    fun `local protocol preserves malformed response as chat text`() {
        val reply = LocalToolProtocol.parse("I cannot use a tool, but I can still answer.")

        assertTrue(reply is LocalModelReply.PlainText)
        assertEquals(
            "I cannot use a tool, but I can still answer.",
            (reply as LocalModelReply.PlainText).content,
        )
    }

    @Test
    fun `local protocol does not crash on wrong JSON shapes`() {
        val raw = """{"type":{"nested":true},"arguments":[]}"""

        assertEquals(LocalModelReply.PlainText(raw), LocalToolProtocol.parse(raw))
    }

    @Test
    fun `local protocol advertises cloud consultation without polluting shared IDE tools`() {
        assertTrue(LocalToolProtocol.instruction.contains("ask_cloud"))
        assertFalse(IdeToolSchema.all.any { it.name == "ask_cloud" })
    }

    @Test
    fun `cloud consultation consent binds exact payload project and conversation`() {
        val messages = listOf(ChatMessage("user", "Why is reload broken?"))
        val request = createLocalCloudConsultRequest(
            projectPath = "/projects/pwa",
            messages = messages,
            question = "Review this service-worker theory",
            context = "The fetch handler returns a cached shell.",
        )

        assertTrue(request.matches(request.requestId, "/projects/pwa", messages))
        assertFalse(request.matches("stale-id", "/projects/pwa", messages))
        assertFalse(request.matches(request.requestId, "/projects/other", messages))
        assertFalse(request.matches(request.requestId, "/projects/pwa", messages + ChatMessage("model", "new")))
        assertFalse(request.matches(request.requestId, "/projects/pwa", listOf(ChatMessage("user", "same count, different text"))))
        assertFalse(request.copy(prompt = "tampered").matches(request.requestId, "/projects/pwa", messages))
        assertFalse(request.copy(model = "different-model").matches(request.requestId, "/projects/pwa", messages))
        assertEquals("Google Gemini", request.provider)
        assertEquals("gemini-2.0-flash", request.model)
        val expectedPrompt = """You are a read-only coding consultant. Give concise technical advice.
You cannot inspect other files, call tools, or change the project.
Question:
Review this service-worker theory
Context explicitly approved by the user:
The fetch handler returns a cached shell."""
        assertEquals(expectedPrompt, request.prompt)
        assertEquals(
            expectedPrompt.toByteArray(Charsets.UTF_8).size,
            request.byteCount,
        )
    }

    @Test
    fun `cloud consultation rejects missing and oversized model payloads`() {
        val messages = listOf(ChatMessage("user", "help"))

        assertThrows(IllegalArgumentException::class.java) {
            createLocalCloudConsultRequest("/project", messages, "", "context")
        }
        assertThrows(IllegalArgumentException::class.java) {
            createLocalCloudConsultRequest("/project", messages, "q".repeat(2_001), "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            createLocalCloudConsultRequest("/project", messages, "question", "c".repeat(6_001))
        }
    }

    @Test
    fun `cloud consent rejects same-size changed attachment content`() {
        val original = listOf(
            ChatMessage("user", listOf(ChatPart.Image(byteArrayOf(1, 2, 3), "image/png")))
        )
        val changed = listOf(
            ChatMessage("user", listOf(ChatPart.Image(byteArrayOf(3, 2, 1), "image/png")))
        )
        val request = createLocalCloudConsultRequest("/project", original, "question", "context")

        assertFalse(request.matches(request.requestId, "/project", changed))
    }

    @Test
    fun `local prompt keeps instructions suffix and newest transcript`() {
        assertEquals(
            "RULES:89:END",
            boundedLocalPrompt("RULES:", "0123456789", ":END", 12),
        )
        assertEquals("PyS", boundedLocalPrompt("P", "x😀y", "S", 4))
    }

    @Test
    fun `local inference limits step up at independently specified RAM boundaries`() {
        assertEquals(256, localInferenceLimits(3_999_999_999L).maxOutputTokens)
        assertEquals(256, localInferenceLimits(0L).maxOutputTokens)
        assertEquals(512, localInferenceLimits(4_000_000_000L).maxOutputTokens)
        assertEquals(768, localInferenceLimits(8_000_000_000L).maxOutputTokens)
        assertEquals(8_192, localInferenceLimits(2_000_000_000L).maxPromptChars)
    }

    @Test
    fun `local provider failure exposes retry policy and opaque diagnostic`() {
        val failure = LocalProviderFailure(
            kind = LocalProviderFailureKind.GENERATION_FAILED,
            message = "On-device generation failed.",
            retryable = true,
            cloudFallbackAllowed = true,
            diagnosticId = "L0000002a",
        )

        assertEquals(LocalProviderFailureKind.GENERATION_FAILED, failure.kind)
        assertTrue(failure.retryable)
        assertTrue(failure.cloudFallbackAllowed)
        assertEquals(
            "On-device generation failed. Retry is available. Diagnostic: L0000002a.",
            failure.displayText(),
        )
    }

    @Test
    fun `local diagnostics retain bounded metadata without cause messages`() {
        LocalProviderDiagnostics.clearForTests()
        val ids = (1..33).map {
            LocalProviderDiagnostics.record(
                kind = LocalProviderFailureKind.GENERATION_FAILED,
                modelId = "model",
                runtimeId = "runtime",
                cause = IllegalStateException("private prompt text"),
            )
        }

        assertEquals(null, LocalProviderDiagnostics.find(ids.first()))
        val newest = LocalProviderDiagnostics.find(ids.last())!!
        assertEquals("IllegalStateException", newest.causeType)
        assertFalse(newest.toString().contains("private prompt text"))
    }

    @Test
    fun `local recovery requires matching failure explicit policy and cloud credential`() {
        val failure = LocalProviderFailure(
            kind = LocalProviderFailureKind.GENERATION_FAILED,
            message = "failed",
            retryable = true,
            cloudFallbackAllowed = true,
            diagnosticId = "L0000002a",
        )

        assertTrue(failure.permits(LocalRecoveryAction.RETRY_LOCAL, "L0000002a", cloudConfigured = false))
        assertFalse(failure.permits(LocalRecoveryAction.RETRY_LOCAL, "stale", cloudConfigured = true))
        assertFalse(failure.permits(LocalRecoveryAction.CLOUD_ONCE, "L0000002a", cloudConfigured = false))
        assertTrue(failure.permits(LocalRecoveryAction.CLOUD_ONCE, "L0000002a", cloudConfigured = true))
        assertFalse(
            failure.copy(cloudFallbackAllowed = false)
                .permits(LocalRecoveryAction.CLOUD_ONCE, "L0000002a", cloudConfigured = true)
        )
    }

    @Test
    fun `local tool loop dispatches exactly six calls before limit`() = runTest {
        var generations = 0
        var dispatches = 0

        val failure = try {
            runBoundedLocalToolLoop(
                transcript = StringBuilder("User: edit\n"),
                buildPrompt = { it },
                generate = {
                    generations++
                    """{"type":"tool","name":"read_file","arguments":{"path":"index.html"}}"""
                },
                dispatch = {
                    dispatches++
                    "contents"
                },
                onCancellation = {},
            )
            null
        } catch (e: LocalToolRoundLimitExceeded) {
            e
        }

        assertEquals(6, failure?.rounds)
        assertEquals(6, generations)
        assertEquals(6, dispatches)
    }

    @Test
    fun `local tool loop propagates cancellation and invokes restoration boundary`() = runTest {
        var restorationCalls = 0
        val generationStarted = CompletableDeferred<Unit>()
        var cancellationObserved = false
        val job = launch {
            try {
                runBoundedLocalToolLoop(
                    transcript = StringBuilder(),
                    buildPrompt = { it },
                    generate = {
                        generationStarted.complete(Unit)
                        awaitCancellation()
                    },
                    dispatch = { "unused" },
                    onCancellation = {
                        yield()
                        restorationCalls++
                    },
                )
            } catch (e: CancellationException) {
                cancellationObserved = true
                throw e
            }
        }
        generationStarted.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(cancellationObserved)
        assertEquals(1, restorationCalls)
    }
}
