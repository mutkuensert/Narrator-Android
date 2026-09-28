package com.mutkuensert.narrator.feature.reader.data.playback

import com.mutkuensert.narrator.feature.reader.domain.model.AudioData
import com.mutkuensert.narrator.feature.reader.domain.model.SpeechChunk
import com.mutkuensert.narrator.feature.reader.domain.playback.AudioPlaybackException
import com.mutkuensert.narrator.feature.reader.domain.playback.AudioPlayer
import com.mutkuensert.narrator.feature.reader.domain.playback.PlaybackError
import com.mutkuensert.narrator.feature.reader.domain.playback.PlaybackQueue
import com.mutkuensert.narrator.feature.reader.domain.playback.PlaybackState
import com.mutkuensert.narrator.feature.reader.domain.playback.TtsPlaybackController
import com.mutkuensert.narrator.feature.reader.domain.tts.TtsEngine
import com.mutkuensert.narrator.feature.reader.domain.tts.TtsEngineException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BufferedTtsPlaybackController @Inject constructor(
    private val ttsEngine: TtsEngine,
    private val audioPlayer: AudioPlayer,
) : TtsPlaybackController {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var queue = PlaybackQueue(emptyList())
    private var playbackJob: Job? = null
    private var warmupJob: Job? = null
    private var playbackVersion = 0L
    private var documentVersion = 0L
    private val audioCache = LinkedHashMap<Int, AudioData>(MAX_BUFFERED_CHUNKS, 0.75f, true)
    private val pendingSynthesis = mutableMapOf<Int, Deferred<AudioData>>()

    override fun load(chunks: List<SpeechChunk>, initialChunkId: Long?) {
        val jobsToCancel: List<Job>
        val version: Long
        synchronized(lock) {
            playbackVersion++
            documentVersion++
            version = documentVersion
            jobsToCancel = buildList {
                playbackJob?.let(::add)
                warmupJob?.let(::add)
                addAll(pendingSynthesis.values)
            }
            playbackJob = null
            warmupJob = null
            pendingSynthesis.clear()
            audioCache.clear()
            queue = PlaybackQueue(chunks)
            initialChunkId?.let(queue::moveTo)
            _state.value = PlaybackState.Idle
        }
        jobsToCancel.forEach(Job::cancel)
        audioPlayer.stop()

        if (chunks.isNotEmpty()) {
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val initialIndex = synchronized(lock) { queue.currentIndex }
                runCatching {
                    audioFor(index = initialIndex, expectedDocumentVersion = version)
                    prefetch(index = initialIndex + 1, expectedDocumentVersion = version)
                }
            }
            synchronized(lock) {
                if (documentVersion == version) warmupJob = job else job.cancel()
            }
            job.start()
        }
    }

    override fun play() {
        val paused = synchronized(lock) {
            val currentState = _state.value
            if (currentState is PlaybackState.Paused && playbackJob != null) {
                _state.value = PlaybackState.Playing(currentState.chunk)
                true
            } else {
                false
            }
        }
        if (paused) {
            audioPlayer.resume()
            return
        }

        val shouldStart = synchronized(lock) {
            queue.current != null && _state.value !is PlaybackState.Playing &&
                    _state.value !is PlaybackState.Preparing
        }
        if (shouldStart) startPlaybackAtCurrent()
    }

    override fun pause() {
        val (jobToCancel, shouldPausePlayer) = synchronized(lock) {
            when (val state = _state.value) {
                is PlaybackState.Playing -> {
                    _state.value = PlaybackState.Paused(state.chunk)
                    null to true
                }

                is PlaybackState.Preparing -> {
                    playbackVersion++
                    val job = playbackJob
                    playbackJob = null
                    _state.value = PlaybackState.Paused(state.chunk)
                    job to false
                }

                else -> null to false
            }
        }
        jobToCancel?.cancel()
        if (shouldPausePlayer) audioPlayer.pause()
    }

    override fun stop() {
        val job: Job?
        synchronized(lock) {
            playbackVersion++
            job = playbackJob
            playbackJob = null
            queue.reset()
            prunePendingSynthesis()
            _state.value = PlaybackState.Idle
        }
        job?.cancel()
        audioPlayer.stop()
    }

    override fun next() {
        val canMove = synchronized(lock) { queue.moveNext() != null }
        if (canMove) startPlaybackAtCurrent()
    }

    override fun previous() {
        val canMove = synchronized(lock) { queue.movePrevious() != null }
        if (canMove) startPlaybackAtCurrent()
    }

    override fun playFrom(chunkId: Long) {
        val canMove = synchronized(lock) { queue.moveTo(chunkId) != null }
        if (canMove) startPlaybackAtCurrent()
    }

    private fun startPlaybackAtCurrent() {
        val oldJob: Job?
        val version: Long
        val docVersion: Long
        synchronized(lock) {
            playbackVersion++
            version = playbackVersion
            docVersion = documentVersion
            oldJob = playbackJob
            playbackJob = null
            prunePendingSynthesis()
        }
        oldJob?.cancel()
        audioPlayer.stop()
        val newJob = scope.launch(start = CoroutineStart.LAZY) {
            playbackLoop(version, docVersion)
        }
        synchronized(lock) {
            if (playbackVersion == version) playbackJob = newJob else newJob.cancel()
        }
        newJob.start()
    }

    private suspend fun playbackLoop(version: Long, docVersion: Long) {
        try {
            while (currentCoroutineContext().isActive) {
                val snapshot = synchronized(lock) {
                    if (version != playbackVersion) return
                    val chunk = queue.current ?: return
                    _state.value = PlaybackState.Preparing(chunk)
                    Pair(queue.currentIndex, chunk)
                }
                val audio = audioFor(snapshot.first, docVersion)
                if (!isCurrentPlayback(version)) return
                prefetch(snapshot.first + 1, docVersion)
                audioPlayer.play(audio) {
                    synchronized(lock) {
                        if (version == playbackVersion) {
                            _state.value = PlaybackState.Playing(snapshot.second)
                        }
                    }
                }

                val hasNext = synchronized(lock) {
                    if (version != playbackVersion) return
                    val next = queue.moveNext()
                    prunePendingSynthesis()
                    next != null
                }
                if (!hasNext) {
                    audioPlayer.stop()
                    synchronized(lock) {
                        if (version == playbackVersion) _state.value = PlaybackState.Idle
                    }
                    return
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            audioPlayer.stop()
            synchronized(lock) {
                if (version == playbackVersion) {
                    _state.value = PlaybackState.Error(error.toPlaybackError())
                }
            }
        }
    }

    private suspend fun audioFor(index: Int, expectedDocumentVersion: Long): AudioData {
        synchronized(lock) { audioCache[index] }?.let { return it }
        val deferred = synchronized(lock) {
            pendingSynthesis[index] ?: scope.async(start = CoroutineStart.LAZY) {
                val chunk = synchronized(lock) {
                    if (documentVersion != expectedDocumentVersion) throw CancellationException()
                    queue[index] ?: throw CancellationException()
                }
                ttsEngine.synthesize(chunk.text)
            }.also {
                pendingSynthesis[index] = it
                it.start()
            }
        }

        return try {
            val audio = deferred.await()
            synchronized(lock) {
                if (documentVersion == expectedDocumentVersion) {
                    audioCache[index] = audio
                    trimCache()
                }
            }
            audio
        } finally {
            synchronized(lock) {
                if (deferred.isCompleted && pendingSynthesis[index] === deferred) {
                    pendingSynthesis.remove(index)
                }
            }
        }
    }

    private fun prefetch(index: Int, expectedDocumentVersion: Long) {
        val exists = synchronized(lock) { queue[index] != null }
        if (!exists) return
        scope.launch {
            runCatching { audioFor(index, expectedDocumentVersion) }
        }
    }

    private fun trimCache() {
        while (audioCache.size > MAX_BUFFERED_CHUNKS) {
            val eldest = audioCache.entries.iterator().next()
            audioCache.remove(eldest.key)
        }
    }

    private fun prunePendingSynthesis() {
        val firstAllowed = queue.currentIndex
        val allowed = setOf(firstAllowed, firstAllowed + 1)
        val obsolete = pendingSynthesis.filterKeys { it !in allowed }
        obsolete.forEach { (index, job) ->
            pendingSynthesis.remove(index)
            job.cancel()
        }
    }

    private fun isCurrentPlayback(version: Long): Boolean =
        synchronized(lock) { version == playbackVersion }

    override fun release() {
        val jobs: List<Job>
        synchronized(lock) {
            playbackVersion++
            documentVersion++
            jobs = buildList {
                playbackJob?.let(::add)
                warmupJob?.let(::add)
                addAll(pendingSynthesis.values)
            }
            playbackJob = null
            warmupJob = null
            pendingSynthesis.clear()
            audioCache.clear()
            queue = PlaybackQueue(emptyList())
            _state.value = PlaybackState.Idle
        }
        jobs.forEach(Job::cancel)
        audioPlayer.release()
        ttsEngine.release()
        scope.cancel()
    }

    private fun Throwable.toPlaybackError(): PlaybackError = when (this) {
        is TtsEngineException -> PlaybackError(
            reason = when (reason) {
                TtsEngineException.Reason.MODEL_FILES_MISSING ->
                    PlaybackError.Reason.MODEL_FILES_MISSING

                TtsEngineException.Reason.INITIALIZATION_FAILED ->
                    PlaybackError.Reason.INITIALIZATION_FAILED

                TtsEngineException.Reason.SYNTHESIS_FAILED ->
                    PlaybackError.Reason.SYNTHESIS_FAILED
            },
            cause = this,
        )

        is AudioPlaybackException -> PlaybackError(
            PlaybackError.Reason.AUDIO_OUTPUT_FAILED,
            this,
        )

        else -> PlaybackError(PlaybackError.Reason.UNKNOWN, this)
    }

    private companion object {
        const val MAX_BUFFERED_CHUNKS = 2
    }
}
