package org.schabi.newpipe.support;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GitHubStarPromptControllerTest {
    @Test
    public void promptWaitsForMeaningfulUse() {
        assertFalse(GitHubStarPromptController.shouldPrompt(11, 12, false, false));
        assertTrue(GitHubStarPromptController.shouldPrompt(12, 12, false, false));
    }

    @Test
    public void notNowCooldownIsRespected() {
        assertFalse(GitHubStarPromptController.shouldPrompt(20, 24, false, false));
        assertTrue(GitHubStarPromptController.shouldPrompt(24, 24, false, false));
    }

    @Test
    public void completedOrDismissedPromptNeverReturns() {
        assertFalse(GitHubStarPromptController.shouldPrompt(100, 12, true, false));
        assertFalse(GitHubStarPromptController.shouldPrompt(100, 12, false, true));
    }
}
