package com.mutkuensert.seslendirmen.feature.reader.domain.model

data class SpeechChunk(
    val id: Long,
    val sectionIndex: Int,
    val paragraphIndex: Int,
    val text: String,
)
