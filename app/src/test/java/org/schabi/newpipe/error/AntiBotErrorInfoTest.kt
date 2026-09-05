package org.schabi.newpipe.error

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.AntiBotException

class AntiBotErrorInfoTest {
    @Test
    fun antiBotBlocksAreActionableButNotReportable() {
        val error = ErrorInfo(
            AntiBotException("Sign in to confirm you're not a bot"),
            UserAction.REQUESTED_STREAM,
            "https://www.youtube.com/watch?v=test",
            ServiceList.YouTube.serviceId
        )

        assertFalse(error.isReportable)
        assertTrue(error.isRetryable)
    }
}
