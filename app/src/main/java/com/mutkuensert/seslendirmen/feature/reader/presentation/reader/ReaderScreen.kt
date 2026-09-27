package com.mutkuensert.seslendirmen.feature.reader.presentation.reader

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mutkuensert.seslendirmen.R
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.TtsQuality
import com.mutkuensert.seslendirmen.feature.reader.domain.model.Document
import com.mutkuensert.seslendirmen.feature.reader.domain.model.DocumentFormat
import com.mutkuensert.seslendirmen.feature.reader.domain.model.LastReadPosition
import com.mutkuensert.seslendirmen.feature.reader.domain.model.LegalDocumentType
import com.mutkuensert.seslendirmen.feature.reader.domain.model.SpeechChunk
import com.mutkuensert.seslendirmen.feature.reader.domain.model.TtsLanguage
import com.mutkuensert.seslendirmen.feature.reader.domain.playback.PlaybackState
import com.mutkuensert.seslendirmen.feature.reader.domain.playback.PlaybackError
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.DocumentExtractionProgress
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.DocumentReadException
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val numSteps by viewModel.numSteps.collectAsStateWithLifecycle()
    val ttsLanguage by viewModel.ttsLanguage.collectAsStateWithLifecycle()
    val savedPositions by viewModel.savedPositions.collectAsStateWithLifecycle()
    val legalDocumentState by viewModel.legalDocumentState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.handleOpenDocument(it.toString()) }
    }
    ReaderContent(
        state = state,
        playbackState = playbackState,
        numSteps = numSteps,
        ttsLanguage = ttsLanguage,
        savedPositions = savedPositions,
        legalDocumentState = legalDocumentState,
        onSelectDocument = { picker.launch(SUPPORTED_MIME_TYPES) },
        onPlay = viewModel::handlePlay,
        onPause = viewModel::handlePause,
        onStop = viewModel::handleStop,
        onPrevious = viewModel::handlePrevious,
        onNext = viewModel::handleNext,
        onParagraphClick = viewModel::handlePlayFromParagraph,
        onDismissShorterDocumentWarning = viewModel::handleDismissShorterDocumentWarning,
        onNumStepsChanged = viewModel::handleNumStepsChanged,
        onTtsLanguageChanged = viewModel::handleTtsLanguageChanged,
        onDeleteSavedPosition = viewModel::handleClearSavedPosition,
        onLegalDocumentSelected = viewModel::handleLegalDocumentSelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderContent(
    state: ReaderUiState,
    playbackState: PlaybackState,
    numSteps: Int,
    ttsLanguage: TtsLanguage,
    savedPositions: List<LastReadPosition>,
    legalDocumentState: LegalDocumentUiState,
    onSelectDocument: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onParagraphClick: (sectionIndex: Int, paragraphIndex: Int) -> Unit,
    onDismissShorterDocumentWarning: () -> Unit,
    onNumStepsChanged: (Int) -> Unit,
    onTtsLanguageChanged: (TtsLanguage) -> Unit,
    onDeleteSavedPosition: (String) -> Unit,
    onLegalDocumentSelected: (LegalDocumentType) -> Unit,
) {
    val title = (state as? ReaderUiState.Content)?.document?.title
        ?: stringResource(R.string.app_name)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showLegalDialog by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pendingPositionDeletion by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = showSettings) { showSettings = false }
    if (state is ReaderUiState.Content && state.showShorterDocumentWarning) {
        AlertDialog(
            onDismissRequest = onDismissShorterDocumentWarning,
            title = { Text(stringResource(R.string.reader_saved_position_unavailable_title)) },
            text = {
                Text(
                    stringResource(R.string.reader_saved_position_unavailable_message),
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissShorterDocumentWarning) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
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
            if (showSettings) {
                TopAppBar(
                    title = { Text(stringResource(R.string.reader_settings)) },
                    navigationIcon = {
                        TextButton(onClick = { showSettings = false }) {
                            Text(stringResource(R.string.action_back))
                        }
                    },
                )
            } else {
                TopAppBar(title = { Text(title, maxLines = 1) })
            }
        },
        bottomBar = {
            Column {
                if (!showSettings && state is ReaderUiState.Content) {
                    PlaybackControls(
                        state = playbackState,
                        chunkCount = state.chunkCount,
                        restoredChunk = state.restoredChunk,
                        document = state.document,
                        onPlay = onPlay,
                        onPause = onPause,
                        onStop = {
                            onStop()
                            coroutineScope.launch { listState.scrollToItem(0) }
                        },
                        onPrevious = onPrevious,
                        onNext = onNext,
                    )
                }
                ReaderBottomNavigation(
                    isSettingsSelected = showSettings,
                    onSettingsClick = { showSettings = true },
                    onSelectDocument = {
                        showSettings = false
                        onSelectDocument()
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showSettings) {
                SettingsScreen(
                    numSteps = numSteps,
                    ttsLanguage = ttsLanguage,
                    savedPositions = savedPositions,
                    onQualityClick = { showQualityDialog = true },
                    onLanguageClick = { showLanguageDialog = true },
                    onLegalClick = {
                        onLegalDocumentSelected(LegalDocumentType.PRIVACY)
                        showLegalDialog = true
                    },
                    onDeleteSavedPosition = { pendingPositionDeletion = it },
                )
            } else {
                when (state) {
                    ReaderUiState.Empty -> EmptyDocument()
                    is ReaderUiState.Loading -> LoadingDocument(state.progress)
                    is ReaderUiState.Content -> DocumentText(
                        document = state.document,
                        activeChunk = playbackState.activeChunk() ?: state.restoredChunk,
                        listState = listState,
                        onParagraphClick = onParagraphClick,
                    )

                    is ReaderUiState.Error -> ErrorDocument(state.reason)
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomNavigation(
    isSettingsSelected: Boolean,
    onSettingsClick: () -> Unit,
    onSelectDocument: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = isSettingsSelected,
            onClick = onSettingsClick,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(R.string.reader_settings)) },
        )
        NavigationBarItem(
            selected = false,
            onClick = onSelectDocument,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_document_add),
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(R.string.reader_select_document)) },
        )
    }
}

@Composable
private fun SettingsScreen(
    numSteps: Int,
    ttsLanguage: TtsLanguage,
    savedPositions: List<LastReadPosition>,
    onQualityClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onLegalClick: () -> Unit,
    onDeleteSavedPosition: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                modifier = Modifier.clickable(onClick = onQualityClick),
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.reader_speech_language)) },
                supportingContent = { Text(stringResource(ttsLanguage.labelResource())) },
                modifier = Modifier.clickable(onClick = onLanguageClick),
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.reader_legal_information)) },
                supportingContent = {
                    Text(stringResource(R.string.reader_legal_information_summary))
                },
                modifier = Modifier.clickable(onClick = onLegalClick),
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
                        TextButton(onClick = { onDeleteSavedPosition(position.fileName) }) {
                            Text(stringResource(R.string.action_delete))
                        }
                    },
                )
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
    var selectedDocument by rememberSaveable {
        mutableStateOf(LegalDocumentType.PRIVACY)
    }
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
    var selectedSteps by remember(currentNumSteps) { mutableFloatStateOf(currentNumSteps.toFloat()) }
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
                    horizontalArrangement = Arrangement.SpaceBetween
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

@Composable
private fun PlaybackControls(
    state: PlaybackState,
    chunkCount: Int,
    restoredChunk: SpeechChunk?,
    document: Document,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val activeChunk = state.activeChunk() ?: restoredChunk
            when (state) {
                is PlaybackState.Preparing -> Text(
                    stringResource(
                        R.string.reader_preparing_status,
                        state.chunk.id + 1,
                        chunkCount,
                        document.locationLabel(state.chunk.sectionIndex),
                    ),
                )

                is PlaybackState.Playing -> Text(
                    stringResource(
                        R.string.reader_playing_status,
                        state.chunk.id + 1,
                        chunkCount,
                        document.locationLabel(state.chunk.sectionIndex),
                    ),
                )

                is PlaybackState.Paused -> Text(
                    stringResource(
                        R.string.reader_paused_status,
                        state.chunk.id + 1,
                        chunkCount,
                        document.locationLabel(state.chunk.sectionIndex),
                    ),
                )

                is PlaybackState.Error -> Text(
                    stringResource(state.error.reason.toStringResource()),
                    color = MaterialTheme.colorScheme.error,
                )

                PlaybackState.Idle -> Text(
                    restoredChunk?.let {
                        stringResource(
                            R.string.reader_restored_status,
                            it.id + 1,
                            chunkCount,
                            document.locationLabel(it.sectionIndex),
                        )
                    } ?: pluralStringResource(
                        R.plurals.reader_ready_status,
                        chunkCount,
                        chunkCount,
                    ),
                )
            }
            activeChunk?.let { chunk ->
                Text(
                    text = chunk.text,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onPrevious,
                    enabled = chunkCount > 1 && (activeChunk == null || activeChunk.id > 0),
                ) { Text(stringResource(R.string.action_back)) }
                if (state is PlaybackState.Playing || state is PlaybackState.Preparing) {
                    Button(onClick = onPause) { Text(stringResource(R.string.action_pause)) }
                } else {
                    Button(onClick = onPlay, enabled = chunkCount > 0) {
                        Text(
                            stringResource(
                                if (state is PlaybackState.Paused) {
                                    R.string.action_resume
                                } else {
                                    R.string.action_play
                                },
                            ),
                        )
                    }
                }
                TextButton(
                    onClick = onNext,
                    enabled = chunkCount > 1 &&
                            (activeChunk == null || activeChunk.id < chunkCount.toLong() - 1L),
                ) {
                    Text(stringResource(R.string.action_next))
                }
                TextButton(onClick = onStop, enabled = state !is PlaybackState.Idle) {
                    Text(stringResource(R.string.action_restart))
                }
            }
        }
    }
}

@Composable
private fun EmptyDocument() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.reader_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.reader_empty_summary),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingDocument(progress: DocumentExtractionProgress?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        val message = when (progress?.stage) {
            DocumentExtractionProgress.Stage.EXTRACTING_TEXT ->
                stringResource(
                    R.string.reader_loading_pdf,
                    progress.sectionNumber,
                    progress.sectionCount,
                )

            DocumentExtractionProgress.Stage.RECOGNIZING_SCAN ->
                stringResource(
                    R.string.reader_loading_ocr,
                    progress.sectionNumber,
                    progress.sectionCount,
                )

            DocumentExtractionProgress.Stage.PARSING_EPUB ->
                stringResource(
                    R.string.reader_loading_epub,
                    progress.sectionNumber,
                    progress.sectionCount,
                )

            null -> stringResource(R.string.reader_loading)
        }
        Text(message, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun ErrorDocument(reason: DocumentReadException.Reason) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(reason.toStringResource()),
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.reader_error_hint),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DocumentReadException.Reason.toStringResource(): Int = when (this) {
    DocumentReadException.Reason.UNSUPPORTED_FILE -> R.string.document_error_unsupported
    DocumentReadException.Reason.ENCRYPTED -> R.string.document_error_encrypted
    DocumentReadException.Reason.NO_EXTRACTABLE_TEXT -> R.string.document_error_no_text
    DocumentReadException.Reason.OCR_FAILED -> R.string.document_error_ocr
    DocumentReadException.Reason.CORRUPTED -> R.string.document_error_corrupted
    DocumentReadException.Reason.ACCESS_DENIED -> R.string.document_error_access
    DocumentReadException.Reason.UNKNOWN -> R.string.document_error_unknown
}

private fun PlaybackError.Reason.toStringResource(): Int = when (this) {
    PlaybackError.Reason.MODEL_FILES_MISSING -> R.string.tts_model_files_missing
    PlaybackError.Reason.INITIALIZATION_FAILED -> R.string.tts_initialization_failed
    PlaybackError.Reason.SYNTHESIS_FAILED -> R.string.tts_synthesis_failed
    PlaybackError.Reason.AUDIO_OUTPUT_FAILED -> R.string.audio_output_failed
    PlaybackError.Reason.UNKNOWN -> R.string.playback_error_unknown
}

@Composable
private fun DocumentText(
    document: Document,
    activeChunk: SpeechChunk?,
    listState: LazyListState,
    onParagraphClick: (sectionIndex: Int, paragraphIndex: Int) -> Unit,
) {
    LaunchedEffect(activeChunk?.id) {
        val targetIndex = activeChunk?.let { ReaderPositionMapper.lazyListIndex(document, it) }
        if (targetIndex != null) listState.animateScrollToItem(targetIndex)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = document.sectionCountLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        document.sections.forEach { section ->
            item(key = "section-${section.index}") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = document.sectionHeading(section.index, section.title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            itemsIndexed(
                items = section.paragraphs,
                key = { index, _ -> "paragraph-${section.index}-$index" },
            ) { paragraphIndex, paragraph ->
                val isActive = activeChunk?.sectionIndex == section.index &&
                        activeChunk.paragraphIndex == paragraphIndex
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onParagraphClick(section.index, paragraphIndex)
                        },
                    color = if (isActive) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = paragraph.text,
                        modifier = Modifier.padding(
                            horizontal = if (isActive) 12.dp else 0.dp,
                            vertical = if (isActive) 8.dp else 0.dp,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
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

@Composable
private fun Document.sectionCountLabel(): String = when (format) {
    DocumentFormat.PDF -> pluralStringResource(
        R.plurals.reader_pdf_section_count,
        sections.size,
        sections.size,
    )
    DocumentFormat.EPUB -> pluralStringResource(
        R.plurals.reader_epub_section_count,
        sections.size,
        sections.size,
    )
}

@Composable
private fun Document.sectionHeading(index: Int, title: String?): String = when (format) {
    DocumentFormat.PDF -> stringResource(R.string.reader_pdf_section, index)
    DocumentFormat.EPUB -> title ?: stringResource(R.string.reader_epub_section, index)
}

@Composable
private fun Document.locationLabel(index: Int): String = when (format) {
    DocumentFormat.PDF -> stringResource(R.string.reader_pdf_section, index)
    DocumentFormat.EPUB -> stringResource(R.string.reader_epub_section, index)
}

private val SUPPORTED_MIME_TYPES = arrayOf("application/pdf", "application/epub+zip")
