package com.mutkuensert.seslendirmen.presentation.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mutkuensert.seslendirmen.data.playback.TtsPlaybackService
import com.mutkuensert.seslendirmen.data.preferences.LastReadPositionStore
import com.mutkuensert.seslendirmen.data.preferences.TtsPreferences
import com.mutkuensert.seslendirmen.domain.model.LastReadPosition
import com.mutkuensert.seslendirmen.domain.model.Document
import com.mutkuensert.seslendirmen.domain.model.SpeechChunk
import com.mutkuensert.seslendirmen.domain.playback.PlaybackState
import com.mutkuensert.seslendirmen.domain.repository.DocumentReadException
import com.mutkuensert.seslendirmen.domain.repository.DocumentExtractionProgress
import com.mutkuensert.seslendirmen.domain.repository.SpeechChunker
import com.mutkuensert.seslendirmen.domain.playback.TtsPlaybackController
import com.mutkuensert.seslendirmen.domain.usecase.OpenDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val openDocument: OpenDocument,
    private val speechChunker: SpeechChunker,
    private val playbackController: TtsPlaybackController,
    private val lastReadPositionStore: LastReadPositionStore,
    private val ttsPreferences: TtsPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Empty)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    val playbackState = playbackController.state
    val savedPositions = lastReadPositionStore.positions
    private val _numSteps = MutableStateFlow(ttsPreferences.readNumSteps())
    val numSteps: StateFlow<Int> = _numSteps.asStateFlow()
    private var loadingJob: Job? = null
    private var chunks: List<SpeechChunk> = emptyList()
    private var currentFileName: String? = null

    init {
        viewModelScope.launch {
            playbackState.collect { state ->
                val chunk = state.activeChunk() ?: return@collect
                val fileName = currentFileName ?: return@collect
                lastReadPositionStore.save(
                    LastReadPosition(fileName, chunk.id, chunks.size),
                )
                val content = _uiState.value as? ReaderUiState.Content ?: return@collect
                if (content.restoredChunk?.id != chunk.id) {
                    _uiState.value = content.copy(restoredChunk = chunk)
                }
            }
        }
    }

    fun openDocument(uri: String) {
        loadingJob?.cancel()
        chunks = emptyList()
        currentFileName = null
        playbackController.load(emptyList())
        loadingJob = viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading()
            try {
                val document = openDocument(uri) { progress ->
                    _uiState.value = ReaderUiState.Loading(progress)
                }
                chunks = speechChunker.createChunks(document)
                currentFileName = document.fileName ?: document.title
                val savedPosition = currentFileName?.let(lastReadPositionStore::read)
                val openedFileIsShorter = savedPosition != null &&
                    savedPosition.chunkCount > chunks.size
                val restoredChunk = if (openedFileIsShorter) {
                    lastReadPositionStore.clear(savedPosition.fileName)
                    null
                } else {
                    savedPosition?.let { saved -> chunks.firstOrNull { it.id == saved.chunkId } }
                }
                playbackController.load(chunks, restoredChunk?.id)
                _uiState.value = ReaderUiState.Content(
                    document = document,
                    chunkCount = chunks.size,
                    restoredChunk = restoredChunk,
                    showShorterDocumentWarning = openedFileIsShorter,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = (error as? DocumentReadException)?.reason?.message
                    ?: DocumentReadException.Reason.UNKNOWN.message
                _uiState.value = ReaderUiState.Error(message)
            }
        }
    }

    fun play() = TtsPlaybackService.play(applicationContext, currentFileName)
    fun pause() = TtsPlaybackService.pause(applicationContext)
    fun stop() = TtsPlaybackService.stop(applicationContext)
    fun next() = TtsPlaybackService.next(applicationContext, currentFileName)
    fun previous() = TtsPlaybackService.previous(applicationContext, currentFileName)

    fun setNumSteps(value: Int) {
        val newValue = value.coerceIn(TtsPreferences.MIN_NUM_STEPS, TtsPreferences.MAX_NUM_STEPS)
        if (newValue == _numSteps.value) return
        ttsPreferences.saveNumSteps(newValue)
        _numSteps.value = newValue

        // Clear audio created with the old setting while preserving the reading position.
        if (chunks.isNotEmpty()) {
            val currentChunkId = playbackState.value.activeChunk()?.id
                ?: (_uiState.value as? ReaderUiState.Content)?.restoredChunk?.id
            playbackController.load(chunks, currentChunkId)
        }
    }

    fun dismissShorterDocumentWarning() {
        val content = _uiState.value as? ReaderUiState.Content ?: return
        _uiState.value = content.copy(showShorterDocumentWarning = false)
    }

    fun clearSavedPosition(fileName: String) {
        lastReadPositionStore.clear(fileName)
        if (currentFileName != fileName) return
        val content = _uiState.value as? ReaderUiState.Content ?: return
        _uiState.value = content.copy(restoredChunk = null)
    }

    fun playFromParagraph(sectionIndex: Int, paragraphIndex: Int) {
        val firstChunk = chunks.firstOrNull {
            it.sectionIndex == sectionIndex && it.paragraphIndex == paragraphIndex
        } ?: return
        TtsPlaybackService.playFrom(applicationContext, currentFileName, firstChunk.id)
    }
}

private fun PlaybackState.activeChunk(): SpeechChunk? = when (this) {
    is PlaybackState.Preparing -> chunk
    is PlaybackState.Playing -> chunk
    is PlaybackState.Paused -> chunk
    is PlaybackState.Error, PlaybackState.Idle -> null
}

sealed interface ReaderUiState {
    data object Empty : ReaderUiState
    data class Loading(val progress: DocumentExtractionProgress? = null) : ReaderUiState
    data class Content(
        val document: Document,
        val chunkCount: Int,
        val restoredChunk: SpeechChunk? = null,
        val showShorterDocumentWarning: Boolean = false,
    ) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}
