package com.hereliesaz.ideaz.ai

import com.hereliesaz.ideaz.ai.local.LocalModelReply
import com.hereliesaz.ideaz.ai.local.LocalProviderDiagnostics
import com.hereliesaz.ideaz.ai.local.LocalProviderFailure
import com.hereliesaz.ideaz.ai.local.LocalProviderFailureKind
import com.hereliesaz.ideaz.ai.local.LocalRecoveryAction
import com.hereliesaz.ideaz.ai.local.LocalToolProtocol
import com.hereliesaz.ideaz.ai.local.boundedLocalPrompt
import com.hereliesaz.ideaz.ai.local.localInferenceLimits
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
