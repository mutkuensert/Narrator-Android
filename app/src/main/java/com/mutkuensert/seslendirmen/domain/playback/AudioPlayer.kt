package com.mutkuensert.seslendirmen.domain.playback

import com.mutkuensert.seslendirmen.domain.model.AudioData

interface AudioPlayer {
    suspend fun play(audio: AudioData)
    fun stop()
    fun release()
}

class AudioPlaybackException(cause: Throwable? = null) :
    Exception("Ses çıkışı başlatılamadı.", cause)
