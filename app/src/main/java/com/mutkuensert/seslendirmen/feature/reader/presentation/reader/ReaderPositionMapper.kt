package com.mutkuensert.seslendirmen.feature.reader.presentation.reader

import com.mutkuensert.seslendirmen.feature.reader.domain.model.Document
import com.mutkuensert.seslendirmen.feature.reader.domain.model.SpeechChunk

internal object ReaderPositionMapper {
    fun lazyListIndex(document: Document, chunk: SpeechChunk): Int? {
        var itemIndex = 1 // Document summary item.
        document.sections.forEach { section ->
            itemIndex++ // Section heading item.
            if (section.index == chunk.sectionIndex) {
                if (chunk.paragraphIndex !in section.paragraphs.indices) return null
                return itemIndex + chunk.paragraphIndex
            }
            itemIndex += section.paragraphs.size
        }
        return null
    }
}
