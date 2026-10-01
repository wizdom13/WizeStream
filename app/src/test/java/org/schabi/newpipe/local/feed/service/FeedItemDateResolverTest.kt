package org.schabi.newpipe.local.feed.service

import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class FeedItemDateResolverTest {
    private val fetchedAt = OffsetDateTime.of(2026, 9, 8, 12, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `undated shorts receive ordered approximate first seen dates`() {
        val first = stream("https://youtube.com/shorts/first", isShort = true)
        val second = stream("https://youtube.com/shorts/second", isShort = true)

        FeedItemDateResolver.applyApproximateDates(listOf(first, second), fetchedAt)

        assertEquals(fetchedAt, first.uploadDate!!.offsetDateTime())
        assertEquals(fetchedAt.minusSeconds(1), second.uploadDate!!.offsetDateTime())
        assertTrue(first.uploadDate!!.isApproximation)
        assertTrue(second.uploadDate!!.isApproximation)
    }

    @Test
    fun `existing dates are preserved and undated regular videos receive first seen dates`() {
        val originalDate = DateWrapper(fetchedAt.minusDays(1))
        val datedShort = stream("https://youtube.com/shorts/dated", isShort = true).apply {
            uploadDate = originalDate
        }
        val regularVideo = stream("https://youtube.com/watch?v=regular", isShort = false)

        FeedItemDateResolver.applyApproximateDates(listOf(datedShort, regularVideo), fetchedAt)

        assertEquals(originalDate, datedShort.uploadDate)
        assertFalse(datedShort.uploadDate!!.isApproximation)
        assertEquals(fetchedAt, regularVideo.uploadDate!!.offsetDateTime())
        assertTrue(regularVideo.uploadDate!!.isApproximation)
        assertNull(regularVideo.textualUploadDate)
    }

    @Test
    fun `live streams remain undated`() {
        val live = stream(
            "https://youtube.com/watch?v=live",
            isShort = false,
            type = StreamType.LIVE_STREAM
        )

        FeedItemDateResolver.applyApproximateDates(listOf(live), fetchedAt)

        assertNull(live.uploadDate)
    }

    @Test
    fun `undated items from other services remain unchanged`() {
        val otherService = stream(
            "https://example.com/watch/regular",
            isShort = false,
            serviceId = 1
        )

        FeedItemDateResolver.applyApproximateDates(listOf(otherService), fetchedAt)

        assertNull(otherService.uploadDate)
    }

    @Test
    fun `regular channel dates do not invent publication dates for shorts`() {
        val newestVideo = stream("https://youtube.com/watch?v=newest", isShort = false).apply {
            uploadDate = DateWrapper(fetchedAt.minusDays(1))
        }
        val oldestVideo = stream("https://youtube.com/watch?v=oldest", isShort = false).apply {
            uploadDate = DateWrapper(fetchedAt.minusDays(11))
        }
        val firstShort = stream("https://youtube.com/shorts/first", isShort = true)
        val secondShort = stream("https://youtube.com/shorts/second", isShort = true)

        FeedItemDateResolver.applyApproximateDates(
            listOf(newestVideo, oldestVideo, firstShort, secondShort),
            fetchedAt
        )

        // These are retention timestamps, not interpolated publication dates.
        assertEquals(fetchedAt, firstShort.uploadDate!!.offsetDateTime())
        assertEquals(fetchedAt.minusSeconds(1), secondShort.uploadDate!!.offsetDateTime())
        assertTrue(firstShort.uploadDate!!.isApproximation)
        assertNull(firstShort.textualUploadDate)
        assertEquals(fetchedAt.minusDays(1), newestVideo.uploadDate!!.offsetDateTime())
    }

    private fun stream(
        url: String,
        isShort: Boolean,
        type: StreamType = StreamType.VIDEO_STREAM,
        serviceId: Int = 0
    ) = StreamInfoItem(
        serviceId,
        url,
        "Title",
        type
    ).apply {
        setShortFormContent(isShort)
    }
}
