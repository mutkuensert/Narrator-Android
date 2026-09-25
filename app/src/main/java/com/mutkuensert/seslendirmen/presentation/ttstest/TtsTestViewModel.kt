package com.mutkuensert.seslendirmen.presentation.ttstest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mutkuensert.seslendirmen.domain.playback.AudioPlayer
import com.mutkuensert.seslendirmen.domain.tts.TtsEngine
import com.mutkuensert.seslendirmen.domain.tts.TtsEngineException
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
            _uiState.update { it.copy(status = TtsTestStatus.Initializing, errorMessage = null) }
            runCatching { ttsEngine.initialize() }
                .onSuccess { _uiState.update { it.copy(status = TtsTestStatus.Ready) } }
                .onFailure(::showError)
        }
    }

    fun onTextChanged(text: String) {
        _uiState.update { it.copy(text = text, errorMessage = null) }
    }

    fun speak() {
        val text = uiState.value.text.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Lütfen seslendirilecek bir metin girin.") }
            return
        }

        stop()
        playbackJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(status = TtsTestStatus.Synthesizing, errorMessage = null) }
                val audio = ttsEngine.synthesize(text)
                _uiState.update { it.copy(status = TtsTestStatus.Playing) }
                audioPlayer.play(audio)
                _uiState.update { it.copy(status = TtsTestStatus.Ready) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error)
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        audioPlayer.stop()
        if (_uiState.value.status != TtsTestStatus.Initializing) {
            _uiState.update { it.copy(status = TtsTestStatus.Ready) }
        }
    }

    private fun showError(error: Throwable) {
        val message = (error as? TtsEngineException)?.reason?.message
            ?: error.message
            ?: "Beklenmeyen bir hata oluştu."
        _uiState.update { it.copy(status = TtsTestStatus.Error, errorMessage = message) }
    }

    override fun onCleared() {
        audioPlayer.release()
    }
}

data class TtsTestUiState(
    val text: String = DEFAULT_TEXT,
    val status: TtsTestStatus = TtsTestStatus.Initializing,
    val errorMessage: String? = null,
) {
    val canSpeak: Boolean
        get() = status == TtsTestStatus.Ready || status == TtsTestStatus.Error
}

enum class TtsTestStatus {
    Initializing,
    Ready,
    Synthesizing,
    Playing,
    Error,
}

const val DEFAULT_TEXT =
    "Merhaba. Bu uygulama internet bağlantısı olmadan Türkçe metinleri seslendirebilir."
