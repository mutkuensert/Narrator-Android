package com.mutkuensert.narrator.feature.reader.domain.playback

interface PlaybackServiceController {
    fun play(fileName: String?)
    fun playFrom(fileName: String?, chunkId: Long)
    fun pause()
    fun previous(fileName: String?)
    fun next(fileName: String?)
    fun stop()
}
