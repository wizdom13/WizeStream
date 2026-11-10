package org.schabi.newpipe.local.feed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.item.StreamItem
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event

class FeedRefreshCompletionTest {
    private val original = listOf(item(1))
    private val refreshed = original + item(2)

    @Test
    fun progressiveSnapshotsRemainQuietAndLateResultsAnnounceOnce() {
        val tracker = FeedRefreshCompletions()
        tracker.update(Event.IdleEvent, original)
        assertNull(tracker.update(Event.ProgressEvent(), refreshed))

        val success = Event.SuccessResultEvent()
        val completion = requireNotNull(tracker.update(success, original))
        assertFalse(completion.announceIfNew(original))
        assertSame(completion, tracker.update(success, refreshed))
        assertSame(completion, tracker.update(Event.IdleEvent, refreshed))
        assertTrue(completion.announceIfNew(refreshed))
        assertFalse(completion.announceIfNew(refreshed))
    }

    @Test
    fun nextRefreshCanAnnounceAndFailedRefreshClearsItsCompletion() {
        val tracker = FeedRefreshCompletions()
        tracker.update(Event.IdleEvent, original)
        tracker.update(Event.ProgressEvent(), original)
        val first = requireNotNull(tracker.update(Event.SuccessResultEvent(), refreshed))
        assertTrue(first.announceIfNew(refreshed))

        val latest = refreshed + item(3)
        assertNull(tracker.update(Event.ProgressEvent(), latest))
        val second = requireNotNull(tracker.update(Event.SuccessResultEvent(), latest))
        assertTrue(second.announceIfNew(latest))
        assertNull(tracker.update(Event.ErrorResultEvent(IllegalStateException()), null))
    }

    @Test
    fun highlightChangesArePartOfRowContent() {
        val row = item(1)
        assertTrue(row.hasSameContentAs(row.copy()))
        assertFalse(row.hasSameContentAs(row.copy(isHighlighted = false)))
    }

    private fun item(id: Long) = StreamItem(
        StreamWithState(
            StreamEntity(
                uid = id,
                serviceId = 0,
                url = "https://example.org/$id",
                title = "Video $id",
                streamType = StreamType.VIDEO_STREAM,
                duration = 30,
                uploader = "Channel"
            ),
            null
        ),
        isHighlighted = true
    )
}
