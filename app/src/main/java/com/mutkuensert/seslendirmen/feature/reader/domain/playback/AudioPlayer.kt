package com.mutkuensert.seslendirmen.feature.reader.domain.playback

import com.mutkuensert.seslendirmen.feature.reader.domain.model.AudioData

interface AudioPlayer {
    suspend fun play(audio: AudioData, onStarted: () -> Unit = {})
    fun pause()
    fun resume()
    fun stop()
    fun release()
}

class AudioPlaybackException(cause: Throwable? = null) : Exception(cause)
