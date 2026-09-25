package com.mutkuensert.seslendirmen.domain.model

data class PdfDocument(
    val title: String?,
    val pages: List<PdfPage>,
    val fileName: String? = null,
)

data class PdfPage(
    val pageNumber: Int,
    val paragraphs: List<PdfParagraph>,
)

data class PdfParagraph(
    val text: String,
)
