package org.schabi.newpipe.fragments.list.playlist;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BookmarkActionGuardTest {
    @Test
    public void repeatedClicksStartOnlyOneBookmarkOperation() {
        final BookmarkActionGuard guard = new BookmarkActionGuard();

        assertTrue(guard.tryStart());
        for (int i = 0; i < 1_000; i++) {
            assertFalse(guard.tryStart());
        }

        guard.finish();
        assertTrue(guard.tryStart());
    }
}
