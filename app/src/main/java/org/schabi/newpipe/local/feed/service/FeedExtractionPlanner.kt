package org.schabi.newpipe.local.feed.service

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Scheduler
import java.util.concurrent.TimeUnit

internal object FeedExtractionPlanner {
    const val DEDICATED_FEED_PARALLELISM = 5
    const val FULL_CHANNEL_PARALLELISM = 3
    private const val YOUTUBE_FULL_EXTRACTION_BATCH_SIZE = 50

    fun isFullYouTubeExtraction(
        isYouTube: Boolean,
        hasDedicatedFeedExtractor: Boolean,
        feedExtractorLookupFailed: Boolean
    ): Boolean = isYouTube && !hasDedicatedFeedExtractor && !feedExtractorLookupFailed

    fun cancellableDelay(
        delayMillis: Long,
        cancelNotifier: Flowable<Boolean>,
        scheduler: Scheduler,
        isCancelled: () -> Boolean
    ): Completable = Flowable.timer(delayMillis, TimeUnit.MILLISECONDS, scheduler)
        .takeUntil(cancelNotifier.filter { it })
        .filter { !isCancelled() }
        .ignoreElements()

    /**
     * Split full-channel requests immediately before the next YouTube request after each batch.
     * Non-YouTube requests do not count toward the limit and remain in the current batch.
     */
    fun <T> batchFullExtractions(
        requests: List<T>,
        isFullYouTubeExtraction: (T) -> Boolean
    ): List<List<T>> {
        if (requests.isEmpty()) {
            return emptyList()
        }

        val batches = mutableListOf<MutableList<T>>()
        var currentBatch = mutableListOf<T>()
        var youtubeExtractionsInBatch = 0

        requests.forEach { request ->
            if (isFullYouTubeExtraction(request) &&
                youtubeExtractionsInBatch == YOUTUBE_FULL_EXTRACTION_BATCH_SIZE
            ) {
                batches += currentBatch
                currentBatch = mutableListOf()
                youtubeExtractionsInBatch = 0
            }

            currentBatch += request
            if (isFullYouTubeExtraction(request)) {
                youtubeExtractionsInBatch++
            }
        }

        batches += currentBatch
        return batches
    }
}
