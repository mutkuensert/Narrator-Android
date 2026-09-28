package com.mutkuensert.narrator.feature.reader.domain.repository

import com.mutkuensert.narrator.feature.reader.domain.model.DocumentParagraph

interface TextPreprocessor {
    fun preprocessPage(rawText: String, pageNumber: Int): List<DocumentParagraph>
}
