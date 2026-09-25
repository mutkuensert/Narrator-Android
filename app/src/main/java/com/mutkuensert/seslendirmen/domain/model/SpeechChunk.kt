package com.mutkuensert.seslendirmen.domain.model

data class SpeechChunk(
    val id: Long,
    val pageNumber: Int,
    val paragraphIndex: Int,
    val text: String,
)
