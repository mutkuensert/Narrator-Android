package com.mutkuensert.seslendirmen.data.pdf

import com.mutkuensert.seslendirmen.domain.model.PdfParagraph
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
        assertTrue(OcrFallbackPolicy.shouldRecognize(listOf(PdfParagraph("- 1 -"))))
    }

    @Test
    fun `normal extracted text bypasses OCR`() {
        assertFalse(OcrFallbackPolicy.shouldRecognize(listOf(PdfParagraph("Merhaba dünya"))))
    }
}
