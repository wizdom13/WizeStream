package org.schabi.newpipe.util

import java.time.OffsetDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
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

    @Test
    fun `history entries support every stream filter using saved progress`() {
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.UNWATCHED,
                historyEntry(progressMillis = 0)
            )
        )
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.LIVE,
                historyEntry(type = StreamType.LIVE_STREAM)
            )
        )
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.SHORTS,
                historyEntry(duration = 120)
            )
        )
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.PARTIALLY_WATCHED,
                historyEntry(duration = 600, progressMillis = 120_000)
            )
        )
        assertFalse(
            StreamListFilter.matches(
                StreamListFilter.PARTIALLY_WATCHED,
                historyEntry(duration = 600, progressMillis = 590_000)
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

    private fun historyEntry(
        url: String = "https://example.com/watch/video",
        duration: Long = 600,
        type: StreamType = StreamType.VIDEO_STREAM,
        progressMillis: Long = 0
    ) = StreamStatisticsEntry(
        streamEntity = StreamEntity(
            serviceId = 0,
            url = url,
            title = "Title",
            streamType = type,
            duration = duration,
            uploader = "Uploader"
        ),
        progressMillis = progressMillis,
        streamId = 1,
        latestAccessDate = OffsetDateTime.parse("2026-08-21T12:00:00Z"),
        watchCount = 1
    )
}
