package com.mutkuensert.seslendirmen.domain.repository

import com.mutkuensert.seslendirmen.domain.model.DocumentParagraph

interface TextPreprocessor {
    fun preprocessPage(rawText: String, pageNumber: Int): List<DocumentParagraph>
}
