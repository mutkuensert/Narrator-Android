package com.mutkuensert.narrator.feature.reader.presentation.ttstest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mutkuensert.narrator.feature.reader.domain.playback.AudioPlayer
import com.mutkuensert.narrator.feature.reader.domain.tts.TtsEngine
import com.mutkuensert.narrator.feature.reader.domain.tts.TtsEngineException
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
class TtsTestViewModel @Inject constructor(
    private val ttsEngine: TtsEngine,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TtsTestUiState())
    val uiState: StateFlow<TtsTestUiState> = _uiState.asStateFlow()
    private var playbackJob: Job? = null

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(status = TtsTestStatus.Initializing, error = null) }
            runCatching { ttsEngine.initialize() }
                .onSuccess { _uiState.update { it.copy(status = TtsTestStatus.Ready) } }
                .onFailure(::showError)
        }
    }

    fun handleTextChanged(text: String) {
        _uiState.update { it.copy(text = text, error = null) }
    }

    fun handleSpeak() {
        val text = uiState.value.text.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(error = TtsTestError.EmptyText) }
            return
        }

        handleStop()
        playbackJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(status = TtsTestStatus.Synthesizing, error = null) }
                val audio = ttsEngine.synthesize(text)
                _uiState.update { it.copy(status = TtsTestStatus.Playing) }
                audioPlayer.play(audio)
                _uiState.update { it.copy(status = TtsTestStatus.Ready) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error)
            } finally {
                audioPlayer.stop()
            }
        }
    }

    fun handleStop() {
        playbackJob?.cancel()
        playbackJob = null
        audioPlayer.stop()
        if (_uiState.value.status != TtsTestStatus.Initializing) {
            _uiState.update { it.copy(status = TtsTestStatus.Ready) }
        }
    }

    private fun showError(error: Throwable) {
        val uiError = when ((error as? TtsEngineException)?.reason) {
            TtsEngineException.Reason.MODEL_FILES_MISSING -> TtsTestError.ModelFilesMissing
            TtsEngineException.Reason.INITIALIZATION_FAILED -> TtsTestError.InitializationFailed
            TtsEngineException.Reason.SYNTHESIS_FAILED -> TtsTestError.SynthesisFailed
            null -> TtsTestError.Unknown
        }
        _uiState.update { it.copy(status = TtsTestStatus.Error, error = uiError) }
    }

    override fun onCleared() {
        audioPlayer.release()
    }
}

data class TtsTestUiState(
    val text: String = "",
    val status: TtsTestStatus = TtsTestStatus.Initializing,
    val error: TtsTestError? = null,
) {
    val canSpeak: Boolean
        get() = status == TtsTestStatus.Ready || status == TtsTestStatus.Error
}

sealed interface TtsTestError {
    data object EmptyText : TtsTestError
    data object ModelFilesMissing : TtsTestError
    data object InitializationFailed : TtsTestError
    data object SynthesisFailed : TtsTestError
    data object Unknown : TtsTestError
}

enum class TtsTestStatus {
    Initializing,
    Ready,
    Synthesizing,
    Playing,
    Error,
}
