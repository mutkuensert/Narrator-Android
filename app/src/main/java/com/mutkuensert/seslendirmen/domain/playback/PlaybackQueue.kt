package com.mutkuensert.seslendirmen.domain.playback

import com.mutkuensert.seslendirmen.domain.model.SpeechChunk

class PlaybackQueue(chunks: List<SpeechChunk>) {
    private val items = chunks.toList()
    var index: Int = 0
        private set

    val current: SpeechChunk?
        get() = items.getOrNull(index)

    val currentIndex: Int
        get() = index

    val size: Int
        get() = items.size

    operator fun get(itemIndex: Int): SpeechChunk? = items.getOrNull(itemIndex)

    fun moveNext(): SpeechChunk? {
        if (index + 1 >= items.size) return null
        index++
        return current
    }

    fun movePrevious(): SpeechChunk? {
        if (index <= 0) return null
        index--
        return current
    }

    fun moveTo(chunkId: Long): SpeechChunk? {
        val targetIndex = items.indexOfFirst { it.id == chunkId }
        if (targetIndex < 0) return null
        index = targetIndex
        return current
    }

    fun reset(): SpeechChunk? {
        index = 0
        return current
    }
}
