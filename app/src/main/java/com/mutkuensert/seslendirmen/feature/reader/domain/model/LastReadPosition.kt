package com.mutkuensert.seslendirmen.feature.reader.domain.model

data class LastReadPosition(
    val fileName: String,
    val chunkId: Long,
    val chunkCount: Int,
)
