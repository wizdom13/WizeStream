package org.schabi.newpipe.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadSegmentsTest {
    @Test
    fun parsesTimestampsWithoutConfusingMinutesAndSeconds() {
        assertEquals(90_000L, DownloadSegments.parseTime("1:30"))
        assertEquals(3_723_000L, DownloadSegments.parseTime("1:02:03"))
        assertEquals(120_000L, DownloadSegments.parseTime("120"))
        assertThrows(IllegalArgumentException::class.java) { DownloadSegments.parseTime("1:60") }
        assertThrows(IllegalArgumentException::class.java) { DownloadSegments.parseTime("-1") }
    }

    @Test
    fun joinsAdjacentAndOverlappingChaptersWithoutDuplicatingMedia() {
        val ranges = listOf(DownloadSegment(20, 30), DownloadSegment(0, 10), DownloadSegment(5, 20), DownloadSegment(40, 50))
        assertEquals(listOf(DownloadSegment(0, 30), DownloadSegment(40, 50)), DownloadSegments.normalize(ranges, 60))
    }

    @Test
    fun rejectsInvalidOrOutOfBoundsSelections() {
        assertThrows(IllegalArgumentException::class.java) { DownloadSegment(10, 10) }
        assertThrows(IllegalArgumentException::class.java) { DownloadSegments.normalize(listOf(DownloadSegment(0, 20)), 10) }
        assertThrows(IllegalArgumentException::class.java) { DownloadSegments.normalize(listOf(DownloadSegment(0, 20)), 0) }
    }

    @Test
    fun preservesSelectedRangesAcrossMissionAndDialogRestoration() {
        val ranges = listOf(DownloadSegment(1000, 4000), DownloadSegment(10000, 15000))
        assertEquals(ranges, DownloadSegments.decode(DownloadSegments.encode(ranges)))
    }
}
