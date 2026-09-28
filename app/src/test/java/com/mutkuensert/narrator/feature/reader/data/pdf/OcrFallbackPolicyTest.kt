package com.mutkuensert.narrator.feature.reader.data.pdf

import com.mutkuensert.narrator.feature.reader.domain.model.DocumentParagraph
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrFallbackPolicyTest {
    @Test
    fun `empty extracted page uses OCR`() {
        assertTrue(OcrFallbackPolicy.shouldRecognize(emptyList()))
    }

    @Test
    fun `formatting noise uses OCR`() {
        assertTrue(OcrFallbackPolicy.shouldRecognize(listOf(DocumentParagraph("- 1 -"))))
    }

    @Test
    fun `normal extracted text bypasses OCR`() {
        assertFalse(OcrFallbackPolicy.shouldRecognize(listOf(DocumentParagraph("Merhaba dünya"))))
    }
}
