package org.schabi.newpipe.local.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedProgressStateTest {
    @Test
    fun progressStateCarriesCurrentChannelDescription() {
        val state = FeedState.ProgressState(
            currentProgress = 38,
            maxProgress = 100,
            updateDescription = "Example Human"
        )

        assertEquals(38, state.currentProgress)
        assertEquals(100, state.maxProgress)
        assertEquals("Example Human", state.updateDescription)
    }
}
