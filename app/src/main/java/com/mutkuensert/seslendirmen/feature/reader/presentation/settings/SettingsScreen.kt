package com.mutkuensert.seslendirmen.feature.reader.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mutkuensert.seslendirmen.R
import com.mutkuensert.seslendirmen.feature.reader.domain.model.LastReadPosition
import com.mutkuensert.seslendirmen.feature.reader.domain.model.LegalDocumentType
import com.mutkuensert.seslendirmen.feature.reader.domain.model.TtsLanguage
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.TtsQuality

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val numSteps by viewModel.numSteps.collectAsStateWithLifecycle()
    val ttsLanguage by viewModel.ttsLanguage.collectAsStateWithLifecycle()
    val savedPositions by viewModel.savedPositions.collectAsStateWithLifecycle()
    val legalDocumentState by viewModel.legalDocumentState.collectAsStateWithLifecycle()

    SettingsContent(
        numSteps = numSteps,
        ttsLanguage = ttsLanguage,
        savedPositions = savedPositions,
        legalDocumentState = legalDocumentState,
        onNavigateBack = viewModel::handleNavigateBack,
        onNumStepsChanged = viewModel::handleNumStepsChanged,
        onTtsLanguageChanged = viewModel::handleTtsLanguageChanged,
        onDeleteSavedPosition = viewModel::handleClearSavedPosition,
        onLegalDocumentSelected = viewModel::handleLegalDocumentSelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    numSteps: Int,
    ttsLanguage: TtsLanguage,
    savedPositions: List<LastReadPosition>,
    legalDocumentState: LegalDocumentUiState,
    onNavigateBack: () -> Unit,
    onNumStepsChanged: (Int) -> Unit,
    onTtsLanguageChanged: (TtsLanguage) -> Unit,
    onDeleteSavedPosition: (String) -> Unit,
    onLegalDocumentSelected: (LegalDocumentType) -> Unit,
) {
    var showQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showLegalDialog by rememberSaveable { mutableStateOf(false) }
    var pendingPositionDeletion by rememberSaveable { mutableStateOf<String?>(null) }

    if (showQualityDialog) {
        QualityDialog(
            currentNumSteps = numSteps,
            onConfirm = {
                onNumStepsChanged(it)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false },
        )
    }
    if (showLanguageDialog) {
        TtsLanguageDialog(
            selectedLanguage = ttsLanguage,
            onSelect = {
                onTtsLanguageChanged(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
    if (showLegalDialog) {
        LegalNoticesDialog(
            state = legalDocumentState,
            onDocumentSelected = onLegalDocumentSelected,
            onDismiss = { showLegalDialog = false },
        )
    }
    pendingPositionDeletion?.let { fileName ->
        AlertDialog(
            onDismissRequest = { pendingPositionDeletion = null },
            title = { Text(stringResource(R.string.reader_delete_position_title)) },
            text = { Text(stringResource(R.string.reader_delete_position_message, fileName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSavedPosition(fileName)
                        pendingPositionDeletion = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPositionDeletion = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reader_settings)) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.reader_settings_application),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reader_reading_quality)) },
                    supportingContent = {
                        Text(stringResource(R.string.reader_step_count, numSteps))
                    },
                    modifier = Modifier.clickable { showQualityDialog = true },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reader_speech_language)) },
                    supportingContent = { Text(stringResource(ttsLanguage.labelResource())) },
                    modifier = Modifier.clickable { showLanguageDialog = true },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reader_legal_information)) },
                    supportingContent = {
                        Text(stringResource(R.string.reader_legal_information_summary))
                    },
                    modifier = Modifier.clickable {
                        onLegalDocumentSelected(LegalDocumentType.PRIVACY)
                        showLegalDialog = true
                    },
                )
            }
            item { HorizontalDivider() }
            item {
                Text(
                    text = stringResource(R.string.reader_saved_positions),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (savedPositions.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.reader_no_saved_positions),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(savedPositions, key = { it.fileName }) { position ->
                    ListItem(
                        headlineContent = { Text(position.fileName) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.reader_saved_position_summary,
                                    position.chunkId + 1,
                                    position.chunkCount,
                                ),
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { pendingPositionDeletion = position.fileName },
                            ) {
                                Text(stringResource(R.string.action_delete))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TtsLanguageDialog(
    selectedLanguage: TtsLanguage,
    onSelect: (TtsLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_speech_language)) },
        text = {
            Column {
                TtsLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(language) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = language == selectedLanguage,
                            onClick = null,
                        )
                        Text(stringResource(language.labelResource()))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@StringRes
private fun TtsLanguage.labelResource(): Int = when (this) {
    TtsLanguage.TURKISH -> R.string.tts_language_turkish
    TtsLanguage.ENGLISH -> R.string.tts_language_english
}

@Composable
private fun LegalNoticesDialog(
    state: LegalDocumentUiState,
    onDocumentSelected: (LegalDocumentType) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedDocument by rememberSaveable { mutableStateOf(LegalDocumentType.PRIVACY) }
    val documentText = when (state) {
        is LegalDocumentUiState.Content -> state.text
        LegalDocumentUiState.Error -> stringResource(
            R.string.reader_legal_load_error,
            stringResource(R.string.unknown_error),
        )
        LegalDocumentUiState.Idle, LegalDocumentUiState.Loading ->
            stringResource(R.string.reader_legal_loading)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_legal_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LegalDocumentType.entries.forEach { document ->
                        TextButton(
                            onClick = {
                                selectedDocument = document
                                onDocumentSelected(document)
                            },
                            enabled = document != selectedDocument,
                        ) {
                            Text(stringResource(document.labelResource()))
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    text = documentText,
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@StringRes
private fun LegalDocumentType.labelResource(): Int = when (this) {
    LegalDocumentType.PRIVACY -> R.string.reader_legal_privacy
    LegalDocumentType.NOTICES -> R.string.reader_legal_notices
    LegalDocumentType.MODEL_LICENSE -> R.string.reader_legal_model_license
    LegalDocumentType.APACHE -> R.string.reader_legal_apache
    LegalDocumentType.ONNX_RUNTIME -> R.string.reader_legal_onnx_runtime
    LegalDocumentType.BOUNCY_CASTLE -> R.string.reader_legal_bouncy_castle
}

@Composable
private fun QualityDialog(
    currentNumSteps: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedSteps by remember(currentNumSteps) {
        mutableFloatStateOf(currentNumSteps.toFloat())
    }
    val steps = selectedSteps.toInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_reading_quality)) },
        text = {
            Column {
                Text(stringResource(R.string.reader_step_count, steps))
                Text(
                    text = stringResource(R.string.reader_quality_description),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = selectedSteps,
                    onValueChange = { selectedSteps = it },
                    valueRange = TtsQuality.MIN_NUM_STEPS.toFloat()..
                        TtsQuality.MAX_NUM_STEPS.toFloat(),
                    steps = TtsQuality.MAX_NUM_STEPS - TtsQuality.MIN_NUM_STEPS - 1,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.reader_quality_fast),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        stringResource(R.string.reader_quality_high),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(steps) }) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

