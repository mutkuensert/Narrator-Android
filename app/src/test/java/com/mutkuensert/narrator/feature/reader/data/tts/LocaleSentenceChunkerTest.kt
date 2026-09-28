package com.mutkuensert.narrator.feature.reader.data.tts

import com.mutkuensert.narrator.feature.reader.domain.model.Document
import com.mutkuensert.narrator.feature.reader.domain.model.DocumentFormat
import com.mutkuensert.narrator.feature.reader.domain.model.DocumentSection
import com.mutkuensert.narrator.feature.reader.domain.model.DocumentParagraph
import com.mutkuensert.narrator.feature.reader.domain.model.TtsLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleSentenceChunkerTest {
    private val chunker = LocaleSentenceChunker()

    @Test
    fun `uses sentence boundaries and preserves source positions`() {
        val document = Document(
            title = "Test",
            sections = listOf(
                DocumentSection(
                    index = 2,
                    paragraphs = listOf(
                        DocumentParagraph("Birinci cümle. İkinci cümle!"),
                        DocumentParagraph("Üçüncü cümle?"),
                    ),
                ),
            ),
            format = DocumentFormat.EPUB,
        )

        val chunks = chunker.createChunks(document, TtsLanguage.TURKISH)

        assertEquals(listOf(0L, 1L, 2L), chunks.map { it.id })
        assertEquals(listOf("Birinci cümle.", "İkinci cümle!", "Üçüncü cümle?"), chunks.map { it.text })
        assertEquals(listOf(2, 2, 2), chunks.map { it.sectionIndex })
        assertEquals(listOf(0, 0, 1), chunks.map { it.paragraphIndex })
    }

    @Test
    fun `uses English sentence boundaries when English is selected`() {
        val document = Document(
            title = "Test",
            sections = listOf(
                DocumentSection(
                    index = 1,
                    paragraphs = listOf(
                        DocumentParagraph("First sentence. Second sentence! Is this the third?"),
                    ),
                ),
            ),
            format = DocumentFormat.EPUB,
        )

        val chunks = chunker.createChunks(document, TtsLanguage.ENGLISH)

        assertEquals(
            listOf("First sentence.", "Second sentence!", "Is this the third?"),
            chunks.map { it.text },
        )
    }

    @Test
    fun `splits extremely long sentences into bounded chunks`() {
        val longSentence = List(100) { "kelime" }.joinToString(" ") + "."
        val document = Document(
            title = null,
            sections = listOf(DocumentSection(1, paragraphs = listOf(DocumentParagraph(longSentence)))),
            format = DocumentFormat.PDF,
        )

        val chunks = chunker.createChunks(document, TtsLanguage.TURKISH)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.text.length <= 280 })
        assertEquals(longSentence, chunks.joinToString(" ") { it.text })
    }
}

