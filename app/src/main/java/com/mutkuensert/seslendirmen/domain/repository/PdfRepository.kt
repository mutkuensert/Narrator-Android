package com.mutkuensert.seslendirmen.domain.repository

import com.mutkuensert.seslendirmen.domain.model.PdfDocument

interface PdfRepository {
    suspend fun openDocument(uri: String): PdfDocument
}

class PdfReadException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(reason.message, cause) {
    enum class Reason(val message: String) {
        UNSUPPORTED_FILE("Seçilen dosya desteklenen bir PDF değil."),
        ENCRYPTED("Şifreli PDF belgeleri henüz desteklenmiyor."),
        NO_EXTRACTABLE_TEXT(
            "Bu belgede okunabilir metin bulunamadı. Taranmış veya yalnızca görüntü içeren PDF'ler henüz desteklenmiyor.",
        ),
        CORRUPTED("PDF belgesi açılamadı veya bozuk."),
        ACCESS_DENIED("PDF belgesine erişilemiyor. Lütfen dosyayı yeniden seçin."),
        UNKNOWN("PDF okunurken beklenmeyen bir hata oluştu."),
    }
}
