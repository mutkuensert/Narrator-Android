package com.mutkuensert.seslendirmen.feature.reader.presentation.ttstest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mutkuensert.seslendirmen.R

@Composable
fun TtsTestScreen(
    viewModel: TtsTestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TtsTestContent(
        state = state,
        onTextChanged = viewModel::handleTextChanged,
        onSpeak = viewModel::handleSpeak,
        onStop = viewModel::handleStop,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsTestContent(
    state: TtsTestUiState,
    onTextChanged: (String) -> Unit,
    onSpeak: () -> Unit,
    onStop: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tts_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.tts_test_description),
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tts_test_text_label)) },
                minLines = 5,
                enabled = state.status != TtsTestStatus.Synthesizing,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onSpeak, enabled = state.canSpeak) {
                    Text(stringResource(R.string.tts_test_speak))
                }
                if (state.status == TtsTestStatus.Synthesizing || state.status == TtsTestStatus.Playing) {
                    OutlinedButton(onClick = onStop) {
                        Text(stringResource(R.string.tts_test_stop))
                    }
                }
            }
            StatusContent(state)
        }
    }
}

@Composable
private fun StatusContent(state: TtsTestUiState) {
    when (state.status) {
        TtsTestStatus.Initializing -> LoadingStatus(
            stringResource(R.string.tts_test_initializing),
        )
        TtsTestStatus.Synthesizing -> LoadingStatus(
            stringResource(R.string.tts_test_synthesizing),
        )
        TtsTestStatus.Playing -> Text(
            stringResource(R.string.tts_test_playing),
            color = MaterialTheme.colorScheme.primary,
        )
        TtsTestStatus.Ready -> Text(
            stringResource(R.string.tts_test_ready),
            color = MaterialTheme.colorScheme.primary,
        )
        TtsTestStatus.Error -> Text(
            text = stringResource(state.error.toStringResource()),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun TtsTestError?.toStringResource(): Int = when (this) {
    TtsTestError.EmptyText -> R.string.tts_test_empty_text_error
    TtsTestError.ModelFilesMissing -> R.string.tts_model_files_missing
    TtsTestError.InitializationFailed -> R.string.tts_initialization_failed
    TtsTestError.SynthesisFailed -> R.string.tts_synthesis_failed
    TtsTestError.Unknown, null -> R.string.generic_error
}

@Composable
private fun LoadingStatus(label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        Text(label)
    }
}
