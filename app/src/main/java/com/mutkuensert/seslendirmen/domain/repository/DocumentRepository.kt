package com.mutkuensert.seslendirmen.domain.repository

import com.mutkuensert.seslendirmen.domain.model.Document

interface DocumentRepository {
    suspend fun openDocument(
        uri: String,
        onProgress: (DocumentExtractionProgress) -> Unit = {},
    ): Document
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
) : Exception(reason.message, cause) {
    enum class Reason(val message: String) {
        UNSUPPORTED_FILE("Yalnızca PDF ve EPUB belgeleri destekleniyor."),
        ENCRYPTED("Şifreli PDF belgeleri henüz desteklenmiyor."),
        NO_EXTRACTABLE_TEXT(
            "Bu belgede okunabilir metin bulunamadı. Tarama kalitesi veya EPUB içeriği yetersiz olabilir.",
        ),
        OCR_FAILED("Taranmış PDF sayfasındaki metin tanınamadı."),
        CORRUPTED("Belge açılamadı veya bozuk."),
        ACCESS_DENIED("Belgeye erişilemiyor. Lütfen dosyayı yeniden seçin."),
        UNKNOWN("Belge okunurken beklenmeyen bir hata oluştu."),
    }
}
