package com.hereliesaz.ideaz.ai.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelDownloadManagerHintTest {

    @Test
    fun huggingFaceRepoPageUrl_extractsRepoFromResolveUrl() {
        assertEquals(
            "https://huggingface.co/litert-community/Gemma2-2B-IT",
            huggingFaceRepoPageUrl(
                "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/" +
                    "Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task"
            ),
        )
    }

    @Test
    fun huggingFaceRepoPageUrl_nullForNonHuggingFaceUrl() {
        assertNull(huggingFaceRepoPageUrl("https://ai.google.dev/gemma/terms"))
    }

    @Test
    fun huggingFaceRepoPageUrl_nullWhenUrlHasNoResolveSegment() {
        assertNull(huggingFaceRepoPageUrl("https://huggingface.co/litert-community/Gemma2-2B-IT"))
    }
}
