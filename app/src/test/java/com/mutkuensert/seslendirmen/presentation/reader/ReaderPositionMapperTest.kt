package com.mutkuensert.seslendirmen.presentation.reader

import com.mutkuensert.seslendirmen.domain.model.PdfDocument
import com.mutkuensert.seslendirmen.domain.model.PdfPage
import com.mutkuensert.seslendirmen.domain.model.PdfParagraph
import com.mutkuensert.seslendirmen.domain.model.SpeechChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPositionMapperTest {
    private val document = PdfDocument(
        title = "Test",
        pages = listOf(
            PdfPage(1, listOf(PdfParagraph("A"), PdfParagraph("B"))),
            PdfPage(2, listOf(PdfParagraph("C"), PdfParagraph("D"), PdfParagraph("E"))),
        ),
    )

    @Test
    fun `maps paragraph to lazy list item including page headers`() {
        val chunk = SpeechChunk(3, pageNumber = 2, paragraphIndex = 1, text = "D")

        assertEquals(6, ReaderPositionMapper.lazyListIndex(document, chunk))
    }

    @Test
    fun `returns null for unknown page`() {
        val chunk = SpeechChunk(0, pageNumber = 9, paragraphIndex = 0, text = "X")

        assertNull(ReaderPositionMapper.lazyListIndex(document, chunk))
    }

    @Test
    fun `returns null for paragraph outside page`() {
        val chunk = SpeechChunk(0, pageNumber = 1, paragraphIndex = 7, text = "X")

        assertNull(ReaderPositionMapper.lazyListIndex(document, chunk))
    }
}
