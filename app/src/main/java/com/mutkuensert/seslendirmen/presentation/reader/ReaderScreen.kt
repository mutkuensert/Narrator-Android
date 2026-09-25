package com.mutkuensert.seslendirmen.presentation.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mutkuensert.seslendirmen.domain.model.PdfDocument
import com.mutkuensert.seslendirmen.domain.model.SpeechChunk
import com.mutkuensert.seslendirmen.domain.playback.PlaybackState
import com.mutkuensert.seslendirmen.domain.repository.PdfExtractionProgress
import com.mutkuensert.seslendirmen.data.preferences.TtsPreferences
import kotlinx.coroutines.launch

@Composable
fun ReaderRoute(viewModel: ReaderViewModel) {
    val state by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val numSteps by viewModel.numSteps.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.openDocument(it.toString()) }
    }
    ReaderScreen(
        state = state,
        playbackState = playbackState,
        numSteps = numSteps,
        onSelectPdf = { picker.launch(arrayOf(PDF_MIME_TYPE)) },
        onPlay = viewModel::play,
        onPause = viewModel::pause,
        onStop = viewModel::stop,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onParagraphClick = viewModel::playFromParagraph,
        onDismissShorterDocumentWarning = viewModel::dismissShorterDocumentWarning,
        onNumStepsChanged = viewModel::setNumSteps,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    state: ReaderUiState,
    playbackState: PlaybackState,
    numSteps: Int,
    onSelectPdf: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onParagraphClick: (pageNumber: Int, paragraphIndex: Int) -> Unit,
    onDismissShorterDocumentWarning: () -> Unit,
    onNumStepsChanged: (Int) -> Unit,
) {
    val title = (state as? ReaderUiState.Content)?.document?.title ?: "Seslendirmen"
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showQualityDialog by rememberSaveable { mutableStateOf(false) }
    if (state is ReaderUiState.Content && state.showShorterDocumentWarning) {
        AlertDialog(
            onDismissRequest = onDismissShorterDocumentWarning,
            title = { Text("Kayıtlı konum kullanılamadı") },
            text = {
                Text(
                    "Aynı isimli bu belge, daha önce açılan belgeden daha kısa. " +
                        "Eski konum uygulanmadı. Bundan sonra bu belgede son okuduğunuz yer " +
                        "kaydedilecek.",
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissShorterDocumentWarning) { Text("Tamam") }
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                actions = {
                    TextButton(onClick = { showQualityDialog = true }) {
                        Text("Kalite: $numSteps")
                    }
                    Button(
                        onClick = onSelectPdf,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(if (state is ReaderUiState.Content) "Başka PDF" else "PDF seç")
                    }
                },
            )
        },
        bottomBar = {
            if (state is ReaderUiState.Content) {
                PlaybackControls(
                    state = playbackState,
                    chunkCount = state.chunkCount,
                    restoredChunk = state.restoredChunk,
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
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state) {
                ReaderUiState.Empty -> EmptyDocument(onSelectPdf)
                is ReaderUiState.Loading -> LoadingDocument(state.progress)
                is ReaderUiState.Content -> DocumentText(
                    document = state.document,
                    activeChunk = playbackState.activeChunk() ?: state.restoredChunk,
                    listState = listState,
                    onParagraphClick = onParagraphClick,
                )
                is ReaderUiState.Error -> ErrorDocument(state.message, onSelectPdf)
            }
        }
    }
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
        title = { Text("Okuma kalitesi") },
        text = {
            Column {
                Text("Adım sayısı: $steps")
                Text(
                    text = "Yüksek değer daha kaliteli ses üretebilir, ancak hazırlanması daha uzun sürer.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = selectedSteps,
                    onValueChange = { selectedSteps = it },
                    valueRange = TtsPreferences.MIN_NUM_STEPS.toFloat()..
                        TtsPreferences.MAX_NUM_STEPS.toFloat(),
                    steps = TtsPreferences.MAX_NUM_STEPS - TtsPreferences.MIN_NUM_STEPS - 1,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Hızlı", style = MaterialTheme.typography.labelSmall)
                    Text("Kaliteli", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(steps) }) { Text("Uygula") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
    )
}

@Composable
private fun PlaybackControls(
    state: PlaybackState,
    chunkCount: Int,
    restoredChunk: SpeechChunk?,
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
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val activeChunk = state.activeChunk() ?: restoredChunk
            when (state) {
                is PlaybackState.Preparing -> Text(
                    "Ses hazırlanıyor • Bölüm ${state.chunk.id + 1}/$chunkCount • " +
                        "Sayfa ${state.chunk.pageNumber}",
                )
                is PlaybackState.Playing -> Text(
                    "Oynatılıyor • Bölüm ${state.chunk.id + 1}/$chunkCount • " +
                        "Sayfa ${state.chunk.pageNumber}",
                )
                is PlaybackState.Paused -> Text(
                    "Duraklatıldı • Bölüm ${state.chunk.id + 1}/$chunkCount • " +
                        "Sayfa ${state.chunk.pageNumber}",
                )
                is PlaybackState.Error -> Text(
                    state.error.message,
                    color = MaterialTheme.colorScheme.error,
                )
                PlaybackState.Idle -> Text(
                    restoredChunk?.let {
                        "Kaldığınız yer hazır • Bölüm ${it.id + 1}/$chunkCount • " +
                            "Sayfa ${it.pageNumber}"
                    } ?: "$chunkCount konuşma bölümü hazır",
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
                ) { Text("Geri") }
                if (state is PlaybackState.Playing) {
                    Button(onClick = onPause) { Text("Duraklat") }
                } else {
                    Button(onClick = onPlay, enabled = chunkCount > 0) {
                        Text(if (state is PlaybackState.Paused) "Sürdür" else "Oynat")
                    }
                }
                TextButton(
                    onClick = onNext,
                    enabled = chunkCount > 1 &&
                        (activeChunk == null || activeChunk.id < chunkCount.toLong() - 1L),
                ) {
                    Text("İleri")
                }
                TextButton(onClick = onStop, enabled = state !is PlaybackState.Idle) {
                    Text("Başa al")
                }
            }
        }
    }
}

@Composable
private fun EmptyDocument(onSelectPdf: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Dinlemek istediğiniz PDF belgesini seçin.",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Metin tabanlı ve taranmış PDF belgeleri otomatik olarak işlenir.",
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onSelectPdf) { Text("PDF seç") }
    }
}

@Composable
private fun LoadingDocument(progress: PdfExtractionProgress?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        val message = when (progress?.stage) {
            PdfExtractionProgress.Stage.EXTRACTING_TEXT ->
                "Sayfa ${progress.pageNumber}/${progress.pageCount} okunuyor…"
            PdfExtractionProgress.Stage.RECOGNIZING_SCAN ->
                "Sayfa ${progress.pageNumber}/${progress.pageCount} taranmış metin olarak tanınıyor…"
            null -> "PDF hazırlanıyor…"
        }
        Text(message, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun ErrorDocument(message: String, onSelectPdf: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onSelectPdf, modifier = Modifier.padding(top = 20.dp)) {
            Text("Başka PDF seç")
        }
    }
}

@Composable
private fun DocumentText(
    document: PdfDocument,
    activeChunk: SpeechChunk?,
    listState: LazyListState,
    onParagraphClick: (pageNumber: Int, paragraphIndex: Int) -> Unit,
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
                text = "${document.pages.size} sayfa",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        document.pages.forEach { page ->
            item(key = "page-${page.pageNumber}") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Sayfa ${page.pageNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            itemsIndexed(
                items = page.paragraphs,
                key = { index, _ -> "paragraph-${page.pageNumber}-$index" },
            ) { paragraphIndex, paragraph ->
                val isActive = activeChunk?.pageNumber == page.pageNumber &&
                    activeChunk.paragraphIndex == paragraphIndex
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onParagraphClick(page.pageNumber, paragraphIndex)
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

private const val PDF_MIME_TYPE = "application/pdf"
