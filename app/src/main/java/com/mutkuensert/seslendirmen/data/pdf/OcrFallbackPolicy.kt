package com.mutkuensert.seslendirmen.data.pdf

import com.mutkuensert.seslendirmen.domain.model.DocumentParagraph

internal object OcrFallbackPolicy {
    fun shouldRecognize(paragraphs: List<DocumentParagraph>): Boolean {
        val meaningfulCharacterCount = paragraphs.sumOf { paragraph ->
            paragraph.text.count(Char::isLetterOrDigit)
        }
        return meaningfulCharacterCount < MIN_MEANINGFUL_CHARACTERS
    }

    private const val MIN_MEANINGFUL_CHARACTERS = 3
}
