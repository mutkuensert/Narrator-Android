package com.mutkuensert.seslendirmen.feature.reader.domain.repository

import com.mutkuensert.seslendirmen.feature.reader.domain.model.Document
import com.mutkuensert.seslendirmen.feature.reader.domain.model.SpeechChunk
import com.mutkuensert.seslendirmen.feature.reader.domain.model.TtsLanguage

interface SpeechChunker {
    fun createChunks(document: Document, language: TtsLanguage): List<SpeechChunk>
}
