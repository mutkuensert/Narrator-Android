package com.mutkuensert.seslendirmen.domain.model

data class Document(
    val title: String?,
    val sections: List<DocumentSection>,
    val fileName: String? = null,
    val format: DocumentFormat,
)

data class DocumentSection(
    val index: Int,
    val title: String? = null,
    val paragraphs: List<DocumentParagraph>,
)

data class DocumentParagraph(
    val text: String,
)

enum class DocumentFormat {
    PDF,
    EPUB,
}
