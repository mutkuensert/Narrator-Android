package com.mutkuensert.seslendirmen.domain.playback

import com.mutkuensert.seslendirmen.domain.model.AudioData

interface AudioPlayer {
    suspend fun play(audio: AudioData, onStarted: () -> Unit = {})
    fun pause()
    fun resume()
    fun stop()
    fun release()
}

class AudioPlaybackException(cause: Throwable? = null) :
    Exception("Ses çıkışı başlatılamadı.", cause)
