package com.mutkuensert.seslendirmen.domain.playback

import com.mutkuensert.seslendirmen.domain.model.SpeechChunk

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Preparing(val chunk: SpeechChunk) : PlaybackState
    data class Playing(val chunk: SpeechChunk) : PlaybackState
    data class Paused(val chunk: SpeechChunk) : PlaybackState
    data class Error(val error: PlaybackError) : PlaybackState
}

data class PlaybackError(
    val message: String,
    val cause: Throwable? = null,
)
