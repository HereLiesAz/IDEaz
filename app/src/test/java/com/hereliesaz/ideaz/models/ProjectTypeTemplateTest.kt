package com.hereliesaz.ideaz.models

import com.hereliesaz.ideaz.utils.TemplateRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTypeTemplateTest {

    @Test
    fun isWebLike_coversWebPwaReact() {
        assertTrue(ProjectType.WEB.isWebLike())
        assertTrue(ProjectType.PWA.isWebLike())
        assertTrue(ProjectType.REACT.isWebLike())
        assertFalse(ProjectType.ANDROID.isWebLike())
        assertFalse(ProjectType.OTHER.isWebLike())
        assertFalse(ProjectType.UNKNOWN.isWebLike())
    }

    @Test
    fun templateRegistry_mapsKnownTypes() {
        assertEquals("ideaz-android", TemplateRegistry.repoFor(ProjectType.ANDROID))
        assertEquals("ideaz-web", TemplateRegistry.repoFor(ProjectType.WEB))
        assertEquals("ideaz-pwa", TemplateRegistry.repoFor(ProjectType.PWA))
        assertEquals("ideaz-react", TemplateRegistry.repoFor(ProjectType.REACT))
    }

    @Test
    fun templateRegistry_nullForUnregisteredTypes() {
        assertNull(TemplateRegistry.repoFor(ProjectType.OTHER))
        assertNull(TemplateRegistry.repoFor(ProjectType.UNKNOWN))
    }

    @Test
    fun fromString_roundTripsReact() {
        assertEquals(ProjectType.REACT, ProjectType.fromString("REACT"))
    }
}
