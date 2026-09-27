package com.mutkuensert.seslendirmen.feature.reader.domain.playback

import com.mutkuensert.seslendirmen.feature.reader.domain.model.SpeechChunk

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Preparing(val chunk: SpeechChunk) : PlaybackState
    data class Playing(val chunk: SpeechChunk) : PlaybackState
    data class Paused(val chunk: SpeechChunk) : PlaybackState
    data class Error(val error: PlaybackError) : PlaybackState
}

data class PlaybackError(
    val reason: Reason,
    val cause: Throwable? = null,
) {
    enum class Reason {
        MODEL_FILES_MISSING,
        INITIALIZATION_FAILED,
        SYNTHESIS_FAILED,
        AUDIO_OUTPUT_FAILED,
        UNKNOWN,
    }
}
