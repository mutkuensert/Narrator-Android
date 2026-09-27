package com.mutkuensert.seslendirmen.feature.reader.domain.repository

import com.mutkuensert.seslendirmen.feature.reader.domain.model.Document
import com.mutkuensert.seslendirmen.feature.reader.domain.model.LastReadPosition
import kotlinx.coroutines.flow.StateFlow

interface DocumentRepository {
    suspend fun openDocument(
        uri: String,
        onProgress: (DocumentExtractionProgress) -> Unit = {},
    ): Document
}

interface LastReadPositionRepository {
    val positions: StateFlow<List<LastReadPosition>>
    fun read(fileName: String): LastReadPosition?
    fun save(position: LastReadPosition)
    fun clear(fileName: String)
}

data class DocumentExtractionProgress(
    val sectionNumber: Int,
    val sectionCount: Int,
    val stage: Stage,
) {
    enum class Stage {
        EXTRACTING_TEXT,
        RECOGNIZING_SCAN,
        PARSING_EPUB,
    }
}

class DocumentReadException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(cause) {
    enum class Reason {
        UNSUPPORTED_FILE,
        ENCRYPTED,
        NO_EXTRACTABLE_TEXT,
        OCR_FAILED,
        CORRUPTED,
        ACCESS_DENIED,
        UNKNOWN,
    }
}
