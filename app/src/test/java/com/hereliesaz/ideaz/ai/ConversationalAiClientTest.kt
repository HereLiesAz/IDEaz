package com.hereliesaz.ideaz.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
