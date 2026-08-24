package com.hereliesaz.ideaz.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextualChatOverlayTest {

    @Test
    fun sourceLabel_capturesFileNameAndLineNumber() {
        // Regression: a lazy "any chars" quantifier immediately followed by
        // an optional lineNumber group meant the group could never actually
        // capture - the engine always satisfied it by matching zero
        // occurrences before ever being pressured to search forward.
        val element = """{"tagName":"BUTTON","source":{"fileName":"/src/App.jsx","lineNumber":42,"columnNumber":5}}"""
        assertEquals("App.jsx:42", sourceLabel(element))
    }

    @Test
    fun sourceLabel_lineNumberBeforeFileNameInKeyOrder() {
        // Key order in the bridge payload isn't guaranteed - the fix must
        // not depend on fileName appearing before lineNumber.
        val element = """{"source":{"lineNumber":7,"fileName":"src/util.ts"}}"""
        assertEquals("util.ts:7", sourceLabel(element))
    }

    @Test
    fun sourceLabel_fileNameOnlyOmitsTheColon() {
        val element = """{"source":{"fileName":"/src/App.jsx"}}"""
        assertEquals("App.jsx", sourceLabel(element))
    }

    @Test
    fun sourceLabel_nullWhenNoSourceBlock() {
        assertNull(sourceLabel("""{"tagName":"DIV"}"""))
        assertNull(sourceLabel(null))
        assertNull(sourceLabel(""))
    }

    @Test
    fun elementLabel_usesTagNameWhenPresent() {
        assertEquals("Selected <BUTTON>", elementLabel("""{"tagName":"BUTTON"}"""))
    }

    @Test
    fun elementLabel_fallsBackWhenNoTagName() {
        assertEquals("Selected element", elementLabel("""{}"""))
        assertEquals("Selected element", elementLabel(null))
    }
}
