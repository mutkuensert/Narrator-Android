package com.mutkuensert.narrator.feature.reader.presentation.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mutkuensert.narrator.feature.reader.domain.model.Document
import com.mutkuensert.narrator.feature.reader.domain.model.DocumentFormat
import com.mutkuensert.narrator.feature.reader.domain.model.SpeechChunk
import com.mutkuensert.narrator.feature.reader.domain.playback.PlaybackError
import com.mutkuensert.narrator.feature.reader.domain.playback.PlaybackState
import com.mutkuensert.narrator.feature.reader.domain.repository.DocumentExtractionProgress
import com.mutkuensert.narrator.feature.reader.domain.repository.DocumentReadException
import com.mutkuensert.narrator.R
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.handleOpenDocument(it.toString()) }
    }
    ReaderContent(
        state = state,
        playbackState = playbackState,
        onOpenSettings = viewModel::handleOpenSettings,
        onSelectDocument = { picker.launch(SUPPORTED_MIME_TYPES) },
        onPlay = viewModel::handlePlay,
        onPause = viewModel::handlePause,
        onStop = viewModel::handleStop,
        onPrevious = viewModel::handlePrevious,
        onNext = viewModel::handleNext,
        onParagraphClick = viewModel::handlePlayFromParagraph,
        onDismissShorterDocumentWarning = viewModel::handleDismissShorterDocumentWarning,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderContent(
    state: ReaderUiState,
    playbackState: PlaybackState,
    onOpenSettings: () -> Unit,
    onSelectDocument: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onParagraphClick: (sectionIndex: Int, paragraphIndex: Int) -> Unit,
    onDismissShorterDocumentWarning: () -> Unit,
) {
    val title = (state as? ReaderUiState.Content)?.document?.title
        ?: stringResource(R.string.app_name)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
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
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title, maxLines = 1) })
        },
        bottomBar = {
            Column {
                if (state is ReaderUiState.Content) {
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
                    onSettingsClick = onOpenSettings,
                    onSelectDocument = onSelectDocument,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
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

@Composable
private fun ReaderBottomNavigation(
    onSettingsClick: () -> Unit,
    onSelectDocument: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = false,
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
