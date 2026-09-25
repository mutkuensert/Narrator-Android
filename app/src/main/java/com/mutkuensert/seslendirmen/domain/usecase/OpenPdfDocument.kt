package com.mutkuensert.seslendirmen.domain.usecase

import com.mutkuensert.seslendirmen.domain.model.PdfDocument
import com.mutkuensert.seslendirmen.domain.repository.PdfRepository
import com.mutkuensert.seslendirmen.domain.repository.PdfExtractionProgress
import javax.inject.Inject

class OpenPdfDocument @Inject constructor(
    private val repository: PdfRepository,
) {
    suspend operator fun invoke(
        uri: String,
        onProgress: (PdfExtractionProgress) -> Unit = {},
    ): PdfDocument = repository.openDocument(uri, onProgress)
}
