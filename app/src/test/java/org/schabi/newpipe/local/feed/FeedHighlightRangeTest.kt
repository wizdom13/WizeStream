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
        val positions = feedHighlightPositions(listOf(unknown, known, old, known, unknown), updatedAt)

        assertEquals(setOf(1, 3), positions)
        // Rebind through the final highlighted item, not merely the number of highlights.
        assertEquals(4, calculateFeedHighlightRebindCount(0, positions.maxOrNull()!! + 1, 5))
        assertEquals(4, calculateFeedHighlightRebindCount(4, 0, 5))
    }

    @Test
    fun emptyFeedDoesNotProduceAnInvalidRange() {
        assertEquals(0, calculateFeedHighlightRebindCount(1, 0, 0))
    }

    @Test
    fun rebindCountClearsStaleHighlightsAfterTheListShrinks() {
        assertEquals(3, calculateFeedHighlightRebindCount(5, 2, 3))
    }

    @Test
    fun rebindCountIncludesAllNewHighlights() {
        assertEquals(5, calculateFeedHighlightRebindCount(2, 5, 8))
    }

    @Test
    fun unchangedUnhighlightedFeedNeedsNoRebind() {
        assertEquals(0, calculateFeedHighlightRebindCount(0, 0, 8))
    }
}
