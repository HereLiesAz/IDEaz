package com.hereliesaz.ideaz.buildlogic

import org.junit.Test

class ClassFinderTest {
    @Test
    fun findClasses() {
        val candidates = listOf(
            "org.eclipse.aether.internal.impl.DefaultSyncContextFactory",
            "org.eclipse.aether.internal.impl.synccontext.DefaultSyncContextFactory",
            "org.eclipse.aether.impl.DefaultSyncContextFactory",
            "org.eclipse.aether.internal.impl.DefaultVersionResolver",
            "org.eclipse.aether.internal.impl.DefaultVersionRangeResolver",
            "org.eclipse.aether.internal.impl.DefaultArtifactDescriptorReader"
        )

        candidates.forEach { className ->
            try {
                Class.forName(className)
                println("FOUND: $className")
            } catch (e: ClassNotFoundException) {
                println("MISSING: $className")
            }
        }
    }
}
