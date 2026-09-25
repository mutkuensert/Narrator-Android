package com.mutkuensert.seslendirmen.domain.model

data class LastReadPosition(
    val fileName: String,
    val chunkId: Long,
    val chunkCount: Int,
)
