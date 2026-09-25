package com.mutkuensert.seslendirmen.domain.playback

import com.mutkuensert.seslendirmen.domain.model.SpeechChunk
import kotlinx.coroutines.flow.StateFlow

interface TtsPlaybackController {
    val state: StateFlow<PlaybackState>
    fun load(chunks: List<SpeechChunk>)
    fun play()
    fun pause()
    fun stop()
    fun next()
    fun previous()
    fun playFrom(chunkId: Long)
    fun release()
}
