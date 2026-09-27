package com.mutkuensert.seslendirmen.feature.reader.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mutkuensert.seslendirmen.feature.reader.domain.model.LegalDocumentType
import com.mutkuensert.seslendirmen.feature.reader.domain.model.TtsLanguage
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.LastReadPositionRepository
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.LegalDocumentRepository
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.TtsQuality
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.TtsSettingsRepository
import com.mutkuensert.seslendirmen.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val ttsSettingsRepository: TtsSettingsRepository,
    private val lastReadPositionRepository: LastReadPositionRepository,
    private val legalDocumentRepository: LegalDocumentRepository,
    private val navigator: Navigator,
) : ViewModel() {
    val numSteps = ttsSettingsRepository.numSteps
    val ttsLanguage = ttsSettingsRepository.language
    val savedPositions = lastReadPositionRepository.positions
    private val _legalDocumentState = MutableStateFlow<LegalDocumentUiState>(
        LegalDocumentUiState.Idle,
    )
    val legalDocumentState: StateFlow<LegalDocumentUiState> = _legalDocumentState.asStateFlow()
    private var legalDocumentJob: Job? = null

    fun handleNumStepsChanged(value: Int) {
        ttsSettingsRepository.saveNumSteps(TtsQuality.validatedNumSteps(value))
    }

    fun handleTtsLanguageChanged(language: TtsLanguage) {
        ttsSettingsRepository.saveLanguage(language)
    }

    fun handleClearSavedPosition(fileName: String) {
        lastReadPositionRepository.clear(fileName)
    }

    fun handleNavigateBack() {
        navigator.navigateBack()
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

sealed interface LegalDocumentUiState {
    data object Idle : LegalDocumentUiState
    data object Loading : LegalDocumentUiState
    data class Content(val text: String) : LegalDocumentUiState
    data object Error : LegalDocumentUiState
}

