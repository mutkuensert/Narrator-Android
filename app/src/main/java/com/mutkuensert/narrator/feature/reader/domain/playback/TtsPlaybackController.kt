package com.mutkuensert.narrator.feature.reader.domain.playback

import com.mutkuensert.narrator.feature.reader.domain.model.SpeechChunk
import kotlinx.coroutines.flow.StateFlow

interface TtsPlaybackController {
    val state: StateFlow<PlaybackState>
    fun load(chunks: List<SpeechChunk>, initialChunkId: Long? = null)
    fun play()
    fun pause()
    fun stop()
    fun next()
    fun previous()
    fun playFrom(chunkId: Long)
    fun release()
}
