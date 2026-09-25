package com.mutkuensert.seslendirmen.presentation.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mutkuensert.seslendirmen.domain.model.PdfDocument
import com.mutkuensert.seslendirmen.domain.model.SpeechChunk
import com.mutkuensert.seslendirmen.domain.repository.PdfReadException
import com.mutkuensert.seslendirmen.domain.repository.PdfExtractionProgress
import com.mutkuensert.seslendirmen.domain.repository.SpeechChunker
import com.mutkuensert.seslendirmen.domain.playback.TtsPlaybackController
import com.mutkuensert.seslendirmen.domain.usecase.OpenPdfDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val openPdfDocument: OpenPdfDocument,
    private val speechChunker: SpeechChunker,
    private val playbackController: TtsPlaybackController,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Empty)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    val playbackState = playbackController.state
    private var loadingJob: Job? = null
    private var chunks: List<SpeechChunk> = emptyList()

    fun openDocument(uri: String) {
        loadingJob?.cancel()
        chunks = emptyList()
        playbackController.load(emptyList())
        loadingJob = viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading()
            try {
                val document = openPdfDocument(uri) { progress ->
                    _uiState.value = ReaderUiState.Loading(progress)
                }
                chunks = speechChunker.createChunks(document)
                playbackController.load(chunks)
                _uiState.value = ReaderUiState.Content(document, chunks.size)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = (error as? PdfReadException)?.reason?.message
                    ?: PdfReadException.Reason.UNKNOWN.message
                _uiState.value = ReaderUiState.Error(message)
            }
        }
    }

    fun play() = playbackController.play()
    fun pause() = playbackController.pause()
    fun stop() = playbackController.stop()
    fun next() = playbackController.next()
    fun previous() = playbackController.previous()

    fun playFromParagraph(pageNumber: Int, paragraphIndex: Int) {
        val firstChunk = chunks.firstOrNull {
            it.pageNumber == pageNumber && it.paragraphIndex == paragraphIndex
        } ?: return
        playbackController.playFrom(firstChunk.id)
    }
}

sealed interface ReaderUiState {
    data object Empty : ReaderUiState
    data class Loading(val progress: PdfExtractionProgress? = null) : ReaderUiState
    data class Content(val document: PdfDocument, val chunkCount: Int) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}
