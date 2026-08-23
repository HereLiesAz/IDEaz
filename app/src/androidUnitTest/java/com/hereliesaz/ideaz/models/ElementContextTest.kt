// app/src/test/java/com/hereliesaz/ideaz/models/ElementContextTest.kt
package com.hereliesaz.ideaz.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementContextTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `round-trip serialization preserves all fields`() {
        val original = ElementContext(
            tagName = "button",
            id = "submit-btn",
            className = "btn btn-primary",
            selector = "form > button#submit-btn",
            outerHtml = "<button id=\"submit-btn\" class=\"btn\">Submit</button>",
            innerText = "Submit",
            boundingRect = BoundingRect(top = 100.0, left = 50.0, bottom = 132.0, right = 150.0, width = 100.0, height = 32.0),
            computedStyles = mapOf("color" to "rgb(255,255,255)", "backgroundColor" to "rgb(0,0,255)"),
            parents = listOf(ParentInfo(tagName = "form", id = "main-form", className = ""))
        )

        val serialized = json.encodeToString(ElementContext.serializer(), original)
        val deserialized = json.decodeFromString(ElementContext.serializer(), serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `defaults produce valid empty context`() {
        val ctx = ElementContext(tagName = "div")
        assertEquals("", ctx.id)
        assertEquals("", ctx.className)
        assertEquals("", ctx.selector)
        assertEquals("", ctx.outerHtml)
        assertEquals("", ctx.innerText)
        assertTrue(ctx.computedStyles.isEmpty())
        assertTrue(ctx.parents.isEmpty())
        assertEquals(0.0, ctx.boundingRect.top, 0.001)
    }

    @Test
    fun `deserializes JS-generated JSON with missing optional fields`() {
        // computedStyles and parents intentionally omitted — must deserialize to defaults
        val jsJson = """
            {
                "tagName": "span",
                "id": "",
                "className": "label",
                "selector": "div > span",
                "outerHtml": "<span class=\"label\">Hello</span>",
                "innerText": "Hello",
                "boundingRect": {"top": 10.5, "left": 5.0, "bottom": 20.5, "right": 55.0, "width": 50.0, "height": 10.0}
            }
        """.trimIndent()

        val ctx = json.decodeFromString(ElementContext.serializer(), jsJson)
        assertEquals("span", ctx.tagName)
        assertEquals("label", ctx.className)
        assertEquals(10.5, ctx.boundingRect.top, 0.001)
        assertTrue(ctx.computedStyles.isEmpty())   // omitted field → default
        assertTrue(ctx.parents.isEmpty())           // omitted field → default
    }
}
