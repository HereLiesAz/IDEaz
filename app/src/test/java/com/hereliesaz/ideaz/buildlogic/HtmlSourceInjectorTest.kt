package com.hereliesaz.ideaz.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlSourceInjectorTest {

    private val injector = HtmlSourceInjector()

    @Test
    fun testInject_simpleDiv() {
        val input = listOf("<div>Hello</div>")
        val output = injector.inject(input, "index.html")

        // Expect line 1
        val expected = "<div aria-label=\"__source:index.html:1__\">Hello</div>"
        assertEquals(expected, output)
    }

    @Test
    fun testInject_withAttributes() {
        val input = listOf("<div class=\"container\">")
        val output = injector.inject(input, "index.html")

        val expected = "<div class=\"container\" aria-label=\"__source:index.html:1__\">"
        assertEquals(expected, output)
    }

    @Test
    fun testInject_skipExistingAriaLabel() {
        val input = listOf("<button aria-label=\"Close\">")
        val output = injector.inject(input, "index.html")

        // Should remain unchanged
        assertEquals(input[0], output)
    }

    @Test
    fun testInject_selfClosing() {
        val input = listOf("<img src=\"foo.png\" />")
        val output = injector.inject(input, "index.html")

        val expected = "<img src=\"foo.png\"  aria-label=\"__source:index.html:1__\" />"
        assertEquals(expected, output)
    }

    @Test
    fun testInject_multipleLines() {
        val input = listOf(
            "<html>",
            "<body>",
            "  <h1>Title</h1>",
            "</body>",
            "</html>"
        )
        val output = injector.inject(input, "index.html")

        val expected = """
            <html aria-label="__source:index.html:1__">
            <body aria-label="__source:index.html:2__">
              <h1 aria-label="__source:index.html:3__">Title</h1>
            </body>
            </html>
        """.trimIndent()

        // Note: closing tags </body> </html> are not touched because regex requires <[a-z].
        // </body> starts with </

        assertEquals(expected, output)
    }
}
