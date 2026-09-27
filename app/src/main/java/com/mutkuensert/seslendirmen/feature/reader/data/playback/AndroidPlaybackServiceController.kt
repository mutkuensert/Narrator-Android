package com.mutkuensert.seslendirmen.feature.reader.data.playback

import android.content.Context
import com.mutkuensert.seslendirmen.feature.reader.domain.playback.PlaybackServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPlaybackServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PlaybackServiceController {
    override fun play(fileName: String?) {
        TtsPlaybackService.play(context, fileName)
    }

    override fun playFrom(fileName: String?, chunkId: Long) {
        TtsPlaybackService.playFrom(context, fileName, chunkId)
    }

    override fun pause() {
        TtsPlaybackService.pause(context)
    }

    override fun previous(fileName: String?) {
        TtsPlaybackService.previous(context, fileName)
    }

    override fun next(fileName: String?) {
        TtsPlaybackService.next(context, fileName)
    }

    override fun stop() {
        TtsPlaybackService.stop(context)
    }
}
