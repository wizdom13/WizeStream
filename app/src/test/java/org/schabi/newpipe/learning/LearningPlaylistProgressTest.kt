package org.schabi.newpipe.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningPlaylistProgressTest {
    @Test
    fun `counts only known durations and preserves duplicate entries`() {
        val progress = LearningPlaylistProgress.calculateValues(
            listOf(
                100L to 100_000L,
                100L to 75_000L,
                100L to 20_000L,
                -1L to 999_000L
            )
        )

        assertEquals(2, progress.completed)
        assertEquals(3, progress.eligible)
        assertEquals(66, progress.percentage)
    }

    @Test
    fun `requires both seventy five percent and no more than sixty seconds remaining`() {
        val progress = LearningPlaylistProgress.calculateValues(
            listOf(
                1_000L to 750_000L,
                100L to 40_000L
            )
        )

        assertEquals(0, progress.completed)
    }
}
