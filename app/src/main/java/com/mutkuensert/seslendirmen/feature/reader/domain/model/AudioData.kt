package com.mutkuensert.seslendirmen.feature.reader.domain.model

data class AudioData(
    val samples: FloatArray,
    val sampleRate: Int,
)
