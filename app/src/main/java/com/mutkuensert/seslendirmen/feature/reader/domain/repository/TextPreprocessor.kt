package com.mutkuensert.seslendirmen.feature.reader.domain.repository

import com.mutkuensert.seslendirmen.feature.reader.domain.model.DocumentParagraph

interface TextPreprocessor {
    fun preprocessPage(rawText: String, pageNumber: Int): List<DocumentParagraph>
}
