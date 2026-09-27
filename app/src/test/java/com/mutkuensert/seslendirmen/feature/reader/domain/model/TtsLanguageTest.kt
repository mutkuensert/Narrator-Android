package com.mutkuensert.seslendirmen.feature.reader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsLanguageTest {
    @Test
    fun `resolves supported persisted language codes`() {
        assertEquals(TtsLanguage.TURKISH, TtsLanguage.fromCode("tr"))
        assertEquals(TtsLanguage.ENGLISH, TtsLanguage.fromCode("en"))
    }

    @Test
    fun `returns no language for missing or unsupported persisted codes`() {
        assertEquals(null, TtsLanguage.fromCode(null))
        assertEquals(null, TtsLanguage.fromCode("de"))
    }

    @Test
    fun `defaults to Turkish when device language is Turkish`() {
        assertEquals(TtsLanguage.TURKISH, TtsLanguage.defaultForDeviceLanguage("tr"))
        assertEquals(TtsLanguage.TURKISH, TtsLanguage.defaultForDeviceLanguage("TR"))
    }

    @Test
    fun `defaults to English for every non-Turkish device language`() {
        assertEquals(TtsLanguage.ENGLISH, TtsLanguage.defaultForDeviceLanguage("en"))
        assertEquals(TtsLanguage.ENGLISH, TtsLanguage.defaultForDeviceLanguage("de"))
        assertEquals(TtsLanguage.ENGLISH, TtsLanguage.defaultForDeviceLanguage(null))
    }
}
