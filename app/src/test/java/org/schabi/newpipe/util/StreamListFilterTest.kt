package org.schabi.newpipe.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class StreamListFilterTest {
    @Test
    fun `unwatched accepts only streams without valid progress`() {
        val stream = stream(duration = 600)

        assertTrue(StreamListFilter.matches(StreamListFilter.UNWATCHED, stream, null))
        assertFalse(
            StreamListFilter.matches(
                StreamListFilter.UNWATCHED,
                stream,
                StreamStateEntity(1, 120_000)
            )
        )
    }

    @Test
    fun `partially watched excludes finished streams`() {
        val stream = stream(duration = 600)

        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.PARTIALLY_WATCHED,
                stream,
                StreamStateEntity(1, 120_000)
            )
        )
        assertFalse(
            StreamListFilter.matches(
                StreamListFilter.PARTIALLY_WATCHED,
                stream,
                StreamStateEntity(1, 590_000)
            )
        )
    }

    @Test
    fun `live uses extractor stream types`() {
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.LIVE,
                stream(type = StreamType.LIVE_STREAM),
                null
            )
        )
        assertFalse(
            StreamListFilter.matches(
                StreamListFilter.LIVE,
                stream(type = StreamType.VIDEO_STREAM),
                null
            )
        )
    }

    @Test
    fun `shorts accepts explicit shorts urls and videos up to three minutes`() {
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.SHORTS,
                stream(duration = 180),
                null
            )
        )
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.SHORTS,
                stream(url = "https://www.youtube.com/shorts/abcdefghijk", duration = -1),
                null
            )
        )
        assertFalse(
            StreamListFilter.matches(
                StreamListFilter.SHORTS,
                stream(duration = 181),
                null
            )
        )
    }

    private fun stream(
        url: String = "https://example.com/watch/video",
        duration: Long = 60,
        type: StreamType = StreamType.VIDEO_STREAM
    ) = StreamInfoItem(0, url, "Title", type).apply {
        this.duration = duration
    }
}
