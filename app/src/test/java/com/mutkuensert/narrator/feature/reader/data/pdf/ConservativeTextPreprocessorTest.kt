package com.mutkuensert.narrator.feature.reader.data.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class ConservativeTextPreprocessorTest {
    private val preprocessor = ConservativeTextPreprocessor()

    @Test
    fun `joins wrapped lines and normalizes repeated whitespace`() {
        val result = preprocessor.preprocessPage(
            rawText = "Bu   birinci satırdır.\nBu da\tdevamıdır.",
            pageNumber = 1,
        )

        assertEquals(listOf("Bu birinci satırdır. Bu da devamıdır."), result.map { it.text })
    }

    @Test
    fun `joins lowercase word split by line ending hyphen`() {
        val result = preprocessor.preprocessPage(
            rawText = "Bu uygulama metinleri seslen-\ndirilebilir hale getirir.",
            pageNumber = 1,
        )

        assertEquals(
            listOf("Bu uygulama metinleri seslendirilebilir hale getirir."),
            result.map { it.text },
        )
    }

    @Test
    fun `joins hyphenated word across whitespace and extraction control characters`() {
        val result = preprocessor.preprocessPage(
            rawText = "Bu metin seslen-\u200B \r\n\u000C\t dirilebilir.",
            pageNumber = 1,
        )

        assertEquals(listOf("Bu metin seslendirilebilir."), result.map { it.text })
    }

    @Test
    fun `joins discretionary hyphens across more than one line break`() {
        val result = preprocessor.preprocessPage("Oku\u00AD\n\n\uFEFFnabilir metin", 1)

        assertEquals(listOf("Okunabilir metin"), result.map { it.text })
    }

    @Test
    fun `keeps hyphen when next line begins with uppercase letter`() {
        val result = preprocessor.preprocessPage("Ankara-\nİstanbul hattı", 1)

        assertEquals(listOf("Ankara- İstanbul hattı"), result.map { it.text })
    }

    @Test
    fun `joins an uppercase word split by a line ending hyphen`() {
        val result = preprocessor.preprocessPage("SESLEN-\nDİRİLEBİLİR METİN", 1)

        assertEquals(listOf("SESLENDİRİLEBİLİR METİN"), result.map { it.text })
    }

    @Test
    fun `preserves paragraphs separated by empty lines`() {
        val result = preprocessor.preprocessPage("Birinci paragraf.\n\nİkinci paragraf.", 1)

        assertEquals(listOf("Birinci paragraf.", "İkinci paragraf."), result.map { it.text })
    }

    @Test
    fun `removes invisible controls and spaces around punctuation`() {
        val result = preprocessor.preprocessPage(
            "Bu\u0000   bir test  ,  ( düzgün )  metin !",
            1,
        )

        assertEquals(listOf("Bu bir test, (düzgün) metin!"), result.map { it.text })
    }

    @Test
    fun `removes matching page number only at page boundary`() {
        val result = preprocessor.preprocessPage("Sayfa 3\n\nMetin.\n\n3\n\nSon.", 3)

        assertEquals(listOf("Metin.", "3", "Son."), result.map { it.text })
    }

    @Test
    fun `does not remove a different boundary number`() {
        val result = preprocessor.preprocessPage("2026\n\nBaşlık", 4)

        assertEquals(listOf("2026", "Başlık"), result.map { it.text })
    }
}
