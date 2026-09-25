package com.mutkuensert.seslendirmen.presentation.reader

import com.mutkuensert.seslendirmen.domain.model.PdfDocument
import com.mutkuensert.seslendirmen.domain.model.SpeechChunk

internal object ReaderPositionMapper {
    fun lazyListIndex(document: PdfDocument, chunk: SpeechChunk): Int? {
        var itemIndex = 1 // Document summary item.
        document.pages.forEach { page ->
            itemIndex++ // Page heading item.
            if (page.pageNumber == chunk.pageNumber) {
                if (chunk.paragraphIndex !in page.paragraphs.indices) return null
                return itemIndex + chunk.paragraphIndex
            }
            itemIndex += page.paragraphs.size
        }
        return null
    }
}
