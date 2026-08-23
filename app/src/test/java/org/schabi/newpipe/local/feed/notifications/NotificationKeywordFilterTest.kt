/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class NotificationKeywordFilterTest {
    @Test
    fun `normalization trims removes blank lines and deduplicates ignoring case`() {
        assertEquals(
            "07K\nVW 2.5L\n5-cylinder",
            NotificationKeywordFilter.normalize(
                " 07K \n\nVW 2.5L\n07k\n 5-cylinder "
            )
        )
    }

    @Test
    fun `phrases match titles case insensitively with any-term semantics`() {
        val filters = "07K\nVW 2.5L"

        assertTrue(NotificationKeywordFilter.matches("Why the vw 2.5l is reliable", filters))
        assertTrue(NotificationKeywordFilter.matches("Rebuilding an 07k Passat", filters))
        assertFalse(NotificationKeywordFilter.matches("Fixing a BMW E46", filters))
    }

    @Test
    fun `keyword mode filters only notification candidates`() {
        val matching = stream("Why the VW 2.5L is reliable")
        val unrelated = stream("Fixing a Ford Focus")

        assertEquals(
            listOf(matching),
            NotificationKeywordFilter.filter(
                listOf(matching, unrelated),
                NotificationMode.KEYWORDS_ONLY,
                "07K\nVW 2.5L"
            )
        )
        assertEquals(
            listOf(matching, unrelated),
            NotificationKeywordFilter.filter(
                listOf(matching, unrelated),
                NotificationMode.ENABLED,
                "07K"
            )
        )
        assertTrue(
            NotificationKeywordFilter.filter(
                listOf(matching),
                NotificationMode.DISABLED,
                "07K"
            ).isEmpty()
        )
    }

    @Test
    fun `keyword-only filters reject empty and oversized input`() {
        assertFalse(NotificationKeywordFilter.isValid(""))
        assertFalse(NotificationKeywordFilter.isValid("a".repeat(101)))
        assertFalse(
            NotificationKeywordFilter.isValid(
                (1..26).joinToString("\n") { "term-$it" }
            )
        )
        assertTrue(NotificationKeywordFilter.isValid("engine\ntransmission"))
    }

    private fun stream(title: String) = StreamInfoItem(
        0,
        "https://example.com/${title.hashCode()}",
        title,
        StreamType.VIDEO_STREAM
    )
}
