package com.mutkuensert.seslendirmen.data.tts

import com.mutkuensert.seslendirmen.domain.model.PdfDocument
import com.mutkuensert.seslendirmen.domain.model.PdfPage
import com.mutkuensert.seslendirmen.domain.model.PdfParagraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishSentenceChunkerTest {
    private val chunker = TurkishSentenceChunker()

    @Test
    fun `uses sentence boundaries and preserves source positions`() {
        val document = PdfDocument(
            title = "Test",
            pages = listOf(
                PdfPage(
                    pageNumber = 2,
                    paragraphs = listOf(
                        PdfParagraph("Birinci cümle. İkinci cümle!"),
                        PdfParagraph("Üçüncü cümle?"),
                    ),
                ),
            ),
        )

        val chunks = chunker.createChunks(document)

        assertEquals(listOf(0L, 1L, 2L), chunks.map { it.id })
        assertEquals(listOf("Birinci cümle.", "İkinci cümle!", "Üçüncü cümle?"), chunks.map { it.text })
        assertEquals(listOf(2, 2, 2), chunks.map { it.pageNumber })
        assertEquals(listOf(0, 0, 1), chunks.map { it.paragraphIndex })
    }

    @Test
    fun `splits extremely long sentences into bounded chunks`() {
        val longSentence = List(100) { "kelime" }.joinToString(" ") + "."
        val document = PdfDocument(
            title = null,
            pages = listOf(PdfPage(1, listOf(PdfParagraph(longSentence)))),
        )

        val chunks = chunker.createChunks(document)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.text.length <= 280 })
        assertEquals(longSentence, chunks.joinToString(" ") { it.text })
    }
}
