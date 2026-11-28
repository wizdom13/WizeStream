package org.schabi.newpipe.local.feed

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

class FeedHighlightRangeTest {
    @Test
    fun discoveryOrderCanHighlightNewVideosAfterUndatedAndOlderItems() {
        val updatedAt = OffsetDateTime.parse("2026-09-30T12:00:00Z")
        val known = StreamEntity(
            serviceId = 0, url = "https://youtube.com/watch?v=known", title = "Known video",
            streamType = StreamType.VIDEO_STREAM, duration = 120, uploader = "Channel",
            uploadDate = updatedAt.plusMinutes(1), isUploadDateApproximation = true,
            textualUploadDate = "1 minute ago"
        )
        val unknown = known.copy(textualUploadDate = null)
        val old = known.copy(uploadDate = updatedAt.minusDays(1))
        val streams = listOf(unknown, known, old, known, unknown)

        assertEquals(listOf(1, 3), streams.indices.filter { shouldHighlightFeedStream(streams[it], updatedAt) })
    }

    @Test
    fun undatedPluginUploadsAreNotHighlightedAsNewPublications() {
        val updatedAt = OffsetDateTime.parse("2026-11-28T12:00:00Z")
        val unknown = StreamEntity(
            serviceId = 1201,
            url = "https://example.com/video/fixture",
            title = "Fixture video",
            streamType = StreamType.VIDEO_STREAM,
            duration = 120,
            uploader = "Fixture channel"
        )
        val entries = listOf(
            unknown,
            unknown.copy(serviceId = 1202, streamType = StreamType.AUDIO_STREAM),
            unknown.copy(uploadDate = updatedAt.plusMinutes(1)),
            unknown.copy(uploadDate = updatedAt.minusMinutes(1)),
            unknown.copy(streamType = StreamType.LIVE_STREAM),
            unknown.copy(streamType = StreamType.AUDIO_LIVE_STREAM),
            unknown.copy(upcomingStatus = true)
        )
        assertEquals(listOf(2, 4, 5, 6), entries.indices.filter { shouldHighlightFeedStream(entries[it], updatedAt) })
    }
}
