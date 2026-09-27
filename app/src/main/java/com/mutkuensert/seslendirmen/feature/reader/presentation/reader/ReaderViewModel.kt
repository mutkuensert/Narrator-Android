package com.mutkuensert.seslendirmen.feature.reader.presentation.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mutkuensert.seslendirmen.feature.reader.domain.model.LastReadPosition
import com.mutkuensert.seslendirmen.feature.reader.domain.model.LegalDocumentType
import com.mutkuensert.seslendirmen.feature.reader.domain.model.Document
import com.mutkuensert.seslendirmen.feature.reader.domain.model.SpeechChunk
import com.mutkuensert.seslendirmen.feature.reader.domain.model.TtsLanguage
import com.mutkuensert.seslendirmen.feature.reader.domain.playback.PlaybackServiceController
import com.mutkuensert.seslendirmen.feature.reader.domain.playback.PlaybackState
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.DocumentReadException
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.DocumentExtractionProgress
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.DocumentRepository
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.LastReadPositionRepository
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.LegalDocumentRepository
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.SpeechChunker
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.TtsQuality
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.TtsSettingsRepository
import com.mutkuensert.seslendirmen.feature.reader.domain.playback.TtsPlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val legalDocumentRepository: LegalDocumentRepository,
    private val ttsSettingsRepository: TtsSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Empty)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    val playbackState = playbackController.state
    val savedPositions = lastReadPositionRepository.positions
    private val _numSteps = MutableStateFlow(ttsSettingsRepository.readNumSteps())
    val numSteps: StateFlow<Int> = _numSteps.asStateFlow()
    private val _ttsLanguage = MutableStateFlow(ttsSettingsRepository.readLanguage())
    val ttsLanguage: StateFlow<TtsLanguage> = _ttsLanguage.asStateFlow()
    private val _legalDocumentState = MutableStateFlow<LegalDocumentUiState>(
        LegalDocumentUiState.Idle,
    )
    val legalDocumentState: StateFlow<LegalDocumentUiState> = _legalDocumentState.asStateFlow()
    private var loadingJob: Job? = null
    private var legalDocumentJob: Job? = null
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
                chunks = speechChunker.createChunks(document, _ttsLanguage.value)
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

    fun handleNumStepsChanged(value: Int) {
        val newValue = TtsQuality.validatedNumSteps(value)
        if (newValue == _numSteps.value) return
        ttsSettingsRepository.saveNumSteps(newValue)
        _numSteps.update { newValue }

        // Clear audio created with the old setting while preserving the reading position.
        if (chunks.isNotEmpty()) {
            val currentChunkId = playbackState.value.activeChunk()?.id
                ?: (_uiState.value as? ReaderUiState.Content)?.restoredChunk?.id
            playbackController.load(chunks, currentChunkId)
        }
    }

    fun handleTtsLanguageChanged(language: TtsLanguage) {
        if (language == _ttsLanguage.value) return
        ttsSettingsRepository.saveLanguage(language)
        _ttsLanguage.update { language }

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

    fun handleClearSavedPosition(fileName: String) {
        lastReadPositionRepository.clear(fileName)
        if (currentFileName != fileName) return
        val content = _uiState.value as? ReaderUiState.Content ?: return
        _uiState.update { content.copy(restoredChunk = null) }
    }

    fun handlePlayFromParagraph(sectionIndex: Int, paragraphIndex: Int) {
        val firstChunk = chunks.firstOrNull {
            it.sectionIndex == sectionIndex && it.paragraphIndex == paragraphIndex
        } ?: return
        playbackServiceController.playFrom(currentFileName, firstChunk.id)
    }

    fun handleLegalDocumentSelected(document: LegalDocumentType) {
        legalDocumentJob?.cancel()
        legalDocumentJob = viewModelScope.launch {
            _legalDocumentState.update { LegalDocumentUiState.Loading }
            runCatching { legalDocumentRepository.read(document) }
                .onSuccess { text ->
                    _legalDocumentState.update { LegalDocumentUiState.Content(text) }
                }
                .onFailure {
                    _legalDocumentState.update { LegalDocumentUiState.Error }
                }
        }
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

sealed interface LegalDocumentUiState {
    data object Idle : LegalDocumentUiState
    data object Loading : LegalDocumentUiState
    data class Content(val text: String) : LegalDocumentUiState
    data object Error : LegalDocumentUiState
}
