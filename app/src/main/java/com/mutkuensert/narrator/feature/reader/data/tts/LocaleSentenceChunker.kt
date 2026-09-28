package com.mutkuensert.narrator.feature.reader.data.tts

import com.mutkuensert.narrator.feature.reader.domain.model.Document
import com.mutkuensert.narrator.feature.reader.domain.model.SpeechChunk
import com.mutkuensert.narrator.feature.reader.domain.model.TtsLanguage
import com.mutkuensert.narrator.feature.reader.domain.repository.SpeechChunker
import java.text.BreakIterator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocaleSentenceChunker @Inject constructor() : SpeechChunker {
    override fun createChunks(document: Document, language: TtsLanguage): List<SpeechChunk> {
        var nextId = 0L
        return buildList {
            document.sections.forEach { section ->
                section.paragraphs.forEachIndexed { paragraphIndex, paragraph ->
                    splitParagraph(paragraph.text, language).forEach { text ->
                        add(
                            SpeechChunk(
                                id = nextId++,
                                sectionIndex = section.index,
                                paragraphIndex = paragraphIndex,
                                text = text,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun splitParagraph(text: String, language: TtsLanguage): List<String> {
        val sentenceIterator = BreakIterator.getSentenceInstance(
            Locale.forLanguageTag(language.localeTag),
        )
        sentenceIterator.setText(text)
        val sentences = mutableListOf<String>()
        var start = sentenceIterator.first()
        var end = sentenceIterator.next()
        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end).trim()
            if (sentence.isNotEmpty()) sentences += splitLongText(sentence)
            start = end
            end = sentenceIterator.next()
        }
        if (sentences.isEmpty() && text.isNotBlank()) sentences += splitLongText(text.trim())
        return sentences
    }

    private fun splitLongText(text: String): List<String> {
        if (text.length <= MAX_CHARS) return listOf(text)
        val result = mutableListOf<String>()
        var remaining = text
        while (remaining.length > MAX_CHARS) {
            val splitAt = findSafeSplit(remaining, MAX_CHARS)
            result += remaining.substring(0, splitAt).trim()
            remaining = remaining.substring(splitAt).trimStart()
        }
        if (remaining.isNotEmpty()) result += remaining
        return result
    }

    private fun findSafeSplit(text: String, preferredEnd: Int): Int {
        val minimum = (preferredEnd * 0.6).toInt()
        for (index in preferredEnd downTo minimum) {
            if (text[index - 1] in SAFE_BOUNDARIES) return index
        }
        for (index in preferredEnd downTo 1) {
            if (text[index - 1].isWhitespace()) return index
        }
        return preferredEnd
    }

    private companion object {
        const val MAX_CHARS = 280
        val SAFE_BOUNDARIES = charArrayOf('.', '!', '?', ';', ':', ',', ' ')
    }
}

