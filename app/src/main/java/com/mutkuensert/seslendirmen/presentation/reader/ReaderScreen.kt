package com.mutkuensert.seslendirmen.presentation.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mutkuensert.seslendirmen.domain.model.PdfDocument

@Composable
fun ReaderRoute(viewModel: ReaderViewModel) {
    val state by viewModel.uiState.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.openDocument(it.toString()) }
    }
    ReaderScreen(
        state = state,
        onSelectPdf = { picker.launch(arrayOf(PDF_MIME_TYPE)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    state: ReaderUiState,
    onSelectPdf: () -> Unit,
) {
    val title = (state as? ReaderUiState.Content)?.document?.title ?: "Seslendirmen"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                actions = {
                    Button(
                        onClick = onSelectPdf,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(if (state is ReaderUiState.Content) "Başka PDF" else "PDF seç")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state) {
                ReaderUiState.Empty -> EmptyDocument(onSelectPdf)
                ReaderUiState.Loading -> LoadingDocument()
                is ReaderUiState.Content -> DocumentText(state.document)
                is ReaderUiState.Error -> ErrorDocument(state.message, onSelectPdf)
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
            text = "Dinlemek istediğiniz metin tabanlı PDF belgesini seçin.",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Taranmış ve yalnızca görüntü içeren belgeler henüz desteklenmiyor.",
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onSelectPdf) { Text("PDF seç") }
    }
}

@Composable
private fun LoadingDocument() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text("PDF metni çıkarılıyor…", modifier = Modifier.padding(top = 16.dp))
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
private fun DocumentText(document: PdfDocument) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
            ) { _, paragraph ->
                Text(
                    text = paragraph.text,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private const val PDF_MIME_TYPE = "application/pdf"
