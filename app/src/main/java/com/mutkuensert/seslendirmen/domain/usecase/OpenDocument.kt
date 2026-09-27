package com.mutkuensert.seslendirmen.domain.usecase

import com.mutkuensert.seslendirmen.domain.model.Document
import com.mutkuensert.seslendirmen.domain.repository.DocumentExtractionProgress
import com.mutkuensert.seslendirmen.domain.repository.DocumentRepository
import javax.inject.Inject

class OpenDocument @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(
        uri: String,
        onProgress: (DocumentExtractionProgress) -> Unit = {},
    ): Document = repository.openDocument(uri, onProgress)
}
