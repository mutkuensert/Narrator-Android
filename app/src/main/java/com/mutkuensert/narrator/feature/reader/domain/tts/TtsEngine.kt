package com.mutkuensert.narrator.feature.reader.domain.tts

import com.mutkuensert.narrator.feature.reader.domain.model.AudioData

interface TtsEngine {
    suspend fun initialize()
    suspend fun synthesize(text: String): AudioData
    fun release()
}

class TtsEngineException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(cause) {
    enum class Reason {
        MODEL_FILES_MISSING,
        INITIALIZATION_FAILED,
        SYNTHESIS_FAILED,
    }
}
