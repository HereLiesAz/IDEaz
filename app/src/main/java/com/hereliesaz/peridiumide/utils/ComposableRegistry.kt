package com.hereliesaz.peridiumide.utils

import com.hereliesaz.peridiumide.models.ComposableDetails

object ComposableRegistry {
    private val composables = mutableMapOf<String, ComposableDetails>()

    fun register(details: ComposableDetails) {
        composables[details.id] = details
    }

    fun get(id: String): ComposableDetails? {
        return composables[id]
    }

    fun getAll(): List<ComposableDetails> {
        return composables.values.toList()
    }
}
