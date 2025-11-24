package com.hereliesaz.ideaz.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlSourceInjectorTest {

    @Test
    fun `injects aria-label into tags`() {
        val injector = HtmlSourceInjector()
        val input = listOf(
            "<div>",
            "  <span>Hello</span>",
            "</div>"
        )
        val expected = """
            <div aria-label="__source:test.html:1__">
              <span aria-label="__source:test.html:2__">Hello</span>
            </div>
        """.trimIndent()

        val result = injector.inject(input, "test.html")
        assertEquals(expected, result)
    }

    @Test
    fun `preserves existing attributes`() {
        val injector = HtmlSourceInjector()
        val input = listOf(
            "<div class=\"container\">"
        )
        val expected = "<div class=\"container\" aria-label=\"__source:test.html:1__\">"

        val result = injector.inject(input, "test.html")
        assertEquals(expected, result)
    }

    @Test
    fun `skips closing tags`() {
        val injector = HtmlSourceInjector()
        val input = listOf(
            "</div>"
        )
        val expected = "</div>"

        val result = injector.inject(input, "test.html")
        assertEquals(expected, result)
    }

    @Test
    fun `handles self-closing tags`() {
         val injector = HtmlSourceInjector()
        val input = listOf(
            "<img src=\"img.png\" />"
        )
        // Note: The current implementation might need adjustment for exact whitespace handling,
        // but let's see what it produces.
        // The regex is <([a-zA-Z0-9-]+)([^>]*)>
        // It captures "img" and " src=\"img.png\" /"
        // Logic: if attributes ends with /, insert before it.
        val expected = "<img src=\"img.png\"  aria-label=\"__source:test.html:1__\" />"

        val result = injector.inject(input, "test.html")
        assertEquals(expected, result)
    }
}
