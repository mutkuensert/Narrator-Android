package com.mutkuensert.seslendirmen.feature.reader.data.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.mutkuensert.seslendirmen.feature.reader.domain.model.AudioData
import com.mutkuensert.seslendirmen.feature.reader.domain.playback.AudioPlaybackException
import com.mutkuensert.seslendirmen.feature.reader.domain.playback.AudioPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class AudioTrackPlayer @Inject constructor(
    @ApplicationContext context: Context,
) : AudioPlayer {
    private val lock = Any()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    @Volatile
    private var activeTrack: AudioTrack? = null
    @Volatile
    private var hasAudioFocus = false
    @Volatile
    private var pausedForFocusLoss = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                val track = synchronized(lock) {
                    if (pausedForFocusLoss) activeTrack else null
                }
                pausedForFocusLoss = false
                track?.takeIf { it.playState == AudioTrack.PLAYSTATE_PAUSED }?.let {
                    runCatching { it.play() }
                        .onSuccess { Log.i(TAG, "Audio focus gained; playback resumed") }
                        .onFailure { error ->
                            Log.e(
                                TAG,
                                "Could not resume after focus gain",
                                error
                            )
                        }
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> {
                hasAudioFocus = false
                val track = synchronized(lock) { activeTrack }
                pausedForFocusLoss = track?.playState == AudioTrack.PLAYSTATE_PLAYING
                if (pausedForFocusLoss) {
                    runCatching { track?.pause() }
                    Log.i(TAG, "Audio focus lost temporarily; playback paused")
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                pausedForFocusLoss = false
                Log.i(TAG, "Audio focus lost; playback stopped")
                stopActiveTrack()
            }
        }
    }

    private val focusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(PLAYBACK_ATTRIBUTES)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setWillPauseWhenDucked(true)
                .build()
        } else {
            null
        }

    override suspend fun play(audio: AudioData, onStarted: () -> Unit) =
        withContext(Dispatchers.IO) {
            stopActiveTrack()
            if (!requestAudioFocus()) throw AudioPlaybackException()
            val minimumBufferSize = AudioTrack.getMinBufferSize(
                audio.sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
            )
            if (minimumBufferSize <= 0) throw AudioPlaybackException()

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        PLAYBACK_ATTRIBUTES,
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(audio.sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(max(minimumBufferSize, STREAM_BUFFER_BYTES))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (error: Throwable) {
                throw AudioPlaybackException(error)
            }

            synchronized(lock) { activeTrack = track }
            try {
                check(track.state == AudioTrack.STATE_INITIALIZED) {
                    "AudioTrack was not initialized: sampleRate=${audio.sampleRate}, " +
                            "minBufferSize=$minimumBufferSize"
                }
                track.play()
                Log.i(
                    TAG,
                    "Playback started: routedDevice=${track.routedDevice?.productName}, " +
                            "deviceType=${track.routedDevice?.type}, sampleRate=${audio.sampleRate}",
                )
                onStarted()
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
                    delay(20.milliseconds)
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

    override fun pause() {
        pausedForFocusLoss = false
        val track = synchronized(lock) { activeTrack } ?: return
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            runCatching { track.pause() }
        }
    }

    override fun resume() {
        if (!hasAudioFocus && !requestAudioFocus()) return
        val track = synchronized(lock) { activeTrack } ?: return
        if (track.playState == AudioTrack.PLAYSTATE_PAUSED) {
            runCatching { track.play() }
        }
    }

    override fun stop() {
        pausedForFocusLoss = false
        stopActiveTrack()
        abandonAudioFocus()
    }

    private fun stopActiveTrack() {
        val track = synchronized(lock) {
            activeTrack.also { activeTrack = null }
        } ?: return
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    override fun release() = stop()

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(checkNotNull(focusRequest))
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasAudioFocus) Log.w(TAG, "Audio focus request was denied: result=$result")
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(checkNotNull(focusRequest))
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        hasAudioFocus = false
    }

    private companion object {
        const val TAG = "OfflineAudioPlayer"
        const val STREAM_BUFFER_BYTES = 16 * 1024

        val PLAYBACK_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
