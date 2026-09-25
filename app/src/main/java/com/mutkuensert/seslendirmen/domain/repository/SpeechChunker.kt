package com.mutkuensert.seslendirmen.domain.repository

import com.mutkuensert.seslendirmen.domain.model.PdfDocument
import com.mutkuensert.seslendirmen.domain.model.SpeechChunk

interface SpeechChunker {
    fun createChunks(document: PdfDocument): List<SpeechChunk>
}
