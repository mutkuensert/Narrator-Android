package com.mutkuensert.seslendirmen.presentation.reader

import android.content.Context
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mutkuensert.seslendirmen.data.preferences.TtsPreferences
import com.mutkuensert.seslendirmen.domain.model.Document
import com.mutkuensert.seslendirmen.domain.model.DocumentFormat
import com.mutkuensert.seslendirmen.domain.model.SpeechChunk
import com.mutkuensert.seslendirmen.domain.playback.PlaybackState
import com.mutkuensert.seslendirmen.domain.repository.DocumentExtractionProgress
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
        onSelectDocument = { picker.launch(SUPPORTED_MIME_TYPES) },
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
    onSelectDocument: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onParagraphClick: (sectionIndex: Int, paragraphIndex: Int) -> Unit,
    onDismissShorterDocumentWarning: () -> Unit,
    onNumStepsChanged: (Int) -> Unit,
) {
    val title = (state as? ReaderUiState.Content)?.document?.title ?: "Seslendirmen"
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showLegalDialog by rememberSaveable { mutableStateOf(false) }
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
    if (showLegalDialog) {
        LegalNoticesDialog(onDismiss = { showLegalDialog = false })
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
            )
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
                    numSteps = numSteps,
                    isLegalSelected = showLegalDialog,
                    isQualitySelected = showQualityDialog,
                    onLegalClick = { showLegalDialog = true },
                    onQualityClick = { showQualityDialog = true },
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

                is ReaderUiState.Error -> ErrorDocument(state.message)
            }
        }
    }
}

@Composable
private fun ReaderBottomNavigation(
    numSteps: Int,
    isLegalSelected: Boolean,
    isQualitySelected: Boolean,
    onLegalClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSelectDocument: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = isLegalSelected,
            onClick = onLegalClick,
            icon = { Text("§", style = MaterialTheme.typography.titleMedium) },
            label = { Text("Yasal") },
        )
        NavigationBarItem(
            selected = isQualitySelected,
            onClick = onQualityClick,
            icon = { Text(numSteps.toString(), style = MaterialTheme.typography.titleMedium) },
            label = { Text("Kalite") },
        )
        NavigationBarItem(
            selected = false,
            onClick = onSelectDocument,
            icon = { Text("+", style = MaterialTheme.typography.titleMedium) },
            label = { Text("Belge seç") },
        )
    }
}

private enum class LegalDocument(val label: String, val assetPath: String) {
    PRIVACY("Gizlilik", "legal/PRIVACY_POLICY.txt"),
    NOTICES("Bildirimler", "legal/THIRD_PARTY_NOTICES.txt"),
    MODEL_LICENSE("Model lisansı", "tts/supertonic3/LICENSE"),
    APACHE("Apache-2.0", "legal/APACHE-2.0.txt"),
    ONNX_RUNTIME("ONNX Runtime", "legal/MIT-ONNXRUNTIME.txt"),
    BOUNCY_CASTLE("Bouncy Castle", "legal/BOUNCY-CASTLE.txt"),
}

@Composable
private fun LegalNoticesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedDocument by rememberSaveable { mutableStateOf(LegalDocument.PRIVACY) }
    val documentText = remember(selectedDocument) {
        readAssetText(context, selectedDocument.assetPath)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gizlilik ve yasal bilgiler") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LegalDocument.entries.forEach { document ->
                        TextButton(
                            onClick = { selectedDocument = document },
                            enabled = document != selectedDocument,
                        ) {
                            Text(document.label)
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
            TextButton(onClick = onDismiss) { Text("Kapat") }
        },
    )
}

private fun readAssetText(context: Context, assetPath: String): String =
    runCatching {
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
    }.getOrElse {
        "Yasal belge yüklenemedi: ${it.message ?: "bilinmeyen hata"}"
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
                    text = "Yüksek değerlerde ses daha doğal duyulur, ancak hazırlanması daha uzun " +
                            "sürebilir ve cihazınızı daha fazla yorabilir. Düşük değerlerde ses daha " +
                            "çabuk hazırlanır ve cihazınız daha az zorlanır; sesin doğallığı ise " +
                            "azalabilir. Dengeli bir deneyim için 5 veya 6 önerilir.",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
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
                    "Ses hazırlanıyor • Parça ${state.chunk.id + 1}/$chunkCount • " +
                            document.locationLabel(state.chunk.sectionIndex),
                )

                is PlaybackState.Playing -> Text(
                    "Oynatılıyor • Parça ${state.chunk.id + 1}/$chunkCount • " +
                            document.locationLabel(state.chunk.sectionIndex),
                )

                is PlaybackState.Paused -> Text(
                    "Duraklatıldı • Parça ${state.chunk.id + 1}/$chunkCount • " +
                            document.locationLabel(state.chunk.sectionIndex),
                )

                is PlaybackState.Error -> Text(
                    state.error.message,
                    color = MaterialTheme.colorScheme.error,
                )

                PlaybackState.Idle -> Text(
                    restoredChunk?.let {
                        "Kaldığınız yer hazır • Parça ${it.id + 1}/$chunkCount • " +
                                document.locationLabel(it.sectionIndex)
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
                if (state is PlaybackState.Playing || state is PlaybackState.Preparing) {
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
private fun EmptyDocument() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Dinlemek istediğiniz PDF veya EPUB belgesini alttaki “Belge seç” " +
                    "seçeneğiyle açın.",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "PDF sayfaları ve EPUB bölümleri otomatik olarak işlenir.",
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
                "Sayfa ${progress.sectionNumber}/${progress.sectionCount} okunuyor…"

            DocumentExtractionProgress.Stage.RECOGNIZING_SCAN ->
                "Sayfa ${progress.sectionNumber}/${progress.sectionCount} taranmış metin olarak tanınıyor…"

            DocumentExtractionProgress.Stage.PARSING_EPUB ->
                "EPUB bölümü ${progress.sectionNumber}/${progress.sectionCount} okunuyor…"

            null -> "Belge hazırlanıyor…"
        }
        Text(message, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun ErrorDocument(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Text(
            text = "Başka bir dosya denemek için alttaki “Belge seç” seçeneğini kullanın.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
                text = "${document.sections.size} ${document.sectionTypeLabel()}",
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

private fun Document.sectionTypeLabel(): String = when (format) {
    DocumentFormat.PDF -> "sayfa"
    DocumentFormat.EPUB -> "bölüm"
}

private fun Document.sectionHeading(index: Int, title: String?): String = when (format) {
    DocumentFormat.PDF -> "Sayfa $index"
    DocumentFormat.EPUB -> title ?: "Bölüm $index"
}

private fun Document.locationLabel(index: Int): String = when (format) {
    DocumentFormat.PDF -> "Sayfa $index"
    DocumentFormat.EPUB -> "Bölüm $index"
}

private val SUPPORTED_MIME_TYPES = arrayOf("application/pdf", "application/epub+zip")
