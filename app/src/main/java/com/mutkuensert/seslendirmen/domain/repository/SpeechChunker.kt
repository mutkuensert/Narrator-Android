package com.mutkuensert.seslendirmen.domain.repository

import com.mutkuensert.seslendirmen.domain.model.Document
import com.mutkuensert.seslendirmen.domain.model.SpeechChunk

interface SpeechChunker {
    fun createChunks(document: Document): List<SpeechChunk>
}
