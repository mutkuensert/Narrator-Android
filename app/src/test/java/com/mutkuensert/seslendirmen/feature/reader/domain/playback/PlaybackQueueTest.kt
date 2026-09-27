package com.mutkuensert.seslendirmen.feature.reader.domain.playback

import com.mutkuensert.seslendirmen.feature.reader.domain.model.SpeechChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueTest {
    private val chunks = (0L..2L).map { id ->
        SpeechChunk(id = id, sectionIndex = 1, paragraphIndex = id.toInt(), text = "Chunk $id")
    }

    @Test
    fun `moves forward and does not pass end`() {
        val queue = PlaybackQueue(chunks)

        assertEquals(0L, queue.current?.id)
        assertEquals(1L, queue.moveNext()?.id)
        assertEquals(2L, queue.moveNext()?.id)
        assertNull(queue.moveNext())
        assertEquals(2L, queue.current?.id)
    }

    @Test
    fun `moves backward and does not pass beginning`() {
        val queue = PlaybackQueue(chunks)
        queue.moveNext()

        assertEquals(0L, queue.movePrevious()?.id)
        assertNull(queue.movePrevious())
        assertEquals(0L, queue.current?.id)
    }

    @Test
    fun `reset returns to first chunk`() {
        val queue = PlaybackQueue(chunks)
        queue.moveNext()
        queue.moveNext()

        assertEquals(0L, queue.reset()?.id)
        assertEquals(0, queue.currentIndex)
    }

    @Test
    fun `moves directly to chunk by id`() {
        val queue = PlaybackQueue(chunks)

        assertEquals(2L, queue.moveTo(2L)?.id)
        assertEquals(2, queue.currentIndex)
    }

    @Test
    fun `unknown chunk id leaves current position unchanged`() {
        val queue = PlaybackQueue(chunks)
        queue.moveNext()

        assertNull(queue.moveTo(99L))
        assertEquals(1L, queue.current?.id)
    }

    @Test
    fun `empty queue remains safe`() {
        val queue = PlaybackQueue(emptyList())

        assertNull(queue.current)
        assertNull(queue.moveNext())
        assertNull(queue.movePrevious())
        assertNull(queue.reset())
    }
}
