package com.mutkuensert.seslendirmen.data.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.mutkuensert.seslendirmen.domain.model.AudioData
import com.mutkuensert.seslendirmen.domain.playback.AudioPlaybackException
import com.mutkuensert.seslendirmen.domain.playback.AudioPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.max

@Singleton
class AudioTrackPlayer @Inject constructor() : AudioPlayer {
    private val lock = Any()
    @Volatile private var activeTrack: AudioTrack? = null

    override suspend fun play(audio: AudioData) = withContext(Dispatchers.IO) {
        stop()
        val minimumBufferSize = AudioTrack.getMinBufferSize(
            audio.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minimumBufferSize <= 0) throw AudioPlaybackException()

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(audio.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(max(minimumBufferSize, 16 * 1024))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (error: Throwable) {
            throw AudioPlaybackException(error)
        }

        synchronized(lock) { activeTrack = track }
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED)
            track.play()
            var offset = 0
            while (offset < audio.samples.size) {
                coroutineContext.ensureActive()
                val written = track.write(
                    audio.samples,
                    offset,
                    minOf(4096, audio.samples.size - offset),
                    AudioTrack.WRITE_BLOCKING,
                )
                if (written < 0) error("AudioTrack write failed: $written")
                offset += written
            }
            while (track.playbackHeadPosition < audio.samples.size) {
                coroutineContext.ensureActive()
                delay(20)
            }
            track.stop()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Log.e(TAG, "Audio playback failed", error)
            throw AudioPlaybackException(error)
        } finally {
            synchronized(lock) {
                if (activeTrack === track) activeTrack = null
            }
            runCatching { track.release() }
        }
    }

    override fun stop() {
        val track = synchronized(lock) {
            activeTrack.also { activeTrack = null }
        } ?: return
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    override fun release() = stop()

    private companion object {
        const val TAG = "OfflineAudioPlayer"
    }
}
