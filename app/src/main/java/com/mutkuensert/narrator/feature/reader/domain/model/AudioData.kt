package com.mutkuensert.narrator.feature.reader.domain.model

data class AudioData(
    val samples: FloatArray,
    val sampleRate: Int,
)
