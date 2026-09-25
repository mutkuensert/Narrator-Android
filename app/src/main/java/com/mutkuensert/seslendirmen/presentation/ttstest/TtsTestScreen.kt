package com.mutkuensert.seslendirmen.presentation.ttstest

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mutkuensert.seslendirmen.R

@Composable
fun TtsTestRoute(viewModel: TtsTestViewModel) {
    val state by viewModel.uiState.collectAsState()
    TtsTestScreen(
        state = state,
        onTextChanged = viewModel::onTextChanged,
        onSpeak = viewModel::speak,
        onStop = viewModel::stop,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsTestScreen(
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
                text = "tr_TR-dfki-medium modeli cihaz üzerinde çalışır. İnternet bağlantısı kullanılmaz.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Metin") },
                minLines = 5,
                enabled = state.status != TtsTestStatus.Synthesizing,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onSpeak, enabled = state.canSpeak) {
                    Text("Seslendir")
                }
                if (state.status == TtsTestStatus.Synthesizing || state.status == TtsTestStatus.Playing) {
                    OutlinedButton(onClick = onStop) { Text("Durdur") }
                }
            }
            StatusContent(state)
        }
    }
}

@Composable
private fun StatusContent(state: TtsTestUiState) {
    when (state.status) {
        TtsTestStatus.Initializing -> LoadingStatus("Türkçe ses modeli hazırlanıyor…")
        TtsTestStatus.Synthesizing -> LoadingStatus("Ses oluşturuluyor…")
        TtsTestStatus.Playing -> Text("Ses çalınıyor", color = MaterialTheme.colorScheme.primary)
        TtsTestStatus.Ready -> Text("Hazır", color = MaterialTheme.colorScheme.primary)
        TtsTestStatus.Error -> Text(
            text = state.errorMessage ?: "Bir hata oluştu.",
            color = MaterialTheme.colorScheme.error,
        )
    }
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
