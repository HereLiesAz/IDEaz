package com.hereliesaz.ideaz.ai

import com.hereliesaz.ideaz.ai.local.LocalModelReply
import com.hereliesaz.ideaz.ai.local.LocalToolProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
