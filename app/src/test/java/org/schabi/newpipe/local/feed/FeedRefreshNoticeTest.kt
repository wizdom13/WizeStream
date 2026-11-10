package org.schabi.newpipe.local.feed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.item.StreamItem
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ErrorResultEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.IdleEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ProgressEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.SuccessResultEvent

class FeedRefreshNoticeTest {
    @Test
    fun countsOnlyNewIdsEvenWhenPublicationDatesAreMissingOrOld() {
        val tracker = FeedRefreshNoticeTracker()
        val cached = listOf(item(1))
        tracker.update(IdleEvent, cached)
        tracker.update(ProgressEvent(0, 2), cached)
        tracker.update(ProgressEvent(1, 2), cached + item(2))
        val notice = tracker.update(SuccessResultEvent(), cached + item(2))!!
        assertFalse(notice.isNewItem(1))
        assertTrue(notice.isNewItem(2))
        assertTrue(notice.isNewItem(3)) // A final snapshot has not arrived yet.
    }

    @Test
    fun refreshWithoutNewVideosDoesNotAnnounceCachedItems() {
        val tracker = FeedRefreshNoticeTracker()
        val cached = listOf(item(1))
        tracker.update(IdleEvent, cached)
        tracker.update(ProgressEvent(0, 1), cached)
        val notice = tracker.update(SuccessResultEvent(), cached)!!
        assertFalse(notice.consume(cached.any { notice.isNewItem(it.id) }))
    }

    @Test
    fun progressNeverAnnouncesAndCancellationDoesNotCreateACompletion() {
        val tracker = FeedRefreshNoticeTracker()
        assertNull(tracker.update(IdleEvent))
        repeat(100) { assertNull(tracker.update(ProgressEvent(it, 100))) }
        assertNull(tracker.update(IdleEvent))
        assertNull(tracker.update(ErrorResultEvent(java.io.IOException("offline"))))
        assertNull(tracker.update(IdleEvent))
    }

    @Test
    fun finalSnapshotCanArriveAfterSuccessAndAnnouncesOnlyOnce() {
        val tracker = FeedRefreshNoticeTracker()
        tracker.update(ProgressEvent(1, 2))
        val success = SuccessResultEvent()
        val notice = tracker.update(success)!!
        assertFalse(notice.consume(false))
        assertSame(notice, tracker.update(IdleEvent))
        assertSame(notice, tracker.update(success))
        assertTrue(notice.consume(true))
        repeat(20) {
            assertSame(notice, tracker.update(IdleEvent))
            assertFalse(notice.consume(true))
        }
    }

    @Test
    fun subsequentRefreshGetsANewNoticeEvenWhenSuccessValuesAreEqual() {
        val tracker = FeedRefreshNoticeTracker()
        val first = tracker.update(SuccessResultEvent())!!
        assertTrue(first.consume(true))
        assertNull(tracker.update(ProgressEvent(0, 2)))
        val second = tracker.update(SuccessResultEvent())!!
        assertNotSame(first, second)
        assertTrue(second.consume(true))
        assertFalse(second.consume(true))
    }

    @Test
    fun anotherGroupHasAnIndependentCompletionNotice() {
        val first = FeedRefreshNoticeTracker().update(SuccessResultEvent())!!
        val second = FeedRefreshNoticeTracker().update(SuccessResultEvent())!!
        assertTrue(first.consume(true))
        assertTrue(second.consume(true))
    }

    private fun item(id: Long) = StreamItem(
        StreamWithState(
            StreamEntity(uid = id, serviceId = 0, url = "https://example.com/$id", title = "Video $id", streamType = StreamType.VIDEO_STREAM, duration = 60, uploader = "Channel"),
            null
        )
    )
}
