package org.schabi.newpipe.local.feed

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedHighlightBaselineTest {
    private val before = OffsetDateTime.parse("2026-11-01T10:00:00Z")
    private val during = before.plusHours(1)
    private val finished = before.plusHours(2)

    @Test
    fun `metadata and final snapshots cannot advance the highlight cutoff`() {
        val baseline = FeedHighlightBaseline()
        assertNull(baseline.update(before, false))
        assertEquals(before, baseline.update(before, true))
        // Metadata can be emitted before a delayed item snapshot.
        assertEquals(before, baseline.update(during, true))
        assertEquals(before, baseline.update(finished, true))
        assertEquals(before, baseline.update(finished, false))
        // Idle replay, sorting and reactivation must retain the same highlights.
        assertEquals(before, baseline.update(finished, false))
        assertEquals(finished, baseline.update(finished, true))
    }

    @Test
    fun `opening during refresh uses the first available timestamp`() {
        val baseline = FeedHighlightBaseline()
        assertEquals(before, baseline.update(before, true))
        assertEquals(before, baseline.update(finished, true))
    }

    @Test
    fun `missing initial timestamp stays missing until the next refresh`() {
        val baseline = FeedHighlightBaseline()
        assertNull(baseline.update(null, false))
        assertNull(baseline.update(during, true))
        assertNull(baseline.update(finished, false))
        assertEquals(finished, baseline.update(finished, true))
    }

    @Test
    fun `cancelled refresh keeps baseline and next refresh captures updated timestamp`() {
        val baseline = FeedHighlightBaseline()
        baseline.update(before, false)
        baseline.update(before, true)
        assertEquals(before, baseline.update(during, false))
        assertEquals(during, baseline.update(during, true))
    }
}
