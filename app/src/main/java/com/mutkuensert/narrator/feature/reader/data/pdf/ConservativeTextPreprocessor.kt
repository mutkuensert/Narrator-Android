package com.mutkuensert.narrator.feature.reader.data.pdf

import com.mutkuensert.narrator.feature.reader.domain.model.DocumentParagraph
import com.mutkuensert.narrator.feature.reader.domain.repository.TextPreprocessor
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
        val lines = normalizeLayout(rawText)
            .split('\n')
            .map { line -> line.replace(HORIZONTAL_WHITESPACE, " ").trim() }
            .toMutableList()

        removeObviousPageNumber(lines, pageNumber)

        val paragraphs = mutableListOf<DocumentParagraph>()
        val current = StringBuilder()

        fun finishParagraph() {
            if (current.isEmpty()) return
            var text = current.toString()
                .replace(REPEATED_WHITESPACE, " ")
                .replace(SPACE_BEFORE_PUNCTUATION, "$1")
                .replace(SPACE_AFTER_OPENING_PUNCTUATION, "$1")
                .replace(SPACE_BEFORE_CLOSING_PUNCTUATION, "$1")
                .trim()
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

    /**
     * Removes characters that commonly leak from PDF extraction/OCR while retaining line breaks
     * long enough to recover words split at the end of a visual line.
     */
    private fun normalizeLayout(rawText: String): String {
        val normalizedLineEndings = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u0085', '\n')
            .replace('\u2028', '\n')
            .replace("\u2029", "\n\n")
        val dehyphenated = joinHyphenatedLineBreaks(normalizedLineEndings)

        return buildString(dehyphenated.length) {
            dehyphenated.forEach { character ->
                when {
                    character == '\n' || character == '\t' -> append(character)
                    character == SOFT_HYPHEN -> Unit
                    character.isWhitespace() -> append(' ')
                    Character.getType(character) == Character.FORMAT.toInt() -> Unit
                    character.isISOControl() -> Unit
                    else -> append(character)
                }
            }
        }
    }

    private fun joinHyphenatedLineBreaks(text: String): String = buildString(text.length) {
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (character in WORD_BREAK_HYPHENS && lastOrNull()?.isLetter() == true) {
                var nextIndex = index + 1
                var crossedLineBreak = false
                while (nextIndex < text.length && text[nextIndex].isLayoutNoise()) {
                    if (
                        text[nextIndex] == '\n' ||
                        text[nextIndex] == '\u000B' ||
                        text[nextIndex] == '\u000C'
                    ) {
                        crossedLineBreak = true
                    }
                    nextIndex++
                }
                val nextCharacter = text.getOrNull(nextIndex)
                if (crossedLineBreak && shouldJoinWord(nextCharacter)) {
                    index = nextIndex
                    continue
                }
            }
            append(character)
            index++
        }
    }

    private fun StringBuilder.shouldJoinWord(nextCharacter: Char?): Boolean {
        if (nextCharacter?.isLowerCase() == true) return true
        if (nextCharacter?.isUpperCase() != true) return false

        var letterCount = 0
        var index = lastIndex
        while (index >= 0 && this[index].isLetter()) {
            if (!this[index].isUpperCase()) return false
            letterCount++
            index--
        }
        return letterCount >= MIN_UPPERCASE_FRAGMENT_LENGTH
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
        if (current.lastOrNull() !in WORD_BREAK_HYPHENS || nextLine.isEmpty()) return false
        return nextLine.first().isLowerCase()
    }

    private fun Char.isLayoutNoise(): Boolean =
        isWhitespace() || isISOControl() || Character.getType(this) == Character.FORMAT.toInt()

    private companion object {
        const val SOFT_HYPHEN = '\u00AD'
        const val MIN_UPPERCASE_FRAGMENT_LENGTH = 2
        val WORD_BREAK_HYPHENS = setOf('-', SOFT_HYPHEN, '\u2010')
        val HORIZONTAL_WHITESPACE = Regex("[\\t\\u00A0 ]+")
        val REPEATED_WHITESPACE = Regex("\\s+")
        val SPACE_BEFORE_PUNCTUATION = Regex("\\s+([,.;:!?%…])")
        val SPACE_AFTER_OPENING_PUNCTUATION = Regex("([({\\[])\\s+")
        val SPACE_BEFORE_CLOSING_PUNCTUATION = Regex("\\s+([)}\\]])")
    }
}
