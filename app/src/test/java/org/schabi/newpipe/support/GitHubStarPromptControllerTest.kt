package org.schabi.newpipe.support

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubStarPromptControllerTest {
    @Test
    fun promptWaitsForMeaningfulUse() {
        assertFalse(GitHubStarPromptController.shouldPrompt(11, 12, false, false))
        assertTrue(GitHubStarPromptController.shouldPrompt(12, 12, false, false))
    }

    @Test
    fun notNowCooldownIsRespected() {
        assertFalse(GitHubStarPromptController.shouldPrompt(20, 24, false, false))
        assertTrue(GitHubStarPromptController.shouldPrompt(24, 24, false, false))
    }

    @Test
    fun completedOrDismissedPromptNeverReturns() {
        assertFalse(GitHubStarPromptController.shouldPrompt(100, 12, true, false))
        assertFalse(GitHubStarPromptController.shouldPrompt(100, 12, false, true))
    }
}
