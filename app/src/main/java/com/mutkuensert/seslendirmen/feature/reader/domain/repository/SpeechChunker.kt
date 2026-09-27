package com.mutkuensert.seslendirmen.feature.reader.domain.repository

import com.mutkuensert.seslendirmen.feature.reader.domain.model.Document
import com.mutkuensert.seslendirmen.feature.reader.domain.model.SpeechChunk

interface SpeechChunker {
    fun createChunks(document: Document): List<SpeechChunk>
}
