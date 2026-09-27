package com.mutkuensert.seslendirmen.feature.reader.domain.model

enum class TtsLanguage(
    val code: String,
    val localeTag: String,
) {
    TURKISH(code = "tr", localeTag = "tr-TR"),
    ENGLISH(code = "en", localeTag = "en-US"),
    ;

    companion object {
        fun fromCode(code: String?): TtsLanguage? {
            return entries.firstOrNull { it.code == code }
        }

        fun defaultForDeviceLanguage(languageCode: String?): TtsLanguage {
            return if (languageCode.equals(TURKISH.code, ignoreCase = true)) {
                TURKISH
            } else {
                ENGLISH
            }
        }
    }
}
