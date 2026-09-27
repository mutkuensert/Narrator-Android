package com.mutkuensert.seslendirmen.data.pdf

import com.mutkuensert.seslendirmen.domain.model.DocumentParagraph
import com.mutkuensert.seslendirmen.domain.repository.TextPreprocessor
import javax.inject.Inject
import javax.inject.Singleton

/** Extension point for future Turkish number, date, currency and abbreviation rules. */
fun interface TextNormalizer {
    fun normalize(text: String): String
}

@Singleton
class ConservativeTextPreprocessor @Inject constructor() : TextPreprocessor {
    private val normalizers: List<TextNormalizer> = emptyList()

    override fun preprocessPage(rawText: String, pageNumber: Int): List<DocumentParagraph> {
        val lines = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { line -> line.replace(HORIZONTAL_WHITESPACE, " ").trim() }
            .toMutableList()

        removeObviousPageNumber(lines, pageNumber)

        val paragraphs = mutableListOf<DocumentParagraph>()
        val current = StringBuilder()

        fun finishParagraph() {
            if (current.isEmpty()) return
            var text = current.toString().replace(REPEATED_WHITESPACE, " ").trim()
            normalizers.forEach { normalizer -> text = normalizer.normalize(text) }
            if (text.isNotEmpty()) paragraphs += DocumentParagraph(text)
            current.clear()
        }

        lines.forEach { line ->
            if (line.isBlank()) {
                finishParagraph()
                return@forEach
            }
            if (current.isEmpty()) {
                current.append(line)
            } else if (isHyphenatedLineBreak(current, line)) {
                current.setLength(current.length - 1)
                current.append(line)
            } else {
                current.append(' ').append(line)
            }
        }
        finishParagraph()
        return paragraphs
    }

    private fun removeObviousPageNumber(lines: MutableList<String>, pageNumber: Int) {
        val nonBlankIndices = lines.indices.filter { lines[it].isNotBlank() }
        if (nonBlankIndices.isEmpty()) return
        val candidates = setOf(nonBlankIndices.first(), nonBlankIndices.last())
        val pageNumberPattern = Regex(
            pattern = "^(?:[-–—]\\s*)?(?:sayfa\\s+)?$pageNumber(?:\\s*[-–—])?$",
            option = RegexOption.IGNORE_CASE,
        )
        candidates.forEach { index ->
            if (lines[index].matches(pageNumberPattern)) lines[index] = ""
        }
    }

    private fun isHyphenatedLineBreak(current: StringBuilder, nextLine: String): Boolean {
        if (!current.endsWith("-") || nextLine.isEmpty()) return false
        return nextLine.first().isLowerCase()
    }

    private companion object {
        val HORIZONTAL_WHITESPACE = Regex("[\\t\\u00A0 ]+")
        val REPEATED_WHITESPACE = Regex("\\s+")
    }
}
