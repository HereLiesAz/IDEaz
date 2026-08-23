package com.hereliesaz.ideaz.api

import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*

class PlanStepTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testPlanStepDeserializationOptionalIndex() {
        val jsonString = """
            {
                "id": "step-1",
                "title": "Step 1",
                "description": "Do something"
            }
        """
        val planStep = json.decodeFromString<PlanStep>(jsonString)
        assertEquals("step-1", planStep.id)
        assertNull(planStep.index)
    }
}
