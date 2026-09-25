package com.mutkuensert.seslendirmen.presentation.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mutkuensert.seslendirmen.domain.model.PdfDocument
import com.mutkuensert.seslendirmen.domain.repository.PdfReadException
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
) : ViewModel() {
    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Empty)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    private var loadingJob: Job? = null

    fun openDocument(uri: String) {
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            try {
                _uiState.value = ReaderUiState.Content(openPdfDocument(uri))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = (error as? PdfReadException)?.reason?.message
                    ?: PdfReadException.Reason.UNKNOWN.message
                _uiState.value = ReaderUiState.Error(message)
            }
        }
    }
}

sealed interface ReaderUiState {
    data object Empty : ReaderUiState
    data object Loading : ReaderUiState
    data class Content(val document: PdfDocument) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}
