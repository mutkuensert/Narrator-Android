package com.mutkuensert.seslendirmen.feature.reader.presentation.reader

import com.mutkuensert.seslendirmen.feature.reader.domain.model.Document
import com.mutkuensert.seslendirmen.feature.reader.domain.model.DocumentFormat
import com.mutkuensert.seslendirmen.feature.reader.domain.model.DocumentSection
import com.mutkuensert.seslendirmen.feature.reader.domain.model.DocumentParagraph
import com.mutkuensert.seslendirmen.feature.reader.domain.model.SpeechChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPositionMapperTest {
    private val document = Document(
        title = "Test",
        sections = listOf(
            DocumentSection(1, paragraphs = listOf(DocumentParagraph("A"), DocumentParagraph("B"))),
            DocumentSection(2, paragraphs = listOf(DocumentParagraph("C"), DocumentParagraph("D"), DocumentParagraph("E"))),
        ),
        format = DocumentFormat.EPUB,
    )

    @Test
    fun `maps paragraph to lazy list item including section headers`() {
        val chunk = SpeechChunk(3, sectionIndex = 2, paragraphIndex = 1, text = "D")

        assertEquals(6, ReaderPositionMapper.lazyListIndex(document, chunk))
    }

    @Test
    fun `returns null for unknown section`() {
        val chunk = SpeechChunk(0, sectionIndex = 9, paragraphIndex = 0, text = "X")

        assertNull(ReaderPositionMapper.lazyListIndex(document, chunk))
    }

    @Test
    fun `returns null for paragraph outside section`() {
        val chunk = SpeechChunk(0, sectionIndex = 1, paragraphIndex = 7, text = "X")

        assertNull(ReaderPositionMapper.lazyListIndex(document, chunk))
    }
}
