package com.mutkuensert.seslendirmen.feature.reader.data.tts

import com.mutkuensert.seslendirmen.feature.reader.domain.model.Document
import com.mutkuensert.seslendirmen.feature.reader.domain.model.DocumentFormat
import com.mutkuensert.seslendirmen.feature.reader.domain.model.DocumentSection
import com.mutkuensert.seslendirmen.feature.reader.domain.model.DocumentParagraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishSentenceChunkerTest {
    private val chunker = TurkishSentenceChunker()

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

        val chunks = chunker.createChunks(document)

        assertEquals(listOf(0L, 1L, 2L), chunks.map { it.id })
        assertEquals(listOf("Birinci cümle.", "İkinci cümle!", "Üçüncü cümle?"), chunks.map { it.text })
        assertEquals(listOf(2, 2, 2), chunks.map { it.sectionIndex })
        assertEquals(listOf(0, 0, 1), chunks.map { it.paragraphIndex })
    }

    @Test
    fun `splits extremely long sentences into bounded chunks`() {
        val longSentence = List(100) { "kelime" }.joinToString(" ") + "."
        val document = Document(
            title = null,
            sections = listOf(DocumentSection(1, paragraphs = listOf(DocumentParagraph(longSentence)))),
            format = DocumentFormat.PDF,
        )

        val chunks = chunker.createChunks(document)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.text.length <= 280 })
        assertEquals(longSentence, chunks.joinToString(" ") { it.text })
    }
}
