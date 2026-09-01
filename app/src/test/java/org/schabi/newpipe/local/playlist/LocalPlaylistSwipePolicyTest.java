package org.schabi.newpipe.local.playlist;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocalPlaylistSwipePolicyTest {
    @Test
    void pinnedPlaylistLeavesHorizontalSwipesToTheMainPager() {
        assertFalse(LocalPlaylistSwipePolicy.isItemRemovalSwipeEnabled(true));
    }

    @Test
    void standalonePlaylistKeepsSwipeToRemove() {
        assertTrue(LocalPlaylistSwipePolicy.isItemRemovalSwipeEnabled(false));
    }
}
