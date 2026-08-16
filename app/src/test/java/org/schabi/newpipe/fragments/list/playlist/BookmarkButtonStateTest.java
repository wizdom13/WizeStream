package org.schabi.newpipe.fragments.list.playlist;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BookmarkButtonStateTest {
    @Test
    public void storedBookmarkCanBeRemovedBeforePlaylistInfoLoads() {
        assertTrue(BookmarkButtonState.isEnabled(true, true, false, false));
    }

    @Test
    public void addingBookmarkWaitsForPlaylistInfo() {
        assertFalse(BookmarkButtonState.isEnabled(true, false, false, false));
        assertTrue(BookmarkButtonState.isEnabled(true, false, true, false));
    }

    @Test
    public void lookupAndRunningActionDisableButton() {
        assertFalse(BookmarkButtonState.isEnabled(false, true, true, false));
        assertFalse(BookmarkButtonState.isEnabled(true, true, true, true));
    }
}
