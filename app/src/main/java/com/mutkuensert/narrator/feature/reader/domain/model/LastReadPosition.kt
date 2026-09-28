package com.mutkuensert.narrator.feature.reader.domain.model

data class LastReadPosition(
    val fileName: String,
    val chunkId: Long,
    val chunkCount: Int,
)
