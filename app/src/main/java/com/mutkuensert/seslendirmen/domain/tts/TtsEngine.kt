package com.mutkuensert.seslendirmen.domain.tts

import com.mutkuensert.seslendirmen.domain.model.AudioData

interface TtsEngine {
    suspend fun initialize()
    suspend fun synthesize(text: String): AudioData
    fun release()
}

class TtsEngineException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(reason.message, cause) {
    enum class Reason(val message: String) {
        MODEL_FILES_MISSING("Türkçe ses modeli dosyaları bulunamadı."),
        INITIALIZATION_FAILED("Çevrimdışı ses modeli başlatılamadı."),
        SYNTHESIS_FAILED("Metin seslendirilirken bir hata oluştu."),
    }
}
