package org.schabi.newpipe.local.feed.service

import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.TestScheduler
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedExtractionPlannerTest {

    @Test
    fun `dedicated feeds use more workers than full channel extraction`() {
        assertEquals(5, FeedExtractionPlanner.DEDICATED_FEED_PARALLELISM)
        assertEquals(3, FeedExtractionPlanner.FULL_CHANNEL_PARALLELISM)
    }

    @Test
    fun `only full YouTube channel extraction is throttled`() {
        assertTrue(
            FeedExtractionPlanner.isFullYouTubeExtraction(
                isYouTube = true,
                hasDedicatedFeedExtractor = false,
                feedExtractorLookupFailed = false
            )
        )
        assertFalse(
            FeedExtractionPlanner.isFullYouTubeExtraction(
                isYouTube = true,
                hasDedicatedFeedExtractor = true,
                feedExtractorLookupFailed = false
            )
        )
        assertFalse(
            FeedExtractionPlanner.isFullYouTubeExtraction(
                isYouTube = false,
                hasDedicatedFeedExtractor = false,
                feedExtractorLookupFailed = false
            )
        )
        assertFalse(
            FeedExtractionPlanner.isFullYouTubeExtraction(
                isYouTube = true,
                hasDedicatedFeedExtractor = false,
                feedExtractorLookupFailed = true
            )
        )
    }

    @Test
    fun `exactly fifty full YouTube requests do not create a trailing delay`() {
        val requests = List(50) { Request(isFullYouTubeExtraction = true) }

        val batches = FeedExtractionPlanner.batchFullExtractions(requests) {
            it.isFullYouTubeExtraction
        }

        assertEquals(listOf(requests), batches)
    }

    @Test
    fun `fifty first full YouTube request starts the next batch`() {
        val requests = List(51) { Request(isFullYouTubeExtraction = true) }

        val batches = FeedExtractionPlanner.batchFullExtractions(requests) {
            it.isFullYouTubeExtraction
        }

        assertEquals(listOf(50, 1), batches.map { it.size })
    }

    @Test
    fun `non YouTube full requests do not count toward throttling`() {
        val firstYouTubeBatch = List(50) { Request(isFullYouTubeExtraction = true) }
        val otherServices = List(3) { Request(isFullYouTubeExtraction = false) }
        val nextYouTubeRequest = Request(isFullYouTubeExtraction = true)
        val requests = firstYouTubeBatch + otherServices + nextYouTubeRequest

        val batches = FeedExtractionPlanner.batchFullExtractions(requests) {
            it.isFullYouTubeExtraction
        }

        assertEquals(listOf(53, 1), batches.map { it.size })
        assertTrue(batches.first().takeLast(3).none { it.isFullYouTubeExtraction })
    }

    @Test
    fun `batching preserves every request in its randomized order`() {
        val requests = List(127) { index ->
            Request(isFullYouTubeExtraction = index % 2 == 0)
        }

        val batches = FeedExtractionPlanner.batchFullExtractions(requests) {
            it.isFullYouTubeExtraction
        }

        assertEquals(requests, batches.flatten())
    }

    @Test
    fun `empty request list creates no batches`() {
        val batches = FeedExtractionPlanner.batchFullExtractions(emptyList<Request>()) {
            it.isFullYouTubeExtraction
        }

        assertTrue(batches.isEmpty())
    }

    @Test
    fun `batch delay completes immediately when loading is cancelled`() {
        val scheduler = TestScheduler()
        val cancelNotifier = BehaviorProcessor.createDefault(false)
        var cancelled = false
        val observer = FeedExtractionPlanner.cancellableDelay(
            delayMillis = 1000,
            cancelNotifier = cancelNotifier,
            scheduler = scheduler,
            isCancelled = { cancelled }
        ).test()

        scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS)
        observer.assertNotComplete()

        cancelled = true
        cancelNotifier.onNext(true)
        observer.assertComplete()
    }

    @Test
    fun `batch delay completes normally when loading continues`() {
        val scheduler = TestScheduler()
        val observer = FeedExtractionPlanner.cancellableDelay(
            delayMillis = 1000,
            cancelNotifier = BehaviorProcessor.createDefault(false),
            scheduler = scheduler,
            isCancelled = { false }
        ).test()

        scheduler.advanceTimeBy(999, TimeUnit.MILLISECONDS)
        observer.assertNotComplete()
        scheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS)
        observer.assertComplete()
    }

    private data class Request(val isFullYouTubeExtraction: Boolean)
}
