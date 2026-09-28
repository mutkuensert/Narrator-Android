package com.mutkuensert.narrator.feature.reader.presentation.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mutkuensert.narrator.feature.reader.domain.model.Document
import com.mutkuensert.narrator.feature.reader.domain.model.LastReadPosition
import com.mutkuensert.narrator.feature.reader.domain.model.SpeechChunk
import com.mutkuensert.narrator.feature.reader.domain.model.TtsLanguage
import com.mutkuensert.narrator.feature.reader.domain.playback.PlaybackServiceController
import com.mutkuensert.narrator.feature.reader.domain.playback.PlaybackState
import com.mutkuensert.narrator.feature.reader.domain.repository.SpeechChunker
import com.mutkuensert.narrator.feature.reader.domain.playback.TtsPlaybackController
import com.mutkuensert.narrator.feature.reader.domain.repository.DocumentExtractionProgress
import com.mutkuensert.narrator.feature.reader.domain.repository.DocumentReadException
import com.mutkuensert.narrator.feature.reader.domain.repository.DocumentRepository
import com.mutkuensert.narrator.feature.reader.domain.repository.LastReadPositionRepository
import com.mutkuensert.narrator.feature.reader.domain.repository.TtsSettingsRepository
import com.mutkuensert.narrator.navigation.Navigator
import com.mutkuensert.narrator.navigation.SettingsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val speechChunker: SpeechChunker,
    private val playbackController: TtsPlaybackController,
    private val playbackServiceController: PlaybackServiceController,
    private val lastReadPositionRepository: LastReadPositionRepository,
    private val ttsSettingsRepository: TtsSettingsRepository,
    private val navigator: Navigator,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Empty)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    val playbackState = playbackController.state
    val ttsLanguage = ttsSettingsRepository.language
    private var loadingJob: Job? = null
    private var chunks: List<SpeechChunk> = emptyList()
    private var currentFileName: String? = null

    init {
        viewModelScope.launch {
            playbackState.collect { state ->
                val chunk = state.activeChunk() ?: return@collect
                val fileName = currentFileName ?: return@collect
                lastReadPositionRepository.save(
                    LastReadPosition(fileName, chunk.id, chunks.size),
                )
                val content = _uiState.value as? ReaderUiState.Content ?: return@collect
                if (content.restoredChunk?.id != chunk.id) {
                    _uiState.update { content.copy(restoredChunk = chunk) }
                }
            }
        }
        viewModelScope.launch {
            ttsSettingsRepository.numSteps.drop(1).collect {
                handleNumStepsUpdated()
            }
        }
        viewModelScope.launch {
            ttsLanguage.drop(1).collect(::handleTtsLanguageUpdated)
        }
        viewModelScope.launch {
            lastReadPositionRepository.positions.drop(1).collect { positions ->
                val fileName = currentFileName ?: return@collect
                if (positions.none { it.fileName == fileName }) {
                    val content = _uiState.value as? ReaderUiState.Content ?: return@collect
                    _uiState.update { content.copy(restoredChunk = null) }
                }
            }
        }
    }

    fun handleOpenDocument(uri: String) {
        loadingJob?.cancel()
        chunks = emptyList()
        currentFileName = null
        playbackController.load(emptyList())
        loadingJob = viewModelScope.launch {
            _uiState.update { ReaderUiState.Loading() }
            try {
                val document = documentRepository.openDocument(uri) { progress ->
                    _uiState.update { ReaderUiState.Loading(progress) }
                }
                chunks = speechChunker.createChunks(document, ttsLanguage.value)
                currentFileName = document.fileName ?: document.title
                val savedPosition = currentFileName?.let(lastReadPositionRepository::read)
                val openedFileIsShorter = savedPosition != null &&
                    savedPosition.chunkCount > chunks.size
                val restoredChunk = if (openedFileIsShorter) {
                    lastReadPositionRepository.clear(savedPosition.fileName)
                    null
                } else {
                    savedPosition?.let { saved -> chunks.firstOrNull { it.id == saved.chunkId } }
                }
                playbackController.load(chunks, restoredChunk?.id)
                _uiState.update { ReaderUiState.Content(
                    document = document,
                    chunkCount = chunks.size,
                    restoredChunk = restoredChunk,
                    showShorterDocumentWarning = openedFileIsShorter,
                ) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val reason = (error as? DocumentReadException)?.reason
                    ?: DocumentReadException.Reason.UNKNOWN
                _uiState.update { ReaderUiState.Error(reason) }
            }
        }
    }

    fun handlePlay() = playbackServiceController.play(currentFileName)
    fun handlePause() = playbackServiceController.pause()
    fun handleStop() = playbackServiceController.stop()
    fun handleNext() = playbackServiceController.next(currentFileName)
    fun handlePrevious() = playbackServiceController.previous(currentFileName)
    fun handleOpenSettings() = navigator.navigateToRoute(SettingsRoute)

    private fun handleNumStepsUpdated() {
        // Clear audio created with the old setting while preserving the reading position.
        if (chunks.isNotEmpty()) {
            val currentChunkId = playbackState.value.activeChunk()?.id
                ?: (_uiState.value as? ReaderUiState.Content)?.restoredChunk?.id
            playbackController.load(chunks, currentChunkId)
        }
    }

    private fun handleTtsLanguageUpdated(language: TtsLanguage) {
        val content = _uiState.value as? ReaderUiState.Content ?: return
        val previousChunk = playbackState.value.activeChunk() ?: content.restoredChunk
        chunks = speechChunker.createChunks(content.document, language)
        val restoredChunk = previousChunk?.let { previous ->
            chunks.firstOrNull {
                it.sectionIndex == previous.sectionIndex &&
                    it.paragraphIndex == previous.paragraphIndex
            }
        }
        playbackController.load(chunks, restoredChunk?.id)
        _uiState.update {
            content.copy(
                chunkCount = chunks.size,
                restoredChunk = restoredChunk,
            )
        }
        val fileName = currentFileName
        if (fileName != null && restoredChunk != null) {
            lastReadPositionRepository.save(
                LastReadPosition(fileName, restoredChunk.id, chunks.size),
            )
        }
    }

    fun handleDismissShorterDocumentWarning() {
        val content = _uiState.value as? ReaderUiState.Content ?: return
        _uiState.update { content.copy(showShorterDocumentWarning = false) }
    }

    fun handlePlayFromParagraph(sectionIndex: Int, paragraphIndex: Int) {
        val firstChunk = chunks.firstOrNull {
            it.sectionIndex == sectionIndex && it.paragraphIndex == paragraphIndex
        } ?: return
        playbackServiceController.playFrom(currentFileName, firstChunk.id)
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
    data class Error(val reason: DocumentReadException.Reason) : ReaderUiState
}
